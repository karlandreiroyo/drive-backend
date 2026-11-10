/*
 * ESP32 Drowsiness Alert Controller
 * ---------------------------------
 * Drives 6 vibration motors + 1 buzzer from commands sent by the Raspberry Pi.
 *
 * Hardware (logic signals only -- see README: use a transistor/MOSFET driver,
 * the GPIO pins must NOT power motors/buzzer directly):
 *   Motors : GPIO 4, 16, 17, 18, 19, 21
 *   Buzzer : GPIO 23
 *   Serial2: RX = GPIO 26 (<- Pi TX / GPIO14), TX = GPIO 27 (-> Pi RX / GPIO15)
 *   USB    : "Serial" is the USB port the Pi opens as /dev/ttyUSB0
 *
 * Command protocol (newline-terminated ASCII, accepted on BOTH Serial & Serial2):
 *   Pi  -> ESP32 : "PING"          -> ESP32 replies "PONG" on the same port
 *                  "DROWSY_ALERT"  -> alert ON continuously (pulsing)
 *                  "CLEAR_ALERT"   -> alert OFF immediately
 *   ESP32 -> Pi  : "READY" | "ALERT_ON" | "ALERT_OFF" | "FAILSAFE_TIMEOUT" | "PONG"
 *
 * Behaviour:
 *   - Non-blocking: no delay() anywhere. Alert pulses 500 ms ON / 300 ms OFF.
 *   - Alert stays active until CLEAR_ALERT (latched), so it does not flicker.
 *   - Fail-safe: if no command/heartbeat arrives for 5 s while alerting, all
 *     outputs are turned off (protects against a crashed/unplugged Pi).
 */

// ---- Pin assignments (kept exactly as requested) ----
const int motorPins[6] = {4, 16, 17, 18, 19, 21};  // 6 vibration modules
const int buzzerPin = 23;                          // buzzer signal
const int RX_PIN = 26;                             // Serial2 RX <- Pi TX (GPIO14)
const int TX_PIN = 27;                             // Serial2 TX -> Pi RX (GPIO15)

// ---- Timing constants (milliseconds) ----
const unsigned long PULSE_ON_MS = 500;
const unsigned long PULSE_OFF_MS = 300;
const unsigned long FAILSAFE_TIMEOUT_MS = 5000;
const unsigned int MAX_LINE_LEN = 120;

// ---- State ----
bool alertActive = false;       // latched alert state
bool pulseOn = false;           // current phase of the pulse
bool readyAnnounced = false;    // re-send READY on first PING so the Pi catches it
unsigned long lastPulseChange = 0;
unsigned long lastCommandTime = 0;  // last time ANY command/heartbeat arrived

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

void startAlert() {
  lastCommandTime = millis();
  if (!alertActive) {
    alertActive = true;
    pulseOn = true;
    lastPulseChange = millis();
    setOutputs(true);
    broadcast("ALERT_ON");
  }
}

void stopAlert() {
  lastCommandTime = millis();
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
void failSafeStop() {
  alertActive = false;
  pulseOn = false;
  setOutputs(false);
  broadcast("ALERT_OFF");
  broadcast("FAILSAFE_TIMEOUT");
}

void handleCommand(const String &cmd, bool fromUsb) {
  // Any command (including the PING heartbeat) refreshes the fail-safe timer.
  lastCommandTime = millis();

  if (cmd == "PING") {
    // Reply on the port the PING came from so the Pi sees it on its link.
    if (fromUsb) {
      Serial.println("PONG");
    } else {
      Serial2.println("PONG");
    }
    // The Pi flushes the ESP32 boot banner, so re-announce READY once on the
    // first heartbeat. (Harmless duplicate of the setup() READY.)
    if (!readyAnnounced) {
      readyAnnounced = true;
      broadcast("READY");
    }
  } else if (cmd == "DROWSY_ALERT") {
    startAlert();
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
  Serial.begin(115200);                              // USB (Pi /dev/ttyUSB0 + monitor)
  Serial2.begin(115200, SERIAL_8N1, RX_PIN, TX_PIN);  // optional UART link to Pi

  pinMode(buzzerPin, OUTPUT);
  digitalWrite(buzzerPin, LOW);
  for (int i = 0; i < 6; i++) {
    pinMode(motorPins[i], OUTPUT);
    digitalWrite(motorPins[i], LOW);
  }

  lastCommandTime = millis();
  broadcast("READY");
  Serial.println("ESP32 alert controller ready. Commands: DROWSY_ALERT, CLEAR_ALERT, PING");
}

void loop() {
  // Service both ports first (non-blocking).
  readPort(Serial, usbLine, true);
  readPort(Serial2, piLine, false);

  // Capture 'now' AFTER reading so it is >= any timestamp set in handleCommand.
  // (Capturing before would underflow the unsigned maths below and could trip
  //  the fail-safe the instant a command arrives.)
  unsigned long now = millis();

  if (alertActive) {
    // Continuous non-blocking pulse: 500 ms ON / 300 ms OFF.
    unsigned long interval = pulseOn ? PULSE_ON_MS : PULSE_OFF_MS;
    if (now - lastPulseChange >= interval) {
      lastPulseChange = now;
      pulseOn = !pulseOn;
      setOutputs(pulseOn);
    }

    // Fail-safe: Pi went silent for 5 s while alerting -> everything off.
    if (now - lastCommandTime >= FAILSAFE_TIMEOUT_MS) {
      failSafeStop();
    }
  }
}