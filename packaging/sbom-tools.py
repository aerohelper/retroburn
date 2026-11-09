# SPDX-FileCopyrightText: 2026 Chetan and contributors
# SPDX-License-Identifier: GPL-3.0-or-later

"""Adds the bundled Windows tools and their DLL libraries to the CycloneDX SBOM that Maven made.

    python3 packaging/sbom-tools.py <maven bom.json> <tools/manifest.tsv> <output.cdx.json>

The manifest comes from windows-tools/bundle.sh: kind, name, version, licence, source file.
Licences from MSYS2 look like "spdx:GPL-2.0-or-later OR FTL" (an SPDX expression) or "custom".
"""

import csv
import json
import sys


def licences(licence, kind):
    """CycloneDX licences for an MSYS2 licence field.

    "spdx:GPL-2.0-or-later OR FTL" is an SPDX expression; fields such as "custom" or "LGPL" are not,
    so they are kept as a licence name (the files are in tools/licenses/). Parts that only concern
    documentation ("documentation:spdx:...") are left out: no documentation is shipped.
    """
    parts = [part for part in licence.split() if part and not part.startswith("documentation:")]
    words = [part.removeprefix("spdx:") for part in parts]
    # Our own tools' rows are written with SPDX identifiers by bundle.sh.
    if parts and (parts[0].startswith("spdx:") or kind == "tool"):
        return [{"expression": " ".join(words)}]
    return [{"license": {"name": " ".join(words) if words else "unknown"}}]


def main(bom_path, manifest_path, output_path):
    with open(bom_path, encoding="utf-8") as file:
        bom = json.load(file)
    components = bom.setdefault("components", [])
    with open(manifest_path, encoding="utf-8", newline="") as file:
        for row in csv.DictReader(file, delimiter="\t"):
            components.append({
                "type": "application" if row["kind"] == "tool" else "library",
                "bom-ref": "windows-tool:" + row["name"] + "@" + row["version"],
                "name": row["name"],
                "version": row["version"],
                "description": "Bundled with RetroBurn on Windows (" + row["kind"] + ")",
                "licenses": licences(row["license"], row["kind"]),
                "properties": [{"name": "retroburn:source", "value": row["source"]}],
            })
    with open(output_path, "w", encoding="utf-8") as file:
        json.dump(bom, file, indent=2)
        file.write("\n")
    print(output_path, len(components), "components")


if __name__ == "__main__":
    main(*sys.argv[1:4])
