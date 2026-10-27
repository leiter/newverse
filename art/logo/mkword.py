import re, sys
src, out = sys.argv[1], sys.argv[2]
d = open(src, encoding='utf-8').read()
x0, y0, vw, vh = (float(g) for g in
    re.search(r'viewBox="([-\d.]+) ([-\d.]+) ([\d.]+) ([\d.]+)"', d).groups())

els = re.findall(r'<path\b.*?/>', d, re.S)
assert len(els) == 1, len(els)
el = els[0]

pd = re.search(r'(?<![\w-])d="(.*?)"', el, re.S).group(1)
tm = re.search(r'transform="matrix\(([^)]*)\)"', el)
a, b, c, dd, e, f = (float(v) for v in re.split(r'[,\s]+', tm.group(1).strip()))
assert b == 0 and c == 0, f"shear/rotation in matrix: b={b} c={c}"
assert abs(a - dd) < 1e-9, f"non-uniform scale: {a} vs {dd}"

NUM = re.compile(r'-?\d*\.?\d+(?:[eE][-+]?\d+)?')
def rnd(m):
    s = f"{float(m.group(0)):.3f}".rstrip('0').rstrip('.')
    return s if s not in ('', '-', '-0') else '0'
p = NUM.sub(rnd, pd)
p = re.sub(r'\s+', ' ', p).strip()
p = re.sub(r'[ ,](-)', r'\1', p)
p = re.sub(r'([MmLlHhVvCcSsQqTtAaZz]) ', r'\1', p)
assert not re.search(r'[eE]', p), "exponent notation is not supported by Android"

DP_W = 288.098
DP_H = round(DP_W * vh / vw, 3)

xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- The BODENSCHAETZE wordmark, generated from art/logo/wordmark.svg.
     Do not hand-edit - see art/logo/README.md. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{DP_W}dp"
    android:height="{DP_H}dp"
    android:viewportWidth="{vw}"
    android:viewportHeight="{vh}">
  <group
      android:translateX="{-x0}"
      android:translateY="{-y0}">
    <group
        android:translateX="{e}"
        android:translateY="{f}"
        android:scaleX="{a}"
        android:scaleY="{dd}">
      <path
          android:fillColor="#FF000000"
          android:pathData="{p}" />
    </group>
  </group>
</vector>
'''
open(out, 'w', encoding='utf-8').write(xml)
print(f"matrix: scale {a}  translate ({e}, {f})")
print(f"viewport {vw} x {vh}  ->  {DP_W}dp x {DP_H}dp  (aspect {vw/vh:.3f})")
print("wrote", out, len(xml), "bytes")
