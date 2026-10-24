"""Tách MTEF thô (bỏ header EQNOLEFILEHDR 28 byte) từ đối tượng MathType trong .docx, để đo bằng mathtype_measure.ps1.

Dùng:
  python -I extract_mtef.py <file.docx> <oleObjectX.bin> <out.mtef> [--drop-space]
--drop-space: bỏ CHAR record khoảng cách (typeface Space) đầu tiên, để có bản "không khoảng" làm mốc so độ rộng.
"""
import io
import struct
import sys
import zipfile

import olefile

SPACE_TYPEFACE = 24 + 128


def mtef_of(docx, name):
    data = zipfile.ZipFile(docx).read("word/embeddings/" + name)
    native = olefile.OleFileIO(io.BytesIO(data)).openstream("Equation Native").read()
    cb_hdr = struct.unpack_from("<H", native, 0)[0]
    cb_obj = struct.unpack_from("<I", native, 8)[0]
    return native[cb_hdr:cb_hdr + cb_obj]


def drop_first_space(m):
    # CHAR record: tag 2, options, typeface + 128, MTCode 16 bit; options 0x04 thì có thêm 1 byte vị trí font.
    for i in range(len(m) - 4):
        if m[i] == 2 and (m[i + 1] & 0xF8) == 0 and m[i + 2] == SPACE_TYPEFACE and m[i + 4] == 0xEF:
            n = 5 + (1 if m[i + 1] & 0x04 else 0)
            return m[:i] + m[i + n:], "U+%04X" % (m[i + 3] | (m[i + 4] << 8))
    raise SystemExit("Không thấy ký tự khoảng cách trong MTEF")


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")  # console Windows mặc định cp1252 (dev-log L-06)
    if len(argv) < 3:
        raise SystemExit(__doc__)
    m = mtef_of(argv[0], argv[1])
    note = ""
    if "--drop-space" in argv[3:]:
        m, code = drop_first_space(m)
        note = " (đã bỏ " + code + ")"
    open(argv[2], "wb").write(m)
    sys.stdout.write("%s :: %s -> %s, %d byte%s\n" % (argv[0], argv[1], argv[2], len(m), note))


if __name__ == "__main__":
    main(sys.argv[1:])
