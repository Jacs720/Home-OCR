from pathlib import Path

from paddleocr import PaddleOCR


ROOT = Path(__file__).resolve().parents[1]

INPUT_DIR = ROOT / "input"
OUTPUT_DIR = ROOT / "output"
JSON_DIR = OUTPUT_DIR / "json"
OCR_IMAGE_DIR = OUTPUT_DIR / "ocr_images"

JSON_DIR.mkdir(parents=True, exist_ok=True)
OCR_IMAGE_DIR.mkdir(parents=True, exist_ok=True)

ocr = PaddleOCR(
    lang="japan",
    use_doc_orientation_classify=False,
    use_doc_unwarping=False,
    use_textline_orientation=False,
)

for image_path in sorted(INPUT_DIR.iterdir()):
    if image_path.suffix.lower() not in {
        ".png",
        ".jpg",
        ".jpeg",
        ".webp",
    }:
        continue

    results = ocr.predict(str(image_path))

    for result in results:
        result.save_to_json(str(JSON_DIR))
        result.save_to_img(str(OCR_IMAGE_DIR))