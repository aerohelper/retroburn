<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Glossary

| Term | Meaning |
|---|---|
| **AC-3** | Dolby Digital audio codec; one of the audio formats allowed on DVD-Video. RetroBurn uses 48 kHz stereo AC-3. |
| **Appendable** | A disc with a closed session but room for more sessions (multisession). |
| **BD-R / BD-RE** | Blu-ray recordable (write-once) / rewritable. |
| **Bleed** | Extra artwork beyond the trim line (3 mm here) so cutting slightly off does not leave white edges. |
| **Blank (media state)** | A disc with nothing written. **Blanking** is erasing a rewritable disc. |
| **Burn reel** | RetroBurn's short vertical MP4 timelapse of a burn. |
| **Burn session** | RetroBurn's local JSON record of one burn (timings, progress, result), used for the reel. |
| **Car-compat mode** | RetroBurn's MP3 disc rules for old/car players: numbered, short, sanitised names and shallow folders. |
| **CBR / VBR** | Constant / variable bitrate encoding. |
| **CD-DA** | Compact Disc Digital Audio, the "Red Book" audio CD format: 44.1 kHz, 16-bit stereo PCM. |
| **CD-R / CD-RW** | Recordable (write-once) / rewritable CD. |
| **CD-Text** | Album and track titles/performers stored in the lead-in of an audio CD; shown by capable players. Limited character sets and lengths. |
| **Chapter** | A navigation point inside a DVD title. |
| **Closed disc** | A finalised disc to which nothing more can be added. |
| **Crop marks** | Printed lines showing where to cut. |
| **cdrdao** | Command-line tool that writes CDs in disc-at-once mode from a TOC file, including CD-Text. |
| **cdrskin** | Command-line burning tool from the libburnia project, used on Linux for audio CDs with CD-Text. |
| **DAO / SAO** | Disc-At-Once / Session-At-Once: the whole session is written in one pass without stopping the laser; needed for CD-Text and controlled gaps. |
| **DL** | Dual Layer (e.g. DVD+R DL, about 8.5 GB). |
| **DRM** | Digital Rights Management. RetroBurn never circumvents it and rejects protected inputs. |
| **dvdauthor** | Tool that builds the DVD-Video `VIDEO_TS` structure (IFO/VOB files) from MPEG streams and an XML description. |
| **DVD±R / DVD±RW** | DVD-R/DVD+R recordable and DVD-RW/DVD+RW rewritable formats. |
| **EBU R128** | Loudness-normalisation standard; optional for audio CDs. |
| **ffprobe** | FFmpeg's tool for reading media metadata, used for tags, durations and stream info. |
| **Gap / pregap** | Silence (or index 0 area) between tracks on an audio CD; 2 seconds by default. |
| **IFO / BUP / VOB** | DVD-Video files: IFO = navigation/info, BUP = backup copy of IFO, VOB = video objects (MPEG-2 program stream with audio and subtitles). |
| **IMAPI2 / IMAPI2FS** | Windows Image Mastering API v2: COM APIs for burning (IMAPI2) and for building ISO 9660/Joliet/UDF file system images (IMAPI2FS). |
| **ISO 9660** | The basic CD/DVD file system; strict name limits. |
| **ISO image (.iso)** | A file containing a complete disc file system, sector by sector. |
| **Jewel case / inlay / slim case / keep case** | CD/DVD packaging formats the cover designer supports. |
| **JSON Lines** | One JSON object per line; used for the Windows helper's event stream. |
| **Joliet** | Microsoft extension to ISO 9660 allowing long Unicode file names. |
| **libmp3lame** | The LAME MP3 encoder as used by FFmpeg. |
| **M-DISC** | Archival write-once media requiring drive support. |
| **MMC** | SCSI Multimedia Commands, the command set all modern optical drives (internal and USB) use. |
| **MPEG-2 PS** | MPEG-2 Program Stream, the container used in DVD VOB files. |
| **Multisession** | Writing more data to a disc later in a new session. |
| **NTSC / PAL** | TV standards. NTSC: 720×480 at 29.97 fps (US, Canada, Japan). PAL: 720×576 at 25 fps (India, Europe, Australia). |
| **OFL** | SIL Open Font License, used by bundled fonts. |
| **Pipeline / step** | RetroBurn's ordered, weighted, cancellable sequence of work for a job. |
| **Rock Ridge** | POSIX extension to ISO 9660 (permissions, long names) read by Linux. |
| **Safe zone** | Area of a printed item where important content won't be trimmed. |
| **SBOM** | Software Bill of Materials (CycloneDX format here). |
| **Spine** | The narrow side of a case insert, visible on a shelf. |
| **spumux** | dvdauthor's tool that multiplexes subtitle/overlay pictures (used for menu button highlights) into MPEG streams. |
| **TAO** | Track-At-Once: each track written separately; simpler but no CD-Text and fixed gaps. |
| **TOC** | Table of Contents of a CD (track start positions); also cdrdao's text input file format. |
| **UDF** | Universal Disk Format; DVD-Video requires UDF 1.02 alongside ISO 9660. Also needed for files over 4 GB. |
| **v07t (Sony Input Sheet 0.7T)** | Text format describing CD-Text, accepted by cdrskin. |
| **VIDEO_TS** | Folder on a DVD-Video disc holding IFO, BUP and VOB files. |
| **Verify** | Reading the burned disc back and comparing it with the source (checksums). |
| **Volume label** | The disc's name as shown by the OS; length and character limits depend on the file system. |
| **xorriso** | GNU tool (libburnia project) for creating, burning and verifying ISO 9660 images on Linux. Its `stdio:` targets write to a file instead of a drive. |
