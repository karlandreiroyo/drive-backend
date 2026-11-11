"""USB serial link to the ESP32 with auto-reconnect and a PING/PONG heartbeat.

Design notes
------------
* All serial I/O runs on a single background daemon thread that *owns* the
  pyserial object. The camera/detection loop in main.py therefore never blocks,
  even while the ESP32 is unplugged, rebooting, or the port is being reopened.
* main.py talks to the link through three thread-safe methods:
    - send(command)  -> queue an outgoing line (e.g. "DROWSY_ALERT")
    - update()       -> drain incoming status lines ("READY"/"ALERT_ON"/...)
    - handshake_ok   -> True when a PONG was received within PONG_TIMEOUT
* "connected" means the OS port is open; "handshake_ok" means the ESP32 is
  actually answering. The video overlay uses handshake_ok, so a port that is
  open but silent still reads as DISCONNECTED.

The wire protocol is newline-terminated ASCII:
    Pi -> ESP32 : PING | DROWSY_ALERT | CLEAR_ALERT
    ESP32 -> Pi : PONG | READY | ALERT_ON | ALERT_OFF | FAILSAFE_TIMEOUT
"""

import queue
import threading
import time

import serial  # pyserial


class SerialLink:
    def __init__(
        self,
        port,
        baud,
        *,
        timeout=0,
        write_timeout=0.5,
        reconnect_delay=2.0,
        boot_wait=2.0,
        ping_interval=1.0,
        pong_timeout=3.0,
        logger=None,
        dtr=False,
        rts=False,
        reset_on_connect=False,
    ):
        self.port = port
        self.baud = baud
        self.timeout = timeout
        self.write_timeout = write_timeout
        self.reconnect_delay = reconnect_delay
        self.boot_wait = boot_wait
        self.ping_interval = ping_interval
        self.pong_timeout = pong_timeout
        self._log = logger or (lambda _msg: None)
        self.dtr = dtr
        self.rts = rts
        self.reset_on_connect = reset_on_connect
        self._sent_pings = 0
        self._received_bytes = 0

        self._outbox = queue.Queue()   # commands waiting to be written
        self._inbox = queue.Queue()    # status lines waiting to be displayed
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread = None

        self._connected = False
        self._connection_generation = 0
        self._last_pong = 0.0          # monotonic timestamp of the last PONG

    # ------------------------------------------------------------------ #
    # Public API (called from the main/detection thread)
    # ------------------------------------------------------------------ #
    def start(self):
        """Start the background I/O thread. Safe to call once."""
        if self._thread is not None:
            return
        self._thread = threading.Thread(target=self._run, name="serial-link", daemon=True)
        self._thread.start()

    def send(self, command):
        """Queue a command (newline is added automatically). Non-blocking."""
        command = command.strip()
        if command:
            self._outbox.put(command)

    def update(self):
        """Return a list of status lines received since the last call."""
        messages = []
        while True:
            try:
                messages.append(self._inbox.get_nowait())
            except queue.Empty:
                break
        return messages

    @property
    def connected(self):
        """True when the serial port is currently open."""
        with self._lock:
            return self._connected

    @property
    def connection_generation(self):
        """Changes on port transitions, including reconnects between polls."""
        with self._lock:
            return self._connection_generation

    @property
    def handshake_ok(self):
        """True when the port is open AND a nonzero, fresh PONG was received."""
        with self._lock:
            if not self._connected or not self._last_pong:
                return False
            return (time.monotonic() - self._last_pong) <= self.pong_timeout

    @property
    def diagnostics(self):
        with self._lock:
            return f"PINGs sent={self._sent_pings}, bytes received={self._received_bytes}"

    def stop(self):
        """Signal the worker to close the port and exit, then wait for it."""
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=self.boot_wait + 3.0)
            self._thread = None

    # ------------------------------------------------------------------ #
    # Background worker (owns the serial object)
    # ------------------------------------------------------------------ #
    def _run(self):
        ser = None
        last_attempt = float("-inf")
        last_ping = float("-inf")
        buffer = ""

        while not self._stop.is_set():
            # (Re)connect when we have no open port.
            if ser is None:
                self._set_connected(False)
                now = time.monotonic()
                if now - last_attempt >= self.reconnect_delay:
                    last_attempt = now
                    ser = self._open_port()
                    if ser is not None:
                        self._set_connected(True)
                        last_ping = float("-inf")
                        buffer = ""
                        self._log(f"Serial port opened: {self.port} (8N1, DTR={self.dtr}, RTS={self.rts}, flow control off)")
                if ser is None:
                    self._stop.wait(0.1)
                    continue

            try:
                # 1) Flush any queued commands.
                while True:
                    try:
                        command = self._outbox.get_nowait()
                    except queue.Empty:
                        break
                    self._write(ser, command)

                # 2) Heartbeat: PING on a fixed interval.
                now = time.monotonic()
                if now - last_ping >= self.ping_interval:
                    last_ping = now
                    self._write(ser, "PING")

                # 3) Read whatever has arrived (non-blocking via timeout=0) and split lines.
                # Avoid relying solely on ser.in_waiting which can return 0 on Windows COM ports (CH340/CP210x).
                data = ser.read(4096)
                if data:
                    buffer += data.decode("utf-8", errors="replace")
                    while "\n" in buffer:
                        line, buffer = buffer.split("\n", 1)
                        line = line.strip()
                        if line:
                            self._handle_line(line)
                    # Guard against a runaway peer that never sends a newline.
                    if len(buffer) > 4096:
                        buffer = ""

            except (serial.SerialException, OSError) as exc:
                # Port was unplugged or became unusable: drop it and reconnect.
                self._log(f"Serial link lost: {exc}")
                self._close(ser)
                ser = None
                self._set_connected(False)
                continue

            # Small sleep so we do not busy-spin; keeps PING timing accurate enough.
            self._stop.wait(0.02)

        # Worker is shutting down: make sure the port is released.
        self._close(ser)
        self._set_connected(False)

    def _open_port(self):
        ser = None
        try:
            ser = serial.Serial(
                port=None,
                baudrate=self.baud,
                bytesize=serial.EIGHTBITS,
                parity=serial.PARITY_NONE,
                stopbits=serial.STOPBITS_ONE,
                timeout=self.timeout,
                write_timeout=self.write_timeout,
                xonxoff=False,
                rtscts=False,
                dsrdtr=False,
            )
            # Set control lines before open so default assertions cannot hold reset/boot pins.
            ser.dtr = self.dtr
            ser.rts = self.rts
            ser.port = self.port
            ser.open()
            if self.reset_on_connect:
                # Pulse RTS/DTR to reset ESP32 board into flash boot mode
                ser.rts = True
                ser.dtr = True
                time.sleep(0.1)
                ser.rts = self.rts
                ser.dtr = self.dtr
                time.sleep(0.1)
            ser.reset_input_buffer()
            ser.reset_output_buffer()
            if self._stop.wait(self.boot_wait):
                self._close(ser)
                return None
            return ser
        except (serial.SerialException, OSError) as exc:
            self._close(ser)
            self._log(f"Could not open {self.port}: {exc}")
            self._log(
                "If the port is busy or access is denied, close Arduino Serial "
                "Monitor/Plotter and any other program using this port."
            )
            return None

    def _write(self, ser, command):
        data = (command + "\n").encode("utf-8")
        if ser.write(data) != len(data):
            raise serial.SerialException("Incomplete serial write")

    def _handle_line(self, line):
        if line == "PONG":
            now = time.monotonic()
            with self._lock:
                first_pong = not self._last_pong or now - self._last_pong > self.pong_timeout
                self._last_pong = now
            if first_pong:
                self._log("ESP32 PONG received")
        else:
            if line == "READY":
                self._log("ESP32 READY received")
            self._inbox.put(line)

    def _set_connected(self, value):
        with self._lock:
            if self._connected != value:
                self._connection_generation += 1
            self._connected = value
            if not value:
                # A reopened port must earn its own handshake and acknowledgements.
                self._last_pong = 0.0
                self.update()

    @staticmethod
    def _close(ser):
        if ser is not None:
            try:
                ser.close()
            except (serial.SerialException, OSError):
                pass