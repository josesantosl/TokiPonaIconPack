#!/usr/bin/env bash
# Incluye en el icon pack lo que asignaste/guardaste en la app y lo reinstala en el teléfono.
#   1. Lee por ADB las asignaciones de la app (tools/apps_device.json)
#   2. Regenera drawables, appfilter.xml y drawable.xml
#   3. Compila e instala la versión debug
# Después: vuelve a aplicar el icon pack en tu launcher para que refresque los iconos.
#
# Si Android Studio está abierto y Gradle se queda colgado desde la terminal,
# ejecuta con --no-install y pulsa Run ▶ en Android Studio.
set -euo pipefail
cd "$(dirname "$0")/.."

PY="${HOME}/.venvs/tokipona-icons/bin/python"
"$PY" -I tools/generate_icons.py --from-device

if [[ "${1:-}" != "--no-install" ]]; then
    ./gradlew --console=plain app:installDebug
fi

echo "Listo. Ahora vuelve a aplicar el icon pack en tu launcher (o reinícialo) para refrescar."
