"""Generate Android launcher resources from the repository's source artwork.

The supplied artwork is never redrawn. This script only trims transparent
padding, scales it with a high-quality filter, and places it on the app's
brand background for legacy and adaptive Android launchers.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "branding" / "app_icon_source.png"
RESOURCES = ROOT / "android" / "app" / "src" / "main" / "res"

LEGACY_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def trim_transparency(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    alpha_bounds = rgba.getchannel("A").getbbox()
    if alpha_bounds is None:
        raise ValueError(f"The icon source has no visible pixels: {SOURCE}")
    return rgba.crop(alpha_bounds)


def fit_artwork(artwork: Image.Image, width: int) -> Image.Image:
    height = max(1, round(artwork.height * width / artwork.width))
    return artwork.resize((width, height), Image.Resampling.LANCZOS)


def vertical_gradient(size: int) -> Image.Image:
    top = (27, 78, 99)
    bottom = (18, 33, 58)
    image = Image.new("RGBA", (size, size))
    pixels = image.load()
    for y in range(size):
        ratio = y / max(1, size - 1)
        color = tuple(round(a + (b - a) * ratio) for a, b in zip(top, bottom))
        for x in range(size):
            pixels[x, y] = (*color, 255)

    glow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(glow)
    inset = round(size * 0.13)
    draw.ellipse(
        (inset, inset, size - inset, size - inset),
        fill=(245, 197, 66, 36),
    )
    return Image.alpha_composite(image, glow.filter(ImageFilter.GaussianBlur(size * 0.08)))


def centered_composite(canvas: Image.Image, artwork: Image.Image, y_offset: int = 0) -> None:
    left = (canvas.width - artwork.width) // 2
    top = (canvas.height - artwork.height) // 2 + y_offset
    canvas.alpha_composite(artwork, (left, top))


def legacy_icon(artwork: Image.Image, size: int, round_icon: bool) -> Image.Image:
    scale = 8
    working_size = size * scale
    background = vertical_gradient(working_size)
    mask = Image.new("L", (working_size, working_size), 0)
    draw = ImageDraw.Draw(mask)
    if round_icon:
        draw.ellipse((0, 0, working_size - 1, working_size - 1), fill=255)
    else:
        radius = round(working_size * 0.22)
        draw.rounded_rectangle(
            (0, 0, working_size - 1, working_size - 1),
            radius=radius,
            fill=255,
        )
    background.putalpha(mask)

    scaled = fit_artwork(artwork, round(working_size * 0.70))
    centered_composite(background, scaled, round(working_size * 0.015))
    return background.resize((size, size), Image.Resampling.LANCZOS)


def write_adaptive_assets(artwork: Image.Image) -> None:
    output = RESOURCES / "drawable-nodpi"
    output.mkdir(parents=True, exist_ok=True)

    foreground = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    scaled = fit_artwork(artwork, 280)
    centered_composite(foreground, scaled, 5)
    foreground.save(output / "ic_launcher_foreground.png", optimize=True)

    alpha = foreground.getchannel("A")
    monochrome = Image.new("RGBA", foreground.size, (255, 255, 255, 0))
    monochrome.putalpha(alpha)
    monochrome.save(output / "ic_launcher_monochrome.png", optimize=True)

    app_logo = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    centered_composite(app_logo, fit_artwork(artwork, 460), 5)
    app_logo.save(output / "app_logo.png", optimize=True)


def main() -> None:
    artwork = trim_transparency(Image.open(SOURCE))
    write_adaptive_assets(artwork)
    for folder, size in LEGACY_SIZES.items():
        output = RESOURCES / folder
        output.mkdir(parents=True, exist_ok=True)
        legacy_icon(artwork, size, round_icon=False).save(
            output / "ic_launcher.png", optimize=True
        )
        legacy_icon(artwork, size, round_icon=True).save(
            output / "ic_launcher_round.png", optimize=True
        )
    print(f"Android icons generated from {SOURCE}")


if __name__ == "__main__":
    main()
