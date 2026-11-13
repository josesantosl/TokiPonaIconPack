# Toki Pona Icon Pack

Icon pack para Android con glifos de **sitelen pona**, dibujados con la fuente
[nasin-nanpa](https://etbcor.com/) de jan Itan.

Cada app se representa con una palabra o un compuesto de toki pona: el teléfono es
*ilo toki*, el reloj *ilo tenpo*, el calendario *lipu tenpo* (tenpo dentro de lipu),
Chrome *linluwi*, Bluesky *waso laso*…

## Qué incluye

- **Icon pack** compatible con los launchers habituales (Niagara, Nova, Lawnchair,
  Smart Launcher, ADW…): `appfilter.xml` + `drawable.xml`.
- **Todas las palabras** de nasin-nanpa (y sus variantes) como iconos sueltos, más
  ~3 000 **combinaciones automáticas** (*ilo …*, *tomo …*, *lipu …*, etc.).
- **Editor de glifos** dentro de la app: escribes `toki pona`, `ilo tenpo` o `poki+sewi1`
  y ves el glifo al instante. Colores, grosor del trazo, exportación a **SVG/PNG**.
- **Accesos directos** con tu glifo para cualquier app (funciona en cualquier launcher,
  sin recompilar).
- **Selector de iconos** para launchers compatibles con `ACTION_PICK_ICON` (Nova, ADW…).
- **Apps sin icono**: la app detecta las apps instaladas que aún no tienen icono en el
  pack y propone un glifo para cada una.

## Sintaxis de los compuestos

| Escribes | Resultado |
|---|---|
| `toki` | una palabra |
| `sewi1` o `sewi:1` | variante 1 de la palabra |
| `ilo tenpo` | automático: si la primera palabra tiene hueco (*tomo, lipu, toki, sona, mani, jan, poki…*) la segunda va **dentro**; si no, se **apilan** con la primera palabra abajo |
| `jan^pona` | fuerza apilado |
| `pilin+pona` | fuerza anidado |

## Compilar

Abre el proyecto en Android Studio y pulsa Run. Los iconos ya generados están en el
repositorio, así que no hace falta Python para compilar.

- minSdk 26 · Kotlin · Jetpack Compose (Material 3)

## Regenerar los iconos

Los drawables, `appfilter.xml` y `drawable.xml` se generan con `tools/generate_icons.py`
a partir de la fuente incluida en `app/src/main/res/font/`.

```sh
python3 -m venv ~/.venvs/tokipona-icons
~/.venvs/tokipona-icons/bin/pip install -r tools/requirements.txt
~/.venvs/tokipona-icons/bin/python -I tools/generate_icons.py
```

- `tools/apps.json`: qué palabra lleva cada app (`components` = `paquete/Actividad`).
- Al principio del script: estilo (`FILL`, `OUTLINE`, `THIN` para el grosor) y
  `COMBO_HEADS` (palabras con las que se generan las combinaciones automáticas).

### Incluir en el pack lo que creas en la app

1. En la app: escribe un glifo → **Asignar a app…** (o guárdalo con **Guardar**).
2. Con el teléfono conectado por ADB:
   ```sh
   tools/sync_from_phone.sh            # lee del teléfono, regenera, compila e instala
   tools/sync_from_phone.sh --no-install   # solo regenera (luego Run en Android Studio)
   ```
3. Vuelve a aplicar el icon pack en tu launcher para que refresque los iconos.

Lo leído del teléfono se guarda en `tools/apps_device.json` (ignorado por git).

## Licencias

- Código de este proyecto: [MIT](LICENSE) © 2026 José Santos L.
- Fuente **nasin-nanpa** ([ETBCOR/nasin-nanpa](https://github.com/ETBCOR/nasin-nanpa)) y los
  iconos generados a partir de sus glifos: Copyright (c) 2022 jan Itan, licencia MIT — ver
  [LICENSE-nasin-nanpa.txt](LICENSE-nasin-nanpa.txt).
