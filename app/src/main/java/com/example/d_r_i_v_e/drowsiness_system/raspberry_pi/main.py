"""Raspberry Pi drowsiness-detection node.

Pipeline:
    Picamera2 (CSI IR-CUT, 640x480) -> YOLO classification (best.pt)
        -> hysteresis debounce -> DROWSY_ALERT / CLEAR_ALERT over USB serial
        -> ESP32 drives buzzer + 6 vibration motors.

Detection keeps running even if the ESP32 is unplugged; the serial link
auto-reconnects in the background (see serial_link.py). Press Q in the video
window (or Ctrl+C in the terminal) to exit. Camera and serial are always
released in the finally block.
"""

import time

import cv2
from picamera2 import Picamera2
from ultralytics import YOLO

import config
from backend_link import BackendLink
from serial_link import SerialLink


def draw_line(frame, text, row, color):
    """Draw one overlay row; row 0 is the top line."""
    cv2.putText(
        frame, text, (20, 40 + row * 35),
        cv2.FONT_HERSHEY_SIMPLEX, 0.8, color, 2, cv2.LINE_AA,
    )


def main():
    link = None
    backend = None
    picam2 = None
    frame_count = 0

    try:
        # --- Serial link (background thread, auto-reconnect, PING/PONG) ---
        print(f"Starting serial link to ESP32 on {config.SERIAL_PORT} @ {config.SERIAL_BAUD}...")
        link = SerialLink(
            config.SERIAL_PORT,
            config.SERIAL_BAUD,
            timeout=config.SERIAL_TIMEOUT,
            write_timeout=config.SERIAL_WRITE_TIMEOUT,
            reconnect_delay=config.SERIAL_RECONNECT_DELAY,
            boot_wait=config.SERIAL_BOOT_WAIT,
            ping_interval=config.PING_INTERVAL,
            pong_timeout=config.PONG_TIMEOUT,
            logger=lambda msg: print(f"[serial] {msg}"),
        )
        link.start()

        # --- Backend bridge (best-effort live status to the phone app) ---
        backend = BackendLink(
            config.BACKEND_BASE_URL,
            config.DEVICE_USER_ID,
            config.DEVICE_API_KEY,
            heartbeat_interval=config.HEARTBEAT_INTERVAL,
            timeout=config.BACKEND_TIMEOUT,
            logger=lambda msg: print(f"[backend] {msg}"),
        )
        backend.start()

        # --- YOLO classification model ---
        print(f"Loading model: {config.MODEL_PATH}")
        model = YOLO(config.MODEL_PATH)
        print(f"Trained class names: {model.names}")

        # --- CSI IR-CUT camera via Picamera2 ---
        print("Initializing Raspberry Pi IR-CUT camera...")
        picam2 = Picamera2()
        picam2.configure(
            picam2.create_video_configuration(
                main={
                    "size": (config.CAMERA_WIDTH, config.CAMERA_HEIGHT),
                    "format": config.CAMERA_FORMAT,
                }
            )
        )
        picam2.start()
        print("Live detection started. Press Q in the video window to exit.")

        drowsy_streak = 0
        clear_streak = 0
        alert_active = False
        last_status = "READY"
        last_warn = 0.0
        warned_no_probs = False
        prev_handshake = False

        while True:
            frame = picam2.capture_array()
            if frame is None:
                print("Failed to capture camera frame.")
                break
            frame_count += 1
            frame_bgr = cv2.cvtColor(frame, cv2.COLOR_RGB2BGR)

            # --- Inference (classification: top-1 class + confidence) ---
            results = model(frame_bgr, verbose=False)
            probs = results[0].probs
            if probs is None:
                if not warned_no_probs:
                    print(
                        "ERROR: model output has no classification 'probs'. "
                        "best.pt must be a YOLO *classification* model, not detection."
                    )
                    warned_no_probs = True
                class_name, confidence, is_drowsy = "unknown", 0.0, False
            else:
                top_id = int(probs.top1)
                class_name = results[0].names[top_id]
                confidence = float(probs.top1conf.item())
                is_drowsy = (
                    class_name.lower() in config.DROWSY_CLASSES
                    and confidence >= config.CONFIDENCE_THRESHOLD
                )

            # --- Hysteresis debounce (anti-flicker) ---
            if is_drowsy:
                drowsy_streak += 1
                clear_streak = 0
            else:
                clear_streak += 1
                drowsy_streak = 0

            if not alert_active and drowsy_streak >= config.DROWSY_FRAME_COUNT:
                alert_active = True
                link.send("DROWSY_ALERT")
                print("--> Sent: DROWSY_ALERT")
            elif alert_active and clear_streak >= config.CLEAR_FRAME_COUNT:
                alert_active = False
                link.send("CLEAR_ALERT")
                print("--> Sent: CLEAR_ALERT")

            # --- Drain ESP32 status messages (READY / ALERT_ON / ALERT_OFF) ---
            for message in link.update():
                last_status = message
                print(f"ESP32 says: {message}")

            # --- Handshake state + rate-limited disconnect warning ---
            connected = link.handshake_ok
            now = time.monotonic()
            if not connected:
                if now - last_warn >= config.DISCONNECT_WARN_INTERVAL:
                    last_warn = now
                    print("WARNING: ESP32 DISCONNECTED (no PONG). Detection continues; check USB.")
            else:
                last_warn = 0.0

            # Re-assert the alert whenever the handshake comes back up. Opening
            # the port resets most ESP32 boards (DTR/RTS), wiping the latched
            # alert; without this, a reconnect mid-drowsiness would leave the
            # driver with no buzzer/motors even though alert_active is still True.
            if connected and not prev_handshake:
                link.send("DROWSY_ALERT" if alert_active else "CLEAR_ALERT")
            prev_handshake = connected

            # Report live status to the backend (no-op when the bridge is disabled).
            backend.set_state(connected, alert_active, class_name, confidence)

            # --- Video overlay ---
            state_color = (0, 0, 255) if is_drowsy else (0, 255, 0)
            draw_line(frame_bgr, f"Status: {class_name} ({confidence:.2f})", 0, state_color)
            draw_line(
                frame_bgr,
                f"Alert: {'ON' if alert_active else 'OFF'}  "
                f"(drowsy {drowsy_streak}/{config.DROWSY_FRAME_COUNT})",
                1, (0, 0, 255) if alert_active else (0, 255, 0),
            )
            draw_line(
                frame_bgr,
                "ESP32: CONNECTED" if connected else "ESP32: DISCONNECTED",
                2, (0, 255, 0) if connected else (0, 0, 255),
            )
            draw_line(frame_bgr, f"ESP32 status: {last_status}", 3, (255, 255, 255))

            cv2.imshow(config.WINDOW_NAME, frame_bgr)
            if cv2.waitKey(1) & 0xFF == ord("q"):
                break

    except KeyboardInterrupt:
        print("\nInterrupted by user.")
    except FileNotFoundError as exc:
        print(
            "ERROR: model weights not found. Place best.pt in raspberry_pi/models/. "
            f"Details: {exc}"
        )
    except Exception as exc:  # noqa: BLE001 - top-level guard so resources always release
        print(f"ERROR: {exc}")
    finally:
        # Best-effort: tell the ESP32 to stop, give the worker a moment to flush,
        # then close everything. If the ESP32 is gone its 5s fail-safe covers us.
        if link is not None:
            try:
                link.send("CLEAR_ALERT")
                time.sleep(0.2)
            except Exception:  # noqa: BLE001
                pass
            link.stop()
        if backend is not None:
            backend.stop()
        if picam2 is not None:
            try:
                picam2.stop()
                picam2.close()
            except Exception:  # noqa: BLE001
                pass
        cv2.destroyAllWindows()
        print(f"Frames processed: {frame_count}")


if __name__ == "__main__":
    main()