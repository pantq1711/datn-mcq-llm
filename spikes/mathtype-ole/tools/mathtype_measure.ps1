# Nhờ chính MathType dựng ảnh từ MTEF thô (MT6.dll, MTXFormEqn: mtxfmLOCAL/mtxfmMTEF -> mtxfmFILE/mtxfmPICT) và in kích
# thước MathType trả về (MTGetLastDimension và MTAPI_DIMS, đơn vị thô; đối chiếu với w:dxaOrig để quy ra point). Dùng để so độ rộng hiển thị của các bản MTEF
# (ví dụ khoảng cách U+EF02 so với U+EF07). Hằng số và chữ ký hàm lấy từ mã VBA của "MathType Commands 2016.dotm".
# Luôn đóng MathType khởi động trong lúc chạy và khôi phục Cut and Copy Preferences (office_guard.ps1).
# Dùng: powershell -NoProfile -File mathtype_measure.ps1 <a.mtef> [<b.mtef> ...]   (PowerShell 64-bit)
param([Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)][string[]]$MtefFiles)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'office_guard.ps1')
$mtDir = 'C:\Program Files (x86)\MathType\System\' + $(if ([Environment]::Is64BitProcess) { '64' } else { '32' })

if (-not ('MathTypeApi.Native' -as [type])) {
    Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
using System.Text;
namespace MathTypeApi {
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int left, top, right, bottom; }
  [StructLayout(LayoutKind.Sequential)] public struct DIMS { public short baseline; public RECT bounds; }
  public static class Native {
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)] public static extern bool SetDllDirectory(string path);
    [DllImport("MT6.dll")] public static extern int MTAPIConnect(short options, short timeout);
    [DllImport("MT6.dll")] public static extern int MTAPIDisconnect();
    [DllImport("MT6.dll")] public static extern int MTXFormReset();
    [DllImport("MT6.dll")] public static extern int MTXFormSetPrefs(short prefType, [MarshalAs(UnmanagedType.LPStr)] string prefStr);
    [DllImport("MT6.dll")] public static extern int MTXFormEqn(short src, short srcFmt, byte[] srcData, int srcDataLen,
        short dst, short dstFmt, StringBuilder dstData, int dstDataLen, [MarshalAs(UnmanagedType.LPStr)] string dstPath, ref DIMS dims);
    [DllImport("MT6.dll")] public static extern int MTGetLastDimension(short dimIndex);
  }
}
"@
}

$mtxfmLOCAL = -3; $mtxfmFILE = -4; $mtxfmMTEF = 4; $mtxfmPICT = 6
$mtxfmPREF_EXISTING = 1
$mtdimWIDTH = 1; $mtdimHEIGHT = 2; $mtdimBASELINE = 3

$guard = Start-OfficeGuard -TimeoutSec 300
$connected = $false
try {
    [void][MathTypeApi.Native]::SetDllDirectory($mtDir)
    $r = [MathTypeApi.Native]::MTAPIConnect(0, 30)   # mtinitLAUNCH_AS_NEEDED, 30 giây
    if ($r -ne 0) { throw "MTAPIConnect = $r" }
    $connected = $true
    "file`twidth`theight`tbaseline`tbounds(l,t,r,b)`tbaseline_dims   (don vi tho cua MathType)"
    foreach ($f in $MtefFiles) {
        $bytes = [IO.File]::ReadAllBytes((Resolve-Path $f).Path)
        [void][MathTypeApi.Native]::MTXFormReset()
        [void][MathTypeApi.Native]::MTXFormSetPrefs($mtxfmPREF_EXISTING, '')   # giữ preferences có trong MTEF
        $out = Join-Path ([IO.Path]::GetTempPath()) ("mt-measure-" + [guid]::NewGuid() + ".wmf")
        $dims = New-Object MathTypeApi.DIMS
        $sb = New-Object Text.StringBuilder 1
        $stat = [MathTypeApi.Native]::MTXFormEqn($mtxfmLOCAL, $mtxfmMTEF, $bytes, $bytes.Length, $mtxfmFILE, $mtxfmPICT, $sb, 0, $out, [ref]$dims)
        if ($stat -ne 0) { "$f`tMTXFormEqn = $stat"; continue }
        $w = [MathTypeApi.Native]::MTGetLastDimension($mtdimWIDTH)
        $h = [MathTypeApi.Native]::MTGetLastDimension($mtdimHEIGHT)
        $b = [MathTypeApi.Native]::MTGetLastDimension($mtdimBASELINE)
        $bd = $dims.bounds
        "{0}`t{1}`t{2}`t{3}`t{4},{5},{6},{7}`t{8}" -f (Split-Path $f -Leaf), $w, $h, $b, $bd.left, $bd.top, $bd.right, $bd.bottom, $dims.baseline
        Remove-Item -LiteralPath $out -ErrorAction SilentlyContinue
    }
} finally {
    if ($connected) { try { [void][MathTypeApi.Native]::MTAPIDisconnect() } catch { } }
    Stop-OfficeGuard $guard $null
}
