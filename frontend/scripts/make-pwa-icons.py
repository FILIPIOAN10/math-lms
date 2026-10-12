"""Draws the app icons into frontend/public/ (run by hand when the icon changes: python scripts/make-pwa-icons.py).

The icon is the theme's primary colour (#171717) with a white "x²". Outputs:
  favicon-32.png, icon-192.png, icon-512.png - "any" icons (rounded square, transparent corners)
  icon-maskable-512.png           - "maskable": full-bleed background, content inside the 80% safe zone
  apple-touch-icon.png (180)      - iOS home screen (opaque; iOS rounds the corners itself)
Needs Pillow and a bold sans font (Segoe UI / Arial / DejaVu, whichever is found first).
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

BACKGROUND = (23, 23, 23, 255)  # oklch(0.205 0 0), the --primary of src/index.css
FOREGROUND = (250, 250, 250, 255)
PUBLIC = Path(__file__).resolve().parent.parent / "public"
FONTS = [
    "C:/Windows/Fonts/segoeuib.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONTS:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    raise SystemExit("No bold font found - add one to FONTS")


def mark(width: int) -> Image.Image:
    """ "x" with a superscript "2", cropped to its ink and scaled to `width` px wide."""
    canvas = Image.new("RGBA", (2000, 2000), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    draw.text((300, 1400), "x", font=font(900), fill=FOREGROUND, anchor="ls")  # left, on the baseline
    x_box = draw.textbbox((300, 1400), "x", font=font(900), anchor="ls")
    x_height = x_box[3] - x_box[1]
    # the exponent: just right of the x, its bottom a little above the x's middle
    draw.text((x_box[2] + 40, x_box[1] + x_height * 0.35), "2", font=font(520), fill=FOREGROUND, anchor="ls")
    ink = canvas.crop(canvas.getbbox())
    return ink.resize((width, round(ink.height * width / ink.width)), Image.LANCZOS)


def draw_mark(image: Image.Image, scale: float) -> None:
    """Centres the mark; `scale` is the share of the canvas width it may use."""
    size = image.width
    m = mark(int(size * 0.62 * scale))
    image.alpha_composite(m, ((size - m.width) // 2, (size - m.height) // 2))


def rounded(size: int) -> Image.Image:
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(image).rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * 0.22), fill=BACKGROUND)
    draw_mark(image, 1.0)
    return image


def full_bleed(size: int, scale: float) -> Image.Image:
    image = Image.new("RGBA", (size, size), BACKGROUND)
    draw_mark(image, scale)
    return image


if __name__ == "__main__":
    rounded(32).save(PUBLIC / "favicon-32.png", optimize=True)
    rounded(192).save(PUBLIC / "icon-192.png", optimize=True)
    rounded(512).save(PUBLIC / "icon-512.png", optimize=True)
    full_bleed(512, 0.8).save(PUBLIC / "icon-maskable-512.png", optimize=True)
    full_bleed(180, 0.9).convert("RGB").save(PUBLIC / "apple-touch-icon.png", optimize=True)
    print("icons written to", PUBLIC)
