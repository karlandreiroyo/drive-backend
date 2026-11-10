# D.R.I.V.E — Drowsiness Detection System

A two-part driver-drowsiness alert system:

- **Raspberry Pi (Python)** — captures video from a CSI IR-CUT camera, runs a custom
  YOLO **classification** model (`models/best.pt`), and decides when the driver is drowsy.
- **ESP32 (Arduino / C++)** — drives **6 vibration motors + 1 buzzer** from commands the
  Pi sends over USB serial. Non-blocking, latched alert with a 5-second fail-safe.

```
                 USB serial (/dev/ttyUSB0, 115200)
   ┌──────────────────────────┐   PING / DROWSY_ALERT / CLEAR_ALERT   ┌───────────────────────┐
   │      Raspberry Pi        │ ───────────────────────────────────► │        ESP32          │
   │  Picamera2 + YOLO model  │ ◄─────────────────────────────────── │  buzzer + 6 motors    │
   │  main.py / serial_link.py│   PONG / READY / ALERT_ON / ALERT_OFF │  esp32_alert.ino      │
   └──────────────────────────┘                                       └───────────────────────┘
        (optional alternative UART: Pi GPIO14/15  <->  ESP32 Serial2 GPIO26/27)
```

---

## Folder structure

```
drowsiness_system/
├── raspberry_pi/
│   ├── main.py            # detection loop + serial sending + video overlay
│   ├── config.py          # serial port, baud, thresholds, class names, frame counts
│   ├── serial_link.py     # threaded serial: connect, auto-reconnect, send, heartbeat
│   ├── requirements.txt   # ultralytics, opencv-python, pyserial, picamera2
│   └── models/            # <-- put your trained best.pt here
├── esp32/
│   └── esp32_alert/
│       └── esp32_alert.ino # non-blocking alert firmware
└── README.md
```

---

## Wiring table

> **Read the hardware safety notes below before connecting motors.** ESP32 GPIO pins
> are 3.3 V *logic signals* and cannot power motors or a buzzer directly.

| Function | ESP32 GPIO | Connect to | Notes |
|---|---|---|---|
| Vibration motor 1 (signal) | **4** | Driver input CH1 | via MOSFET/driver, external power |
| Vibration motor 2 (signal) | **16** | Driver input CH2 | via MOSFET/driver |
| Vibration motor 3 (signal) | **17** | Driver input CH3 | via MOSFET/driver |
| Vibration motor 4 (signal) | **18** | Driver input CH4 | via MOSFET/driver |
| Vibration motor 5 (signal) | **19** | Driver input CH5 | via MOSFET/driver |
| Vibration motor 6 (signal) | **21** | Driver input CH6 | via MOSFET/driver |
| Buzzer (signal) | **23** | Driver/transistor input | active buzzer assumed |
| Serial2 **RX** | **26** | Pi **TX** = GPIO14 (physical pin 8) | 3.3 V logic, direct OK |
| Serial2 **TX** | **27** | Pi **RX** = GPIO15 (physical pin 10) | 3.3 V logic, direct OK |
| USB (primary link) | — | Pi USB port → `/dev/ttyUSB0` | this is ESP32 `Serial` |
| **GND** | GND | Pi GND (pin 6) + motor supply GND | **common ground required** |

### Hardware safety notes
- **Do not drive motors/buzzer from GPIO directly.** Each vibration motor draws
  ~60–100 mA and a buzzer ~20–30 mA; an ESP32 pin should source ≤ ~12 mA. Use a
  logic-level MOSFET per channel (e.g. AO3400 / IRLZ44N), a driver IC (e.g. ULN2003),
  or vibration **modules that already include a driver transistor** (then GPIO only
  carries the signal).
- **Flyback diode** across each bare motor (ULN2003 has clamp diodes built in) to
  protect the driver from back-EMF.
- **Power motors from an external supply** sized for all 6 motors, not the ESP32
  3V3 pin. Tie that supply's ground to ESP32 GND and Pi GND.
- **GPIO16/17 caveat:** on ESP32-**WROVER** modules (with PSRAM), GPIO16 and 17 are
  wired to the PSRAM and are **not** usable as general GPIO. If you have a WROVER,
  move motors 2 and 3 to other free pins (e.g. 25, 32) and update `motorPins[]` in the
  sketch. Standard ESP32-WROOM-32 / Dev Module boards expose 16/17 fine.
- **Buzzer type:** this firmware uses `digitalWrite` (HIGH = on), which suits an
  **active** buzzer. A **passive** buzzer needs `tone()`/PWM instead.
- **Pi ↔ ESP32 UART:** both are 3.3 V logic, so GPIO26/27 ↔ GPIO14/15 can be wired
  directly — just share ground. Do **not** feed 5 V into ESP32 pins.
- **IR-CUT camera:** attach the CSI ribbon to the Pi camera port with the contacts
  facing the correct way; IR LEDs are needed for night vision.

---

## Command protocol

All messages are newline-terminated ASCII (`\n`). The ESP32 accepts commands on
**both** USB `Serial` and `Serial2`, and replies `PONG` on the port the `PING` came from.

| Direction | Message | Meaning |
|---|---|---|
| Pi → ESP32 | `PING` | Heartbeat, sent every **1 s** |
| ESP32 → Pi | `PONG` | Heartbeat reply |
| Pi → ESP32 | `DROWSY_ALERT` | Latch alert **ON** (buzzer + 6 motors pulse 500 ms ON / 300 ms OFF) |
| Pi → ESP32 | `CLEAR_ALERT` | Alert **OFF** immediately |
| ESP32 → Pi | `READY` | Controller booted / first contact |
| ESP32 → Pi | `ALERT_ON` | Alert activated |
| ESP32 → Pi | `ALERT_OFF` | Alert cleared (manual clear or fail-safe) |
| ESP32 → Pi | `FAILSAFE_TIMEOUT` | No command/heartbeat for **5 s** while alerting → outputs forced off |

**Alert logic on the Pi:** raise the alert after **5 consecutive** drowsy frames
(above the confidence threshold); clear it only after **5 consecutive** awake frames
(hysteresis, so a single noisy frame does not make the alert flicker).

**Fail-safe:** if the Pi crashes or is unplugged mid-alert, the ESP32 stops everything
after 5 s of silence. A live Pi sends `PING` every 1 s, which keeps the alert alive.

**Reconnect re-sync:** opening the serial port resets most ESP32 boards (DTR/RTS), so
after a USB unplug/replug or a brown-out the Pi re-asserts its current alert state the
moment the PING/PONG handshake recovers. A driver who is still drowsy keeps the alert
after a reconnect instead of silently losing it.

---

## Setup — ESP32 (Arduino IDE)

1. Install **Arduino IDE 2.x**.
2. Add ESP32 board support: **File → Preferences → Additional board manager URLs**, paste:
   ```
   https://espressif.github.io/arduino-esp32/package_esp32_index.json
   ```
3. **Tools → Board → Boards Manager**, search **esp32**, install **"esp32 by Espressif Systems"**.
4. **Tools → Board** → select your board (e.g. **ESP32 Dev Module**).
5. Open `drowsiness_system/esp32/esp32_alert/esp32_alert.ino`.
6. Connect the ESP32 by USB. **Tools → Port** → select it (`COMx` on Windows,
   `/dev/ttyUSBx` on Linux/Pi).
7. Click **Upload (→)** and wait for *Done uploading*.
8. **Tools → Serial Monitor**, set **115200 baud** and line ending "Newline". You should
   see `READY` and the ready banner.

> Compile from the CLI instead? `arduino-cli compile --fqbn esp32:esp32:esp32 esp32_alert`
> then `arduino-cli upload -p <PORT> --fqbn esp32:esp32:esp32 esp32_alert`.

---

## Setup — Raspberry Pi

1. Use **Raspberry Pi OS (Bookworm) with desktop** (the video window needs a display).
   Enable/verify the camera: `sudo raspi-config` → **Interface Options → Camera**, then reboot.
2. Check the camera is detected:
   ```bash
   rpicam-hello --list-cameras     # (older OS: libcamera-hello --list-cameras)
   ```
3. Install dependencies. **Picamera2 + OpenCV come from apt** on the Pi (pip versions
   can conflict with the system libcamera stack):
   ```bash
   sudo apt update
   sudo apt install -y python3-picamera2 python3-opencv python3-serial
   pip install --break-system-packages ultralytics pyserial
   ```
   Prefer a virtual environment? Use system site packages so the apt modules are visible:
   ```bash
   python3 -m venv --system-site-packages venv
   source venv/bin/activate
   pip install ultralytics pyserial
   ```
   > `ultralytics` pulls in PyTorch, which is large. On a Pi you may need Pi-compatible
   > torch wheels — allow time and check for arm64/aarch64 builds if the install fails.
4. Copy `drowsiness_system/raspberry_pi/` to the Pi and put your trained weights at
   `raspberry_pi/models/best.pt` (a **classification** model).
5. Find the ESP32 serial port and, if it differs, edit `SERIAL_PORT` in `config.py`:
   ```bash
   ls /dev/ttyUSB* /dev/ttyACM*
   ```
6. Grant serial permission (then log out and back in):
   ```bash
   sudo usermod -aG dialout $USER
   ```
7. Run it:
   ```bash
   cd raspberry_pi
   python3 main.py
   ```
   Press **Q** in the video window (or **Ctrl+C**) to exit. Camera and serial are released
   automatically.

---

## Test with Serial Monitor (no Pi required)

Open **Tools → Serial Monitor** at **115200 baud**, line ending **Newline**, then type:

| Type this | You should see / happen |
|---|---|
| `PING` | `PONG` |
| `DROWSY_ALERT` | `ALERT_ON`; buzzer + all 6 motors pulse 500 ms ON / 300 ms OFF **continuously** |
| `CLEAR_ALERT` | `ALERT_OFF`; everything stops **immediately** |
| `DROWSY_ALERT`, then wait 5 s | `ALERT_OFF` + `FAILSAFE_TIMEOUT` (fail-safe kicks in) |
| `DROWSY_ALERT`, then `PING` every <5 s | alert **stays on** (heartbeat keeps it alive) |

This lets you validate the firmware and wiring before involving the Pi/camera.

---

## Configuration (`raspberry_pi/config.py`)

| Setting | Default | Purpose |
|---|---|---|
| `SERIAL_PORT` | `/dev/ttyUSB0` | ESP32 USB port (may be `/dev/ttyACM0`) |
| `SERIAL_BAUD` | `115200` | must match the firmware |
| `PING_INTERVAL` | `1.0` s | heartbeat rate |
| `PONG_TIMEOUT` | `3.0` s | no PONG for this long → DISCONNECTED |
| `SERIAL_RECONNECT_DELAY` | `2.0` s | gap between reconnect attempts |
| `MODEL_PATH` | `models/best.pt` | trained classification weights |
| `DROWSY_CLASSES` | `drowsy, sleepy, closed` | class names treated as drowsy (case-insensitive) |
| `CONFIDENCE_THRESHOLD` | `0.6` | minimum top-1 confidence |
| `DROWSY_FRAME_COUNT` | `5` | consecutive drowsy frames to raise alert |
| `CLEAR_FRAME_COUNT` | `5` | consecutive awake frames to clear alert |
| `CAMERA_WIDTH/HEIGHT` | `640 / 480` | capture resolution |

---

## Troubleshooting

- **"Could not open /dev/ttyUSB0"** — wrong port (try `/dev/ttyACM0`), not in `dialout`
  group, or a charge-only USB cable. Confirm with `ls /dev/ttyUSB* /dev/ttyACM*`.
- **Video shows "ESP32: DISCONNECTED" but the port is open** — baud mismatch, firmware not
  uploaded, or (for the UART link) TX/RX swapped. Check Serial Monitor prints `READY`/`PONG`.
- **Camera error** — reseat the ribbon, enable the camera, run `rpicam-hello --list-cameras`,
  and make sure `python3-picamera2` is installed via apt.
- **"model output has no classification 'probs'"** — `best.pt` is a *detection* model.
  Export/train a YOLO **classification** model so `results[0].probs.top1` exists.
- **Motors weak or silent** — you are driving them from GPIO directly. Use a driver board +
  external power (see safety notes).
- **Alert never clears / flickers** — tune `CONFIDENCE_THRESHOLD`, `DROWSY_FRAME_COUNT`,
  and `CLEAR_FRAME_COUNT` in `config.py`.
- **Phone Live Feed shows "ESP32: DISCONNECTED" though the Pi overlay says CONNECTED** —
  the backend bridge isn't reaching the app. Check: `DEVICE_API_KEY` matches on Railway and
  in `config.py`; `DEVICE_USER_ID` is the UUID you log into the app with; the backend was
  redeployed after adding the variable; the Pi logs `[backend] started` (not `disabled`).

---

## Connecting to the D.R.I.V.E app (implemented)

The Pi keeps running `best.pt` and driving the ESP32 locally, and **also** reports its
live status to the backend so the phone app's **Live Feed** shows
`ESP32: CONNECTED / DISCONNECTED` (plus a `Drowsy alert` marker). The phone is a
dashboard for the hardware node — detection still happens on the Pi, not the phone.

```
Pi (best.pt + ESP32)                          Phone app (Live Feed)
   │  POST /api/device/heartbeat  ──►  Backend  ──►  GET /api/device/status
   │  every 2 s {esp32Connected,       (in-memory       polls every 2.5 s while
   │   alertActive, lastDetection,      latest status   a session is active;
   │   lastConfidence} + x-device-key   per user)       shows CONNECTED/DISCONNECTED
```

- `POST /api/device/heartbeat` — Pi → backend, protected by a shared `DEVICE_API_KEY`
  (sent as the `x-device-key` header). Body keyed by the driver's user UUID.
- `GET /api/device/status` — phone → backend, authenticated; returns the caller's own
  node status. A node is shown offline if no heartbeat arrives for ~7 s.
- The bridge is **optional**: leave `DEVICE_USER_ID` / `DEVICE_API_KEY` empty in
  `config.py` and the Pi still detects + drives the ESP32 (the phone just shows DISCONNECTED).
- Uses only the Python standard library (`backend_link.py`), so no extra pip package.

### Setup (one-time)

1. **Railway** → your backend service → **Variables** → add, then **redeploy** (the new
   `/api/device/*` routes only go live after a deploy):
   ```
   DEVICE_API_KEY = <paste a long random secret>
   ```
2. **`raspberry_pi/config.py`** → set the same secret and your user id:
   ```python
   DEVICE_USER_ID = "<your user UUID>"   # Supabase → Table Editor → users → copy your row's id
   DEVICE_API_KEY = "<the SAME secret you put on Railway>"
   ```
   `DEVICE_USER_ID` must be the UUID of the account you log into the app with.
3. Run the Pi (`python3 main.py`). You should see `[backend] started -> ...`. Start a
   driving session in the app; the Live Feed shows `ESP32: CONNECTED` when the Pi's
   handshake is up.

> The web **Users & Devices** dashboard still uses sample data; persisting device
> registration/history is a separate future addition. This bridge provides the
> **live** connection/alert status only.