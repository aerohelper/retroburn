#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Chetan and contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# Builds RetroBurn's packages with jlink and jpackage (docs/design/LLD-packaging.md). Runs in bash
# on Linux and in Git Bash on Windows, from the repository root.
#
#   packaging/package.sh <deb|windows|image> <version> [windows-tools-folder]
#
# deb      -> dist/retroburn_<version>_amd64.deb
# windows  -> dist/RetroBurn-<version>.msi and dist/RetroBurn-<version>-windows-portable.zip
#             (the tools folder, from windows-tools.yml's bundle job, is shipped in the app image)
# image    -> target/package/image/RetroBurn only, for a quick local check

set -euo pipefail

type="$1"
version="$2"
tools="${3:-}"
root="$(pwd)"
build="$root/target/package"
dist="$root/dist"
rm -rf "$build"
mkdir -p "$build/lib" "$build/javafx" "$build/legal" "$dist"

# jpackage on Windows wants Windows paths; Git Bash gives /c/... paths.
native() {
    if command -v cygpath > /dev/null; then cygpath -w "$1"; else echo "$1"; fi
}

./mvnw -B -q -DskipTests package
./mvnw -B -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="$(native "$build/deps")"
jar_version="$(./mvnw -B -q help:evaluate -Dexpression=project.version -DforceStdout)"
cp "target/retroburn-${jar_version}.jar" "$build/lib/retroburn.jar"
for jar in "$build"/deps/*.jar; do
    name="$(basename "$jar")"
    case "$name" in
        javafx-*-linux.jar | javafx-*-win.jar) cp "$jar" "$build/javafx/" ;;
        javafx-*) ;; # the empty jars without a platform
        *) cp "$jar" "$build/lib/" ;;
    esac
done

# Licences that go next to the app: RetroBurn's, the third-party notices, the in-app credit texts.
cp LICENSE THIRD_PARTY_NOTICES.md "$build/legal/"
cp -r src/main/resources/credits/licenses "$build/legal/licenses"
cp -r src/main/resources/credits/notices "$build/legal/notices"

jlink \
    --add-modules java.base,java.desktop,java.sql,java.xml,java.logging,jdk.unsupported,jdk.jfr,jdk.localedata \
    --strip-debug --no-header-files --no-man-pages \
    --output "$build/runtime"

content=(--app-content "$(native "$build/javafx")" --app-content "$(native "$build/legal")")
if [ -n "$tools" ]; then
    rm -rf "$build/tools"
    cp -r "$tools" "$build/tools"
    content+=(--app-content "$(native "$build/tools")")
fi
icon="$root/packaging/icons/retroburn.png"
command -v cygpath > /dev/null && icon="$root/packaging/icons/retroburn.ico"

jpackage --type app-image \
    --name RetroBurn \
    --app-version "$version" \
    --vendor "RetroBurn contributors" \
    --description "Burns CDs, DVDs and Blu-ray discs" \
    --copyright "2026 Chetan and contributors, GPL-3.0-or-later" \
    --icon "$(native "$icon")" \
    --input "$(native "$build/lib")" \
    --main-jar retroburn.jar \
    --main-class io.aerohelper.retroburn.app.Main \
    --runtime-image "$(native "$build/runtime")" \
    "${content[@]}" \
    --java-options '--module-path=$APPDIR/../javafx' \
    --java-options '--add-modules=javafx.controls' \
    --java-options '--enable-native-access=javafx.graphics' \
    --dest "$(native "$build/image")"

case "$type" in
    deb)
        jpackage --type deb \
            --name retroburn \
            --app-version "$version" \
            --app-image "$build/image/RetroBurn" \
            --install-dir /opt \
            --vendor "RetroBurn contributors" \
            --description "Burns CDs, DVDs and Blu-ray discs" \
            --license-file LICENSE \
            --linux-package-deps "xorriso, cdrskin, genisoimage, ffmpeg, dvdauthor" \
            --linux-shortcut \
            --linux-menu-group "AudioVideo" \
            --linux-app-category sound \
            --linux-deb-maintainer "RetroBurn contributors <noreply@github.com>" \
            --dest "$dist"
        ;;
    windows)
        jpackage --type msi \
            --name RetroBurn \
            --app-version "$version" \
            --app-image "$(native "$build/image/RetroBurn")" \
            --vendor "RetroBurn contributors" \
            --description "Burns CDs, DVDs and Blu-ray discs" \
            --license-file LICENSE \
            --win-menu \
            --win-menu-group RetroBurn \
            --win-dir-chooser \
            --win-upgrade-uuid 6b3f1c64-8f7e-4a39-9a4c-2a0f5d7e8b11 \
            --dest "$(native "$dist")"
        (cd "$build/image" && powershell -NoProfile -Command \
            "Compress-Archive -Path RetroBurn -DestinationPath '$(native "$dist/RetroBurn-${version}-windows-portable.zip")' -Force")
        ;;
    image)
        ;;
    *)
        echo "Unknown package type $type" >&2
        exit 2
        ;;
esac
ls -l "$dist"
