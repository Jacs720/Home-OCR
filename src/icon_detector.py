from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np


ROOT = Path(__file__).resolve().parents[1]

BALL_TEMPLATES = ROOT / "data" / "templates" / "balls"
ORIGIN_TEMPLATES = ROOT / "data" / "templates" / "origin_marks"
DEBUG_DIR = ROOT / "output" / "debug"


@dataclass
class IconMatch:
    label: str
    score: float
    template_path: Path | None = None


def read_image(path: Path) -> np.ndarray:
    if not path.exists():
        raise FileNotFoundError(f"No se encontró la imagen:\n{path}")

    data = np.fromfile(str(path), dtype=np.uint8)
    image = cv2.imdecode(data, cv2.IMREAD_COLOR)

    if image is None:
        raise ValueError(f"No se pudo abrir la imagen:\n{path}")

    return image


def read_template(path: Path) -> np.ndarray:
    data = np.fromfile(str(path), dtype=np.uint8)
    image = cv2.imdecode(data, cv2.IMREAD_UNCHANGED)

    if image is None:
        raise ValueError(f"No se pudo abrir la plantilla:\n{path}")

    if image.ndim != 3 or image.shape[2] not in {3, 4}:
        raise ValueError(f"La plantilla debe ser PNG RGB o RGBA: {path}")

    if image.shape[2] == 3:
        # Algunas capturas nuevas llegan con un fondo plano y sin canal alfa.
        # Inferimos la transparencia desde las cuatro esquinas para no exigir
        # una edición manual que pueda alterar el icono.
        corners = np.array(
            [image[0, 0], image[0, -1], image[-1, 0], image[-1, -1]],
            dtype=np.float32,
        )
        background = np.median(corners, axis=0)
        distance = np.mean(
            np.abs(image.astype(np.float32) - background),
            axis=2,
        )
        alpha = (distance >= 18).astype(np.uint8) * 255
        alpha = cv2.morphologyEx(
            alpha,
            cv2.MORPH_CLOSE,
            np.ones((3, 3), np.uint8),
        )
        image = np.dstack([image, alpha])

    return image


def save_image(path: Path, image: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    success, encoded = cv2.imencode(".png", image)

    if not success:
        raise ValueError(f"No se pudo guardar:\n{path}")

    encoded.tofile(str(path))


def template_label(path: Path) -> str:
    base_name = path.stem.split("__", 1)[0]
    return base_name.replace("_", " ")


def detect_ball_bbox(
    image: np.ndarray,
) -> tuple[int, int, int, int]:
    height, width = image.shape[:2]

    left = 0
    right = int(width * 0.13)
    top = int(height * 0.035)
    bottom = int(height * 0.105)

    roi = image[top:bottom, left:right]
    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)

    edges = cv2.Canny(gray, 25, 90)
    edges = cv2.dilate(
        edges,
        np.ones((3, 3), np.uint8),
        iterations=1,
    )

    contours, _ = cv2.findContours(
        edges,
        cv2.RETR_EXTERNAL,
        cv2.CHAIN_APPROX_SIMPLE,
    )

    expected_x = width * 0.055
    expected_y = height * 0.070 - top

    best = None

    for contour in contours:
        x, y, box_width, box_height = cv2.boundingRect(
            contour
        )

        if not (
            16 <= box_width <= 75
            and 16 <= box_height <= 75
        ):
            continue

        ratio = box_width / max(box_height, 1)

        if ratio < 0.55 or ratio > 1.70:
            continue

        center_x = x + box_width / 2
        center_y = y + box_height / 2

        distance = (
            (center_x - expected_x) ** 2
            + (center_y - expected_y) ** 2
        )

        score = box_width * box_height - 0.60 * distance

        if best is None or score > best[0]:
            best = (
                score,
                (
                    x + left,
                    y + top,
                    box_width,
                    box_height,
                ),
            )

    if best is None:
        return (
            int(width * 0.035),
            int(height * 0.055),
            45,
            45,
        )

    return best[1]


def normalize_ball_icon(
    image: np.ndarray,
    canvas_size: int = 64,
) -> np.ndarray:
    x, y, box_width, box_height = detect_ball_bbox(
        image
    )

    center_x = x + box_width / 2
    center_y = y + box_height / 2

    side = int(round(max(box_width, box_height) + 12))
    side = max(52, min(66, side))

    height, width = image.shape[:2]

    left = int(round(center_x - side / 2))
    top = int(round(center_y - side / 2))

    left = max(0, min(width - side, left))
    top = max(0, min(height - side, top))

    crop = image[top:top + side, left:left + side]
    crop = cv2.resize(
        crop,
        (canvas_size, canvas_size),
        interpolation=cv2.INTER_CUBIC,
    )

    yy, xx = np.ogrid[:canvas_size, :canvas_size]

    radius = np.sqrt(
        (xx - (canvas_size - 1) / 2) ** 2
        + (yy - (canvas_size - 1) / 2) ** 2
    )

    grab_mask = np.full(
        (canvas_size, canvas_size),
        cv2.GC_PR_BGD,
        dtype=np.uint8,
    )

    grab_mask[radius > 30] = cv2.GC_BGD
    grab_mask[radius <= 27] = cv2.GC_PR_FGD
    grab_mask[radius <= 10] = cv2.GC_FGD

    background_model = np.zeros((1, 65), np.float64)
    foreground_model = np.zeros((1, 65), np.float64)

    try:
        cv2.grabCut(
            crop,
            grab_mask,
            None,
            background_model,
            foreground_model,
            5,
            cv2.GC_INIT_WITH_MASK,
        )

        alpha = np.where(
            (grab_mask == cv2.GC_FGD)
            | (grab_mask == cv2.GC_PR_FGD),
            255,
            0,
        ).astype(np.uint8)

    except cv2.error:
        alpha = (radius <= 27).astype(np.uint8) * 255

    component_count, labels, stats, _ = (
        cv2.connectedComponentsWithStats(
            (alpha > 0).astype(np.uint8),
            8,
        )
    )

    if component_count > 1:
        center_label = labels[
            canvas_size // 2,
            canvas_size // 2,
        ]

        if center_label == 0:
            center_label = max(
                range(1, component_count),
                key=lambda index: stats[
                    index,
                    cv2.CC_STAT_AREA,
                ],
            )

        alpha = (
            labels == center_label
        ).astype(np.uint8) * 255

    alpha = cv2.morphologyEx(
        alpha,
        cv2.MORPH_CLOSE,
        np.ones((5, 5), np.uint8),
        iterations=2,
    )

    contours, _ = cv2.findContours(
        alpha,
        cv2.RETR_EXTERNAL,
        cv2.CHAIN_APPROX_SIMPLE,
    )

    if contours:
        hull = cv2.convexHull(
            max(contours, key=cv2.contourArea)
        )

        filled = np.zeros_like(alpha)

        cv2.drawContours(
            filled,
            [hull],
            -1,
            255,
            -1,
        )

        circular_limit = (
            radius <= 29
        ).astype(np.uint8) * 255

        alpha = cv2.bitwise_and(
            filled,
            circular_limit,
        )

    return np.dstack([crop, alpha])


def shift_array(
    array: np.ndarray,
    dx: int,
    dy: int,
) -> np.ndarray:
    matrix = np.float32(
        [
            [1, 0, dx],
            [0, 1, dy],
        ]
    )

    return cv2.warpAffine(
        array,
        matrix,
        (array.shape[1], array.shape[0]),
        flags=cv2.INTER_NEAREST,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=0,
    )


def compare_ball_icons(
    query: np.ndarray,
    template: np.ndarray,
) -> float:
    query_rgb = query[:, :, :3]
    query_alpha = query[:, :, 3] > 0

    query_lab = cv2.cvtColor(
        query_rgb,
        cv2.COLOR_BGR2LAB,
    ).astype(np.float32)

    query_edges = cv2.Canny(
        cv2.cvtColor(
            query_rgb,
            cv2.COLOR_BGR2GRAY,
        ),
        40,
        100,
    ) > 0

    best_score = -1.0

    for dy in range(-2, 3):
        for dx in range(-2, 3):
            shifted_rgb = shift_array(
                template[:, :, :3],
                dx,
                dy,
            )

            shifted_alpha = shift_array(
                template[:, :, 3],
                dx,
                dy,
            ) > 0

            intersection = query_alpha & shifted_alpha
            union = query_alpha | shifted_alpha

            if int(intersection.sum()) < 100:
                continue

            shifted_lab = cv2.cvtColor(
                shifted_rgb,
                cv2.COLOR_BGR2LAB,
            ).astype(np.float32)

            mean_difference = float(
                np.mean(
                    np.abs(
                        query_lab[intersection]
                        - shifted_lab[intersection]
                    )
                )
            )

            color_score = max(
                0.0,
                1.0 - mean_difference / 85.0,
            )

            shape_score = float(
                intersection.sum()
                / max(int(union.sum()), 1)
            )

            shifted_edges = cv2.Canny(
                cv2.cvtColor(
                    shifted_rgb,
                    cv2.COLOR_BGR2GRAY,
                ),
                40,
                100,
            ) > 0

            edge_union = (
                query_edges | shifted_edges
            ) & union

            edge_intersection = (
                query_edges & shifted_edges
            ) & union

            edge_score = float(
                edge_intersection.sum()
                / max(int(edge_union.sum()), 1)
            )

            score = (
                0.72 * color_score
                + 0.20 * shape_score
                + 0.08 * edge_score
            )

            best_score = max(best_score, score)

    return best_score


def detect_ball(
    image: np.ndarray,
    minimum_score: float = 0.55,
) -> IconMatch:
    query = normalize_ball_icon(image)

    save_image(
        DEBUG_DIR / "ball_normalized.png",
        query,
    )

    grouped_scores: dict[str, IconMatch] = {}

    for template_path in sorted(
        BALL_TEMPLATES.glob("*.png")
    ):
        template = read_template(template_path)

        score = compare_ball_icons(
            query,
            template,
        )

        label = template_label(template_path)
        current = grouped_scores.get(label)

        if current is None or score > current.score:
            grouped_scores[label] = IconMatch(
                label=label,
                score=score,
                template_path=template_path,
            )

    if not grouped_scores:
        raise FileNotFoundError(
            f"No hay plantillas PNG en:\n{BALL_TEMPLATES}"
        )

    for match in sorted(
        grouped_scores.values(),
        key=lambda item: item.score,
        reverse=True,
    ):
        print(f"  {match.label}: {match.score:.3f}")

    best = max(
        grouped_scores.values(),
        key=lambda item: item.score,
    )

    if best.score < minimum_score:
        return IconMatch(
            label="Revisar",
            score=best.score,
            template_path=best.template_path,
        )

    return best


def crop_origin_region(
    image: np.ndarray,
) -> np.ndarray:
    height, width = image.shape[:2]

    left = int(width * 0.177)
    right = int(width * 0.255)
    top = int(height * 0.451)
    bottom = int(height * 0.488)

    return image[top:bottom, left:right]


def normalize_origin_icon(
    image: np.ndarray,
    canvas_size: int = 64,
) -> tuple[np.ndarray | None, int]:
    crop = crop_origin_region(image)
    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)

    border = np.concatenate(
        [
            gray[:4].ravel(),
            gray[-4:].ravel(),
            gray[:, :4].ravel(),
            gray[:, -4:].ravel(),
        ]
    )

    background = float(np.median(border))

    binary = (
        gray < background - 35
    ).astype(np.uint8)

    binary = cv2.morphologyEx(
        binary,
        cv2.MORPH_OPEN,
        np.ones((2, 2), np.uint8),
    )

    component_count, labels, stats, centroids = (
        cv2.connectedComponentsWithStats(
            binary,
            8,
        )
    )

    height, width = binary.shape
    candidates = []

    for index in range(1, component_count):
        area = int(stats[index, cv2.CC_STAT_AREA])

        if area < 12:
            continue

        center_x, center_y = centroids[index]

        distance = (
            (center_x - width / 2) ** 2
            + (center_y - height / 2) ** 2
        )

        candidates.append(
            (
                area - 0.05 * distance,
                index,
            )
        )

    if not candidates:
        return None, int(binary.sum())

    selected_label = max(candidates)[1]

    component = (
        labels == selected_label
    ).astype(np.uint8)

    nearby = cv2.dilate(
        component,
        np.ones((9, 9), np.uint8),
        iterations=1,
    )

    component = (
        (nearby > 0)
        & (binary > 0)
    ).astype(np.uint8)

    ys, xs = np.where(component > 0)

    left = int(xs.min())
    right = int(xs.max()) + 1
    top = int(ys.min())
    bottom = int(ys.max()) + 1

    object_crop = crop[top:bottom, left:right]
    object_mask = (
        component[top:bottom, left:right] * 255
    )

    maximum_dimension = 48

    scale = min(
        maximum_dimension / object_crop.shape[1],
        maximum_dimension / object_crop.shape[0],
    )

    resized_width = max(
        1,
        int(round(object_crop.shape[1] * scale)),
    )
    resized_height = max(
        1,
        int(round(object_crop.shape[0] * scale)),
    )

    resized_object = cv2.resize(
        object_crop,
        (resized_width, resized_height),
        interpolation=cv2.INTER_CUBIC,
    )
    resized_mask = cv2.resize(
        object_mask,
        (resized_width, resized_height),
        interpolation=cv2.INTER_NEAREST,
    )

    output = np.zeros(
        (canvas_size, canvas_size, 4),
        dtype=np.uint8,
    )

    offset_x = (canvas_size - resized_width) // 2
    offset_y = (canvas_size - resized_height) // 2

    output[
        offset_y:offset_y + resized_height,
        offset_x:offset_x + resized_width,
        :3,
    ] = resized_object

    output[
        offset_y:offset_y + resized_height,
        offset_x:offset_x + resized_width,
        3,
    ] = resized_mask

    return output, int(binary.sum())


def compare_origin_icons(
    query: np.ndarray,
    template: np.ndarray,
) -> float:
    query_mask = query[:, :, 3] > 0

    best_score = -1.0

    for dy in range(-3, 4):
        for dx in range(-3, 4):
            template_mask = shift_array(
                template[:, :, 3],
                dx,
                dy,
            ) > 0

            intersection = int(
                (query_mask & template_mask).sum()
            )
            union = int(
                (query_mask | template_mask).sum()
            )

            score = intersection / max(union, 1)
            best_score = max(best_score, score)

    return best_score


def detect_origin(
    image: np.ndarray,
    minimum_score: float = 0.45,
) -> IconMatch:
    query, dark_pixels = normalize_origin_icon(image)

    save_image(
        DEBUG_DIR / "origin_region.png",
        crop_origin_region(image),
    )

    if query is None:
        print(
            "  Sin marca (Gen 3-5): "
            f"posición vacía, {dark_pixels} píxeles oscuros"
        )

        return IconMatch(
            label="Sin marca (Gen 3-5)",
            score=1.0,
            template_path=None,
        )

    save_image(
        DEBUG_DIR / "origin_normalized.png",
        query,
    )

    grouped_scores: dict[str, IconMatch] = {}

    for template_path in sorted(
        ORIGIN_TEMPLATES.glob("*.png")
    ):
        if template_path.stem.startswith("Sin_marca"):
            continue
        template = read_template(template_path)

        score = compare_origin_icons(
            query,
            template,
        )

        label = template_label(template_path)
        current = grouped_scores.get(label)

        if current is None or score > current.score:
            grouped_scores[label] = IconMatch(
                label=label,
                score=score,
                template_path=template_path,
            )

    if not grouped_scores:
        raise FileNotFoundError(
            f"No hay plantillas PNG en:\n{ORIGIN_TEMPLATES}"
        )

    for match in sorted(
        grouped_scores.values(),
        key=lambda item: item.score,
        reverse=True,
    ):
        print(f"  {match.label}: {match.score:.3f}")

    best = max(
        grouped_scores.values(),
        key=lambda item: item.score,
    )

    if best.score < minimum_score:
        return IconMatch(
            label="Revisar",
            score=best.score,
            template_path=best.template_path,
        )

    return best


def detect_known_icons(
    image_path: Path,
) -> dict[str, str | float]:
    image = read_image(image_path)

    print("\nComparando Poké Balls:")
    ball_match = detect_ball(image)

    print("\nComparando marcas de origen:")
    origin_match = detect_origin(image)

    return {
        "Bola": ball_match.label,
        "Bola_score": ball_match.score,
        "Marca de origen": origin_match.label,
        "Marca_score": origin_match.score,
    }


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Detecta la Poké Ball y la marca de origen "
            "en una captura de Pokémon HOME."
        )
    )

    parser.add_argument(
        "image",
        type=Path,
        help="Ruta de la captura.",
    )

    return parser.parse_args()


def main() -> None:
    args = parse_arguments()

    image_path = args.image

    if not image_path.is_absolute():
        image_path = ROOT / image_path

    results = detect_known_icons(image_path)

    print("\nResultado:")

    print(
        f"Bola: {results['Bola']} "
        f"({results['Bola_score']:.3f})"
    )

    print(
        f"Marca de origen: "
        f"{results['Marca de origen']} "
        f"({results['Marca_score']:.3f})"
    )


if __name__ == "__main__":
    main()
