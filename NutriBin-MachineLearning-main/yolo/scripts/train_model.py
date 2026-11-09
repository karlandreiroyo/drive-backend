
"""Train a YOLO classification model for driver drowsiness detection.

Expected dataset structure:
  yolo/data/images/
  ├── train/
  │   ├── Drowsy/
  │   └── Non Drowsy/
  └── val/
      ├── Drowsy/
      └── Non Drowsy/

Usage:
  python yolo/scripts/train_model.py --epochs 30
"""

from pathlib import Path
import argparse
from datetime import datetime
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]
DATASET_DIR = ROOT / "data" / "images"
OUTPUT_DIR = ROOT / "outputs"

CLASSES = ("Drowsy", "Non Drowsy")
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def validate_dataset():
    """Check that training and validation images exist."""
    total_images = 0

    for split in ("train", "val"):
        for cls in CLASSES:
            folder = DATASET_DIR / split / cls

            if not folder.is_dir():
                print(f"ERROR: Missing dataset folder: {folder}")
                return False

            count = sum(
                1 for path in folder.rglob("*")
                if path.is_file()
                and path.suffix.lower() in IMAGE_EXTENSIONS
            )

            print(f"{split}/{cls}: {count:,} images")

            if count == 0:
                print(f"ERROR: No images found in {folder}")
                return False

            total_images += count

    print(f"\nTotal images found: {total_images:,}")
    return True


def main():
    parser = argparse.ArgumentParser(
        description="Train a YOLO image-classification model."
    )
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--imgsz", type=int, default=224)
    parser.add_argument("--batch", type=int, default=8)
    parser.add_argument("--workers", type=int, default=4)
    args = parser.parse_args()

    if args.epochs < 1 or args.imgsz < 1 or args.batch < 1:
        parser.error("epochs, imgsz, and batch must be positive integers.")

    if not validate_dataset():
        print("\nTraining cancelled. Fix the dataset folders first.")
        sys.exit(1)

    try:
        from ultralytics import YOLO
    except ImportError:
        print("Ultralytics is not installed. Run:")
        print("python -m pip install ultralytics")
        sys.exit(1)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    run_name = f"yolo_training_{timestamp}"

    # Classification model: predicts Drowsy or Non Drowsy.
    model = YOLO("yolov8n-cls.pt")

    print("\nStarting YOLO classification training...")
    print(f"Dataset: {DATASET_DIR}")
    print(f"Epochs: {args.epochs}")
    print(f"Image size: {args.imgsz}")
    print(f"Batch size: {args.batch}")

    results = model.train(
        data=str(DATASET_DIR),
        epochs=args.epochs,
        imgsz=args.imgsz,
        batch=args.batch,
        project=str(OUTPUT_DIR),
        name=run_name,
        exist_ok=False,
        patience=20,
        workers=args.workers,
    )

    training_dir = OUTPUT_DIR / run_name
    weights_dir = training_dir / "weights"

    if results and weights_dir.exists():
        for weight_name in ("best.pt", "last.pt"):
            source = weights_dir / weight_name

            if source.exists():
                destination = OUTPUT_DIR / f"{timestamp}_{weight_name}"
                shutil.copy2(source, destination)
                print(f"Saved model: {destination}")

    print(f"\nTraining run finished. Results: {training_dir}")


if __name__ == "__main__":
    main()
