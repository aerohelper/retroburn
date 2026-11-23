# SPDX-FileCopyrightText: 2026 Chetan and contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# RetroBurn's Windows helper. Compiles RetroBurnHelper.cs (next to this script) and runs it; the C#
# reads one JSON request from stdin and writes JSON Lines to stdout.
# See docs/design/LLD-windows-platform.md.
#
# Started by RetroBurn as:
#   powershell.exe -NoLogo -NoProfile -NonInteractive -Mta -ExecutionPolicy Bypass -File retroburn-helper.ps1

$ErrorActionPreference = 'Stop'
Add-Type -Path (Join-Path $PSScriptRoot 'RetroBurnHelper.cs') -ReferencedAssemblies @(
    'Microsoft.CSharp', 'System.Core', 'System.Web.Extensions', 'System.Windows.Forms')
exit [RetroBurn.Helper.Program]::Run(
    $PSVersionTable.PSVersion.ToString(), $ExecutionContext.SessionState.LanguageMode.ToString())
