#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Chetan and contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# Builds cdrdao for Windows from pinned source, in an MSYS2 MSYS shell (see
# .github/workflows/windows-tools.yml and docs/adr/0014-cd-text-on-windows-through-cdrdao.md).
# cdrdao needs POSIX networking headers (CDDB), so it builds in MSYS2's POSIX environment, like
# upstream's own cdrdao126.zip (which ships msys-2.0.dll); a native UCRT64 build fails on netdb.h.
# configure.ac selects the Windows drive interface (ScsiIf-nt.o) for *-pc-cygwin* hosts, which
# MSYS2 is a fork of, so the build type is given as such.
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
# lec.cc uses int16_t without <cstdint>, which current GCC no longer pulls in by itself.
CXXFLAGS="-O2 -include cstdint" ./configure --build=x86_64-pc-cygwin     --without-gcdmaster --without-ogg-support --without-mp3-support | tee "$out/configure.log"
grep -n "scsilib\|ScsiIf" config.log | head -n 5 || true
make -j"$(nproc)" | tail -n 20
cp dao/cdrdao.exe "$out/"
cp ../source.tar.bz2 "$out/cdrdao-${VERSION}.tar.bz2"
cp COPYING "$out/COPYING.cdrdao"

# The DLLs the program needs from MSYS2 (each must be shipped with its licence).
ldd "$out/cdrdao.exe" | tee "$out/dlls.txt"
