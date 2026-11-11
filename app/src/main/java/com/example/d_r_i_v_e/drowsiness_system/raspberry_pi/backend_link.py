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
import urllib.error
import urllib.request


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward the shared device key to a redirect target.
        return None


class BackendLink:
    def __init__(self, base_url, user_id, device_key,
                 heartbeat_interval=2.0, timeout=3.0, firmware="esp32_alert",
                 logger=None, status_only=False):
        self._base_url = (base_url or "").rstrip("/")
        self._user_id = (user_id or "").strip()
        self._device_key = (device_key or "").strip()
        self._heartbeat_interval = max(0.5, float(heartbeat_interval))
        self._timeout = float(timeout)
        self._firmware = firmware
        self._log = logger or (lambda msg: None)
        self._status_only = status_only
        self._opener = urllib.request.build_opener(_NoRedirect())
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
        self._last_failure = None
        self._reported_success = False

    def start(self):
        if not self.enabled:
            self._log("disabled (backend URL, DEVICE_USER_ID and DEVICE_API_KEY are required)")
            return
        if self._thread is not None:
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, name="backend-link", daemon=True)
        self._thread.start()
        self._log(f"started (heartbeat interval {self._heartbeat_interval:g}s)")

    def stop(self, publish_disconnected=False):
        """Stop uploads; optionally send one final, bounded disconnected heartbeat."""
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=self._timeout + 1.0)
            if self._thread.is_alive():
                # Do not race an in-flight upload with a final disconnected one.
                self._log("heartbeat worker still stopping; final status skipped")
                return
            self._thread = None
        if publish_disconnected and self.enabled:
            self.set_state(False, False)
            self._post_heartbeat(self._payload())

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

    def _payload(self):
        with self._lock:
            payload = dict(self._state)
        if self._status_only:
            # No detector means no detection measurements to report.
            payload.pop("lastDetectionType", None)
            payload.pop("lastConfidence", None)
        payload["userId"] = self._user_id
        payload["firmware"] = self._firmware
        return payload

    def _run(self):
        while not self._stop.is_set():
            self._post_heartbeat(self._payload())
            # Wait for the interval, but wake immediately on stop().
            self._stop.wait(self._heartbeat_interval)

    def _post_heartbeat(self, payload):
        try:
            request = urllib.request.Request(
                f"{self._base_url}/api/device/heartbeat",
                data=json.dumps(payload).encode("utf-8"), method="POST",
                headers={
                    "Content-Type": "application/json",
                    "x-device-key": self._device_key,
                },
            )
            with self._opener.open(request, timeout=self._timeout) as response:
                code = response.getcode()
                # The response body is not needed and must never enter logs.
                if not 200 <= code < 300:
                    self._http_failure(code)
                    return False
        except urllib.error.HTTPError as exc:
            self._http_failure(exc.code)
            exc.close()
            return False
        except (urllib.error.URLError, OSError):
            self._failure("heartbeat failed (connectivity: check network, DNS, TLS and backend availability)")
            return False
        except Exception:  # noqa: BLE001 - best-effort; never crash detection
            self._failure("heartbeat failed (request could not be completed)")
            return False

        if self._last_failure is not None:
            self._log(f"heartbeat recovered (HTTP {code})")
        elif not self._reported_success:
            self._log(f"heartbeat accepted (HTTP {code})")
        self._last_failure = None
        self._reported_success = True
        return True

    def _http_failure(self, code):
        if code == 401:
            detail = ("invalid shared key; x-device-key must exactly match DEVICE_API_KEY "
                      "in the running D.R.I.V.E backend, not a Supabase key or Bearer token. "
                      "Check the backend URL and Railway variable (no wrapping quotes/spaces); "
                      "restart pc_bridge.py with --prompt-key to bypass a stale environment value")
        elif code == 503:
            detail = "server not configured or unavailable; check server DEVICE_API_KEY configuration"
        elif 300 <= code < 400:
            detail = "redirect refused to protect the shared key; check backend URL"
        else:
            detail = "server rejected heartbeat"
        self._failure(f"heartbeat failed (HTTP {code}: {detail})")

    def _failure(self, message):
        now = time.monotonic()
        if message != self._last_failure or now - self._last_warn >= 10.0:
            self._last_warn = now
            self._log(message)
        self._last_failure = message