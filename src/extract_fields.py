from __future__ import annotations

import csv
import json
import re
from pathlib import Path
from typing import Any, Callable
from icon_detector import detect_known_icons


ROOT = Path(__file__).resolve().parents[1]

JSON_PATH = ROOT / "output" / "captura_prueba_res.json"
IMAGE_PATH = ROOT / "input" / "captura_prueba.png"
CSV_PATH = ROOT / "output" / "pokemon_collection.csv"

CSV_COLUMNS = [
    "No.",
    "Especie",
    "Forma",
    "Marca de origen",
    "Shiny",
    "OT",
    "IDNo.",
    "Bola",
    "Idioma",
]

# Mapa temporal para comprobar el prototipo.
# Después lo sustituiremos por una Pokédex completa en CSV.
SPECIES_BY_NUMBER = {
    1: "Bulbasaur",
}


def load_detections(json_path: Path) -> list[dict[str, Any]]:
    """Convierte la respuesta de PaddleOCR en una lista más sencilla."""

    if not json_path.exists():
        raise FileNotFoundError(
            f"No se encontró el archivo JSON:\n{json_path}"
        )

    with json_path.open("r", encoding="utf-8") as file:
        data = json.load(file)

    texts = data.get("rec_texts", [])
    scores = data.get("rec_scores", [])
    boxes = data.get("rec_boxes", [])

    if not (len(texts) == len(scores) == len(boxes)):
        raise ValueError(
            "El JSON contiene cantidades diferentes de textos, "
            "puntuaciones y coordenadas."
        )

    detections: list[dict[str, Any]] = []

    for text, score, box in zip(texts, scores, boxes):
        x1, y1, x2, y2 = box

        detections.append(
            {
                "text": str(text).strip(),
                "score": float(score),
                "box": box,
                "cx": (x1 + x2) / 2,
                "cy": (y1 + y2) / 2,
            }
        )

    return detections


def find_by_regex(
    detections: list[dict[str, Any]],
    pattern: str,
    minimum_score: float = 0.60,
) -> dict[str, Any] | None:
    """Busca el resultado con mayor confianza que coincida con un patrón."""

    regex = re.compile(pattern, re.IGNORECASE)

    matches = [
        detection
        for detection in detections
        if detection["score"] >= minimum_score
        and regex.search(detection["text"])
    ]

    if not matches:
        return None

    return max(matches, key=lambda detection: detection["score"])


def find_value_to_right(
    detections: list[dict[str, Any]],
    label_patterns: list[str],
    candidate_filter: Callable[[dict[str, Any]], bool],
    maximum_vertical_distance: float = 90,
) -> dict[str, Any] | None:
    """Encuentra el valor situado a la derecha de una etiqueta."""

    labels = []

    for detection in detections:
        for pattern in label_patterns:
            if re.search(pattern, detection["text"], re.IGNORECASE):
                labels.append(detection)
                break

    if not labels:
        return None

    label = max(labels, key=lambda detection: detection["score"])

    candidates = [
        detection
        for detection in detections
        if detection["cx"] > label["cx"]
        and abs(detection["cy"] - label["cy"])
        <= maximum_vertical_distance
        and candidate_filter(detection)
    ]

    if not candidates:
        return None

    return min(
        candidates,
        key=lambda detection: (
            abs(detection["cy"] - label["cy"]),
            detection["cx"] - label["cx"],
        ),
    )


def extract_national_number(
    detections: list[dict[str, Any]],
) -> tuple[int, dict[str, Any]]:
    number_detection = find_by_regex(
        detections,
        r"\bNo\.?\s*0*(\d{1,4})\b",
        minimum_score=0.70,
    )

    if number_detection is None:
        raise ValueError("No se pudo encontrar el número nacional.")

    match = re.search(
        r"No\.?\s*0*(\d{1,4})",
        number_detection["text"],
        re.IGNORECASE,
    )

    if match is None:
        raise ValueError("No se pudo limpiar el número nacional.")

    return int(match.group(1)), number_detection


def extract_language(detections: list[dict[str, Any]]) -> str:
    language = find_by_regex(
        detections,
        r"\b[A-Z]{2}-[A-Z]{2}\b",
        minimum_score=0.70,
    )

    return language["text"].upper() if language else "Revisar"


def extract_ot(detections: list[dict[str, Any]]) -> str:
    ot = find_value_to_right(
        detections=detections,
        label_patterns=[
            r"^おや$",
            r"^OT$",
            r"^EO$",
            r"^DO$",
            r"^D\.O\.$",
        ],
        candidate_filter=lambda detection: (
            detection["score"] >= 0.60
            and not re.search(
                r"ID\s*No|IDNo",
                detection["text"],
                re.IGNORECASE,
            )
            and not detection["text"].isdigit()
        ),
    )

    return ot["text"] if ot else "Revisar"


def extract_trainer_id(detections: list[dict[str, Any]]) -> str:
    trainer_id = find_value_to_right(
        detections=detections,
        label_patterns=[
            r"ID\s*No",
            r"IDNo",
        ],
        candidate_filter=lambda detection: (
            detection["score"] >= 0.60
            and bool(re.fullmatch(r"\d{1,6}", detection["text"]))
        ),
    )

    return trainer_id["text"] if trainer_id else "Revisar"


def extract_shiny(
    detections: list[dict[str, Any]],
    number_detection: dict[str, Any],
) -> str:
    """
        En esta captura PaddleOCR interpreta el brillo shiny como '+'.
    """

    candidates = [
        detection
        for detection in detections
        if detection["text"] in {"+", "★", "☆", "✦", "✧"}
        and detection["score"] >= 0.70
        and number_detection["cy"] + 50
        <= detection["cy"]
        <= number_detection["cy"] + 350
        and detection["cx"] <= number_detection["cx"] + 300
    ]

    return "Sí" if candidates else "No"


def append_to_csv(row: dict[str, str]) -> None:
    CSV_PATH.parent.mkdir(parents=True, exist_ok=True)

    file_exists = CSV_PATH.exists()

    # utf-8-sig permite abrir correctamente el CSV en Excel.
    with CSV_PATH.open(
        "a",
        newline="",
        encoding="utf-8-sig",
    ) as file:
        writer = csv.DictWriter(file, fieldnames=CSV_COLUMNS)

        if not file_exists:
            writer.writeheader()

        writer.writerow(row)


def main() -> None:
    detections = load_detections(JSON_PATH)
    icons = detect_known_icons(IMAGE_PATH)

    national_number, number_detection = extract_national_number(
        detections
    )

    species = SPECIES_BY_NUMBER.get(
        national_number,
        "Especie pendiente",
    )

    row = {
        "No.": f"{national_number:04d}",
        "Especie": species,
        "Forma": "Estándar",
        "Marca de origen": str(icons["Marca de origen"]),
        "Shiny": extract_shiny(
            detections,
            number_detection,
        ),
        "OT": extract_ot(detections),
        "IDNo.": extract_trainer_id(detections),
        "Bola": str(icons["Bola"]),
        "Idioma": extract_language(detections),
    }

    print("\nDatos extraídos:\n")

    for column in CSV_COLUMNS:
        print(f"{column}: {row[column]}")

    append_to_csv(row)

    print(f"\nRegistro añadido a:\n{CSV_PATH}")


if __name__ == "__main__":
    main()