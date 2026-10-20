import json, sys
sys.stdout.reconfigure(encoding="utf-8")
for path in sys.argv[1:]:
    t = open(path, encoding="utf-8").read().strip()
    if t.startswith("```"):
        t = t.strip("`")
        if t.lower().startswith("json"):
            t = t[4:]
    d = json.loads(t)
    print("==", path)
    print(d["question_text"])
    for k in ("option_a", "option_b", "option_c", "option_d"):
        print(k[-1].upper() + ")", d[k])
    print("KEY:", d["correct_option"])
    print()
