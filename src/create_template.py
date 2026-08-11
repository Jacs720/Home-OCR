from __future__ import annotations

import argparse
import re
from pathlib import Path

import cv2
import numpy as np


ROOT = Path(__file__).resolve().parents[1]
TEMPLATE_ROOT = ROOT / "data" / "templates"


def read_image(path: Path) -> np.ndarray:
    """Lee imágenes incluso cuando la ruta contiene acentos."""

    if not path.exists():
        raise FileNotFoundError(f"No se encontró la imagen:\n{path}")

    data = np.fromfile(str(path), dtype=np.uint8)
    image = cv2.imdecode(data, cv2.IMREAD_COLOR)

    if image is None:
        raise ValueError(f"No se pudo abrir la imagen:\n{path}")

    return image


def save_image(path: Path, image: np.ndarray) -> None:
    """Guarda imágenes admitiendo caracteres Unicode en la ruta."""

    path.parent.mkdir(parents=True, exist_ok=True)

    success, encoded = cv2.imencode(".png", image)

    if not success:
        raise ValueError("OpenCV no pudo codificar la plantilla.")

    encoded.tofile(str(path))


def safe_filename(label: str) -> str:
    """Convierte una etiqueta en un nombre de archivo válido."""

    cleaned = re.sub(r'[<>:"/\\|?*]', "_", label)
    cleaned = re.sub(r"\s+", "_", cleaned.strip())

    if not cleaned:
        raise ValueError("La etiqueta no puede estar vacía.")

    return cleaned


def select_template(
    image: np.ndarray,
    label: str,
) -> np.ndarray:
    """
    Muestra una versión reducida de la captura y permite seleccionar
    manualmente el icono.
    """

    original_height, original_width = image.shape[:2]

    maximum_width = 900
    maximum_height = 900

    scale = min(
        1.0,
        maximum_width / original_width,
        maximum_height / original_height,
    )

    display_width = int(original_width * scale)
    display_height = int(original_height * scale)

    display_image = cv2.resize(
        image,
        (display_width, display_height),
        interpolation=cv2.INTER_AREA,
    )

    window_name = f"Selecciona: {label}"

    x, y, width, height = cv2.selectROI(
        window_name,
        display_image,
        showCrosshair=True,
        fromCenter=False,
    )

    cv2.destroyAllWindows()

    if width == 0 or height == 0:
        raise RuntimeError("La selección fue cancelada.")

    original_x1 = int(round(x / scale))
    original_y1 = int(round(y / scale))
    original_x2 = int(round((x + width) / scale))
    original_y2 = int(round((y + height) / scale))

    crop = image[
        original_y1:original_y2,
        original_x1:original_x2,
    ]

    if crop.size == 0:
        raise RuntimeError("La selección generó un recorte vacío.")

    return crop


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Crea plantillas de iconos de Pokémon HOME."
    )

    parser.add_argument(
        "image",
        type=Path,
        help="Ruta de la captura.",
    )

    parser.add_argument(
        "category",
        choices=["balls", "origin_marks"],
        help="Categoría de la plantilla.",
    )

    parser.add_argument(
        "label",
        help="Nombre que se guardará en el CSV.",
    )

    return parser.parse_args()


def main() -> None:
    args = parse_arguments()

    image_path = args.image

    if not image_path.is_absolute():
        image_path = ROOT / image_path

    image = read_image(image_path)

    print(f"Selecciona únicamente el icono de: {args.label}")
    print("Pulsa Enter o Espacio para confirmar.")
    print("Pulsa Esc para cancelar.")

    crop = select_template(image, args.label)

    filename = f"{safe_filename(args.label)}.png"
    output_path = TEMPLATE_ROOT / args.category / filename

    save_image(output_path, crop)

    print("\nPlantilla guardada correctamente:")
    print(output_path)


if __name__ == "__main__":
    main()