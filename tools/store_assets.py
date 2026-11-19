#!/usr/bin/env python3
"""
Genera las imágenes de la ficha de la tienda (Google Play / F-Droid, formato fastlane):

  - fastlane/metadata/android/en-US/images/icon.png            512×512
  - fastlane/metadata/android/en-US/images/featureGraphic.png  1024×500

Los glifos se dibujan con la misma fuente y las mismas reglas que el pack
(tools/generate_icons.py); el SVG se pasa a PNG con rsvg-convert (librsvg).

Uso:
  ~/.venvs/tokipona-icons/bin/python -I tools/store_assets.py
"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from xml.sax.saxutils import escape

sys.path.insert(0, str(Path(__file__).resolve().parent))  # -I no añade la carpeta del script
import generate_icons as gi  # noqa: E402

OUT = gi.ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images"

# Mismos colores que la app: fondo del icono de la app y de las miniaturas del pack.
BG = "#1B1B1F"
TILE = "#3A4556"
FG = "#FFFFFF"
MUTED = "#B8C0CC"
TEXT_FONT = "Roboto, Inter, 'Noto Sans', sans-serif"

TITLE = "Toki Pona Icon Pack"
SUBTITLE = "Your apps, written in sitelen pona"
# Glifo y traducción que se muestran en el feature graphic.
SHOWCASE = [
    ("ilo toki", "phone"),
    ("tenpo", "clock"),
    ("ma", "maps"),
    ("kalama musi", "music"),
    ("linluwi", "browser"),
    ("sona", "AI"),
]


def glyph(font, expr, size, cx, cy, thin=gi.THIN):
    """Path SVG del glifo: `size` = lado de la caja em, centrado en (cx, cy) como en el pack."""
    path = font.compose(expr)
    s, ox, oy = font.place(path, size, cx, cy)
    return font.svg(path, s, ox, oy, thin)


def svg(w, h, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}">\n{body}\n</svg>\n')


def icon_svg(font):
    # Cuadrado completo: Play aplica su propia máscara de esquinas redondeadas.
    d = glyph(font, gi.LAUNCHER_WORD, 512 * 0.66, 256, 256)
    return svg(512, 512, f'<rect width="512" height="512" fill="{BG}"/>\n'
                         f'<path fill="{FG}" d="{d}"/>')


def feature_svg(font):
    w, h = 1024, 500
    tile, gap = 124, 28
    x = (w - (len(SHOWCASE) * tile + (len(SHOWCASE) - 1) * gap)) / 2
    y = 262
    body = [
        f'<rect width="{w}" height="{h}" fill="{BG}"/>',
        f'<text x="{w / 2}" y="138" text-anchor="middle" font-family="{TEXT_FONT}" '
        f'font-size="68" font-weight="bold" fill="{FG}">{escape(TITLE)}</text>',
        f'<text x="{w / 2}" y="196" text-anchor="middle" font-family="{TEXT_FONT}" '
        f'font-size="32" fill="{MUTED}">{escape(SUBTITLE)}</text>',
    ]
    for i, (expr, gloss) in enumerate(SHOWCASE):
        tx = x + i * (tile + gap)
        cx, cy = tx + tile / 2, y + tile / 2
        # Misma proporción glifo/miniatura que en el pack (SAFE / VIEWPORT).
        d = glyph(font, expr, tile * gi.SAFE / gi.VIEWPORT, cx, cy)
        body += [
            f'<rect x="{tx}" y="{y}" width="{tile}" height="{tile}" rx="30" fill="{TILE}"/>',
            f'<path fill="{FG}" d="{d}"/>',
            f'<text x="{cx}" y="{y + tile + 40}" text-anchor="middle" font-family="{TEXT_FONT}" '
            f'font-size="24" fill="{FG}">{escape(expr)}</text>',
            f'<text x="{cx}" y="{y + tile + 70}" text-anchor="middle" font-family="{TEXT_FONT}" '
            f'font-size="20" fill="{MUTED}">{escape(gloss)}</text>',
        ]
    return svg(w, h, "\n".join(body))


def render(svg_text, w, h, dest: Path, tmp: Path):
    src = tmp / (dest.stem + ".svg")
    src.write_text(svg_text, encoding="utf-8")
    subprocess.run(["rsvg-convert", "-w", str(w), "-h", str(h), "-b", BG,
                    "-o", str(dest), str(src)], check=True)
    print(f"{dest.relative_to(gi.ROOT)} ({w}×{h})")


def main():
    if not shutil.which("rsvg-convert"):
        raise SystemExit("Falta rsvg-convert (paquete librsvg)")
    font = gi.Font(gi.RES / "font" / "fairfax_pona_hd.ttf")
    gi.NEST_HEADS.update(font.containers - gi.NEVER_NEST)
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        render(icon_svg(font), 512, 512, OUT / "icon.png", tmp)
        render(feature_svg(font), 1024, 500, OUT / "featureGraphic.png", tmp)


if __name__ == "__main__":
    main()
