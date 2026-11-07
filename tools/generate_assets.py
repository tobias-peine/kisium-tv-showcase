#!/usr/bin/env python3
"""Generates all raster branding and demo artwork for Kisium TV.

Run from the project root:
    python3 tools/generate_assets.py
"""
from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app-tv" / "src" / "main" / "res"
DRAWABLE = RES / "drawable-nodpi"
MIPMAP = RES / "mipmap-xxxhdpi"
FONT_BOLD = "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf"
FONT_REGULAR = "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf"

PALETTES = {
    "nordlicht": ((8, 20, 32), (18, 76, 92), (118, 226, 214), (227, 248, 244)),
    "stille": ((10, 18, 28), (36, 56, 74), (124, 170, 190), (235, 243, 246)),
    "frequenz": ((17, 10, 30), (73, 24, 97), (240, 80, 110), (255, 198, 112)),
    "nachtzug": ((8, 12, 17), (30, 34, 46), (200, 120, 54), (247, 221, 177)),
    "glut": ((20, 6, 8), (92, 25, 18), (240, 92, 48), (255, 207, 122)),
    "kosmos": ((5, 10, 28), (22, 38, 90), (96, 134, 255), (188, 221, 255)),
    "hafenkante": ((7, 20, 28), (20, 66, 72), (246, 168, 82), (250, 232, 204)),
    "archiv": ((13, 14, 18), (42, 46, 58), (170, 180, 198), (242, 214, 160)),
    "live1": ((14, 8, 10), (68, 18, 22), (255, 66, 72), (255, 184, 123)),
    "live2": ((8, 12, 24), (22, 38, 74), (103, 132, 255), (209, 225, 255)),
    "live3": ((6, 18, 16), (14, 62, 48), (64, 218, 174), (206, 255, 238)),
    "live4": ((16, 12, 6), (58, 44, 14), (255, 196, 54), (255, 242, 196)),
}


def font(size: int, bold: bool = True) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REGULAR, size)


def gradient(size: tuple[int, int], a: tuple[int, int, int], b: tuple[int, int, int]) -> Image.Image:
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / w * 0.55 + y / h * 0.45)
            t = min(1.0, max(0.0, t))
            px[x, y] = (
                int(a[0] + (b[0] - a[0]) * t),
                int(a[1] + (b[1] - a[1]) * t),
                int(a[2] + (b[2] - a[2]) * t),
            )
    return img


def add_glow(img: Image.Image, center: tuple[int, int], radius: int, color: tuple[int, int, int], alpha: int) -> None:
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for i in range(5, 0, -1):
        r = int(radius * (i / 5))
        a = int(alpha * (1 - (i - 1) / 5) * 0.55)
        draw.ellipse(
            (center[0] - r, center[1] - r, center[0] + r, center[1] + r),
            fill=(*color, a),
        )
    layer = layer.filter(ImageFilter.GaussianBlur(radius / 4))
    img.alpha_composite(layer)


def add_grain(img: Image.Image, strength: int = 10, seed: int = 7) -> None:
    rng = random.Random(seed)
    w, h = img.size
    noise = Image.new("L", (w, h))
    noise.putdata([rng.randrange(0, 255) for _ in range(w * h)])
    noise = noise.convert("RGB").filter(ImageFilter.GaussianBlur(0.4))
    img.paste(Image.blend(img.convert("RGB"), noise, strength / 255).convert("RGBA"), (0, 0))


def draw_tracked(draw: ImageDraw.ImageDraw, xy, text: str, f: ImageFont.FreeTypeFont, fill, tracking: int = 0):
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=f, fill=fill)
        x += draw.textlength(ch, font=f) + tracking


def vignette(img: Image.Image, strength: int = 130) -> None:
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((-w * 0.25, -h * 0.25, w * 1.25, h * 1.25), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(min(w, h) / 6))
    black = Image.new("RGBA", (w, h), (0, 0, 0, strength))
    img.paste(Image.composite(img, black, mask), (0, 0))


def make_poster(slug: str, title: str, kicker: str, subtitle: str, seed: int) -> None:
    w, h = 500, 750
    p = PALETTES[slug]
    img = gradient((w, h), p[0], p[1]).convert("RGBA")
    add_glow(img, (int(w * 0.75), int(h * 0.22)), int(w * 0.7), p[2], 200)
    add_glow(img, (int(w * 0.15), int(h * 0.7)), int(w * 0.5), p[3], 70)
    draw = ImageDraw.Draw(img, "RGBA")
    # Abstract horizon/geometry.
    for i in range(6):
        y = int(h * (0.42 + i * 0.055))
        draw.line((0, y, w, y - 80), fill=(*p[3], 18 + i * 9), width=2)
    draw.ellipse((w * 0.52, h * 0.24, w * 1.18, h * 0.82), outline=(*p[3], 90), width=3)
    draw.arc((w * 0.1, h * 0.55, w * 0.75, h * 1.15), 200, 340, fill=(*p[2], 160), width=8)
    # Bottom scrim.
    scrim = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(scrim)
    for i in range(320):
        alpha = int(230 * (i / 320) ** 1.2)
        sd.rectangle((0, h - 320 + i, w, h - 320 + i + 1), fill=(3, 5, 9, alpha))
    img.alpha_composite(scrim)
    # Text.
    draw = ImageDraw.Draw(img, "RGBA")
    draw_tracked(draw, (42, 50), kicker.upper(), font(17), (*p[3], 235), tracking=2)
    draw.line((42, 82, 150, 82), fill=(*p[2], 255), width=3)
    title_font = font(62 if len(title) < 12 else 50)
    y = h - 205
    for line in wrap(draw, title, title_font, w - 84):
        draw.text((42, y), line, font=title_font, fill=(255, 255, 255, 255))
        y += int(title_font.size * 1.06)
    draw.text((44, h - 58), subtitle, font=font(19, False), fill=(215, 223, 233, 230))
    draw.rectangle((42, h - 38, 114, h - 35), fill=(*p[2], 255))
    vignette(img)
    add_grain(img, 8, seed)
    out = DRAWABLE / f"demo_poster_{slug}.webp"
    img.convert("RGB").save(out, "WEBP", quality=84, method=6)
    print(out.relative_to(ROOT))


def make_hero(slug: str, title: str, kicker: str, seed: int, live: bool = False) -> None:
    w, h = 1280, 720
    p = PALETTES[slug]
    img = gradient((w, h), p[0], p[1]).convert("RGBA")
    add_glow(img, (int(w * 0.7), int(h * 0.3)), int(w * 0.45), p[2], 180)
    add_glow(img, (int(w * 0.2), int(h * 0.8)), int(w * 0.4), p[3], 60)
    draw = ImageDraw.Draw(img, "RGBA")
    # Cinematic geometry.
    for i in range(7):
        y = int(h * (0.3 + i * 0.07))
        draw.line((0, y, w, y - 140), fill=(*p[3], 20 + i * 7), width=2)
    draw.ellipse((w * 0.55, -h * 0.35, w * 1.2, h * 0.9), outline=(*p[3], 80), width=2)
    draw.arc((-w * 0.2, h * 0.2, w * 0.65, h * 1.25), 230, 340, fill=(*p[2], 150), width=10)
    # Left scrim for the UI copy.
    scrim = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(scrim)
    for i in range(900):
        alpha = int(205 * (1 - i / 900) ** 1.5)
        sd.rectangle((i, 0, i + 1, h), fill=(3, 5, 9, alpha))
    img.alpha_composite(scrim)
    bottom = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bottom)
    for i in range(220):
        alpha = int(180 * (i / 220) ** 1.2)
        bd.rectangle((0, h - 220 + i, w, h - 220 + i + 1), fill=(3, 5, 9, alpha))
    img.alpha_composite(bottom)
    vignette(img, 90)
    add_grain(img, 7, seed)
    out = DRAWABLE / f"demo_hero_{slug}.webp"
    img.convert("RGB").save(out, "WEBP", quality=84, method=6)
    print(out.relative_to(ROOT))


def wrap(draw: ImageDraw.ImageDraw, text: str, f: ImageFont.FreeTypeFont, max_width: int, max_lines: int = 3):
    words = text.split()
    lines: list[str] = []
    current = ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if draw.textlength(candidate, font=f) <= max_width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
            if len(lines) == max_lines - 1:
                break
    if current and len(lines) < max_lines:
        lines.append(current)
    return lines


def make_banner() -> None:
    w, h = 640, 360
    img = Image.new("RGBA", (w, h), (4, 6, 10, 255))
    add_glow(img, (500, 80), 320, (255, 92, 48), 170)
    add_glow(img, (100, 330), 300, (28, 229, 195), 60)
    draw = ImageDraw.Draw(img, "RGBA")
    # Faint TV frame.
    draw.rounded_rectangle((462, 84, 596, 260), radius=18, outline=(255, 255, 255, 45), width=3)
    draw.line((506, 276, 552, 276), fill=(255, 255, 255, 70), width=5)
    draw.ellipse((516, 112, 546, 112 + 30), outline=(255, 120, 70, 210), width=4)
    draw.text((60, 118), "KISIUM", font=font(90), fill=(255, 255, 255, 255))
    draw.text((60, 208), "TV", font=font(90), fill=(255, 108, 60, 255))
    draw_tracked(draw, (65, 64), "PRIVATE STREAMING", font(18), (32, 229, 197, 235), 4)
    draw.rectangle((65, 318, 250, 322), fill=(255, 108, 60, 255))
    draw.text((266, 310), "DEIN KINO. DEINE REGELN.", font=font(17, False), fill=(200, 210, 222, 210))
    add_grain(img, 7, 31)
    out_dir = RES / "drawable-xhdpi"
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / "tv_banner.png"
    img.convert("RGB").resize((320, 180), Image.LANCZOS).save(out, "PNG", optimize=True)
    print(out.relative_to(ROOT))


def make_icon() -> None:
    size = 512
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Rounded square with diagonal gradient.
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size, size), radius=112, fill=255)
    grad = gradient((size, size), (12, 16, 26), (104, 22, 18)).convert("RGBA")
    add_glow(grad, (390, 110), 360, (255, 92, 48), 230)
    add_glow(grad, (80, 430), 300, (32, 229, 197), 80)
    img.paste(grad, (0, 0), mask)
    draw = ImageDraw.Draw(img)
    draw.text((132, 118), "K", font=font(310), fill=(255, 255, 255, 255))
    draw.rectangle((150, 414, 360, 423), fill=(255, 112, 62, 255))
    add_grain(img, 8, 17)
    for name, px in {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }.items():
        outdir = RES / name
        outdir.mkdir(parents=True, exist_ok=True)
        img.resize((px, px), Image.LANCZOS).save(outdir / "ic_launcher.png", "PNG", optimize=True)
        img.resize((px, px), Image.LANCZOS).save(outdir / "ic_launcher_round.png", "PNG", optimize=True)
    print("mipmap-*/ic_launcher.png")


def main() -> None:
    DRAWABLE.mkdir(parents=True, exist_ok=True)
    MIPMAP.mkdir(parents=True, exist_ok=True)
    make_banner()
    make_icon()
    posters = [
        ("nordlicht", "Nordlicht", "KISIUM ORIGINAL", "Krimi · 2 Staffeln"),
        ("stille", "Stille Wasser", "KISIUM ORIGINAL", "Drama · 2025"),
        ("frequenz", "Die letzte Frequenz", "KISIUM ORIGINAL", "Sci-Fi · 2024"),
        ("nachtzug", "Nachtzug", "KISIUM ORIGINAL", "Thriller · 2023"),
        ("glut", "Glut", "KISIUM ORIGINAL", "Drama · 2025"),
        ("kosmos", "Kosmos 9", "KISIUM ORIGINAL", "Sci-Fi · 1 Staffel"),
        ("hafenkante", "Hafenkante", "KISIUM ORIGINAL", "Alltag · 3 Staffeln"),
        ("archiv", "Archiv 77", "KISIUM ORIGINAL", "Mystery · 1 Staffel"),
    ]
    for i, (slug, title, kicker, subtitle) in enumerate(posters):
        make_poster(slug, title, kicker, subtitle, seed=100 + i)
    heroes = [
        ("nordlicht", "Nordlicht", "Im hohen Norden taut ein alter Fall auf. Jetzt streamen."),
        ("stille", "Stille Wasser", "Ein See. Ein Boot. Eine Lüge, die nicht untergeht."),
        ("frequenz", "Die letzte Frequenz", "Das Signal kommt aus der Zukunft."),
        ("nachtzug", "Nachtzug", "Zwischen Wien und Paris verschwindet ein Passagier."),
        ("glut", "Glut", "Drei Generationen. Ein Sommer. Kein Zurück."),
        ("kosmos", "Kosmos 9", "Ein Signal. Zehn Jahre entfernt."),
        ("hafenkante", "Hafenkante", "Eine Stadt, die niemals schläft."),
        ("archiv", "Archiv 77", "Akten, die es nie gegeben hat."),
    ]
    for i, (slug, title, kicker) in enumerate(heroes):
        make_hero(slug, title, kicker, seed=200 + i)
    live = [
        ("live1", "Kisium One", "Showcase · Jetzt live"),
        ("live2", "Kisium Cinema", "Filme · Jetzt live"),
        ("live3", "Kisium Doku", "Wissen · Jetzt live"),
        ("live4", "Kisium Sport", "Sport · Jetzt live"),
    ]
    for i, (slug, title, kicker) in enumerate(live):
        make_hero(slug, title, kicker, seed=300 + i, live=True)


if __name__ == "__main__":
    main()
