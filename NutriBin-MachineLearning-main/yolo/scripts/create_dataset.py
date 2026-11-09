
"""Validate a YOLOv8 image-classification dataset."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATASET_DIR = ROOT / "data" / "images"

SPLITS = ("train", "val")
CLASSES = ("Drowsy", "Non Drowsy")
EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def main():
    total = 0

    for split in SPLITS:
        print(f"\nChecking {split} dataset...")

        for cls in CLASSES:
            folder = DATASET_DIR / split / cls

            if not folder.is_dir():
                print(f"ERROR: Missing folder: {folder}")
                return False

            count = sum(
                1 for p in folder.rglob("*")
                if p.is_file() and p.suffix.lower() in EXTENSIONS
            )

            print(f"{split}/{cls}: {count:,} images")

            if count == 0:
                print(f"ERROR: No images found in {folder}")
                return False

            total += count

    print(f"\nTotal images: {total:,}")
    print("Dataset structure verified!")
    print("Dataset is ready for YOLOv8 classification.")

    return True


if __name__ == "__main__":
    main()
