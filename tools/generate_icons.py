#!/usr/bin/env python3
"""
Genera el icon pack a partir de la fuente Fairfax Pona HD (Kreative Software, OFL), recortada a
su parte de sitelen pona en app/src/main/res/font/fairfax_pona_hd.ttf.

  - app/src/main/res/drawable/<palabra>.xml      (un vector por glifo / compuesto)
  - app/src/main/res/xml/appfilter.xml           (componente -> drawable)
  - app/src/main/res/xml/drawable.xml            (lista para el selector de iconos del launcher)
  - copias de ambos XML en app/src/main/assets/
  - app/src/main/res/drawable/ic_launcher_foreground.xml (icono de la app; también monochrome)

Sintaxis de "words" en tools/apps.json:
  toki            una palabra
  ilo tenpo       compuesto automático: anidado si la primera palabra tiene hueco (NEST_HEADS),
                  apilado en los demás casos
  ilo^tenpo       fuerza apilado: la primera palabra abajo (ilo), la segunda arriba (tenpo)
  poki+sewi       fuerza anidado: la segunda palabra dentro de la primera (sewi dentro de poki)

Compuestos: si Fairfax trae ese compuesto dibujado (unos 400), se usa tal cual. Si no, se
compone con lo que se aprende de los suyos: dentro de cada contenedor, la caja donde Fairfax
pone la segunda palabra; en los apilados, sus dos cajas (abajo y arriba). La pieza reducida
recupera el grosor de trazo de la fuente, como en los compuestos de Fairfax.

Uso:
  python3 -m venv ~/.venvs/tokipona-icons
  ~/.venvs/tokipona-icons/bin/pip install -r tools/requirements.txt
  ~/.venvs/tokipona-icons/bin/python -I tools/generate_icons.py [--from-device] [--font RUTA.ttf]
"""
import argparse
import json
import os
import re
import shutil
import subprocess
from pathlib import Path
from xml.sax.saxutils import quoteattr

from fontTools.pens.roundingPen import RoundingPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
import pathops
import uharfbuzz as hb

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app" / "src" / "main"
RES = MAIN / "res"

# ---- Estilo (edita aquí) ----------------------------------------------------
VIEWPORT = 192          # unidades del viewport del vector
ICON_SIZE_DP = 96       # tamaño intrínseco del drawable
SAFE = 150              # lado del área donde cabe el glifo (dentro del viewport)
FILL = "#FFFFFFFF"      # color del glifo
OUTLINE = "#59000000"   # contorno suave para que se lea en fondos claros (None = sin contorno)
OUTLINE_WIDTH = 4       # en unidades del viewport
THIN = 25               # adelgaza el trazo: unidades de fuente que se quitan por cada lado
                        # (el trazo de Fairfax HD mide 80; 0 = grosor original)
LAUNCHER_WORD = "toki pona"  # glifo del icono de la propia app (pona dentro de toki)

# Combinaciones automáticas: cada una de estas palabras + cualquier otra palabra de la fuente.
# Si la primera tiene hueco (contenedor), la segunda va dentro; si no, apiladas.
# [] = desactivado. Cada cabeza añade ~170 iconos (~0,6 KB cada uno en el APK).
COMBO_HEADS = [
    "ilo", "tomo", "lipu", "jan", "sitelen", "toki", "poki", "ma", "kalama", "musi",
    "esun", "mani", "nasin", "tenpo", "kulupu", "pilin", "lawa", "sona", "telo", "moku",
    "nimi", "kama",
]
# Palabras con hueco que NO quieres anidar automáticamente (se apilan salvo que uses '+').
NEVER_NEST: set = set()
# Nombres de personas (no son palabras del idioma y confunden en el pack) y te/to, que son las
# comillas.
NOT_WORDS = {"itan", "lepeka", "lipamanka", "te", "to"}
# La fuente acepta varias grafías para algunas palabras (ale/ali, tomo/toma, tenpo/ten…);
# estas son las que se usan como nombre. Para el resto de sinónimos, la más larga.
PREFERRED_NAMES = {"ale", "kepeken", "kijetesantakalu", "linluwi", "ni", "oke", "san", "sike",
                   "tenpo", "tomo", "wuwojiti"}
# -----------------------------------------------------------------------------

# Medidas de Fairfax HD (unidades de fuente, 1200 por em). Todos los glifos se escalan igual,
# así el grosor del trazo es el mismo en todos los iconos.
EM_W = 1200             # lado de la caja que se ajusta a SAFE
EM_CENTER_X = 600
EM_CENTER_Y = 300       # los glifos ocupan de -190 a 790
EM_X0, EM_X1 = 110, 1090   # y de 110 a 1090 en horizontal
EM_Y1 = 790
FONT_STROKE = 80        # grosor del trazo
STACK, SCALE = "\U000F1995", "\U000F1996"      # controles UCSUR: apilar / escalar (anidar)
# Palabras de sitelen pona en UCSUR (fuera quedan la puntuación y los controles, U+F1990–F199F).
WORD_RANGES = ((0xF1900, 0xF198F), (0xF19A0, 0xF19FF))
# Si un contenedor no tiene compuestos anidados propios en Fairfax, su caja sale del hueco del
# glifo: solo cuenta un hueco vacío que ocupe al menos esta parte de la caja del glifo.
MIN_HOLLOW = 0.3
MIN_NEST_BOX = 0.35     # y la caja que queda para la segunda palabra, al menos esta parte del lado
MIN_HOLLOW_FILL = 0.75  # hueco redondo o cuadrado: ocupa al menos esto de su caja

MARKER = "glifo de Fairfax HD"    # comentario que identifica los drawables generados
OLD_MARKERS = ("glifo de nasin-nanpa",)   # generados con la fuente anterior (se borran igual)
# Palabras que anidan automáticamente: las que tienen hueco (se calcula al cargar la fuente,
# menos NEVER_NEST). Debe coincidir con SitelenExpression.NEST_HEADS en la app.
NEST_HEADS: set = set()


def fmt(v: float) -> str:
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


def _median_box(boxes):
    return tuple(sorted(b[i] for b in boxes)[len(boxes) // 2] for i in range(4))


def _bbox(contours):
    xs = [x for c in contours for x, _ in c]
    ys = [y for c in contours for _, y in c]
    return min(xs), min(ys), max(xs), max(ys)


def _area(contour):
    """Área con signo (shoelace): el signo distingue tinta de huecos."""
    return sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(contour, contour[1:] + contour[:1])) / 2


class Font:
    def __init__(self, path: Path):
        self.tt = TTFont(str(path))
        self.gs = self.tt.getGlyphSet()
        self.glyf = self.tt["glyf"]
        self.hb = hb.Font(hb.Face(path.read_bytes()))
        self.words = self._read_words()             # palabra -> glifo
        self.tokens = sorted(self.words, key=lambda w: self._cp(w))
        self.nest_box, self.stack_boxes = self._learn()
        self.containers = set(self.nest_box)
        self.unnested = set()                       # compuestos con '+' que se apilaron
        self.suspicious = set()                     # iconos cuyo adelgazado no salió bien

    # -- palabras -------------------------------------------------------------------------
    def _read_words(self):
        """Palabras ASCII que la fuente convierte en un glifo de sitelen pona (sus ligaduras)."""
        cmap = self.tt.getBestCmap()
        letters = {g: chr(c) for c, g in cmap.items() if 0x61 <= c <= 0x7A}
        word_glyphs = {g: c for c, g in cmap.items() if any(a <= c <= b for a, b in WORD_RANGES)}
        names = {}                                  # glifo -> nombres
        for lookup in self.tt["GSUB"].table.LookupList.Lookup:
            for sub in lookup.SubTable:
                if lookup.LookupType == 7:
                    sub = sub.ExtSubTable
                if not hasattr(sub, "ligatures"):
                    continue
                for first, ligs in sub.ligatures.items():
                    for lig in ligs:
                        seq = [first] + list(lig.Component)
                        if lig.LigGlyph in word_glyphs and all(g in letters for g in seq):
                            names.setdefault(lig.LigGlyph, set()).add("".join(letters[g] for g in seq))
        for ch in "aeon":                           # palabras de una letra: no son ligaduras
            [g] = self.shape(ch)
            if g in word_glyphs:
                names.setdefault(g, set()).add(ch)
        words = {}
        for glyph, ns in names.items():
            # Sinónimos: el preferido; si no hay, el más largo (y si empatan, por orden alfabético).
            word = sorted(ns, key=lambda n: (n not in PREFERRED_NAMES, -len(n), n))[0]
            if word not in NOT_WORDS:
                words[word] = glyph
        return words

    def _cp(self, word):
        return int(self.words[word][1:].split(".")[0], 16)

    def char(self, word):
        if word not in self.words:
            raise SystemExit(f"'{word}' no es una palabra de la fuente")
        return chr(self._cp(word))

    def shape(self, text: str):
        buf = hb.Buffer()
        buf.add_str(text)
        buf.guess_segment_properties()
        hb.shape(self.hb, buf, {})
        return [self.hb.glyph_to_string(i.codepoint) for i in buf.glyph_infos]

    def contours(self, glyph):
        """Contornos del glifo como tuplas de puntos (sirven para compararlos entre glifos)."""
        if self.glyf[glyph].isComposite():
            p = pathops.Path()
            self.gs[glyph].draw(p.getPen(glyphSet=self.gs))
            return self._contours_from_path(p)
        co, ends, _ = self.glyf[glyph].getCoordinates(self.glyf)
        out, start = [], 0
        for e in ends:
            out.append(tuple(map(tuple, co[start:e + 1])))
            start = e + 1
        return out

    @staticmethod
    def _contours_from_path(path):
        out, cur = [], []
        for verb, pts in path:
            if verb == pathops.PathVerb.MOVE:
                cur = [pts[0]]
            elif verb == pathops.PathVerb.CLOSE:
                out.append(tuple(cur))
            else:
                cur.extend(pts)
        return out

    # -- lo que se aprende de los compuestos de la fuente --------------------------------
    def _learn(self):
        nest, bottoms, tops = {}, [], []
        for a in self.tokens:
            a_cont = set(self.contours(self.words[a]))
            for b in self.tokens:
                g = self.shape(self.char(a) + SCALE + self.char(b))
                if len(g) == 1:
                    allc = self.contours(g[0])
                    rest = [c for c in allc if c not in a_cont]
                    if rest and len(rest) < len(allc):     # el contenedor quedó intacto
                        nest.setdefault(a, []).append(_bbox(rest))
                g = self.shape(self.char(a) + STACK + self.char(b))
                if len(g) == 1:
                    allc = self.contours(g[0])
                    low = [c for c in allc if (_bbox([c])[1] + _bbox([c])[3]) / 2 < EM_CENTER_Y]
                    high = [c for c in allc if c not in low]
                    # Solo los apilados limpios: una pieza entera debajo de la otra.
                    if low and high and _bbox(low)[3] < _bbox(high)[1]:
                        bottoms.append(_bbox(low))
                        tops.append(_bbox(high))
        nest_box = {a: _median_box(bs) for a, bs in nest.items()}
        # Mitad de abajo: la de Fairfax (sus piezas de abajo llenan el hueco). La de arriba, igual
        # de alta y pegada arriba (las piezas de arriba suelen ser bajitas y la mediana engaña).
        # Anchura: toda la del em, así cada pieza toma la suya al escalarse sin deformarse.
        b = _median_box(bottoms)
        h = b[3] - b[1]
        stack_boxes = ((EM_X0, b[1], EM_X1, b[3]), (EM_X0, EM_Y1 - h, EM_X1, EM_Y1))
        # Contenedores sin compuestos anidados propios: la caja sale de su hueco, con el mismo
        # margen que deja Fairfax en los que sí tiene.
        insets = []
        for a, box in nest_box.items():
            hole = self._hollow(a)
            if hole:
                h = hole[0]
                insets.append(min(box[0] - h[0], box[1] - h[1], h[2] - box[2], h[3] - box[3]))
        margin = sorted(insets)[len(insets) // 2] if insets else FONT_STROKE
        for a in self.tokens:
            if a in nest_box:
                continue
            hole = self._hollow(a)
            if not hole:
                continue
            hole_box, fill = hole
            box = (hole_box[0] + margin, hole_box[1] + margin, hole_box[2] - margin, hole_box[3] - margin)
            gx0, gy0, gx1, gy1 = _bbox(self.contours(self.words[a]))
            # Caja razonable (no un resquicio como el de palisa), y hueco redondo o cuadrado: los
            # que forman las líneas al cruzarse (ku, oko, pakala…) ocupan poco de su caja.
            if (fill >= MIN_HOLLOW_FILL and
                    min(box[2] - box[0], box[3] - box[1]) >= MIN_NEST_BOX * max(gx1 - gx0, gy1 - gy0)):
                nest_box[a] = box
        return nest_box, stack_boxes

    def _hollow(self, word):
        """(caja, relleno) del mayor hueco vacío del glifo (contorno de signo contrario sin tinta
        dentro); relleno = qué parte de su caja ocupa el hueco."""
        cs = self.contours(self.words[word])
        if not cs:
            return None
        outer_sign = 1 if _area(max(cs, key=lambda c: abs(_area(c)))) > 0 else -1
        gx0, gy0, gx1, gy1 = _bbox(cs)
        best = None
        for c in cs:
            if _area(c) * outer_sign >= 0:
                continue
            x0, y0, x1, y1 = _bbox([c])
            inside = [d for d in cs if d is not c and _area(d) * outer_sign > 0
                      and x0 <= _bbox([d])[0] and _bbox([d])[2] <= x1
                      and y0 <= _bbox([d])[1] and _bbox([d])[3] <= y1]
            if inside:
                continue                            # el hueco tiene algo dentro (ojo de lukin…)
            if (x1 - x0) * (y1 - y0) < MIN_HOLLOW * (gx1 - gx0) * (gy1 - gy0):
                continue
            if best is None or (x1 - x0) * (y1 - y0) > (best[0][2] - best[0][0]) * (best[0][3] - best[0][1]):
                best = ((x0, y0, x1, y1), abs(_area(c)) / ((x1 - x0) * (y1 - y0)))
        return best

    # -- dibujo -----------------------------------------------------------------------------
    def glyph_path(self, glyph, transform=(1, 0, 0, 1, 0, 0)):
        p = pathops.Path()
        # Coordenadas redondeadas a unidades enteras: con ciertos desplazamientos (decimales)
        # simplify() de skia-pathops interpreta mal los huecos y los rellena (tenpo en tenpo^suno).
        self.gs[glyph].draw(TransformPen(RoundingPen(p.getPen(glyphSet=self.gs)), transform))
        p.simplify()
        return p

    def piece(self, word, box):
        """Glifo suelto escalado sin deformar dentro de `box`, con el trazo devuelto a FONT_STROKE."""
        glyph = self.words[word]
        gx0, gy0, gx1, gy1 = self.glyph_path(glyph).bounds
        bx0, by0, bx1, by1 = box
        k = min((bx1 - bx0) / (gx1 - gx0), (by1 - by0) / (gy1 - gy0))
        tx = (bx0 + bx1) / 2 - (gx0 + gx1) / 2 * k
        ty = (by0 + by1) / 2 - (gy0 + gy1) / 2 * k
        p = self.glyph_path(glyph, (k, 0, 0, k, tx, ty))
        grow = FONT_STROKE * (1 - k) / 2        # el trazo escalado mide FONT_STROKE·k
        if grow > 0:
            edge = pathops.Path(p)
            edge.stroke(2 * grow, pathops.LineCap.BUTT_CAP, pathops.LineJoin.MITER_JOIN, 4)
            edge.convertConicsToQuads()
            p = pathops.op(p, edge, pathops.PathOp.UNION)
        return p

    def compose(self, expr: str):
        """Path del glifo o compuesto, en unidades de fuente."""
        op, tokens = parse(expr)
        if len(tokens) == 1:
            return self.glyph_path(self.words[tokens[0]] if tokens[0] in self.words
                                   else self.shape(self.char(tokens[0]))[0])
        if len(tokens) > 2:
            raise SystemExit(f"'{expr}': solo se admiten compuestos de dos palabras")
        a, b = tokens
        g = self.shape(self.char(a) + (SCALE if op == "+" else STACK) + self.char(b))
        if len(g) == 1:                             # Fairfax lo trae dibujado
            return self.glyph_path(g[0])
        if op == "+" and a not in self.nest_box:
            self.unnested.add(expr)                 # sin hueco donde anidar: se apila
            op = "^"
        if op == "+":
            return pathops.op(self.glyph_path(self.words[a]), self.piece(b, self.nest_box[a]),
                              pathops.PathOp.UNION)
        bottom, top = self.stack_boxes
        return pathops.op(self.piece(a, bottom), self.piece(b, top), pathops.PathOp.UNION)

    def thinned(self, path, thin):
        """Erosión: al relleno se le resta un trazo redondo que sigue su propio contorno.

        skia-pathops falla a veces con coordenadas decimales y rellena huecos (lawa en
        lawa^powe). Se detecta porque la tinta apenas baja: adelgazar el trazo de FONT_STROKE a
        FONT_STROKE - 2·thin debería dejar como mucho esa proporción (más algo por puntos y
        extremos). Si pasa, se reintenta con coordenadas redondeadas y desplazadas un poco."""
        expected = (FONT_STROKE - 2 * thin) / FONT_STROKE
        for shift in (0, 0.37, 0.71, 1.13):
            p = pathops.Path()
            path.draw(TransformPen(RoundingPen(p.getPen()), (1, 0, 0, 1, shift, shift)))
            p.simplify()
            edge = pathops.Path(p)
            edge.stroke(2 * thin, pathops.LineCap.ROUND_CAP, pathops.LineJoin.ROUND_JOIN, 4)
            edge.convertConicsToQuads()
            out = pathops.op(p, edge, pathops.PathOp.DIFFERENCE)
            if out.area <= path.area * min(1.0, expected + 0.25):
                back = pathops.Path()
                out.draw(TransformPen(back.getPen(), (1, 0, 0, 1, -shift, -shift)))
                return back, True
        return out, False

    def svg(self, path, s, ox, oy, thin=0.0, name=None) -> str:
        """Adelgaza `path` (unidades de fuente) y lo pasa a coordenadas SVG (y hacia abajo)."""
        p = pathops.Path(path)
        if thin > 0:
            p, ok = self.thinned(p, thin)
            if not ok:
                self.suspicious.add(name or "?")
        q = pathops.Path()
        p.draw(TransformPen(q.getPen(), (s, 0, 0, -s, ox, oy)))
        q.convertConicsToQuads()
        pen = SVGPathPen(None, ntos=fmt)
        q.draw(pen)
        return pen.getCommands()

    def place(self, path, size, cx, cy):
        """Escala y desplazamiento para centrar `path` en (cx, cy) con la caja em de lado `size`.

        Centrado horizontal por la tinta y vertical por la caja em, como el pack."""
        s = size / EM_W
        x0, _, x1, _ = path.bounds
        return s, cx - (x0 + x1) / 2 * s, cy + EM_CENTER_Y * s


def parse(expr: str):
    """Devuelve (op, tokens) con op '^' (apilado) o '+' (anidado)."""
    expr = expr.strip()
    if "+" in expr:
        return "+", [t.strip() for t in expr.split("+")]
    if "^" in expr:
        return "^", [t.strip() for t in expr.split("^")]
    tokens = expr.split()
    return ("+" if len(tokens) > 1 and tokens[0] in NEST_HEADS else "^"), tokens


def vector_xml(font: Font, expr: str, viewport, safe, size_dp, fill, outline, outline_w):
    path = font.compose(expr)
    s, ox, oy = font.place(path, safe, viewport / 2, viewport / 2)
    d = font.svg(path, s, ox, oy, THIN, name=expr)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- {expr} — {MARKER} (Kreative Software, licencia OFL) -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{size_dp}dp"',
        f'    android:height="{size_dp}dp"',
        f'    android:viewportWidth="{viewport}"',
        f'    android:viewportHeight="{viewport}">',
    ]
    if outline:
        lines += [
            "    <path",
            f'        android:strokeColor="{outline}"',
            f'        android:strokeWidth="{outline_w}"',
            '        android:strokeLineJoin="round"',
            f'        android:pathData="{d}" />',
        ]
    lines += [
        "    <path",
        f'        android:fillColor="{fill}"',
        f'        android:pathData="{d}" />',
        "</vector>",
    ]
    return "\n".join(lines) + "\n"


def drawable_name(expr: str) -> str:
    op, tokens = parse(expr)
    return ("_en_" if op == "+" else "_").join(tokens)


def layout_json(font: Font) -> str:
    """Lo que el editor de la app necesita para componer igual que el pack (GlyphRenderer.kt)."""
    def box(b):
        return [round(v) for v in b]
    data = {
        "_comment": "Generado por tools/generate_icons.py a partir de fairfax_pona_hd.ttf. No editar.",
        "em": EM_W,
        "centerY": EM_CENTER_Y,
        "stroke": FONT_STROKE,
        "thin": THIN,
        "words": {w: f"{font._cp(w):X}" for w in font.tokens},
        "nest": {w: box(font.nest_box[w]) for w in sorted(font.nest_box)},
        "neverNest": sorted(NEVER_NEST),     # tienen hueco pero solo anidan con '+'
        "stack": [box(font.stack_boxes[0]), box(font.stack_boxes[1])],
    }
    return json.dumps(data, indent=1, ensure_ascii=False) + "\n"


APP_PACKAGE = "com.pepesantos.tokiponaiconpack"
DEVICE_EXPORT = "tokipona_export.json"   # lo escribe la app (data/AssignmentsStore.kt)


def pull_from_device(dest: Path):
    """Copia por ADB el archivo con las asignaciones y palabras guardadas en la app."""
    adb = [shutil.which("adb") or str(Path.home() / "Android/Sdk/platform-tools/adb")]
    # Con varias conexiones (p. ej. el mismo teléfono por USB y por wifi) adb exige elegir una:
    # se usa ANDROID_SERIAL si está definida y, si no, la primera conexión disponible.
    if not os.environ.get("ANDROID_SERIAL"):
        try:
            out = subprocess.run(adb + ["devices"], capture_output=True, text=True, timeout=15).stdout
        except (OSError, subprocess.TimeoutExpired):
            out = ""
        serials = [l.split("\t")[0] for l in out.splitlines()[1:] if l.endswith("\tdevice")]
        if len(serials) > 1:
            print(f"Varias conexiones ADB; uso {serials[0]} (elige otra con ANDROID_SERIAL=...)")
            adb += ["-s", serials[0]]
    attempts = [
        adb + ["exec-out", "run-as", APP_PACKAGE, "cat", f"files/{DEVICE_EXPORT}"],   # build debug
        adb + ["exec-out", "cat", f"/sdcard/Android/data/{APP_PACKAGE}/files/{DEVICE_EXPORT}"],
    ]
    for cmd in attempts:
        try:
            r = subprocess.run(cmd, capture_output=True, timeout=30)
        except (OSError, subprocess.TimeoutExpired):
            continue
        data = r.stdout.strip()
        if r.returncode == 0 and data.startswith(b"{"):
            json.loads(data)
            dest.write_bytes(data + b"\n")
            print(f"Leído del teléfono -> {dest.relative_to(ROOT)}")
            return
    raise SystemExit("No pude leer las asignaciones del teléfono. ¿Está conectado por ADB y "
                     "has abierto la app al menos una vez?")


# Fuente de los nombres exactos de actividad (paquete/Actividad) para las categorías: el
# appfilter de Arcticons, mantenido por su comunidad. Solo se extraen los identificadores de
# los paquetes listados en tools/categories.json, nada de sus iconos.
ARCTICONS_APPFILTER = ("https://raw.githubusercontent.com/Arcticons-Team/Arcticons/main/"
                       "app/src/main/res/xml/appfilter.xml")


def refresh_components(categories, dest: Path):
    """Descarga el appfilter de Arcticons y guarda los componentes de nuestros paquetes."""
    import urllib.request
    wanted = {p for c in categories for p in c.get("packages", [])}
    print(f"Descargando {ARCTICONS_APPFILTER} …")
    with urllib.request.urlopen(ARCTICONS_APPFILTER, timeout=60) as r:
        text = r.read().decode("utf-8", errors="replace")
    found = {}
    for comp in set(re.findall(r"ComponentInfo\{([^}]+)\}", text)):
        pkg = comp.split("/", 1)[0]
        if pkg in wanted and "/" in comp:
            found.setdefault(pkg, []).append(comp)
    data = {"_source": ARCTICONS_APPFILTER,
            "components": {p: sorted(found[p]) for p in sorted(found)}}
    dest.write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    missing = sorted(wanted - set(found))
    print(f"{sum(len(v) for v in found.values())} componentes para {len(found)} paquetes -> "
          f"{dest.relative_to(ROOT)}")
    if missing:
        print("Sin componentes conocidos (revisa el nombre del paquete): " + ", ".join(missing))


def main():
    ap = argparse.ArgumentParser()
    # Por defecto, la misma fuente que lleva la app (así el pack y el editor coinciden).
    ap.add_argument("--font", default=str(RES / "font" / "fairfax_pona_hd.ttf"))
    ap.add_argument("--apps", default=str(ROOT / "tools" / "apps.json"))
    ap.add_argument("--device-file", default=str(ROOT / "tools" / "apps_device.json"),
                    help="asignaciones hechas en la app (se usan si el archivo existe)")
    ap.add_argument("--from-device", action="store_true",
                    help="antes de generar, copia por ADB las asignaciones de la app")
    ap.add_argument("--categories", default=str(ROOT / "tools" / "categories.json"))
    ap.add_argument("--known-components", default=str(ROOT / "tools" / "known_components.json"))
    ap.add_argument("--refresh-components", action="store_true",
                    help="actualiza known_components.json desde el appfilter de Arcticons")
    args = ap.parse_args()

    categories_file = Path(args.categories)
    categories = (json.loads(categories_file.read_text(encoding="utf-8"))["categories"]
                  if categories_file.exists() else [])
    known_file = Path(args.known_components)
    if args.refresh_components:
        refresh_components(categories, known_file)
    known = (json.loads(known_file.read_text(encoding="utf-8"))["components"]
             if known_file.exists() else {})

    font = Font(Path(args.font))
    apps = json.loads(Path(args.apps).read_text(encoding="utf-8"))["icons"]

    NEST_HEADS.update(font.containers - NEVER_NEST)

    # Asignaciones hechas en la app: tienen prioridad sobre apps.json.
    device_file = Path(args.device_file)
    if args.from_device:
        pull_from_device(device_file)
    device = json.loads(device_file.read_text(encoding="utf-8")) if device_file.exists() else {}
    device_icons = [dict(a, words=a["words"].strip().lower()) for a in device.get("icons", [])]
    device_saved = [w.strip().lower() for w in device.get("saved", []) if w.strip()]
    overridden = {c for a in device_icons for c in a["components"]}
    apps = [dict(a, components=[c for c in a["components"] if c not in overridden]) for a in apps]

    drawable_dir = RES / "drawable"
    drawable_dir.mkdir(parents=True, exist_ok=True)
    # Borra lo generado antes (marcado con MARKER o con el de la fuente anterior; tp_* es el
    # formato antiguo).
    for old in drawable_dir.glob("*.xml"):
        text = old.read_text(encoding="utf-8")
        if old.name.startswith("tp_") or any(m in text for m in (MARKER, *OLD_MARKERS)):
            old.unlink()

    written = {}

    def write_icon(expr):
        name = drawable_name(expr)
        if name not in written:
            (drawable_dir / f"{name}.xml").write_text(
                vector_xml(font, expr, VIEWPORT, SAFE, ICON_SIZE_DP, FILL, OUTLINE, OUTLINE_WIDTH),
                encoding="utf-8",
            )
            written[name] = expr
        return name

    # 1) Todas las palabras, para asignarlas a mano desde el launcher.
    all_words = [write_icon(t) for t in font.tokens]

    def try_write_icon(expr, origin):
        """Como write_icon, pero lo que viene del teléfono no rompe la generación si es inválido."""
        try:
            return write_icon(expr)
        except SystemExit as e:
            print(f"AVISO ({origin}): se ignora '{expr}': {e}")
            return None

    # 2) Iconos asignados a apps (incluye compuestos). Primero apps.json, luego lo de la app.
    filter_items, app_drawables, seen = [], [], set()
    for app in apps + [dict(a, _device=True) for a in device_icons]:
        if app.get("_device"):
            name = try_write_icon(app["words"], f"app, {app['label']}")
            if name is None:
                continue
        else:
            name = write_icon(app["words"])
        if name not in app_drawables:
            app_drawables.append(name)
        for comp in app["components"]:
            if comp in seen:
                raise SystemExit(f"Componente duplicado: {comp}")
            seen.add(comp)
            filter_items.append(
                f"    <!-- {app['label']} : {app['words']} -->\n"
                f"    <item component={quoteattr('ComponentInfo{' + comp + '}')} drawable=\"{name}\" />"
            )

    # 2b) Categorías: glifo genérico para apps conocidas que no tienen uno específico.
    #     Van después de apps.json y del teléfono, así los específicos siempre ganan.
    n_category, unknown_pkgs = 0, []
    for cat in categories:
        comps = list(cat.get("components", []))
        for pkg in cat.get("packages", []):
            if pkg in known:
                comps += known[pkg]
            else:
                unknown_pkgs.append(pkg)
        comps = [c for c in dict.fromkeys(comps) if c not in seen]
        if not comps:
            continue
        name = write_icon(cat["words"])
        if name not in app_drawables:
            app_drawables.append(name)
        filter_items.append(f"    <!-- category: {cat['label']} : {cat['words']} -->")
        for comp in comps:
            seen.add(comp)
            filter_items.append(
                f"    <item component={quoteattr('ComponentInfo{' + comp + '}')} drawable=\"{name}\" />"
            )
            n_category += 1
    if unknown_pkgs:
        print(f"AVISO: {len(unknown_pkgs)} paquetes de categories.json sin componentes conocidos "
              f"(ejecuta con --refresh-components): " + ", ".join(unknown_pkgs))

    # 3) Compuestos extra (sin app asignada) para el selector manual.
    for expr in json.loads(Path(args.apps).read_text(encoding="utf-8")).get("extra_compounds", []):
        write_icon(expr)
    # Palabras guardadas en la app: categoría propia en el selector del launcher.
    saved_names = []
    for expr in device_saved:
        name = try_write_icon(expr, "app, nimi sina")
        if name and name not in saved_names:
            saved_names.append(name)
    curated = list(written)  # todo lo anterior (en orden): palabras, apps, extras y tus palabras

    # 4) Combinaciones automáticas: COMBO_HEADS × todas las palabras.
    combo_categories, combo_failed = [], 0
    for head in COMBO_HEADS:
        names = []
        for w in font.tokens:
            if w == head:
                continue
            expr = f"{head}+{w}" if head in NEST_HEADS else f"{head}^{w}"
            try:
                names.append(write_icon(expr))
            except SystemExit:
                combo_failed += 1
        combo_categories.append((head, names))

    appfilter = (
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        + "\n".join(filter_items)
        + "\n</resources>\n"
    )
    (RES / "xml").mkdir(parents=True, exist_ok=True)
    (RES / "xml" / "appfilter.xml").write_text(appfilter, encoding="utf-8")
    (MAIN / "assets").mkdir(parents=True, exist_ok=True)
    (MAIN / "assets" / "appfilter.xml").write_text(appfilter, encoding="utf-8")

    def items(names):
        return "\n".join(f'    <item drawable="{n}" />' for n in names)

    compounds = [n for n in curated if n not in all_words]
    combos_xml = "".join(
        f'    <category title="{head} …" />\n' + items(names) + "\n"
        for head, names in combo_categories if names
    )
    drawable_xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        "    <version>1</version>\n"
        # Títulos en inglés para los selectores de los launchers; la app los traduce por la
        # parte en toki pona (PackIcons.localizedTitle).
        + ('    <category title="nimi sina (your words)" />\n' + items(saved_names) + "\n" if saved_names else "")
        + '    <category title="ilo (apps)" />\n' + items(app_drawables) + "\n"
        '    <category title="nimi ale (all words)" />\n' + items(all_words) + "\n"
        '    <category title="nimi tu (compounds)" />\n' + items(compounds) + "\n"
        + combos_xml
        + "</resources>\n"
    )
    (RES / "xml" / "drawable.xml").write_text(drawable_xml, encoding="utf-8")
    shutil.copyfile(RES / "xml" / "drawable.xml", MAIN / "assets" / "drawable.xml")

    # Para el editor de la app: palabras y cajas aprendidas de la fuente.
    (MAIN / "assets" / "sitelen_layout.json").write_text(layout_json(font), encoding="utf-8")

    # 5) Icono de la propia app (adaptive icon: viewport 108, zona segura 66).
    fg = vector_xml(font, LAUNCHER_WORD, 108, 60, 108, "#FFFFFFFF", None, 0)
    (drawable_dir / "ic_launcher_foreground.xml").write_text(fg, encoding="utf-8")

    learned = sorted(font.containers)
    print(f"{len(font.tokens)} palabras; {len(learned)} contenedores (anidan): " + " ".join(learned))
    if font.unnested:
        print("AVISO: piden anidar en una palabra sin hueco y se apilan: "
              + ", ".join(sorted(font.unnested)))
    if font.suspicious:
        print(f"AVISO: {len(font.suspicious)} iconos con el adelgazado dudoso (revísalos): "
              + ", ".join(sorted(font.suspicious)))
    print(f"{n_category} componentes cubiertos por categorías "
          f"({len(categories)} categorías, {len(known)} paquetes conocidos)")
    n_combos = sum(len(n) for _, n in combo_categories)
    print(f"{n_combos} combinaciones automáticas ({combo_failed} que no se pudieron componer)")
    print(f"{len(all_words)} palabras, {len(compounds)} compuestos, "
          f"{len(filter_items)} componentes en appfilter.xml"
          + (f" ({len(device_icons)} asignaciones y {len(saved_names)} palabras desde la app)"
             if device_icons or saved_names else ""))


if __name__ == "__main__":
    main()
