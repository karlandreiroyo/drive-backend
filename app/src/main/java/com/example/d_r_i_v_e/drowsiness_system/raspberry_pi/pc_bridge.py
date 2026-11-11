"""Headless USB ESP32 status bridge. No camera, detector, or Pi configuration."""

import argparse
from dataclasses import dataclass, field
import getpass
import os
import sys
import time
from urllib.parse import urlsplit
import warnings

from backend_link import BackendLink


DEFAULT_BASE_URL = "https://drive-backend.up.railway.app"
CHECK_TIMEOUT = 10.0
POLL_INTERVAL = 0.1


@dataclass(frozen=True)
class BridgeOptions:
    port: str
    baud: int = 115200
    check: bool = False
    base_url: str = ""
    user_id: str = field(default="", repr=False)
    device_key: str = field(default="", repr=False)
    dtr: bool = False
    rts: bool = False
    key_source: str = ""
    reset: bool = False


def validate_base_url(value):
    """Allow HTTPS, or explicit loopback HTTP for local testing only."""
    value = value.strip()
    if not value or any(ord(char) <= 32 or ord(char) == 127 for char in value):
        raise ValueError("BACKEND_BASE_URL must be a valid HTTPS URL")
    if any(char in value for char in ("?", "#", "\\")):
        raise ValueError("BACKEND_BASE_URL must not contain a query, fragment or backslash")
    try:
        parts = urlsplit(value)
        hostname = parts.hostname
        port = parts.port
        if not hostname or not parts.netloc or (port is not None and port < 1):
            raise ValueError
    except ValueError:
        raise ValueError("BACKEND_BASE_URL has an invalid host or port") from None
    if parts.username is not None or parts.password is not None:
        raise ValueError("BACKEND_BASE_URL must not contain credentials")
    local_http = parts.scheme == "http" and hostname.lower() in {
        "localhost", "127.0.0.1", "::1",
    }
    if parts.scheme != "https" and not local_http:
        raise ValueError("BACKEND_BASE_URL requires HTTPS (HTTP is allowed only for localhost)")
    return value.rstrip("/")


def _positive_baud(value):
    try:
        baud = int(value)
    except ValueError:
        raise argparse.ArgumentTypeError("baud must be a positive integer") from None
    if baud <= 0:
        raise argparse.ArgumentTypeError("baud must be a positive integer")
    return baud


def load_dotenv():
    for path in (".env", os.path.join(os.path.dirname(__file__), ".env"), os.path.join(os.path.dirname(__file__), "..", ".env")):
        if os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if not line or line.startswith("#"):
                            continue
                        if "=" in line:
                            k, v = line.split("=", 1)
                            k = k.strip()
                            v = v.strip().strip("'\"")
                            if k and k not in os.environ:
                                os.environ[k] = v
                break
            except Exception:
                pass


def parse_options(argv=None, environ=None):
    load_dotenv()
    parser = argparse.ArgumentParser(
        description="Report ESP32 USB PING/PONG and acknowledged alert status; no detection or alert commands.",
        epilog="Example: python pc_bridge.py --port COM8 --check",
        allow_abbrev=False,
    )
    parser.add_argument("--port", required=True, help="USB serial port, e.g. COM8")
    parser.add_argument("--baud", type=_positive_baud, default=115200)
    parser.add_argument("--dtr", choices=("off", "on"), default="off", help="DTR line state; default off, native USB CDC may need on")
    parser.add_argument("--rts", choices=("off", "on"), default="off", help="RTS line state; default off to avoid holding ESP32 in reset")
    parser.add_argument("--reset", action="store_true", help="pulse RTS to reset an ESP32 USB-UART board on each open; requires DTR/RTS off")
    parser.add_argument("--check", action="store_true", help="serial-only PONG check (10 seconds); no credentials or network")
    parser.add_argument("--user-id", help="backend user ID (otherwise DEVICE_USER_ID)")
    parser.add_argument("--prompt-key", action="store_true", help="ignore DEVICE_API_KEY environment value and request hidden input")
    args, unknown = parser.parse_known_args(argv)
    if unknown:
        parser.error("unsupported arguments; use DEVICE_API_KEY or --prompt-key, never a key value on the command line")
    port = args.port.strip()
    if not port or any(ord(char) < 32 or ord(char) == 127 for char in port):
        parser.error("--port must be a nonempty serial port")

    if args.reset and (args.dtr == "on" or args.rts == "on"):
        parser.error("--reset requires --dtr off --rts off to boot from flash")

    # Deliberately bypass all backend settings and prompting in diagnostic mode.
    if args.check:
        return BridgeOptions(port=port, baud=args.baud, check=True, dtr=args.dtr == "on", rts=args.rts == "on", reset=args.reset)

    env = os.environ if environ is None else environ
    try:
        base_url = validate_base_url(env.get("BACKEND_BASE_URL", DEFAULT_BASE_URL))
    except ValueError as exc:
        parser.error(str(exc))
    user_id = (args.user_id if args.user_id is not None else env.get("DEVICE_USER_ID", "")).strip()
    if not user_id or any(ord(char) < 32 or ord(char) == 127 for char in user_id):
        parser.error("provide --user-id or DEVICE_USER_ID for normal mode")
    device_key = "" if args.prompt_key else env.get("DEVICE_API_KEY", "").strip()
    key_source = "DEVICE_API_KEY environment"
    if not device_key:
        if not sys.stdin.isatty():
            parser.error("hidden input requires an interactive terminal; use DEVICE_API_KEY without --prompt-key for automation")
        key_source = "hidden prompt"
        try:
            # Refuse getpass's echoed fallback to avoid exposing the key.
            with warnings.catch_warnings():
                warnings.simplefilter("error", getpass.GetPassWarning)
                device_key = getpass.getpass("Railway DEVICE_API_KEY (hidden, not a Supabase key): ").strip()
        except (EOFError, OSError, getpass.GetPassWarning):
            parser.error("hidden key input unavailable; set DEVICE_API_KEY instead")
    if not device_key or any(not 33 <= ord(char) <= 126 for char in device_key):
        parser.error("DEVICE_API_KEY must be a nonempty printable ASCII key without whitespace")
    return BridgeOptions(
        port, args.baud, False, base_url, user_id, device_key,
        dtr=args.dtr == "on", rts=args.rts == "on", key_source=key_source,
    )


def create_serial_link(options, logger):
    # Keep --help and option validation available without pyserial installed.
    from serial_link import SerialLink

    return SerialLink(
        options.port, options.baud, timeout=0,
        dtr=options.dtr, rts=options.rts,
        reset_on_connect=options.reset, logger=logger,
    )


class StatusMonitor:
    """Consume acknowledgements only; a port opening is not a handshake."""

    def __init__(self, link, logger=print):
        self.link = link
        self.log = logger
        self.connected = None
        self.alert_active = False
        self._generation = None
        self._waiting_since = None
        self._last_warning = None

    def poll(self):
        generation = self.link.connection_generation
        messages = self.link.update()
        connected = bool(self.link.handshake_ok)
        if generation != self.link.connection_generation:
            # Ignore acknowledgements when the serial session changes during a poll.
            connected = False
        alert_active = self.alert_active if generation == self._generation else False
        self._generation = generation
        if not connected:
            alert_active = False
        else:
            for message in messages:
                if message == "ALERT_ON":
                    alert_active = True
                elif message in ("READY", "ALERT_OFF", "FAILSAFE_TIMEOUT"):
                    alert_active = False

        if connected != self.connected:
            self.log("ESP32 CONNECTED (fresh PONG)" if connected else
                     "ESP32 DISCONNECTED (waiting for a fresh PONG)")
        if alert_active != self.alert_active:
            self.log("ESP32 acknowledged alert ON" if alert_active else
                     "ESP32 alert OFF (acknowledgement or link lost)")
        self.connected = connected
        self.alert_active = alert_active

        now = time.monotonic()
        if connected or not self.link.connected:
            self._waiting_since = None
            self._last_warning = None
        else:
            if self._waiting_since is None:
                self._waiting_since = now
            if now - self._waiting_since >= 5.0 and (
                self._last_warning is None or now - self._last_warning >= 10.0
            ):
                self.log("Serial port is open but no fresh PONG. Check ESP32 firmware supports newline PING -> PONG at the selected baud.")
                self._last_warning = now
        return connected, alert_active


def check_serial(link, logger=print):
    """Finite diagnostic; SerialLink alone sends PING, never alert commands."""
    deadline = time.monotonic() + CHECK_TIMEOUT
    monitor = StatusMonitor(link, logger)
    while time.monotonic() < deadline:
        connected, _ = monitor.poll()
        if connected:
            logger("Serial check passed: fresh firmware PONG received.")
            return 0
        time.sleep(POLL_INTERVAL)
    logger("Serial check timed out: no fresh PONG. Check USB/port/baud and PING/PONG firmware; close Arduino Serial Monitor/Plotter if the port is busy.")
    return 1


def run_bridge(link, backend, logger=print):
    monitor = StatusMonitor(link, logger)
    while True:
        connected, alert_active = monitor.poll()
        backend.set_state(connected, alert_active)
        time.sleep(POLL_INTERVAL)


def main(argv=None):
    link = None
    backend = None
    try:
        options = parse_options(argv)
        try:
            link = create_serial_link(options, lambda msg: print(f"[serial] {msg}"))
        except ImportError:
            print("pyserial is required to open the USB port.", file=sys.stderr)
            return 2
        print(f"Starting serial status bridge on {options.port} @ {options.baud}; no alert commands are sent.")
        link.start()
        if options.check:
            return check_serial(link)
        print(f"[backend] Authentication: x-device-key from {options.key_source}; credential values are not logged.")
        if options.key_source == "DEVICE_API_KEY environment":
            print("[backend] Use --prompt-key to override a stale environment key. No .env or Pi config is loaded.")
        backend = BackendLink(
            options.base_url, options.user_id, options.device_key,
            status_only=True, logger=lambda msg: print(f"[backend] {msg}"),
        )
        backend.start()
        run_bridge(link, backend)
    except KeyboardInterrupt:
        print("Stopping status bridge.")
        return 130
    finally:
        try:
            if backend is not None:
                backend.stop(publish_disconnected=True)
        except KeyboardInterrupt:
            print("Final disconnected heartbeat interrupted; closing serial port.")
        finally:
            if link is not None:
                link.stop()


if __name__ == "__main__":
    raise SystemExit(main())