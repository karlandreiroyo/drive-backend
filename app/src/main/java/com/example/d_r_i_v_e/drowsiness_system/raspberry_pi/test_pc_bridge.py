"""Offline bridge tests: python -B -m unittest discover -s <this directory>."""

import contextlib
import io
import json
import sys
import types
import unittest
from unittest import mock
import urllib.error
import urllib.request

# Tests never require pyserial to be installed and cannot open real hardware.
serial_stub = types.ModuleType("serial")
serial_stub.SerialException = type("SerialException", (OSError,), {})
serial_stub.EIGHTBITS = 8
serial_stub.PARITY_NONE = "N"
serial_stub.STOPBITS_ONE = 1
serial_stub.Serial = mock.Mock(side_effect=AssertionError("real serial access forbidden"))
with mock.patch.dict(sys.modules, {"serial": serial_stub}):
    import serial_link
import backend_link
import pc_bridge


class FakeClock:
    def __init__(self):
        self.now = 100.0

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class ConfigurationTests(unittest.TestCase):
    def setUp(self):
        self.env = {"DEVICE_USER_ID": "test-user", "DEVICE_API_KEY": "private-test-key"}

    def parse_error(self, args, env):
        output = io.StringIO()
        with contextlib.redirect_stderr(output), self.assertRaises(SystemExit) as raised:
            pc_bridge.parse_options(args, env)
        self.assertEqual(raised.exception.code, 2)
        return output.getvalue()

    def test_defaults_and_environment(self):
        options = pc_bridge.parse_options(["--port", "COM8"], self.env)
        self.assertEqual(options.port, "COM8")
        self.assertEqual(options.baud, 115200)
        self.assertEqual(options.base_url, "https://drive-backend.up.railway.app")
        self.assertEqual(options.user_id, "test-user")
        self.assertEqual(options.device_key, "private-test-key")
        self.assertNotIn(options.device_key, repr(options))
        self.assertNotIn(options.user_id, repr(options))

    def test_user_override_and_local_url(self):
        self.env["BACKEND_BASE_URL"] = "http://localhost:8080/"
        options = pc_bridge.parse_options(["--port", "COM8", "--user-id", "override", "--baud", "57600"], self.env)
        self.assertEqual(options.user_id, "override")
        self.assertEqual(options.baud, 57600)
        self.assertEqual(options.base_url, "http://localhost:8080")

    def test_port_required_and_nonempty(self):
        self.parse_error(["--check"], {})
        self.parse_error(["--port", " ", "--check"], {})

    def test_baud_must_be_positive_integer(self):
        for baud in ("0", "-1", "fast", "1.5"):
            with self.subTest(baud=baud):
                self.parse_error(["--port", "COM8", "--baud", baud, "--check"], {})

    def test_no_command_line_key_or_abbreviations(self):
        for option in ("--key", "--device-key", "--api-key", "--device-api-key", "--po"):
            with self.subTest(option=option):
                self.parse_error(["--port", "COM8", option, "value"], self.env)

    def test_check_does_not_access_backend_environment_or_prompt(self):
        env = mock.Mock()
        with mock.patch.object(pc_bridge.getpass, "getpass") as prompt:
            options = pc_bridge.parse_options(["--port", "COM8", "--check"], env)
        env.get.assert_not_called()
        prompt.assert_not_called()
        self.assertTrue(options.check)
        self.assertEqual(options.device_key, "")
        self.assertEqual(options.user_id, "")

    def test_missing_user(self):
        self.assertIn("DEVICE_USER_ID", self.parse_error(["--port", "COM8"], {"DEVICE_API_KEY": "key"}))

    def test_missing_key_noninteractive(self):
        with mock.patch.object(pc_bridge.sys.stdin, "isatty", return_value=False), mock.patch.object(pc_bridge.getpass, "getpass") as prompt:
            self.assertIn("DEVICE_API_KEY", self.parse_error(["--port", "COM8"], {"DEVICE_USER_ID": "user"}))
        prompt.assert_not_called()

    def test_hidden_prompt_only_for_missing_key(self):
        with mock.patch.object(pc_bridge.sys.stdin, "isatty", return_value=True), mock.patch.object(pc_bridge.getpass, "getpass", return_value="hidden-value") as prompt:
            options = pc_bridge.parse_options(["--port", "COM8"], {"DEVICE_USER_ID": "user"})
            self.assertEqual(options.device_key, "hidden-value")
            prompt.assert_called_once()
            prompt.reset_mock()
            pc_bridge.parse_options(["--port", "COM8"], self.env)
            prompt.assert_not_called()

    def test_no_echoed_prompt_fallback(self):
        with mock.patch.object(pc_bridge.sys.stdin, "isatty", return_value=True), mock.patch.object(pc_bridge.getpass, "getpass", side_effect=pc_bridge.getpass.GetPassWarning("unsafe terminal")):
            output = self.parse_error(["--port", "COM8"], {"DEVICE_USER_ID": "user"})
        self.assertIn("hidden key input unavailable", output)

    def test_forced_hidden_prompt_overrides_stale_environment(self):
        with mock.patch.object(pc_bridge.sys.stdin, "isatty", return_value=True), mock.patch.object(pc_bridge.getpass, "getpass", return_value="fresh-hidden-key") as prompt:
            options = pc_bridge.parse_options(["--port", "COM8", "--prompt-key"], self.env)
        prompt.assert_called_once()
        self.assertEqual(options.device_key, "fresh-hidden-key")
        self.assertEqual(options.key_source, "hidden prompt")
        self.assertNotIn("fresh-hidden-key", repr(options))
        self.assertEqual(self.env["DEVICE_API_KEY"], "private-test-key")

    def test_forced_prompt_never_silently_uses_environment_without_terminal(self):
        with mock.patch.object(pc_bridge.sys.stdin, "isatty", return_value=False), mock.patch.object(pc_bridge.getpass, "getpass") as prompt:
            output = self.parse_error(["--port", "COM8", "--prompt-key"], self.env)
        prompt.assert_not_called()
        self.assertIn("interactive terminal", output)
        self.assertNotIn("private-test-key", output)

    def test_environment_key_source_is_visible_without_value(self):
        options = pc_bridge.parse_options(["--port", "COM8"], self.env)
        self.assertEqual(options.key_source, "DEVICE_API_KEY environment")
        self.assertNotIn("private-test-key", repr(options))

    def test_check_ignores_forced_prompt_and_all_credentials(self):
        env = mock.Mock()
        with mock.patch.object(pc_bridge.getpass, "getpass") as prompt:
            options = pc_bridge.parse_options(["--port", "COM8", "--check", "--prompt-key"], env)
        env.get.assert_not_called()
        prompt.assert_not_called()
        self.assertEqual(options.key_source, "")

    def test_accidental_command_line_secret_is_not_echoed(self):
        output = self.parse_error(["--port", "COM8", "--device-key", "private-cli-secret"], self.env)
        self.assertNotIn("private-cli-secret", output)
        self.assertIn("--prompt-key", output)

    def test_serial_control_lines_default_off_and_can_be_overridden(self):
        for args, expected in (([], (False, False)), (["--dtr", "on"], (True, False)), (["--rts", "on"], (False, True))):
            options = pc_bridge.parse_options(["--port", "COM8", "--check", *args], {})
            link = pc_bridge.create_serial_link(options, self.fail)
            self.assertEqual((link.dtr, link.rts), expected)
            self.assertEqual(link.timeout, 0)

    def test_invalid_key_not_logged(self):
        self.env["DEVICE_API_KEY"] = "private-key\r\nInjected: header"
        output = self.parse_error(["--port", "COM8"], self.env)
        self.assertNotIn("private-key", output)
        self.assertNotIn("Injected", output)

    def test_allowed_urls(self):
        for url in ("https://example.com", "https://example.com:8443/base", "http://localhost:8000", "http://127.0.0.1:9000", "http://[::1]:9000"):
            with self.subTest(url=url):
                self.assertEqual(pc_bridge.validate_base_url(url), url)

    def test_unsafe_or_malformed_urls_are_rejected_without_echo(self):
        for url in ("", "example.com", "ftp://localhost", "http://example.com", "http://localhost.evil", "http://0.0.0.0", "https://user:private-key@example.com", "https://@example.com", "https://example.com?key=private-key", "https://example.com?", "https://example.com#fragment", "https://example.com\\path", "https://example.com\n/path", "https://", "https://example.com:bad", "https://example.com:70000", "https://example.com:0", "https://[bad"):
            with self.subTest(url=url), self.assertRaises(ValueError) as raised:
                pc_bridge.validate_base_url(url)
            self.assertNotIn("private-key", str(raised.exception))


class SerialAndMonitorTests(unittest.TestCase):
    def setUp(self):
        self.clock = FakeClock()
        self.timer = mock.patch.object(serial_link.time, "monotonic", self.clock.monotonic)
        self.timer.start()
        self.addCleanup(self.timer.stop)
        self.link = serial_link.SerialLink("COM8", 115200)
        self.logs = []
        self.monitor = pc_bridge.StatusMonitor(self.link, self.logs.append)

    def connect(self):
        self.link._set_connected(True)
        self.link._handle_line("PONG")

    def test_open_port_requires_nonzero_pong_even_near_clock_zero(self):
        self.clock.now = 0.5
        self.link._set_connected(True)
        self.assertTrue(self.link.connected)
        self.assertFalse(self.link.handshake_ok)
        self.link._handle_line("READY")
        self.assertFalse(self.link.handshake_ok)
        self.link._handle_line("PONG")
        self.assertTrue(self.link.handshake_ok)

    def test_pong_expiry_and_recovery(self):
        self.connect()
        self.assertTrue(self.link.handshake_ok)
        self.clock.sleep(3.1)
        self.assertFalse(self.link.handshake_ok)
        self.link._handle_line("PONG")
        self.assertTrue(self.link.handshake_ok)

    def test_disconnect_invalidates_recent_pong(self):
        self.connect()
        self.link._set_connected(False)
        self.assertEqual(self.link._last_pong, 0.0)
        self.assertFalse(self.link.handshake_ok)
        self.link._set_connected(True)
        self.assertFalse(self.link.handshake_ok)
        self.link._handle_line("PONG")
        self.assertTrue(self.link.handshake_ok)

    def test_alert_acknowledgements_and_unknown_lines(self):
        self.connect()
        self.assertEqual(self.monitor.poll(), (True, False))
        for off_status in ("READY", "ALERT_OFF", "FAILSAFE_TIMEOUT"):
            self.link._handle_line("ALERT_ON")
            self.assertEqual(self.monitor.poll(), (True, True))
            self.link._handle_line("unknown firmware output")
            self.assertEqual(self.monitor.poll(), (True, True))
            self.link._handle_line(off_status)
            self.assertEqual(self.monitor.poll(), (True, False))
        self.assertNotIn("unknown firmware output", " ".join(self.logs))
        self.assertTrue(self.link._outbox.empty())

    def test_multiple_acknowledgements_use_last_one(self):
        self.connect()
        for message in ("ALERT_ON", "ALERT_OFF", "ALERT_ON", "FAILSAFE_TIMEOUT"):
            self.link._handle_line(message)
        self.assertEqual(self.monitor.poll(), (True, False))

    def test_alert_reset_on_timeout_and_not_restored_by_pong(self):
        self.connect()
        self.link._handle_line("ALERT_ON")
        self.assertEqual(self.monitor.poll(), (True, True))
        self.clock.sleep(4.0)
        self.link._handle_line("ALERT_ON")
        self.assertEqual(self.monitor.poll(), (False, False))
        self.link._handle_line("PONG")
        self.assertEqual(self.monitor.poll(), (True, False))
        self.link._handle_line("ALERT_ON")
        self.assertEqual(self.monitor.poll(), (True, True))

    def test_reconnection_between_polls_resets_alert_and_stale_inbox(self):
        self.connect()
        self.link._handle_line("ALERT_ON")
        self.assertEqual(self.monitor.poll(), (True, True))
        self.link._handle_line("ALERT_ON")
        self.link._set_connected(False)
        self.connect()
        self.assertEqual(self.monitor.poll(), (True, False))
        self.link._handle_line("ALERT_ON")
        self.assertEqual(self.monitor.poll(), (True, True))

    def test_reconnection_during_poll_is_conservatively_disconnected(self):
        self.connect()
        self.link._handle_line("ALERT_ON")
        # Simulate a port transition between the two generation reads.
        with mock.patch.object(type(self.link), "connection_generation", new_callable=mock.PropertyMock, side_effect=[1, 2]):
            self.assertEqual(self.monitor.poll(), (False, False))
        self.assertEqual(self.monitor.poll(), (True, False))

    def test_missing_firmware_pong_warning_is_rate_limited(self):
        self.link._set_connected(True)
        self.monitor.poll()
        self.clock.sleep(5.0)
        self.monitor.poll()
        self.monitor.poll()
        warnings = [line for line in self.logs if "firmware supports" in line]
        self.assertEqual(len(warnings), 1)
        self.assertIn("PING -> PONG", warnings[0])

    def test_busy_port_guidance(self):
        link = serial_link.SerialLink("COM8", 115200, logger=self.logs.append)
        with mock.patch.object(serial_link.serial, "Serial", side_effect=OSError("access denied")):
            self.assertIsNone(link._open_port())
        self.assertIn("COM8", " ".join(self.logs))
        self.assertIn("Arduino Serial Monitor/Plotter", " ".join(self.logs))

    def test_serial_check_success_waits_for_pong_without_alert_commands(self):
        self.link._set_connected(True)

        def receive_pong(seconds):
            self.clock.sleep(seconds)
            self.link._handle_line("PONG")

        with mock.patch.object(pc_bridge.time, "sleep", side_effect=receive_pong):
            self.assertEqual(pc_bridge.check_serial(self.link, self.logs.append), 0)
        self.assertTrue(self.link._outbox.empty())
        self.assertIn("fresh firmware PONG", " ".join(self.logs))

    def test_serial_check_times_out_on_ready_without_pong(self):
        self.link._set_connected(True)
        self.link._handle_line("READY")
        start = self.clock.now
        with mock.patch.object(pc_bridge.time, "sleep", self.clock.sleep):
            self.assertEqual(pc_bridge.check_serial(self.link, self.logs.append), 1)
        self.assertGreaterEqual(self.clock.now - start, 10.0)
        self.assertLess(self.clock.now - start, 10.2)
        self.assertTrue(self.link._outbox.empty())

    def test_normal_loop_posts_only_acknowledged_status(self):
        self.connect()
        self.link._handle_line("ALERT_ON")
        backend = mock.Mock()
        with mock.patch.object(pc_bridge.time, "sleep", side_effect=KeyboardInterrupt), self.assertRaises(KeyboardInterrupt):
            pc_bridge.run_bridge(self.link, backend, self.logs.append)
        backend.set_state.assert_called_once_with(True, True)
        self.assertTrue(self.link._outbox.empty())


class SerialTransportTests(unittest.TestCase):
    def setUp(self):
        self.logs = []
        self.link = serial_link.SerialLink("COM8", 115200, logger=self.logs.append)
        self.port = mock.MagicMock()
        self.port.write.side_effect = len

    def test_control_lines_and_8n1_are_configured_before_open(self):
        def open_port():
            self.assertFalse(self.port.dtr)
            self.assertFalse(self.port.rts)
            self.assertEqual(self.port.port, "COM8")

        def boot_wait(seconds):
            self.assertEqual(seconds, 2.0)
            self.port.reset_input_buffer.assert_called_once()
            self.port.reset_output_buffer.assert_called_once()
            return False

        self.port.open.side_effect = open_port
        with mock.patch.object(serial_link.serial, "Serial", return_value=self.port) as factory, mock.patch.object(self.link._stop, "wait", side_effect=boot_wait):
            self.assertIs(self.link._open_port(), self.port)
        factory.assert_called_once_with(
            port=None, baudrate=115200, bytesize=8, parity="N", stopbits=1,
            timeout=0, write_timeout=0.5, xonxoff=False, rtscts=False, dsrdtr=False,
        )
        self.port.open.assert_called_once()
        self.port.reset_input_buffer.assert_called_once()
        self.port.close.assert_not_called()

    def test_explicit_dtr_state_is_set_before_open(self):
        self.link.dtr = True
        self.port.open.side_effect = lambda: self.assertTrue(self.port.dtr)
        with mock.patch.object(serial_link.serial, "Serial", return_value=self.port), mock.patch.object(self.link._stop, "wait", return_value=False):
            self.assertIs(self.link._open_port(), self.port)

    def test_failed_open_closes_port_and_reports_contention(self):
        self.port.open.side_effect = OSError("access denied")
        with mock.patch.object(serial_link.serial, "Serial", return_value=self.port):
            self.assertIsNone(self.link._open_port())
        self.port.close.assert_called_once()
        self.assertIn("Arduino Serial Monitor/Plotter", " ".join(self.logs))

    def test_failed_initialization_closes_partially_open_port(self):
        self.port.reset_input_buffer.side_effect = OSError("disconnected")
        with mock.patch.object(serial_link.serial, "Serial", return_value=self.port):
            self.assertIsNone(self.link._open_port())
        self.port.close.assert_called_once()

    def test_shutdown_during_boot_releases_port_without_ping(self):
        with mock.patch.object(serial_link.serial, "Serial", return_value=self.port), mock.patch.object(self.link._stop, "wait", return_value=True):
            self.assertIsNone(self.link._open_port())
        self.port.close.assert_called_once()
        self.port.write.assert_not_called()

    def test_ping_is_newline_terminated_without_unbounded_flush(self):
        self.link._write(self.port, "PING")
        self.port.write.assert_called_once_with(b"PING\n")
        self.port.flush.assert_not_called()

    def test_partial_write_is_a_link_error(self):
        self.port.write.side_effect = None
        self.port.write.return_value = 2
        with self.assertRaises(serial_link.serial.SerialException):
            self.link._write(self.port, "PING")
        self.port.flush.assert_not_called()

    def run_chunks(self, chunks):
        pending = list(chunks)
        clock = FakeClock()
        received = []
        handle_line = self.link._handle_line

        def in_waiting():
            if pending:
                return len(pending[0])
            self.link._stop.set()
            return 0

        def read(size):
            self.assertLessEqual(size, 4096)
            chunk = pending.pop(0)
            if len(chunk) > size:
                pending.insert(0, chunk[size:])
            return chunk[:size]

        def on_line(line):
            handle_line(line)
            received.append((line, self.link.handshake_ok))

        type(self.port).in_waiting = mock.PropertyMock(side_effect=in_waiting)
        self.port.read.side_effect = read
        with mock.patch.object(self.link, "_open_port", return_value=self.port), mock.patch.object(self.link._stop, "wait", side_effect=clock.sleep), mock.patch.object(serial_link.time, "monotonic", clock.monotonic), mock.patch.object(self.link, "_handle_line", side_effect=on_line):
            self.link._run()
        self.port.close.assert_called_once()
        self.assertFalse(self.link.handshake_ok)
        self.assertTrue(all(call.args == (b"PING\n",) for call in self.port.write.call_args_list))
        self.port.write.assert_called()
        self.port.flush.assert_not_called()
        return received

    def test_reader_handles_fragmented_pong_and_split_crlf(self):
        received = self.run_chunks([b"PO", b"NG\r", b"\nREADY\r\n", b"PONG\n"])
        self.assertEqual(received, [("PONG", True), ("READY", True), ("PONG", True)])
        self.assertEqual(self.logs.count("ESP32 PONG received"), 1)

    def test_reader_does_not_treat_noise_or_incomplete_line_as_pong(self):
        received = self.run_chunks([b"\xffboot\r\n", b"NOT_PONG\nREADY\n", b"PONG"])
        self.assertTrue(received)
        self.assertTrue(all(not connected for _, connected in received))
        self.assertNotIn("NOT_PONG", " ".join(self.logs))

    def test_reader_caps_each_read_and_recovers_after_long_noise(self):
        received = self.run_chunks([b"x" * 9000 + b"\nPONG\r\n"])
        self.assertEqual(received[-1], ("PONG", True))

    def test_partial_line_does_not_cross_reconnect(self):
        clock = FakeClock()
        first = mock.MagicMock()
        first.write.side_effect = len
        type(first).in_waiting = mock.PropertyMock(side_effect=[3, OSError("unplugged")])
        first.read.return_value = b"PON"
        second = mock.MagicMock()
        second.write.side_effect = len
        type(second).in_waiting = mock.PropertyMock(return_value=3)
        second.read.return_value = b"G\n"
        received = []
        handle_line = self.link._handle_line

        def on_line(line):
            handle_line(line)
            received.append((line, self.link.handshake_ok))
            self.link._stop.set()

        with mock.patch.object(self.link, "_open_port", side_effect=[first, second]), mock.patch.object(self.link._stop, "wait", side_effect=clock.sleep), mock.patch.object(serial_link.time, "monotonic", clock.monotonic), mock.patch.object(self.link, "_handle_line", side_effect=on_line):
            self.link._run()
        self.assertEqual(received, [("G", False)])
        first.close.assert_called_once()
        second.close.assert_called_once()


class BackendTests(unittest.TestCase):
    def setUp(self):
        self.logs = []
        self.backend = backend_link.BackendLink(
            "https://example.invalid", "private-user", "private-key",
            status_only=True, logger=self.logs.append,
        )
        self.response = mock.MagicMock()
        self.response.__enter__.return_value = self.response
        self.response.getcode.return_value = 200
        self.open = mock.Mock(return_value=self.response)
        self.backend._opener.open = self.open

    def post(self):
        return self.backend._post_heartbeat(self.backend._payload())

    def assert_no_secrets_logged(self):
        logs = " ".join(self.logs)
        for secret in ("private-key", "private-user", "private-body", "private-url"):
            self.assertNotIn(secret, logs)

    def test_status_payload_and_request(self):
        self.backend.set_state(True, True)
        self.assertTrue(self.post())
        request = self.open.call_args.args[0]
        self.assertEqual(request.full_url, "https://example.invalid/api/device/heartbeat")
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(request.get_header("X-device-key"), "private-key")
        self.assertEqual(request.get_header("Content-type"), "application/json")
        self.assertIsNone(request.get_header("Authorization"))
        self.assertIsNone(request.get_header("Apikey"))
        self.assertNotIn(b"private-key", request.data)
        self.assertEqual(json.loads(request.data), {
            "userId": "private-user", "firmware": "esp32_alert",
            "esp32Connected": True, "alertActive": True,
        })
        self.assertEqual(self.open.call_args.kwargs["timeout"], 3.0)
        self.response.read.assert_not_called()
        self.assert_no_secrets_logged()

    def test_initial_status_is_disconnected(self):
        self.assertEqual(self.backend._payload(), {
            "userId": "private-user", "firmware": "esp32_alert",
            "esp32Connected": False, "alertActive": False,
        })

    def test_pi_detection_payload_preserved_by_default(self):
        backend = backend_link.BackendLink("https://example.invalid", "user", "key")
        backend.set_state(True, True, "drowsy", 0.93)
        self.assertEqual(backend._payload()["lastDetectionType"], "drowsy")
        self.assertEqual(backend._payload()["lastConfidence"], 0.93)

    def test_http_failures_have_specific_codes_without_body(self):
        for code, expected in ((401, "invalid shared key"), (503, "server not configured"), (403, "server rejected"), (429, "server rejected"), (500, "server rejected"), (302, "redirect refused")):
            with self.subTest(code=code):
                body = io.BytesIO(b"private-body private-key private-user")
                self.open.side_effect = urllib.error.HTTPError("https://private-url", code, "private-key", {}, body)
                self.assertFalse(self.post())
                self.assertIn(f"HTTP {code}", self.logs[-1])
                self.assertIn(expected, self.logs[-1])
                self.assertTrue(body.closed)
        self.assert_no_secrets_logged()

    def test_connectivity_failures_without_exception_details(self):
        for error in (urllib.error.URLError("private-url private-key"), TimeoutError("private-body"), OSError("private-user")):
            self.open.side_effect = error
            self.assertFalse(self.post())
        self.assertIn("connectivity", self.logs[-1])
        self.assert_no_secrets_logged()

    def test_unexpected_failure_is_best_effort_and_redacted(self):
        self.open.side_effect = ValueError("private-key private-body")
        self.assertFalse(self.post())
        self.assertIn("could not be completed", self.logs[-1])
        self.assert_no_secrets_logged()

    def test_success_failure_throttle_and_recovery(self):
        self.assertTrue(self.post())
        self.assertTrue(self.post())
        self.assertEqual(len(self.logs), 1)
        self.assertIn("accepted (HTTP 200)", self.logs[0])
        with mock.patch.object(backend_link.time, "monotonic", return_value=0.5):
            self.open.side_effect = urllib.error.URLError("offline")
            self.assertFalse(self.post())
            self.assertFalse(self.post())
        self.assertEqual(len(self.logs), 2)
        self.open.side_effect = None
        self.response.getcode.return_value = 204
        self.assertTrue(self.post())
        self.assertIn("recovered (HTTP 204)", self.logs[-1])
        self.assert_no_secrets_logged()

    def test_redirect_does_not_forward_key(self):
        handler = backend_link._NoRedirect()
        request = urllib.request.Request("https://example.invalid", headers={"x-device-key": "private-key"})
        for target in ("http://other.invalid", "https://other.invalid"):
            self.assertIsNone(handler.redirect_request(request, None, 302, "redirect", {}, target))
        self.assertTrue(any(isinstance(handler, backend_link._NoRedirect) for handler in self.backend._opener.handlers))

    def test_final_disconnected_upload_happens_after_worker_joins(self):
        thread = mock.Mock()
        thread.is_alive.return_value = False
        self.backend._thread = thread
        self.backend.set_state(True, True)

        def upload_after_join(request, **kwargs):
            thread.join.assert_called_once()
            self.assertTrue(self.backend._stop.is_set())
            return self.response

        self.open.side_effect = upload_after_join
        self.backend.stop(publish_disconnected=True)
        payload = json.loads(self.open.call_args.args[0].data)
        self.assertFalse(payload["esp32Connected"])
        self.assertFalse(payload["alertActive"])
        self.assertNotIn("lastConfidence", payload)

    def test_no_final_upload_race_with_live_worker(self):
        self.backend._thread = mock.Mock()
        self.backend._thread.is_alive.return_value = True
        self.backend.stop(publish_disconnected=True)
        self.open.assert_not_called()
        self.assertIn("final status skipped", self.logs[-1])

    def test_default_pi_stop_does_not_upload(self):
        self.backend.stop()
        self.open.assert_not_called()

    def test_disabled_backend_never_uploads(self):
        self.backend.enabled = False
        self.backend.start()
        self.backend.stop(publish_disconnected=True)
        self.open.assert_not_called()


class LifecycleTests(unittest.TestCase):
    def setUp(self):
        self.link = mock.Mock()
        self.backend = mock.Mock()
        self.stdout = contextlib.redirect_stdout(io.StringIO())
        self.stdout.__enter__()
        self.addCleanup(self.stdout.__exit__, None, None, None)

    def test_check_cli_does_not_construct_backend_and_closes_serial(self):
        with mock.patch.object(pc_bridge, "create_serial_link", return_value=self.link), mock.patch.object(pc_bridge, "BackendLink") as backend_factory, mock.patch.object(pc_bridge, "check_serial", return_value=0) as check, mock.patch.dict(pc_bridge.os.environ, {}, clear=True):
            self.assertEqual(pc_bridge.main(["--port", "COM8", "--check"]), 0)
        self.link.start.assert_called_once()
        self.link.stop.assert_called_once()
        self.link.send.assert_not_called()
        check.assert_called_once_with(self.link)
        backend_factory.assert_not_called()

    def test_check_timeout_exit_code_and_cleanup(self):
        with mock.patch.object(pc_bridge, "create_serial_link", return_value=self.link), mock.patch.object(pc_bridge, "BackendLink") as backend_factory, mock.patch.object(pc_bridge, "check_serial", return_value=1):
            self.assertEqual(pc_bridge.main(["--port", "COM8", "--check"]), 1)
        self.link.stop.assert_called_once()
        backend_factory.assert_not_called()

    def test_ctrl_c_publishes_disconnect_before_serial_close(self):
        events = []
        self.backend.stop.side_effect = lambda **kwargs: events.append(("backend", kwargs))
        self.link.stop.side_effect = lambda: events.append(("serial", {}))
        with mock.patch.object(pc_bridge, "create_serial_link", return_value=self.link), mock.patch.object(pc_bridge, "BackendLink", return_value=self.backend) as factory, mock.patch.object(pc_bridge, "run_bridge", side_effect=KeyboardInterrupt), mock.patch.dict(pc_bridge.os.environ, {"DEVICE_USER_ID": "user", "DEVICE_API_KEY": "key"}, clear=True):
            self.assertEqual(pc_bridge.main(["--port", "COM8"]), 130)
        self.assertTrue(factory.call_args.kwargs["status_only"])
        self.assertEqual(events, [("backend", {"publish_disconnected": True}), ("serial", {})])
        self.link.send.assert_not_called()

    def test_serial_closes_even_if_final_upload_interrupted(self):
        self.backend.stop.side_effect = KeyboardInterrupt
        with mock.patch.object(pc_bridge, "create_serial_link", return_value=self.link), mock.patch.object(pc_bridge, "BackendLink", return_value=self.backend), mock.patch.object(pc_bridge, "run_bridge", side_effect=KeyboardInterrupt), mock.patch.dict(pc_bridge.os.environ, {"DEVICE_USER_ID": "user", "DEVICE_API_KEY": "key"}, clear=True):
            self.assertEqual(pc_bridge.main(["--port", "COM8"]), 130)
        self.link.stop.assert_called_once()

    def test_forced_prompt_key_reaches_backend_without_logging_secrets(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output), mock.patch.object(pc_bridge, "create_serial_link", return_value=self.link), mock.patch.object(pc_bridge, "BackendLink", return_value=self.backend) as factory, mock.patch.object(pc_bridge, "run_bridge", side_effect=KeyboardInterrupt), mock.patch.dict(pc_bridge.os.environ, {"DEVICE_USER_ID": "private-account", "DEVICE_API_KEY": "stale-env-key"}, clear=True), mock.patch.object(pc_bridge.sys.stdin, "isatty", return_value=True), mock.patch.object(pc_bridge.getpass, "getpass", return_value="fresh-hidden-key"):
            self.assertEqual(pc_bridge.main(["--port", "COM8", "--prompt-key"]), 130)
        self.assertEqual(factory.call_args.args[2], "fresh-hidden-key")
        self.assertIn("from hidden prompt", output.getvalue())
        for secret in ("private-account", "stale-env-key", "fresh-hidden-key"):
            self.assertNotIn(secret, output.getvalue())


if __name__ == "__main__":
    unittest.main()
