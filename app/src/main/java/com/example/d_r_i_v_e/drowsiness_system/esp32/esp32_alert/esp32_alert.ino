/*
 * ESP32 Drowsiness Alert Controller  (Serial + Wi-Fi HTTP)
 * --------------------------------------------------------
 * Drives 6 vibration motors + 1 buzzer. Two ways to control it:
 *
 *  1) Mobile app over Wi-Fi (HTTP, port 80)
 *       GET /        -> "ESP32 Drowsiness System API is Active!"  (app health check)
 *       GET /alert   -> alert ON  (pulsing, stays on until /clear)
 *       GET /clear   -> alert OFF
 *       GET /status  -> JSON: {"esp32":"CONNECTED","alert":bool,"rssi":n,"uptime_s":n}
 *
 *  2) Raspberry Pi / PC bridge over Serial (USB) or Serial2 (UART)
 *       Newline-terminated ASCII:
 *       Pi  -> ESP32 : "PING"          -> ESP32 replies "PONG" on the same port
 *                      "DROWSY_ALERT"  -> alert ON (pulsing)
 *                      "CLEAR_ALERT"   -> alert OFF immediately
 *       ESP32 -> Pi  : "READY" | "ALERT_ON" | "ALERT_OFF" | "FAILSAFE_TIMEOUT" | "PONG"
 *
 * Hardware (logic signals only: use a transistor/MOSFET driver, the GPIO pins
 * must NOT power motors/buzzer directly):
 *   Motors : GPIO 4, 16, 17, 18, 19, 21
 *   Buzzer : GPIO 23
 *   Serial2: RX = GPIO 26 (<- Pi TX / GPIO14), TX = GPIO 27 (-> Pi RX / GPIO15)
 *
 * Behaviour:
 *   - Non-blocking: no delay() in loop(); Wi-Fi never blocks startup, so serial
 *     control works even if Wi-Fi is down. Pulse is 500 ms ON / 300 ms OFF.
 *   - Alert is latched until /clear or CLEAR_ALERT.
 *   - Serial fail-safe: if an alert was started over SERIAL and no command or
 *     PING arrives for 5 s, outputs turn off (crashed/unplugged Pi/PC).
 *   - Alerts started over HTTP are NOT subject to that 5 s fail-safe (the app
 *     does not send heartbeats). Optional cap: HTTP_ALERT_MAX_MS below.
 *
 * Only ONE controller should drive alerts at a time (the app OR the Pi/PC),
 * otherwise one will switch the other off.
 */

#include <WiFi.h>
#include <WebServer.h>

// ---- Wi-Fi (2.4 GHz only). Fill these in; do not share your real password. ----
const char* WIFI_SSID = "Mosh";
const char* WIFI_PASS = "09666741206";

// Optional: fixed IP so the app's saved address never changes.
// Uncomment and match YOUR network (gateway = your router/hotspot address).
// #define USE_STATIC_IP
#ifdef USE_STATIC_IP
IPAddress STATIC_IP(10, 188, 151, 218);
IPAddress GATEWAY(10, 188, 151, 1);
IPAddress SUBNET(255, 255, 255, 0);
IPAddress DNS1(8, 8, 8, 8);
#endif

WebServer server(80);

// ---- Pin assignments ----
const int motorPins[6] = {4, 16, 17, 18, 19, 21};  // 6 vibration modules
const int buzzerPin = 23;                          // buzzer signal
const int RX_PIN = 26;                             // Serial2 RX <- Pi TX (GPIO14)
const int TX_PIN = 27;                             // Serial2 TX -> Pi RX (GPIO15)

// ---- Timing constants (milliseconds) ----
const unsigned long PULSE_ON_MS = 500;
const unsigned long PULSE_OFF_MS = 300;
const unsigned long FAILSAFE_TIMEOUT_MS = 5000;     // serial-started alerts only
const unsigned long HTTP_ALERT_MAX_MS = 0;          // 0 = no cap; e.g. 120000 = auto-off after 2 min
const unsigned long WIFI_RETRY_MS = 10000;
const unsigned int MAX_LINE_LEN = 120;

// ---- State ----
bool alertActive = false;        // latched alert state
bool alertFromSerial = false;    // who started the current alert
bool pulseOn = false;            // current phase of the pulse
bool readyAnnounced = false;     // re-send READY on first PING so the Pi catches it
bool wifiWasConnected = false;
unsigned long lastPulseChange = 0;
unsigned long lastCommandTime = 0;  // last time ANY serial command/heartbeat arrived
unsigned long alertStartedAt = 0;
unsigned long lastWifiRetry = 0;

// ---- Per-port line buffers (non-blocking accumulation) ----
String usbLine = "";
String piLine = "";

// Turn the buzzer and all 6 motors on/off together.
void setOutputs(bool on) {
  digitalWrite(buzzerPin, on ? HIGH : LOW);
  for (int i = 0; i < 6; i++) {
    digitalWrite(motorPins[i], on ? HIGH : LOW);
  }
}

// Send a status line to both the USB port and Serial2.
void broadcast(const String &msg) {
  Serial.println(msg);
  Serial2.println(msg);
}

void startAlert(bool fromSerial) {
  if (fromSerial) lastCommandTime = millis();
  alertFromSerial = fromSerial;
  if (!alertActive) {
    alertActive = true;
    pulseOn = true;
    lastPulseChange = millis();
    alertStartedAt = millis();
    setOutputs(true);
    broadcast("ALERT_ON");
  }
}

void stopAlert() {
  if (alertActive) {
    alertActive = false;
    pulseOn = false;
    setOutputs(false);
    broadcast("ALERT_OFF");
  } else {
    setOutputs(false);
  }
}

// Fail-safe stop: same as stopAlert but also reports why.
void failSafeStop(const char *reason) {
  alertActive = false;
  pulseOn = false;
  setOutputs(false);
  broadcast("ALERT_OFF");
  broadcast(reason);
}

// ---------- HTTP (mobile app) ----------
void sendJson(int code, const String &body) {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.sendHeader("Cache-Control", "no-store");
  server.send(code, "application/json", body);
}

void handleRoot() {
  server.sendHeader("Cache-Control", "no-store");
  server.send(200, "text/plain", "ESP32 Drowsiness System API is Active!");
}

void handleApiAlert() {
  Serial.println("--> HTTP /alert");
  startAlert(false);
  sendJson(200, "{\"status\":\"success\",\"message\":\"Alert triggered\"}");
}

void handleApiClear() {
  Serial.println("--> HTTP /clear");
  stopAlert();
  sendJson(200, "{\"status\":\"success\",\"message\":\"Alert cleared\"}");
}

void handleApiStatus() {
  String json = "{\"esp32\":\"CONNECTED\",\"alert\":";
  json += alertActive ? "true" : "false";
  json += ",\"rssi\":" + String(WiFi.RSSI());
  json += ",\"uptime_s\":" + String(millis() / 1000) + "}";
  sendJson(200, json);
}

void handleNotFound() {
  sendJson(404, "{\"status\":\"error\",\"message\":\"Not found\"}");
}

// ---------- Serial (Raspberry Pi / PC bridge) ----------
void handleCommand(const String &cmd, bool fromUsb) {
  // Any serial command (including the PING heartbeat) refreshes the fail-safe timer.
  lastCommandTime = millis();

  if (cmd == "PING") {
    // Reply on the port the PING came from so the bridge sees it on its link.
    if (fromUsb) {
      Serial.print("\r\n");
      Serial.println("PONG");
      Serial.flush();
    } else {
      Serial2.print("\r\n");
      Serial2.println("PONG");
      Serial2.flush();
    }
    // Re-announce READY once on the first heartbeat if needed.
    if (!readyAnnounced) {
      readyAnnounced = true;
      broadcast("READY");
    }
  } else if (cmd == "DROWSY_ALERT") {
    startAlert(true);
  } else if (cmd == "CLEAR_ALERT") {
    stopAlert();
  } else if (cmd.length() > 0) {
    Serial.println("UNKNOWN_CMD: " + cmd);
  }
}

// Read available bytes from a port without blocking; dispatch complete lines.
void readPort(Stream &port, String &buffer, bool fromUsb) {
  while (port.available()) {
    char c = (char)port.read();
    if (c == '\n') {
      buffer.trim();
      if (buffer.length() > 0) {
        handleCommand(buffer, fromUsb);
      }
      buffer = "";
    } else if (c != '\r') {
      if (buffer.length() < MAX_LINE_LEN) {
        buffer += c;
      } else {
        buffer = "";  // overflow guard: drop a malformed/runaway line
      }
    }
  }
}

void setup() {
  Serial.begin(115200);                               // USB Serial
  delay(1000);                                        // let USB Serial stabilize
  Serial2.begin(115200, SERIAL_8N1, RX_PIN, TX_PIN);  // UART link

  pinMode(buzzerPin, OUTPUT);
  digitalWrite(buzzerPin, LOW);
  for (int i = 0; i < 6; i++) {
    pinMode(motorPins[i], OUTPUT);
    digitalWrite(motorPins[i], LOW);
  }

  // Non-blocking Wi-Fi: serial control works even if Wi-Fi is down.
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);          // lower latency for /alert and /clear
  WiFi.setAutoReconnect(true);
#ifdef USE_STATIC_IP
  WiFi.config(STATIC_IP, GATEWAY, SUBNET, DNS1);
#endif
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.println("Connecting to Wi-Fi (non-blocking)...");

  server.on("/", handleRoot);
  server.on("/alert", handleApiAlert);
  server.on("/clear", handleApiClear);
  server.on("/status", handleApiStatus);
  server.onNotFound(handleNotFound);
  server.begin();

  lastCommandTime = millis();
  broadcast("READY");
  Serial.println("ESP32 alert controller ready. Serial: DROWSY_ALERT, CLEAR_ALERT, PING. HTTP: /, /alert, /clear, /status");
}

void loop() {
  // 1. HTTP requests from the mobile app
  server.handleClient();

  // 2. Serial commands from the Pi / PC bridge
  readPort(Serial, usbLine, true);
  readPort(Serial2, piLine, false);

  unsigned long now = millis();

  // 3. Wi-Fi status + manual retry (in addition to auto-reconnect)
  bool connected = (WiFi.status() == WL_CONNECTED);
  if (connected && !wifiWasConnected) {
    Serial.println("Wi-Fi connected!");
    Serial.print("ESP32 Web Server IP for Mobile App: http://");
    Serial.println(WiFi.localIP());
  } else if (!connected && wifiWasConnected) {
    Serial.println("Wi-Fi lost, retrying...");
  }
  wifiWasConnected = connected;
  if (!connected && now - lastWifiRetry >= WIFI_RETRY_MS) {
    lastWifiRetry = now;
    WiFi.disconnect();
    WiFi.begin(WIFI_SSID, WIFI_PASS);
  }

  // 4. Alert pulse + safety
  if (alertActive) {
    // Continuous non-blocking pulse: 500 ms ON / 300 ms OFF.
    unsigned long interval = pulseOn ? PULSE_ON_MS : PULSE_OFF_MS;
    if (now - lastPulseChange >= interval) {
      lastPulseChange = now;
      pulseOn = !pulseOn;
      setOutputs(pulseOn);
    }

    // Serial fail-safe: Pi/PC went silent for 5 s while it owns the alert.
    if (alertFromSerial && now - lastCommandTime >= FAILSAFE_TIMEOUT_MS) {
      failSafeStop("FAILSAFE_TIMEOUT");
    }

    // Optional cap for app-started alerts (disabled when HTTP_ALERT_MAX_MS == 0).
    if (!alertFromSerial && HTTP_ALERT_MAX_MS > 0 && now - alertStartedAt >= HTTP_ALERT_MAX_MS) {
      failSafeStop("HTTP_ALERT_TIMEOUT");
    }
  }
}
