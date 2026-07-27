#!/usr/bin/env bash
#
# Runs Gradle from WSL using the Windows JVM bundled with Android Studio.
#
# The repository lives on the WSL filesystem, but Gradle cannot build there: as a
# Windows process it sees a \\wsl.localhost\... UNC path, and its file hasher fails
# with "The function is incorrect" on that share. So the sources are mirrored to a
# native Windows directory and built from there. The WSL repo stays the source of
# truth -- the mirror is disposable and is never edited by hand.
#
# Usage: tools/wbuild.sh assembleDebug
set -euo pipefail

JBR="/mnt/c/Program Files/Android/Android Studio/jbr/bin/java.exe"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Override with MDVIEW_BUILD_DIR if the Windows profile lives elsewhere.
if [[ -z "${MDVIEW_BUILD_DIR:-}" ]]; then
    sdk_dir="$(ls -d /mnt/c/Users/*/AppData/Local/Android/Sdk | head -1)"
    MDVIEW_BUILD_DIR="${sdk_dir%/AppData/Local/Android/Sdk}/AndroidStudioProjects/md-view"
fi
MIRROR="$MDVIEW_BUILD_DIR"

if [[ ! -x "$JBR" ]]; then
    echo "Android Studio's bundled JDK was not found at: $JBR" >&2
    exit 1
fi

mkdir -p "$MIRROR"
rsync -a --delete \
    --exclude '.git/' \
    --exclude 'build/' \
    --exclude '.gradle/' \
    --exclude '.idea/' \
    "$REPO/" "$MIRROR/"

cd "$MIRROR"
"$JBR" \
    -Dorg.gradle.appname=gradlew \
    -classpath 'gradle\wrapper\gradle-wrapper.jar' \
    org.gradle.wrapper.GradleWrapperMain "$@"

# Surface build outputs back in the repo so paths printed by Gradle can be opened
# from WSL without knowing where the mirror lives.
if [[ -d "$MIRROR/app/build/outputs" ]]; then
    mkdir -p "$REPO/build-outputs"
    rsync -a --delete "$MIRROR/app/build/outputs/" "$REPO/build-outputs/"
fi
