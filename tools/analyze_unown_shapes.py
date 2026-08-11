"""Compare Unown silhouettes in Pokemon HOME screenshots with HOME sprites.

This is a development aid for deciding whether a compact shape descriptor is
stable enough to ship in the Android recognizer. It does not modify inputs.
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image


FORMS = [*"abcdefghijklmnopqrstuvwxyz", "exclamation", "question"]


def largest_component(mask: np.ndarray) -> np.ndarray:
    height, width = mask.shape
    seen = np.zeros_like(mask, dtype=bool)
    best: list[tuple[int, int]] = []
    for start_y, start_x in zip(*np.nonzero(mask)):
        if seen[start_y, start_x]:
            continue
        stack = [(int(start_y), int(start_x))]
        seen[start_y, start_x] = True
        component: list[tuple[int, int]] = []
        while stack:
            y, x = stack.pop()
            component.append((y, x))
            for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < height and 0 <= nx < width and mask[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    stack.append((ny, nx))
        if len(component) > len(best):
            best = component
    result = np.zeros_like(mask, dtype=bool)
    for y, x in best:
        result[y, x] = True
    return result


def fill_holes(mask: np.ndarray) -> np.ndarray:
    height, width = mask.shape
    outside = np.zeros_like(mask, dtype=bool)
    stack: list[tuple[int, int]] = []
    for x in range(width):
        if not mask[0, x]:
            stack.append((0, x))
        if not mask[height - 1, x]:
            stack.append((height - 1, x))
    for y in range(height):
        if not mask[y, 0]:
            stack.append((y, 0))
        if not mask[y, width - 1]:
            stack.append((y, width - 1))
    while stack:
        y, x = stack.pop()
        if outside[y, x] or mask[y, x]:
            continue
        outside[y, x] = True
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < height and 0 <= nx < width and not outside[ny, nx] and not mask[ny, nx]:
                stack.append((ny, nx))
    return mask | (~outside & ~mask)


def hu_descriptor(mask: np.ndarray) -> np.ndarray:
    y, x = np.nonzero(mask)
    if len(x) < 10:
        raise ValueError("mask too small")
    x = x.astype(float)
    y = y.astype(float)
    cx, cy = x.mean(), y.mean()
    dx, dy = x - cx, y - cy
    m00 = float(len(x))

    def eta(p: int, q: int) -> float:
        mu = np.sum((dx**p) * (dy**q))
        return float(mu / (m00 ** (1.0 + (p + q) / 2.0)))

    n20, n02, n11 = eta(2, 0), eta(0, 2), eta(1, 1)
    n30, n12, n21, n03 = eta(3, 0), eta(1, 2), eta(2, 1), eta(0, 3)
    hu = np.array(
        [
            n20 + n02,
            (n20 - n02) ** 2 + 4 * n11**2,
            (n30 - 3 * n12) ** 2 + (3 * n21 - n03) ** 2,
            (n30 + n12) ** 2 + (n21 + n03) ** 2,
            (n30 - 3 * n12)
            * (n30 + n12)
            * ((n30 + n12) ** 2 - 3 * (n21 + n03) ** 2)
            + (3 * n21 - n03)
            * (n21 + n03)
            * (3 * (n30 + n12) ** 2 - (n21 + n03) ** 2),
            (n20 - n02) * ((n30 + n12) ** 2 - (n21 + n03) ** 2)
            + 4 * n11 * (n30 + n12) * (n21 + n03),
            (3 * n21 - n03)
            * (n30 + n12)
            * ((n30 + n12) ** 2 - 3 * (n21 + n03) ** 2)
            - (n30 - 3 * n12)
            * (n21 + n03)
            * (3 * (n30 + n12) ** 2 - (n21 + n03) ** 2),
        ]
    )
    return -np.sign(hu) * np.log10(np.maximum(np.abs(hu), 1e-30))


def normalized_mask(mask: np.ndarray, size: int = 64) -> np.ndarray:
    y, x = np.nonzero(mask)
    if len(x) < 10:
        raise ValueError("mask too small")
    crop = mask[y.min() : y.max() + 1, x.min() : x.max() + 1]
    target = int(size * 0.78)
    scale = target / max(crop.shape)
    resized = Image.fromarray((crop * 255).astype("uint8")).resize(
        (max(1, round(crop.shape[1] * scale)), max(1, round(crop.shape[0] * scale))),
        Image.Resampling.BILINEAR,
    )
    result = np.zeros((size, size), dtype=bool)
    values = np.asarray(resized) > 127
    top = (size - values.shape[0]) // 2
    left = (size - values.shape[1]) // 2
    result[top : top + values.shape[0], left : left + values.shape[1]] = values
    return result


def overlap_score(query: np.ndarray, reference: np.ndarray) -> float:
    best = 0.0
    source = Image.fromarray((query * 255).astype("uint8"))
    for angle in range(-24, 25, 4):
        rotated = np.asarray(source.rotate(angle, Image.Resampling.BILINEAR, expand=False)) > 127
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                shifted = np.roll(rotated, (dy, dx), axis=(0, 1))
                intersection = np.count_nonzero(shifted & reference)
                union = np.count_nonzero(shifted | reference)
                if union:
                    best = max(best, intersection / union)
    return best


def android_normalized_mask(mask: np.ndarray, size: int = 64) -> np.ndarray:
    """Mirror the cheaper nearest-neighbour normalization used on the phone."""
    y, x = np.nonzero(mask)
    crop = mask[y.min() : y.max() + 1, x.min() : x.max() + 1]
    target = round(size * 0.78)
    scale = target / max(crop.shape)
    resized = Image.fromarray((crop * 255).astype("uint8")).resize(
        (max(1, round(crop.shape[1] * scale)), max(1, round(crop.shape[0] * scale))),
        Image.Resampling.NEAREST,
    )
    values = np.asarray(resized) > 127
    result = np.zeros((size, size), dtype=bool)
    top = (size - values.shape[0]) // 2
    left = (size - values.shape[1]) // 2
    result[top : top + values.shape[0], left : left + values.shape[1]] = values
    return result


def android_overlap_score(query: np.ndarray, reference: np.ndarray) -> float:
    best = 0.0
    source = Image.fromarray((query * 255).astype("uint8"))
    for angle in range(-24, 25, 4):
        rotated = np.asarray(source.rotate(angle, Image.Resampling.NEAREST, expand=False)) > 127
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                shifted = np.zeros_like(rotated)
                source_y = slice(max(0, -dy), min(rotated.shape[0], rotated.shape[0] - dy))
                source_x = slice(max(0, -dx), min(rotated.shape[1], rotated.shape[1] - dx))
                target_y = slice(max(0, dy), min(rotated.shape[0], rotated.shape[0] + dy))
                target_x = slice(max(0, dx), min(rotated.shape[1], rotated.shape[1] + dx))
                shifted[target_y, target_x] = rotated[source_y, source_x]
                intersection = np.count_nonzero(shifted & reference)
                union = np.count_nonzero(shifted | reference)
                if union:
                    best = max(best, intersection / union)
    return best


def rle(mask: np.ndarray) -> str:
    result: list[int] = []
    current = False
    count = 0
    for value in mask.ravel():
        if bool(value) == current:
            count += 1
        else:
            result.append(count)
            current = bool(value)
            count = 1
    result.append(count)
    return ";".join(str(value) for value in result)


def sprite_mask(path: Path) -> np.ndarray:
    rgba = np.asarray(Image.open(path).convert("RGBA"))
    return fill_holes(largest_component(rgba[:, :, 3] > 32))


def screenshot_mask(path: Path) -> np.ndarray:
    rgb = np.asarray(Image.open(path).convert("RGB"))
    height, width = rgb.shape[:2]
    crop = rgb[int(height * 0.105) : int(height * 0.365), int(width * 0.16) : int(width * 0.82)]
    red, green, blue = (crop[:, :, index].astype(int) for index in range(3))
    blue_body = (blue > green + 18) & (green > red + 18) & (green > 48)
    white_eye = (red > 165) & (green > 165) & (blue > 165) & (np.max(crop, axis=2) - np.min(crop, axis=2) < 45)
    return fill_holes(largest_component(blue_body | white_eye))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--references",
        type=Path,
        required=True,
        help="Directory containing 201.png and the 201-<form>.png sprites.",
    )
    parser.add_argument(
        "--screenshots",
        type=Path,
        help="Directory containing the Pokémon HOME screenshots to compare.",
    )
    parser.add_argument(
        "--pattern",
        default="*.jpg",
        help="Glob used inside --screenshots (default: *.jpg).",
    )
    parser.add_argument("--emit-rle", action="store_true")
    parser.add_argument("--android-like", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    references: dict[str, np.ndarray] = {}
    for form in FORMS:
        name = "201.png" if form == "a" else f"201-{form}.png"
        references[form] = normalized_mask(sprite_mask(args.references / name))

    if args.emit_rle:
        print("form,runs")
        for form, mask in references.items():
            print(f"{form},{rle(mask)}")
        return

    if args.screenshots is None:
        raise SystemExit("--screenshots is required unless --emit-rle is used")
    screenshots = sorted(args.screenshots.glob(args.pattern))
    if not screenshots:
        raise SystemExit(f"No screenshots matched {args.pattern!r} in {args.screenshots}")
    for screenshot in screenshots:
        descriptor = (android_normalized_mask if args.android_like else normalized_mask)(
            screenshot_mask(screenshot)
        )
        scorer = android_overlap_score if args.android_like else overlap_score
        ranking = sorted(
            ((scorer(descriptor, reference), form) for form, reference in references.items()),
            reverse=True,
        )
        top = ", ".join(f"{form}:{score:.3f}" for score, form in ranking[:5])
        print(f"{screenshot.name}\t{top}")


if __name__ == "__main__":
    main()
