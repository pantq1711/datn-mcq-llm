"""Giải mã MTEF v5 thành cây chữ để so sánh (theo đặc tả MTEF5 của Design Science/Wiris).

Dùng:
  python -I mtef_dump.py <oleObject.bin | .docx> ...
Với .docx: giải mã mọi word/embeddings/*.bin.
"""
import io
import struct
import sys
import zipfile

import olefile

TMPL = {0: "tmANGLE", 1: "tmPAREN", 2: "tmBRACE", 3: "tmBRACK", 4: "tmBAR", 5: "tmDBAR", 6: "tmFLOOR",
        7: "tmCEILING", 8: "tmOBRACK", 9: "tmINTERVAL", 10: "tmROOT", 11: "tmFRACT", 12: "tmUBAR", 13: "tmOBAR",
        14: "tmARROW", 15: "tmINTEG", 16: "tmSUM", 17: "tmPROD", 18: "tmCOPROD", 19: "tmUNION", 20: "tmINTER",
        21: "tmINTOP", 22: "tmSUMOP", 23: "tmLIM", 24: "tmHBRACE", 25: "tmHBRACK", 26: "tmLDIV", 27: "tmSUB",
        28: "tmSUP", 29: "tmSUBSUP", 30: "tmDIRAC", 31: "tmVEC", 32: "tmTILDE", 33: "tmHAT", 34: "tmARC",
        35: "tmJSTATUS", 36: "tmSTRIKE", 37: "tmBOX"}
FACE = {1: "TEXT", 2: "FUNC", 3: "VAR", 4: "LCGREEK", 5: "UCGREEK", 6: "SYM", 7: "VEC", 8: "NUM", 11: "MTEXTRA",
        12: "TEXT_FE", 22: "EXPAND", 23: "MARKER", 24: "SPACE"}


class Reader:
    def __init__(self, data):
        self.d = data
        self.p = 0

    def u8(self):
        v = self.d[self.p]
        self.p += 1
        return v

    def u16(self):
        v = struct.unpack_from("<H", self.d, self.p)[0]
        self.p += 2
        return v

    def uint(self):
        v = self.u8()
        return self.u16() if v == 255 else v

    def cstr(self):
        e = self.d.index(b"\x00", self.p)
        s = self.d[self.p:e].decode("latin-1")
        self.p = e + 1
        return s

    def eof(self):
        return self.p >= len(self.d)


def nudge(r, opts):
    if opts & 0x08:
        dx, dy = r.u8(), r.u8()
        if dx == 128 and dy == 128:
            r.u16(); r.u16()


def dim_array(r):
    count = r.u8()
    nibbles = []
    done = 0
    while done < count:
        b = r.u8()
        for n in (b >> 4, b & 0xF):
            nibbles.append(n)
            if n == 0xF:
                done += 1
                if done == count:
                    break
    return count


def ruler(r):
    tag = r.u8()
    assert tag == 7, f"expected RULER got {tag}"
    n = r.u8()
    for _ in range(n):
        r.u8(); r.u16()


def read_list(r, depth, out):
    """Đọc danh sách object tới END; trả về False nếu gặp hết dữ liệu."""
    while not r.eof():
        if not read_record(r, depth, out):
            return True
    return False


def read_record(r, depth, out):
    """Đọc một record; trả về False nếu là END."""
    pad = "  " * depth
    t = r.u8()
    if t == 0:
        return False
    if t == 1:
        o = r.u8(); nudge(r, o)
        if o & 0x04:
            r.u16()
        if o & 0x02:
            ruler(r)
        if o & 0x01:
            out.append(f"{pad}LINE(null)")
        else:
            out.append(f"{pad}LINE")
            read_list(r, depth + 1, out)
    elif t == 2:
        o = r.u8(); nudge(r, o)
        face = r.u8() - 128
        code = None if o & 0x20 else r.u16()
        fp = r.u8() if o & 0x04 else (r.u16() if o & 0x10 else None)
        ch = chr(code) if code and code < 0xE000 else (f"U+{code:04X}" if code else "?")
        flags = []
        if o & 0x02:
            flags.append("funcstart")
        out.append(f"{pad}CHAR {FACE.get(face, face)} {ch!r} U+{(code or 0):04X}"
                   + (f" fontpos=0x{fp:02X}" if fp is not None else "") + (f" {flags}" if flags else ""))
        if o & 0x01:
            read_list(r, depth + 1, out)
    elif t == 3:
        o = r.u8(); nudge(r, o)
        sel = r.u8()
        v = r.u8()
        if v & 0x80:
            v = (v & 0x7F) | (r.u8() << 8)
        topt = r.u8()
        out.append(f"{pad}TMPL {TMPL.get(sel, sel)} var=0x{v:X} opt={topt}")
        read_list(r, depth + 1, out)
    elif t == 4:
        o = r.u8(); nudge(r, o)
        ha, va = r.u8(), r.u8()
        if o & 0x02:
            ruler(r)
        out.append(f"{pad}PILE halign={ha} valign={va}")
        read_list(r, depth + 1, out)
    elif t == 5:
        o = r.u8(); nudge(r, o)
        va, hj, vj, rows, cols = r.u8(), r.u8(), r.u8(), r.u8(), r.u8()
        r.p += ((rows + 1) * 2 + 7) // 8
        r.p += ((cols + 1) * 2 + 7) // 8
        out.append(f"{pad}MATRIX {rows}x{cols} valign={va} hjust={hj} vjust={vj}")
        read_list(r, depth + 1, out)
    elif t == 6:
        o = r.u8(); nudge(r, o)
        out.append(f"{pad}EMBELL type={r.u8()}")
    elif t == 7:
        r.p -= 1
        ruler(r)
        out.append(f"{pad}RULER")
    elif t == 8:
        idx = r.uint(); st = r.u8()
        out.append(f"{pad}FONT_STYLE_DEF font={idx} style={st}")
    elif t == 9:
        a = r.u8()
        if a == 101:
            out.append(f"{pad}SIZE explicit={-struct.unpack('<h', bytes([r.u8(), r.u8()]))[0]}")
        elif a == 100:
            ls = r.u8(); ds = r.u16()
            out.append(f"{pad}SIZE lsize={ls} dsize={ds}")
        else:
            out.append(f"{pad}SIZE lsize={a} dsize={r.u8() - 128}")
    elif 10 <= t <= 14:
        out.append(f"{pad}{['FULL', 'SUB', 'SUB2', 'SYM', 'SUBSYM'][t - 10]}")
    elif t == 15:
        out.append(f"{pad}COLOR {r.uint()}")
    elif t == 16:
        o = r.u8()
        for _ in range(4 if o & 1 else 3):
            r.u16()
        if o & 4:
            r.cstr()
        out.append(f"{pad}COLOR_DEF")
    elif t == 17:
        idx = r.uint(); name = r.cstr()
        out.append(f"{pad}FONT_DEF enc={idx} {name!r}")
    elif t == 18:
        r.u8()
        ns = dim_array(r); nsp = dim_array(r)
        n = r.u8()
        for _ in range(n):
            if r.uint():
                r.u8()
        out.append(f"{pad}EQN_PREFS sizes={ns} spaces={nsp} styles={n}")
    elif t == 19:
        out.append(f"{pad}ENCODING_DEF {r.cstr()!r}")
    elif t >= 100:
        n = r.uint(); r.p += n
        out.append(f"{pad}FUTURE type={t} len={n}")
    else:
        raise ValueError(f"unknown record type {t} at {r.p - 1}")
    return True


def dump_mtef(mtef, show_defs=False):
    r = Reader(mtef)
    ver, plat, prod, pver, psub = (r.u8() for _ in range(5))
    key = r.cstr()
    opts = r.u8()
    out = [f"MTEF v{ver} plat={plat} prod={prod} {pver}.{psub} key={key} opts={opts}"]
    body = []
    try:
        while not r.eof():
            if not read_record(r, 0, body):
                body.append("END")
    except Exception as e:  # noqa: BLE001
        body.append(f"!! PARSE ERROR at byte {r.p}: {e}")
    if not show_defs:
        body = [b for b in body if not b.lstrip().startswith(("FONT_DEF", "ENCODING_DEF", "EQN_PREFS",
                                                               "FONT_STYLE_DEF", "COLOR_DEF", "FUTURE"))]
    return "\n".join(out + body)


def equation_native(ole_bytes):
    ole = olefile.OleFileIO(io.BytesIO(ole_bytes))
    data = ole.openstream("Equation Native").read()
    cb_hdr, _ver, _cf, cb_obj = struct.unpack_from("<HIHI", data, 0)
    return data[cb_hdr:cb_hdr + cb_obj]


def main(paths):
    show_defs = "--defs" in paths
    for p in [x for x in paths if not x.startswith("--")]:
        if p.lower().endswith(".docx"):
            with zipfile.ZipFile(p) as z:
                names = sorted((n for n in z.namelist() if n.startswith("word/embeddings/")),
                               key=lambda n: int("".join(c for c in n if c.isdigit()) or 0))
                for n in names:
                    print(f"===== {p} :: {n}")
                    print(dump_mtef(equation_native(z.read(n)), show_defs))
        else:
            print(f"===== {p}")
            with open(p, "rb") as f:
                print(dump_mtef(equation_native(f.read()), show_defs))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main(sys.argv[1:])
