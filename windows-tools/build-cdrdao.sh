#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Chetan and contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# Builds cdrdao for Windows from pinned source, in an MSYS2 UCRT64 shell (see
# .github/workflows/windows-tools.yml and docs/adr/0014-cd-text-on-windows-through-cdrdao.md).
# cdrdao's configure.ac selects the native Windows drive interface (ScsiIf-nt.o) for *mingw* hosts.
#
#   windows-tools/build-cdrdao.sh <output-folder>

set -euo pipefail

VERSION=1.2.6
URL="https://github.com/cdrdao/cdrdao/releases/download/rel_1_2_6/cdrdao-${VERSION}.tar.bz2"
SHA256=0cf29e1183ffd8e4d167c403d7a6ea2109a2dd4cd97e61f80417cf1373220ff4

out="$(realpath -m "$1")"
work="$(mktemp -d)"
mkdir -p "$out"
cd "$work"

curl -sSfL -o source.tar.bz2 "$URL"
echo "${SHA256}  source.tar.bz2" | sha256sum -c -
tar xjf source.tar.bz2
cd "cdrdao-${VERSION}"

# No GUI, and no OGG or MP3 input: RetroBurn hands cdrdao WAV files only.
./configure --without-gcdmaster --without-ogg-support --without-mp3-support | tee "$out/configure.log"
make -j"$(nproc)" | tail -n 20
cp dao/cdrdao.exe "$out/"
cp source.tar.bz2 "$out/cdrdao-${VERSION}.tar.bz2" 2>/dev/null || cp ../source.tar.bz2 "$out/cdrdao-${VERSION}.tar.bz2"
cp COPYING "$out/COPYING.cdrdao"

# The DLLs the program needs from MSYS2 (each must be shipped with its licence).
ldd "$out/cdrdao.exe" | tee "$out/dlls.txt"
