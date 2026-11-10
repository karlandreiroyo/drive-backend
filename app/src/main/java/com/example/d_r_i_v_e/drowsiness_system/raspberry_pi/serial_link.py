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
        timeout=0.1,
        write_timeout=0.5,
        reconnect_delay=2.0,
        boot_wait=2.0,
        ping_interval=1.0,
        pong_timeout=3.0,
        logger=None,
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

        self._outbox = queue.Queue()   # commands waiting to be written
        self._inbox = queue.Queue()    # status lines waiting to be displayed
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread = None

        self._connected = False
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
    def handshake_ok(self):
        """True when the port is open AND a PONG arrived recently."""
        with self._lock:
            if not self._connected:
                return False
            return (time.monotonic() - self._last_pong) <= self.pong_timeout

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
        last_attempt = 0.0
        last_ping = 0.0
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
                        last_ping = 0.0      # send a PING immediately
                        buffer = ""
                        self._log(f"Serial port opened: {self.port}")
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

                # 3) Read whatever has arrived (non-blocking) and split lines.
                waiting = ser.in_waiting
                if waiting:
                    buffer += ser.read(waiting).decode("utf-8", errors="replace")
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
        try:
            ser = serial.Serial(
                self.port,
                self.baud,
                timeout=self.timeout,
                write_timeout=self.write_timeout,
            )
        except (serial.SerialException, OSError) as exc:
            self._log(f"Could not open {self.port}: {exc}")
            return None

        # Opening the port toggles DTR/RTS and resets most ESP32 dev boards.
        # Wait for the sketch to boot, then discard its boot banner/noise so the
        # first line we parse is a clean response to our PING.
        self._stop.wait(self.boot_wait)
        try:
            ser.reset_input_buffer()
            ser.reset_output_buffer()
        except (serial.SerialException, OSError):
            pass
        return ser

    def _write(self, ser, command):
        ser.write((command + "\n").encode("utf-8"))
        ser.flush()

    def _handle_line(self, line):
        if line == "PONG":
            with self._lock:
                self._last_pong = time.monotonic()
        else:
            # READY / ALERT_ON / ALERT_OFF / FAILSAFE_TIMEOUT / anything else.
            self._inbox.put(line)

    def _set_connected(self, value):
        with self._lock:
            self._connected = value

    @staticmethod
    def _close(ser):
        if ser is not None:
            try:
                ser.close()
            except (serial.SerialException, OSError):
                pass