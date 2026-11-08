#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Chetan and contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# Builds FFmpeg and ffprobe for Windows from the pinned release tarball, in an MSYS2 UCRT64 shell,
# with only what RetroBurn uses: libx264 (burn reels), LAME (MP3 discs), zlib (PNG menus and
# thumbnails) and FFmpeg's own decoders, encoders and muxers. --enable-gpl is needed for libx264,
# so the result is GPL-2.0-or-later; --enable-nonfree is never used (ADR-0006, ADR-0019).
#
#   windows-tools/build-ffmpeg.sh <output-folder>

set -euo pipefail

VERSION=9.0.2
URL="https://ffmpeg.org/releases/ffmpeg-${VERSION}.tar.xz"
SHA256=8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e

out="$(realpath -m "$1")"
work="$(mktemp -d)"
mkdir -p "$out"
cd "$work"

curl -sSfL -o source.tar.xz "$URL"
echo "${SHA256}  source.tar.xz" | sha256sum -c -
tar xJf source.tar.xz
cd "ffmpeg-${VERSION}"

configure_line=(
    --prefix="$work/install"
    --disable-autodetect
    --enable-w32threads
    --enable-gpl
    --enable-libx264
    --enable-libmp3lame
    --enable-zlib
    --disable-ffplay
    --disable-doc
    --disable-debug
)
./configure "${configure_line[@]}" | tail -n 30 | tee "$out/configure.log"
make -j"$(nproc)" > /dev/null
make install > /dev/null

cp "$work/install/bin/ffmpeg.exe" "$work/install/bin/ffprobe.exe" "$out/"
cp "$work/install/bin/"*.dll "$out/" 2>/dev/null || true
printf '%s\n' "${configure_line[@]}" | grep -v -- '--prefix' > "$out/ffmpeg-configure-line.txt"
cp ../source.tar.xz "$out/ffmpeg-${VERSION}.tar.xz"
cp COPYING.GPLv2 "$out/COPYING.GPLv2.ffmpeg"
cp LICENSE.md "$out/LICENSE.ffmpeg.md"
"$out/ffmpeg.exe" -hide_banner -version | head -n 3
"$out/ffmpeg.exe" -hide_banner -encoders | grep -E " (libx264|libmp3lame|aac|ac3|mpeg2video|mpeg4) "
