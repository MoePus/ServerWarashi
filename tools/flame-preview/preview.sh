#!/usr/bin/env bash
# Offline render of the world flame overlay. Usage: tools/flame-preview/preview.sh [key=value ...]
# Needs a JDK 21+ `java`, `unzip`, and a Gradle cache populated by one `./gradlew build`.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
OUT_DIR="$ROOT/build/flame-preview"
TEXTURE_DIR="$OUT_DIR/textures"
GRADLE_CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
LWJGL_VERSION="${LWJGL_VERSION:-3.3.3}"

case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*) SEPARATOR=';'; NATIVES='natives-windows'; JAVA_FLAGS=() ;;
    Darwin*) SEPARATOR=':'; NATIVES='natives-macos'; JAVA_FLAGS=(-XstartOnFirstThread) ;;
    *) SEPARATOR=':'; NATIVES='natives-linux'; JAVA_FLAGS=() ;;
esac

native_path() {
    if command -v cygpath > /dev/null; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

classpath=""
for module in lwjgl lwjgl-glfw lwjgl-opengl; do
    for jar in "$module-$LWJGL_VERSION.jar" "$module-$LWJGL_VERSION-$NATIVES.jar"; do
        found="$(find "$GRADLE_CACHE/modules-2/files-2.1/org.lwjgl/$module/$LWJGL_VERSION" \
            -name "$jar" 2> /dev/null | head -n 1)"
        if [[ -z "$found" ]]; then
            echo "Missing $jar in the Gradle cache; run ./gradlew build once first." >&2
            exit 1
        fi
        classpath="$classpath$SEPARATOR$(native_path "$found")"
    done
done

client_jar="$(find "$GRADLE_CACHE/neoformruntime/artifacts" -name 'minecraft_*_client.jar' \
    2> /dev/null | head -n 1)"
if [[ ! -f "$TEXTURE_DIR/stone.png" ]]; then
    if [[ -z "$client_jar" ]]; then
        echo "Missing the Minecraft client jar in the Gradle cache; run ./gradlew build once first." >&2
        exit 1
    fi
    mkdir -p "$TEXTURE_DIR"
    unzip -o -q -j "$client_jar" -d "$TEXTURE_DIR" \
        'assets/minecraft/textures/block/furnace_front_on.png' \
        'assets/minecraft/textures/block/furnace_side.png' \
        'assets/minecraft/textures/block/furnace_top.png' \
        'assets/minecraft/textures/block/stone.png'
fi

mkdir -p "$OUT_DIR"
exec java "${JAVA_FLAGS[@]}" -cp "${classpath#"$SEPARATOR"}" "$(native_path "$HERE/FlamePreview.java")" \
    "shaders=$(native_path "$ROOT/src/main/resources/assets/serverwarashi/shaders/core")" \
    "textures=$(native_path "$TEXTURE_DIR")" \
    "out=$(native_path "$OUT_DIR/flame")" \
    "$@"
