"""In cấu trúc một file OLE (oleObject*.bin): CLSID gốc, các stream, header Equation Native + MTEF.

Dùng: python -I inspect_ole.py <file.bin> [<file.bin> ...]
"""
import struct
import sys

import olefile


def hexdump(b, n=96):
    return " ".join(f"{x:02x}" for x in b[:n])


def main(paths):
    for p in paths:
        print("=" * 80)
        print(p)
        ole = olefile.OleFileIO(p)
        print("root clsid:", ole.root.clsid)
        for entry in ole.listdir(streams=True, storages=True):
            name = "/".join(entry)
            size = ole.get_size(name) if ole.get_type(name) == olefile.STGTY_STREAM else "-"
            print(f"  stream {name!r:28} size={size}")
        for entry in ole.listdir():
            name = "/".join(entry)
            data = ole.openstream(name).read()
            if name != "Equation Native":
                print(f"  [{name!r}] {hexdump(data, 120)}")
                continue
            cb_hdr, ver, cf, cb_obj = struct.unpack_from("<HIHI", data, 0)
            print(f"  EQNOLEFILEHDR cbHdr={cb_hdr} version=0x{ver:08x} cf=0x{cf:04x} cbObject={cb_obj}")
            mtef = data[cb_hdr:cb_hdr + cb_obj]
            ver, plat, prod, pver, psub = mtef[:5]
            end = mtef.index(b"\x00", 5)
            key = mtef[5:end].decode("latin-1")
            opts = mtef[end + 1]
            print(f"  MTEF v{ver} platform={plat} product={prod} {pver}.{psub} key={key!r} eqn_opts={opts}")
            print(f"  MTEF body: {hexdump(mtef[end + 2:], 400)}")
        ole.close()


if __name__ == "__main__":
    main(sys.argv[1:])
