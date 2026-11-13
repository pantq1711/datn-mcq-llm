"""Công cụ đo cho chương 4 (D2 bộ chấm mù, D4 đường cơ sở gọi thẳng Gemini, D6 ngưỡng dedup).

Chỉ dùng thư viện chuẩn, trừ openpyxl (đọc/ghi .xlsx) và pandoc (chương trình ngoài, tạo .docx).
Xem tools/measurement/README.md.
"""
import sys

# Console Windows mặc định cp1252: in tiếng Việt thì lỗi (như llm-probe, đổi sang UTF-8).
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")
