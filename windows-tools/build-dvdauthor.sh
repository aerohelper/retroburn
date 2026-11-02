#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Chetan and contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# Builds dvdauthor and spumux for Windows from pinned source, in an MSYS2 UCRT64 shell (see
# .github/workflows/windows-tools.yml and docs/design/LLD-video-dvd.md). Needs autotools, libpng,
# freetype and libxml2; fribidi and fontconfig are used when present.
#
#   windows-tools/build-dvdauthor.sh <output-folder>

set -euo pipefail

VERSION=0.7.2
URL="https://github.com/ldo/dvdauthor/archive/refs/tags/${VERSION}.tar.gz"
SHA256=fd66b92f6c67bd406030ff0f1c3233175768b534df4d341c3f380ecd61de91b5

out="$(realpath -m "$1")"
work="$(mktemp -d)"
mkdir -p "$out"
cd "$work"

curl -sSfL -o source.tar.gz "$URL"
echo "${SHA256}  source.tar.gz" | sha256sum -c -
tar xzf source.tar.gz
cd "dvdauthor-${VERSION}"

./bootstrap
./configure --prefix="$work/install" | tee "$out/configure.log"
make -j"$(nproc)" | tail -n 20
make install | tail -n 5
cp "$work/install/bin/dvdauthor.exe" "$work/install/bin/spumux.exe" "$out/"
cp ../source.tar.gz "$out/dvdauthor-${VERSION}.tar.gz"
cp COPYING "$out/COPYING.dvdauthor"

# The DLLs the programs need from MSYS2 (each must be shipped with its licence).
{ ldd "$out/dvdauthor.exe"; ldd "$out/spumux.exe"; } | grep -iv "/c/windows" | sort -u | tee "$out/dlls.txt"
