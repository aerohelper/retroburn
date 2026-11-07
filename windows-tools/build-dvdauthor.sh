#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Chetan and contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# Builds dvdauthor and spumux for Windows from pinned source, in an MSYS2 UCRT64 shell (see
# .github/workflows/windows-tools.yml and docs/design/LLD-video-dvd.md). Needs autotools, libpng,
# freetype, libxml2 and libdvdread; fribidi and fontconfig are used when present.
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

# libxml2 2.13 and later no longer ship libxml.m4 (AM_PATH_XML2), which configure.ac needs; find
# libxml2 with pkg-config instead, setting the XML_CPPFLAGS and XML_LIBS that src/Makefile.am uses.
perl -0pi -e 's/ifdef\(\[AM_PATH_XML2\].*?m4_exit\(1\)AC_MSG_ERROR\(\[You must have libxml2 \(>= 2\.6\.0\) installed\]\)\]\)/PKG_CHECK_MODULES([XML], [libxml-2.0 >= 2.6.0])\nXML_CPPFLAGS="\$XML_CFLAGS"\nAC_SUBST(XML_CPPFLAGS)/s' configure.ac
grep -n "libxml-2.0" configure.ac

# What upstream's ./bootstrap does: copy gettext's config.rpath, then autoreconf -i.
mkdir -p autotools
cp /usr/share/gettext/config.rpath autotools/
ACLOCAL_PATH=/ucrt64/share/aclocal autoreconf -i
# configure.ac asks freetype-config, which FreeType no longer installs; answer from pkg-config.
mkdir -p "$work/bin"
cat > "$work/bin/freetype-config" <<'SHIM'
#!/bin/sh
case "$1" in
    --cflags) exec pkg-config --cflags freetype2 ;;
    --libs) exec pkg-config --libs freetype2 ;;
    *) exec pkg-config --modversion freetype2 ;;
esac
SHIM
chmod +x "$work/bin/freetype-config"
export PATH="$work/bin:$PATH"

# MinGW gaps: no <netinet/in.h> (the byte-order functions are in winsock2) and a one-argument
# mkdir. Without iconv (am_cv_func_iconv=no), nl_langinfo is not needed and text subtitles are
# read as UTF-8; the app's menus use image buttons only.
mkdir -p "$work/compat/netinet"
cat > "$work/compat/netinet/in.h" <<'SHIM'
#include <winsock2.h>
SHIM
cat > "$work/compat/mingw-compat.h" <<'SHIM'
#include <io.h>
#include <direct.h>
#define mkdir(path, mode) _mkdir(path)
SHIM
export CPPFLAGS="-I$work/compat -include $work/compat/mingw-compat.h"
export LIBS="-lws2_32"

./configure --prefix="$work/install" am_cv_func_iconv=no | tee "$out/configure.log"
# Only the programs: the manual pages need docbook2man, and the app does not ship them.
# -k lists every compile error in one run.
make -C src -k -j"$(nproc)" | tail -n 20
make -C src install | tail -n 5
cp "$work/install/bin/dvdauthor.exe" "$work/install/bin/spumux.exe" "$out/"
cp ../source.tar.gz "$out/dvdauthor-${VERSION}.tar.gz"
cp COPYING "$out/COPYING.dvdauthor"

# The DLLs the programs need from MSYS2 (each must be shipped with its licence).
{ ldd "$out/dvdauthor.exe"; ldd "$out/spumux.exe"; } | grep -iv "/c/windows" | sort -u | tee "$out/dlls.txt"
