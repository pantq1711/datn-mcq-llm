# Tạo file tham chiếu: gõ 10 biểu thức TeX vào Word rồi gọi lệnh MathType "Toggle TeX" (macro của add-in
# MathType Commands) để chính MathType sinh đối tượng Equation.DSMT4. Lưu ra docx, không đụng file khác.
# Luôn đóng Word/MathType do script mở và khôi phục Cut and Copy Preferences (office_guard.ps1).
# Dùng: powershell -NoProfile -File mathtype_textoggle_ref.ps1 <out.docx> [<exprs.tex.txt>] [-TimeoutSec 600]
param([Parameter(Mandatory = $true)][string]$OutDocx, [string]$ExprFile, [int]$TimeoutSec = 600)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'office_guard.ps1')
if ($ExprFile) {
    $exprs = Get-Content -LiteralPath $ExprFile -Encoding UTF8 | Where-Object { $_.Trim() -ne '' }
} else {
    $exprs = @(
        '\frac{3}{4}', '\sqrt{x^2+1}', 'x_1', '2^{10}', '\widehat{ABC}=60^\circ', '\Delta ABC', 'a\ne b',
        '\left(x+\frac12\right)^2', '\begin{cases}x+y=3\\x-y=1\end{cases}', '\pi r^2'
    )
}
$guard = Start-OfficeGuard -TimeoutSec $TimeoutSec
$word = $null
try {
    $word = New-GuardedWord $guard
    $doc = $word.Documents.Add()
    $i = 0
    foreach ($e in $exprs) {
        $i++
        $doc.Content.InsertAfter("$i. `$$e`$")
        $doc.Content.InsertParagraphAfter()
    }
    $doc.Content.Select()
    $macros = @('MTCommand_TeXToggle', 'MTCommand_ToggleTeX')
    $ok = $false
    foreach ($m in $macros) {
        try { $word.Run($m) | Out-Null; "Ran macro: $m"; $ok = $true; break }
        catch { "Macro $m failed: $($_.Exception.Message)" }
    }
    "OMaths: $($doc.OMaths.Count)  InlineShapes: $($doc.InlineShapes.Count)"
    foreach ($s in $doc.InlineShapes) { try { "  progID=$($s.OLEFormat.ProgID)" } catch { "  (no OLE)" } }
    $doc.SaveAs2($OutDocx, 16)  # wdFormatXMLDocument
    "Saved: $OutDocx"
    $doc.Close([ref]0)
} finally {
    Stop-OfficeGuard $guard $word
}
