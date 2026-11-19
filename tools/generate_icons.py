#!/usr/bin/env python3
"""
Genera el icon pack a partir de la fuente nasin-nanpa.

  - app/src/main/res/drawable/<palabra>.xml      (un vector por glifo / compuesto)
  - app/src/main/res/xml/appfilter.xml           (componente -> drawable)
  - app/src/main/res/xml/drawable.xml            (lista para el selector de iconos del launcher)
  - copias de ambos XML en app/src/main/assets/
  - app/src/main/res/drawable/ic_launcher_foreground.xml (icono de la app; también monochrome)

Sintaxis de "words" en tools/apps.json:
  toki            una palabra
  linluwi:1       variante 1 de la palabra (glifo <palabra>Tok_VAR01)
  ilo tenpo       compuesto automático: anidado si la primera palabra tiene hueco en la fuente
                  (NEST_HEADS), apilado en los demás casos
  ilo^tenpo       fuerza apilado: la primera palabra abajo (ilo), la segunda arriba (tenpo)
  poki+sewi       fuerza anidado: la segunda palabra dentro de la primera (sewi dentro de poki)

Uso:
  python3 -m venv ~/.venvs/tokipona-icons
  ~/.venvs/tokipona-icons/bin/pip install -r tools/requirements.txt
  ~/.venvs/tokipona-icons/bin/python -I tools/generate_icons.py [--from-device] [--font RUTA.otf]
"""
import argparse
import json
import os
import re
import shutil
import subprocess
from pathlib import Path
from xml.sax.saxutils import quoteattr

from fontTools.pens.boundsPen import BoundsPen
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
THIN = 34               # adelgaza el trazo: unidades de fuente que se quitan por cada lado
                        # (el trazo de nasin-nanpa mide ~100; 0 = grosor original)
LAUNCHER_WORD = "toki pona"  # glifo del icono de la propia app (pona dentro de toki)

# Combinaciones automáticas: cada una de estas palabras + cualquier otra palabra de la fuente.
# Si la primera tiene hueco (contenedor), la segunda va dentro; si no, apiladas.
# [] = desactivado. Cada cabeza añade ~140 iconos (~0,6 KB cada uno en el APK).
COMBO_HEADS = [
    "ilo", "tomo", "lipu", "jan", "sitelen", "toki", "poki", "ma", "kalama", "musi",
    "esun", "mani", "nasin", "tenpo", "kulupu", "pilin", "lawa", "sona", "telo", "moku",
    "nimi", "kama",
]
# Palabras con hueco que NO quieres anidar automáticamente (se apilan salvo que uses '+').
NEVER_NEST: set = set()
# -----------------------------------------------------------------------------

# Caja de los glifos de nasin-nanpa (unidades de fuente). Todos se escalan igual,
# así el grosor del trazo es el mismo en todos los iconos.
EM_W = 1000
EM_CENTER_Y = 400
MARKER = "glifo de nasin-nanpa"   # comentario que identifica los drawables generados
# Palabras que anidan automáticamente: todas las que tienen hueco en la fuente (se calcula
# al cargarla, menos NEVER_NEST). Debe coincidir con SitelenExpression.NEST_HEADS en la app.
NEST_HEADS: set = set()
NOT_WORDS = {"te", "to", "colon", "space"}  # comillas/puntuación con nombre "...Tok"
# Nombres de personas que trae la fuente (jan Itan, jan Lepeka, jan lipamanka): no son
# palabras del idioma y confunden en el pack.
NOT_WORDS |= {"itan", "lepeka", "lipamanka"}
BASE_RE = re.compile(r"([a-z]+)Tok(?:_VAR(\d+))?")


def fmt(v: float) -> str:
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


class Font:
    def __init__(self, path: Path):
        self.font = TTFont(str(path))
        self.gs = self.font.getGlyphSet()
        self.hb = hb.Font(hb.Face(path.read_bytes()))
        # Palabras con hueco: la fuente trae una versión "<palabra>Tok_joinScaleTok".
        suffix = "Tok_joinScaleTok"
        self.containers = {n[:-len(suffix)] for n in self.font.getGlyphOrder()
                           if n.endswith(suffix) and BASE_RE.fullmatch(n[:-len("_joinScaleTok")])}
        # Todas las palabras (y variantes) que trae la fuente, en su orden.
        self.tokens = []
        for name in self.font.getGlyphOrder():
            m = BASE_RE.fullmatch(name)
            if m and m.group(1) not in NOT_WORDS:
                self.tokens.append(m.group(1) + (f":{int(m.group(2))}" if m.group(2) else ""))

    def shape(self, text: str):
        """Compone el texto con las reglas de la fuente (GSUB + GPOS): [(glifo, x, y)]."""
        buf = hb.Buffer()
        buf.add_str(text)
        buf.guess_segment_properties()
        hb.shape(self.hb, buf, {})
        out, pen_x = [], 0
        for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
            out.append((self.hb.glyph_to_string(info.codepoint),
                        pen_x + pos.x_offset, pos.y_offset))
            pen_x += pos.x_advance
        return out

    def bounds(self, parts):
        pen = BoundsPen(self.gs)
        for glyph, dx, dy in parts:
            self.gs[glyph].draw(TransformPen(pen, (1, 0, 0, 1, dx, dy)))
        return pen.bounds

    def path(self, parts, s, ox, oy, thin=0.0) -> str:
        p = pathops.Path()
        for glyph, dx, dy in parts:
            self.gs[glyph].draw(TransformPen(p.getPen(), (s, 0, 0, -s, ox + dx * s, oy - dy * s)))
        if thin > 0:
            # Erosión: al relleno se le resta un trazo redondo que sigue su propio contorno.
            edge = pathops.Path(p)
            edge.stroke(2 * thin * s, pathops.LineCap.ROUND_CAP, pathops.LineJoin.ROUND_JOIN, 4)
            edge.convertConicsToQuads()
            p = pathops.op(p, edge, pathops.PathOp.DIFFERENCE)
        p.convertConicsToQuads()
        pen = SVGPathPen(None, ntos=fmt)
        p.draw(pen)
        return pen.getCommands()


def ascii_token(token: str) -> str:
    """'sewi:1' -> 'sewi1' (así pide nasin-nanpa las variantes)."""
    word, _, var = token.partition(":")
    return word + var


def parse(expr: str):
    """Devuelve (op, tokens) con op '^' (apilado) o '+' (anidado)."""
    expr = expr.strip()
    if "+" in expr:
        return "+", [t.strip() for t in expr.split("+")]
    if "^" in expr:
        return "^", [t.strip() for t in expr.split("^")]
    tokens = expr.split()
    head = tokens[0].partition(":")[0]
    return ("+" if len(tokens) > 1 and head in NEST_HEADS else "^"), tokens


def compose(font: Font, expr: str):
    """Devuelve [(glifo, x, y)] tal como los coloca la propia fuente.

    Se le pasa a la fuente el texto ASCII que entiende: 'toki+pona' (anidado, la segunda
    palabra pequeña dentro de la primera) o 'ilo-tenpo' (apilado).
    """
    op, tokens = parse(expr)
    if len(tokens) > 2:
        raise SystemExit(f"'{expr}': solo se admiten compuestos de dos palabras")
    joiner = "+" if op == "+" else "-"
    text = joiner.join(ascii_token(t) for t in tokens)
    parts = font.shape(text)
    words = [g for g, _, _ in parts if "Tok" in g]
    if len(words) != len(tokens) or len(parts) != len(tokens):
        raise SystemExit(f"'{expr}': la fuente no lo compone como {len(tokens)} glifo(s): "
                         f"{[g for g, _, _ in parts]}")
    if len(tokens) == 2 and not all("join" in g for g in words):
        how = "anidar ('+')" if op == "+" else "apilar ('^')"
        raise SystemExit(f"'{expr}': la fuente no sabe {how} estas palabras: {words}")
    return parts


def vector_xml(font: Font, expr: str, viewport, safe, size_dp, fill, outline, outline_w):
    parts = compose(font, expr)
    s = safe / EM_W
    x0, _, x1, _ = font.bounds(parts)
    ox = viewport / 2 - (x0 + x1) / 2 * s          # centrado horizontal por la tinta
    oy = viewport / 2 + EM_CENTER_Y * s            # centrado vertical por la caja em
    d = font.path(parts, s, ox, oy, THIN)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- {expr} — {MARKER} de jan Itan (licencia MIT) -->",
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
    names = [t.replace(":", "_v") for t in tokens]
    return ("_en_" if op == "+" else "_").join(names)


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
    ap.add_argument("--font", default=str(RES / "font" / "nasin_nanpa.otf"))
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
    # Borra lo generado antes (marcado con MARKER; tp_* es el formato antiguo).
    for old in drawable_dir.glob("*.xml"):
        if old.name.startswith("tp_") or MARKER in old.read_text(encoding="utf-8"):
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

    # 1) Todas las palabras y variantes, para asignarlas a mano desde el launcher.
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

    # 4) Combinaciones automáticas: COMBO_HEADS × todas las palabras base.
    base_words = [t for t in font.tokens if ":" not in t]
    combo_categories, combo_failed = [], 0
    for head in COMBO_HEADS:
        names = []
        for w in base_words:
            if w == head:
                continue
            expr = f"{head}+{w}" if head in NEST_HEADS else f"{head}^{w}"
            try:
                names.append(write_icon(expr))
            except SystemExit:
                combo_failed += 1  # la fuente no sabe componer esa pareja
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
        + ('    <category title="nimi sina (tus palabras)" />\n' + items(saved_names) + "\n" if saved_names else "")
        + '    <category title="ilo (apps)" />\n' + items(app_drawables) + "\n"
        '    <category title="nimi tu (compuestos)" />\n' + items(compounds) + "\n"
        '    <category title="nimi ale (todas las palabras)" />\n' + items(all_words) + "\n"
        + combos_xml
        + "</resources>\n"
    )
    (RES / "xml" / "drawable.xml").write_text(drawable_xml, encoding="utf-8")
    shutil.copyfile(RES / "xml" / "drawable.xml", MAIN / "assets" / "drawable.xml")

    # 5) Icono de la propia app (adaptive icon: viewport 108, zona segura 66).
    fg = vector_xml(font, LAUNCHER_WORD, 108, 60, 108, "#FFFFFFFF", None, 0)
    (drawable_dir / "ic_launcher_foreground.xml").write_text(fg, encoding="utf-8")

    print(f"{n_category} componentes cubiertos por categorías "
          f"({len(categories)} categorías, {len(known)} paquetes conocidos)")
    n_combos = sum(len(n) for _, n in combo_categories)
    print(f"{n_combos} combinaciones automáticas ({combo_failed} parejas que la fuente no compone)")
    print(f"{len(all_words)} palabras/variantes, {len(compounds)} compuestos, "
          f"{len(filter_items)} componentes en appfilter.xml"
          + (f" ({len(device_icons)} asignaciones y {len(saved_names)} palabras desde la app)"
             if device_icons or saved_names else ""))


if __name__ == "__main__":
    main()
