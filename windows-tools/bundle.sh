#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Chetan and contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# Collects the Windows tools RetroBurn bundles (ADR-0019) into one folder for the installer, with
# every DLL they load from MSYS2, each DLL package's licence files, and a second folder with the
# corresponding source: our pinned tarballs and build scripts, and the MSYS2 source package of
# every DLL. Runs in an MSYS2 UCRT64 shell that has the same packages installed as the builds.
#
#   windows-tools/bundle.sh <dvdauthor-out> <ffmpeg-out> <tools-folder> <sources-folder>

set -euo pipefail

dvd="$(realpath "$1")"
ff="$(realpath "$2")"
tools="$(realpath -m "$3")"
sources="$(realpath -m "$4")"
here="$(dirname "$(realpath "$0")")"
mkdir -p "$tools/licenses" "$sources/build-scripts"

cp "$ff/ffmpeg.exe" "$ff/ffprobe.exe" "$dvd/dvdauthor.exe" "$dvd/spumux.exe" "$tools/"
cp "$ff/COPYING.GPLv2.ffmpeg" "$ff/LICENSE.ffmpeg.md" "$ff/ffmpeg-configure-line.txt" "$tools/licenses/"
cp "$dvd/COPYING.dvdauthor" "$tools/licenses/"
cp "$ff"/ffmpeg-*.tar.xz "$dvd"/dvdauthor-*.tar.gz "$sources/"
cp "$here"/build-*.sh "$here"/bundle.sh "$sources/build-scripts/"

# manifest.tsv: kind, name, version, licence, file in the source folder
manifest="$tools/manifest.tsv"
printf 'kind\tname\tversion\tlicense\tsource\n' > "$manifest"
ffmpeg_version="$(basename "$ff"/ffmpeg-*.tar.xz .tar.xz | sed 's/^ffmpeg-//')"
dvd_version="$(basename "$dvd"/dvdauthor-*.tar.gz .tar.gz | sed 's/^dvdauthor-//')"
printf 'tool\tFFmpeg\t%s\tGPL-2.0-or-later\tffmpeg-%s.tar.xz\n' "$ffmpeg_version" "$ffmpeg_version" >> "$manifest"
printf 'tool\tdvdauthor\t%s\tGPL-2.0-or-later\tdvdauthor-%s.tar.gz\n' "$dvd_version" "$dvd_version" >> "$manifest"

# The DLLs the programs load from MSYS2 (not from Windows itself), and the packages that own them.
for exe in "$tools"/*.exe; do
    ldd "$exe"
done | awk '$3 ~ /^\/ucrt64\// {print $3}' | sort -u > "$sources/dlls.txt"
cat "$sources/dlls.txt"

while read -r dll; do
    cp "$dll" "$tools/"
    pacman -Qqo "$dll"
done < "$sources/dlls.txt" | sort -u > "$sources/packages.txt"

while read -r package; do
    version="$(pacman -Q "$package" | awk '{print $2}')"
    licence="$(pacman -Qi "$package" | sed -n 's/^Licenses *: //p')"
    name="${package#mingw-w64-ucrt-x86_64-}"
    base="mingw-w64-${name}"
    if [ -d "/ucrt64/share/licenses/$name" ]; then
        mkdir -p "$tools/licenses/$name"
        cp -r "/ucrt64/share/licenses/$name/." "$tools/licenses/$name/"
    else
        echo "No licence folder for $name; listing what pacman says: $licence" >&2
    fi
    source_file="${base}-${version}.src.tar.zst"
    curl -sSfL -o "$sources/$source_file" "https://repo.msys2.org/mingw/sources/$source_file" \
        || { echo "Source package not found: $source_file" >&2; exit 1; }
    printf 'library\t%s\t%s\t%s\t%s\n' "$name" "$version" "$licence" "$source_file" >> "$manifest"
done < "$sources/packages.txt"

column -t -s $'\t' "$manifest"
(cd "$sources" && sha256sum * build-scripts/* > SHA256SUMS 2>/dev/null || true)
