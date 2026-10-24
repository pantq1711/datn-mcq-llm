# Mở .docx bằng Word (COM, ẩn), đếm công thức OMML và đối tượng OLE, in ProgID từng đối tượng,
# xuất PDF để xem hiển thị/in. Không lưu đè file gốc.
# Dùng: powershell -NoProfile -File word_probe.ps1 <file.docx> [<pdf-out>]
param([Parameter(Mandatory = $true)][string]$Path, [string]$PdfOut)

$ErrorActionPreference = 'Stop'
$full = (Resolve-Path $Path).Path
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
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
        try { $prog = $s.OLEFormat.ProgID } catch { $prog = '(không có OLEFormat)' }
        "  #$i type=$($s.Type) progID=$prog size=$([math]::Round($s.Width,1))x$([math]::Round($s.Height,1))pt"
    }
    if ($PdfOut) {
        $doc.ExportAsFixedFormat($PdfOut, 17)  # wdExportFormatPDF
        "PDF: $PdfOut"
    }
    $noSave = [ref]0
    $doc.Close($noSave)
} finally {
    $noSave2 = [ref]0
    $word.Quit($noSave2)
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
}
