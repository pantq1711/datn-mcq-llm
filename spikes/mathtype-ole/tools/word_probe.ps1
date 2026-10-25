# Mở .docx bằng Word (COM, ẩn), đếm công thức OMML và đối tượng OLE, in ProgID từng đối tượng,
# xuất PDF để xem hiển thị/in. Không lưu đè file gốc.
# Luôn đóng Word/MathType do script mở và khôi phục Cut and Copy Preferences (office_guard.ps1).
# Dùng: powershell -NoProfile -File word_probe.ps1 <file.docx> [<pdf-out>] [-TimeoutSec 600]
param([Parameter(Mandatory = $true)][string]$Path, [string]$PdfOut, [int]$TimeoutSec = 600)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'office_guard.ps1')
$full = (Resolve-Path $Path).Path
$guard = Start-OfficeGuard -TimeoutSec $TimeoutSec
$word = $null
try {
    $word = New-GuardedWord $guard
    # ReadOnly, không thêm vào danh sách gần đây
    $doc = $word.Documents.Open($full, $false, $true, $false)
    "File: $full"
    "CompatibilityMode: $($doc.CompatibilityMode)"
    "OMaths (Equation Word): $($doc.OMaths.Count)"
    "InlineShapes: $($doc.InlineShapes.Count)"
    $i = 0
    foreach ($s in $doc.InlineShapes) {
        $i++
        $prog = ''
        try { $prog = $s.OLEFormat.ProgID } catch { $prog = '(khong co OLEFormat)' }
        "  #$i type=$($s.Type) progID=$prog size=$([math]::Round($s.Width,1))x$([math]::Round($s.Height,1))pt"
    }
    if ($PdfOut) {
        $doc.ExportAsFixedFormat($PdfOut, 17)  # wdExportFormatPDF
        "PDF: $PdfOut"
    }
    $doc.Close([ref]0)
} finally {
    Stop-OfficeGuard $guard $word
}
