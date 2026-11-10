"""Best-effort bridge to the D.R.I.V.E backend.

Reports the node's live status (ESP32 connection + alert state) to the backend
so the phone app's Live Feed can show "ESP32: CONNECTED/DISCONNECTED". Runs in a
daemon thread and NEVER raises into the detection loop: if the backend is
unreachable the Pi keeps detecting and driving the ESP32 regardless.

Uses only the Python standard library (urllib) so no extra pip package is needed.
"""

import json
import threading
import time
import urllib.request


class BackendLink:
    def __init__(self, base_url, user_id, device_key,
                 heartbeat_interval=2.0, timeout=3.0, firmware="esp32_alert",
                 logger=None):
        self._base_url = (base_url or "").rstrip("/")
        self._user_id = (user_id or "").strip()
        self._device_key = (device_key or "").strip()
        self._heartbeat_interval = max(0.5, float(heartbeat_interval))
        self._timeout = float(timeout)
        self._firmware = firmware
        self._log = logger or (lambda msg: None)
        self.enabled = bool(self._base_url and self._user_id and self._device_key)

        self._lock = threading.Lock()
        self._state = {
            "esp32Connected": False,
            "alertActive": False,
            "lastDetectionType": None,
            "lastConfidence": 0.0,
        }
        self._stop = threading.Event()
        self._thread = None
        self._last_warn = 0.0

    def start(self):
        if not self.enabled:
            self._log("disabled (set DEVICE_USER_ID and DEVICE_API_KEY in config.py to enable)")
            return
        if self._thread is not None:
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, name="backend-link", daemon=True)
        self._thread.start()
        self._log(f"started -> {self._base_url}")

    def stop(self):
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=self._timeout + 1.0)
            self._thread = None

    def set_state(self, esp32_connected, alert_active, detection_type=None, confidence=0.0):
        """Update the latest status; the worker posts it on the next heartbeat."""
        if not self.enabled:
            return
        with self._lock:
            self._state = {
                "esp32Connected": bool(esp32_connected),
                "alertActive": bool(alert_active),
                "lastDetectionType": detection_type,
                "lastConfidence": float(confidence),
            }

    def _run(self):
        while not self._stop.is_set():
            with self._lock:
                payload = dict(self._state)
            payload["userId"] = self._user_id
            payload["firmware"] = self._firmware
            self._post_heartbeat(payload)
            # Wait for the interval, but wake immediately on stop().
            self._stop.wait(self._heartbeat_interval)

    def _post_heartbeat(self, payload):
        url = f"{self._base_url}/api/device/heartbeat"
        data = json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            url, data=data, method="POST",
            headers={
                "Content-Type": "application/json",
                "x-device-key": self._device_key,
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=self._timeout) as response:
                response.read()
        except Exception as exc:  # noqa: BLE001 - best-effort; never crash detection
            now = time.monotonic()
            if now - self._last_warn >= 10.0:
                self._last_warn = now
                self._log(f"heartbeat failed (backend unreachable?): {exc}")