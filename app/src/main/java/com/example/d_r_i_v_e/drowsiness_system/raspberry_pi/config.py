"""Central configuration for the Raspberry Pi drowsiness-detection node.

Everything you are likely to tune lives here so main.py and serial_link.py stay
generic. Paths are resolved relative to this file, so you can launch the script
from any working directory.
"""

from pathlib import Path
import os

# --------------------------------------------------------------------------- #
# Serial link to the ESP32 (USB). On many Pi setups the ESP32 enumerates as
# /dev/ttyUSB0 (CH340/CP210x) or /dev/ttyACM0 (native USB). Check with:
#   ls /dev/ttyUSB* /dev/ttyACM*
# --------------------------------------------------------------------------- #
SERIAL_PORT = "/dev/ttyUSB0"
SERIAL_BAUD = 115200
SERIAL_TIMEOUT = 0.1          # read timeout (s); small so the worker stays responsive
SERIAL_WRITE_TIMEOUT = 0.5    # give up on a write after this many seconds
SERIAL_RECONNECT_DELAY = 2.0  # seconds between (re)connection attempts
SERIAL_BOOT_WAIT = 2.0        # ESP32 auto-resets when the port opens; wait for boot

# --------------------------------------------------------------------------- #
# Handshake / heartbeat. Pi sends PING every PING_INTERVAL seconds; if no PONG
# arrives within PONG_TIMEOUT seconds the link is shown as DISCONNECTED.
# --------------------------------------------------------------------------- #
PING_INTERVAL = 1.0
PONG_TIMEOUT = 3.0

# --------------------------------------------------------------------------- #
# Model. This is a YOLO *classification* model (uses results[0].probs.top1).
# Drop your trained weights at raspberry_pi/models/best.pt
# --------------------------------------------------------------------------- #
BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = str(BASE_DIR / "models" / "best.pt")

# Class names (case-insensitive) that count as drowsy.
DROWSY_CLASSES = {"drowsy", "sleepy", "closed"}

# Minimum top-1 confidence required to treat a frame as drowsy.
CONFIDENCE_THRESHOLD = 0.6

# --------------------------------------------------------------------------- #
# Debounce / hysteresis (anti-flicker).
#   * DROWSY_FRAME_COUNT consecutive drowsy frames  -> raise the alert.
#   * CLEAR_FRAME_COUNT  consecutive awake frames   -> clear the alert.
# Keeping the alert latched until several clear frames arrive stops a single
# noisy frame from making the buzzer/motors flicker on and off.
# --------------------------------------------------------------------------- #
DROWSY_FRAME_COUNT = 5
CLEAR_FRAME_COUNT = 5

# --------------------------------------------------------------------------- #
# Camera (CSI IR-CUT module via Picamera2).
# --------------------------------------------------------------------------- #
CAMERA_WIDTH = 640
CAMERA_HEIGHT = 480
CAMERA_FORMAT = "RGB888"

# --------------------------------------------------------------------------- #
# Display.
# --------------------------------------------------------------------------- #
WINDOW_NAME = "Drowsiness Detection"
DISCONNECT_WARN_INTERVAL = 3.0  # rate-limit the console warning while disconnected

# --------------------------------------------------------------------------- #
# Backend bridge (optional). The Pi reports live ESP32/alert status to the
# D.R.I.V.E backend so the phone app's Live Feed shows CONNECTED/DISCONNECTED.
# Leave DEVICE_USER_ID / DEVICE_API_KEY empty to disable the bridge entirely
# (detection + ESP32 still work). Uses only the Python standard library.
#   * DEVICE_USER_ID = the driver's user UUID (the account logged into the app;
#                      Supabase -> Table Editor -> users -> copy your row's id).
#   * DEVICE_API_KEY = the SAME secret set as DEVICE_API_KEY on Railway.
# Both come from the environment so this file can live in git without secrets:
#   export DEVICE_USER_ID="your-user-uuid"
#   export DEVICE_API_KEY="the-railway-device-key"
# --------------------------------------------------------------------------- #
BACKEND_BASE_URL = "https://drive-backend.up.railway.app"
DEVICE_USER_ID = os.environ.get("DEVICE_USER_ID", "40")
DEVICE_API_KEY = os.environ.get("DEVICE_API_KEY", "c410909fb3fda8f5f30328a38f88c71759c0a36ecd0cdfca")
HEARTBEAT_INTERVAL = 2.0   # seconds between status posts to the backend
BACKEND_TIMEOUT = 3.0      # give up on a backend POST after this many seconds