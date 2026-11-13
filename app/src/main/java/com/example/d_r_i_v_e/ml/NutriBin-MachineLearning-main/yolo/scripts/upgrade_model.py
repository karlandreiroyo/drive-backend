"""Fine-tune a YOLOv8 image-classification model for driver drowsiness.

Expected dataset structure:
    yolo/data/images/
      train/Drowsy/
      train/Non Drowsy/
      val/Drowsy/
      val/Non Drowsy/

This script does not use data.yaml or bounding-box labels. It loads a
classification checkpoint and trains it on the folder-based dataset.
The original checkpoint is not overwritten; each run gets its own output folder.

Example (run from the yolo directory):
    python scripts/upgrade_model.py --base-weights yolov8n-cls.pt \
        --epochs 100 --imgsz 960 --batch 8 --device auto
"""

from pathlib import Path
import argparse
import time
import sys

ROOT = Path(__file__).resolve().parents[1]
DATASET_DIR = ROOT / "data" / "images"
OUTPUT_DIR = ROOT / "outputs"
CLASSES = ("Drowsy", "Non Drowsy")
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def validate_dataset():
    """Validate the classification folder structure and count images."""
    total = 0
    for split in ("train", "val"):
        for class_name in CLASSES:
            folder = DATASET_DIR / split / class_name
            if not folder.is_dir():
                raise FileNotFoundError(f"Missing dataset folder: {folder}")

            count = sum(
                1 for path in folder.rglob("*")
                if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS
            )
            print(f"{split}/{class_name}: {count:,} images")
            if count == 0:
                raise ValueError(f"No images found in: {folder}")
            total += count

    print(f"\nTotal images found: {total:,}")
    print("Classification dataset structure verified.")


def resolve_weights_path(value):
    """Resolve relative weight paths from the yolo project directory."""
    path = Path(value).expanduser()
    if not path.is_absolute():
        path = ROOT / path
    return path.resolve()


def choose_device(requested):
    if requested.lower() != "auto":
        if requested.lower() == "cpu":
            return "cpu"
        try:
            import torch
            if not torch.cuda.is_available():
                raise RuntimeError(
                    f"CUDA device '{requested}' requested, but CUDA is unavailable. "
                    "Choose --device cpu or --device auto."
                )
        except ImportError:
            raise RuntimeError("PyTorch is not installed in this environment.")
        return requested

    try:
        import torch
        return "0" if torch.cuda.is_available() else "cpu"
    except ImportError:
        return "cpu"


def main():
    parser = argparse.ArgumentParser(
        description="Fine-tune an existing YOLOv8 classification model."
    )
    parser.add_argument("--base-weights", required=True,
                        help="Path to a classification checkpoint (.pt)")
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--imgsz", type=int, default=960)
    parser.add_argument("--batch", type=int, default=8)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--device", default="auto",
                        help="auto, cpu, or CUDA device index such as 0")
    args = parser.parse_args()

    if min(args.epochs, args.imgsz, args.batch) < 1:
        parser.error("epochs, imgsz, and batch must be positive integers")
    if args.workers < 0:
        parser.error("workers cannot be negative")

    weights_path = resolve_weights_path(args.base_weights)
    if not weights_path.is_file():
        raise FileNotFoundError(f"Classification weights not found: {weights_path}")

    print("Validating classification dataset...")
    validate_dataset()

    try:
        from ultralytics import YOLO
    except ImportError:
        print("Ultralytics is not installed. Run: python -m pip install ultralytics")
        sys.exit(1)

    print(f"\nLoading model: {weights_path}")
    model = YOLO(str(weights_path))

    if getattr(model, "task", None) != "classify":
        raise ValueError(
            f"The selected checkpoint is a '{getattr(model, 'task', 'unknown')}' model, "
            "not a classification model. Select yolov8n-cls.pt or a classification-trained best.pt. "
            "Your original checkpoint has not been changed."
        )

    device = choose_device(args.device)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = time.strftime("%Y%m%d-%H%M%S")
    run_name = f"classification_upgrade_{weights_path.stem}_{timestamp}"

    print("\nStarting YOLOv8 classification fine-tuning...")
    print(f"Dataset: {DATASET_DIR}")
    print(f"Epochs: {args.epochs}")
    print(f"Image size: {args.imgsz}")
    print(f"Batch size: {args.batch}")
    print(f"Device: {device}")
    print("The source weights will not be overwritten.")

    results = model.train(
        data=str(DATASET_DIR),
        epochs=args.epochs,
        imgsz=args.imgsz,
        batch=args.batch,
        device=device,
        project=str(OUTPUT_DIR),
        name=run_name,
        exist_ok=False,
        patience=20,
        workers=args.workers,
        pretrained=True,
    )

    save_dir = Path(getattr(results, "save_dir", OUTPUT_DIR / run_name))
    best_weights = save_dir / "weights" / "best.pt"
    last_weights = save_dir / "weights" / "last.pt"

    if not best_weights.is_file():
        raise RuntimeError(
            "Training returned, but best.pt was not found. "
            f"Check the training output folder: {save_dir}"
        )

    print("\nCLASSIFICATION FINE-TUNING COMPLETED.")
    print(f"Best model: {best_weights}")
    if last_weights.is_file():
        print(f"Last model: {last_weights}")
    print(f"Training results: {save_dir}")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"\nTRAINING FAILED: {error}")
        sys.exit(1)
