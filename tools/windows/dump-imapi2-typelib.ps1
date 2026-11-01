# SPDX-FileCopyrightText: 2026 Chetan and contributors
# SPDX-License-Identifier: GPL-3.0-or-later

# Prints the GUIDs, DISPIDs and enum values RetroBurn's Windows helper relies on, read from the
# type libraries inside imapi2.dll and imapi2fs.dll. Use it to check the constants in
# src/main/resources/windows-helper/ against the Windows version at hand.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\windows\dump-imapi2-typelib.ps1

$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
using ComTypes = System.Runtime.InteropServices.ComTypes;

public static class TypeLibDump {
    [DllImport("oleaut32.dll", CharSet = CharSet.Unicode)]
    static extern int LoadTypeLibEx(string file, int regKind, out ComTypes.ITypeLib lib);

    public static void Dump(string file, string[] names) {
        ComTypes.ITypeLib lib;
        Marshal.ThrowExceptionForHR(LoadTypeLibEx(file, 2 /* REGKIND_NONE */, out lib));
        for (int i = 0; i < lib.GetTypeInfoCount(); i++) {
            ComTypes.ITypeInfo info;
            lib.GetTypeInfo(i, out info);
            string name = NameOf(info, -1);
            if (Array.IndexOf(names, name) < 0) continue;
            ComTypes.TYPEATTR attr = AttrOf(info);
            if (attr.typekind == ComTypes.TYPEKIND.TKIND_ALIAS) {
                // An alias (VT_USERDEFINED) names the real enum; follow it.
                ComTypes.ITypeInfo target;
                info.GetRefTypeInfo((int)attr.tdescAlias.lpValue.ToInt64(), out target);
                info = target;
                attr = AttrOf(info);
            }
            Console.WriteLine(name + " {" + attr.guid + "} " + attr.typekind);
            for (int f = 0; f < attr.cFuncs; f++) {
                IntPtr fdPtr;
                info.GetFuncDesc(f, out fdPtr);
                ComTypes.FUNCDESC fd = (ComTypes.FUNCDESC)Marshal.PtrToStructure(fdPtr, typeof(ComTypes.FUNCDESC));
                Console.WriteLine("    " + NameOf(info, fd.memid) + " dispid=0x" + fd.memid.ToString("X") + " params=" + fd.cParams);
                info.ReleaseFuncDesc(fdPtr);
            }
            for (int v = 0; v < attr.cVars; v++) {
                IntPtr vdPtr;
                info.GetVarDesc(v, out vdPtr);
                ComTypes.VARDESC vd = (ComTypes.VARDESC)Marshal.PtrToStructure(vdPtr, typeof(ComTypes.VARDESC));
                object value = Marshal.GetObjectForNativeVariant(vd.desc.lpvarValue);
                Console.WriteLine("    " + NameOf(info, vd.memid) + " = 0x" + Convert.ToInt64(value).ToString("X"));
                info.ReleaseVarDesc(vdPtr);
            }
        }
    }

    static string NameOf(ComTypes.ITypeInfo info, int memid) {
        string name, doc, helpFile;
        int helpContext;
        info.GetDocumentation(memid, out name, out doc, out helpContext, out helpFile);
        return name;
    }

    static ComTypes.TYPEATTR AttrOf(ComTypes.ITypeInfo info) {
        IntPtr attrPtr;
        info.GetTypeAttr(out attrPtr);
        try {
            return (ComTypes.TYPEATTR)Marshal.PtrToStructure(attrPtr, typeof(ComTypes.TYPEATTR));
        } finally {
            info.ReleaseTypeAttr(attrPtr);
        }
    }
}
"@

[TypeLibDump]::Dump("$env:windir\System32\imapi2.dll", @(
    'DDiscMaster2Events', 'DDiscFormat2DataEvents', 'DDiscFormat2EraseEvents', 'IDiscFormat2DataEventArgs',
    'IMAPI_MEDIA_PHYSICAL_TYPE', 'IMAPI_FORMAT2_DATA_MEDIA_STATE', 'IMAPI_FORMAT2_DATA_WRITE_ACTION'))
[TypeLibDump]::Dump("$env:windir\System32\imapi2fs.dll", @('FsiFileSystems'))
