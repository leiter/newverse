# Bodenschätze logo assets

The master artwork and the generator that turns it into every launcher, store and
in-app icon the project ships.

## Files

| File | What it is |
|------|------------|
| `Bodenschaetze-LOGO-E.D.ai` | The original Illustrator file (CS4, 2018). The authority; never edited here. |
| `mark.svg` | The three vegetables — beet, potato plant, carrot. Used for every app icon. |
| `wordmark.svg` | The BODENSCHÄTZE lettering alone. Source of `ic_bodenschatz.xml`. |
| `lockup.svg` | Mark above wordmark, the full logo. |
| `generate-icons.sh` | Rasterises the above into every icon the apps use. |

The three SVGs were extracted from the `.ai` with Inkscape and carry its paths
unchanged — they are pure vector, with no embedded bitmaps. Everything downstream
is rasterised from them at its exact final pixel size, so no shipped icon is ever
an upscale of a smaller bitmap.

## Regenerating

```bash
./art/logo/generate-icons.sh      # needs inkscape + ImageMagick
```

It overwrites the Android, iOS, Play Store and Compose icons in place. The adaptive
icon background colour and the XML that wires the layers together are *not*
generated — those live in each flavor's `res/values/colors.xml` and
`res/mipmap-anydpi-v26/`.

Inkscape is installed as a snap on this machine and cannot read `/tmp`, so the
script stages intermediate renders in `tmp/icon-stage` inside the repo.

## Geometry, and why

**The mark is 1.789 : 1** (965.764 × 539.847 in master units). That wide, three-part
silhouette is what makes sizing it a judgement call rather than a default.

* **Adaptive icon foreground — mark is 60dp wide on the 108dp canvas.** Only a
  centred 66dp circle of that canvas is guaranteed to survive every launcher's mask.
  60dp is the largest width at which *no* ink falls outside that circle; at 64dp
  0.31% of it clips, at 72dp 4.5%. The foreground PNGs are transparent — the orange
  or green comes from `ic_vegi_background.xml`, which is how an adaptive icon is
  supposed to be layered.
* **Legacy icons (API < 26) — 70% wide for the square, 66% for the round one.**
  These are fully visible, with no safe-zone shrink, so the mark can breathe.
* **Store tiles — 62%.** A little more padding reads better at listing sizes.

## Colours

| | Background | Mark |
|---|---|---|
| buy, and the combined build | `#FA9C4D` BodenOrange | `#000000`, the logo's own black |
| sell | `#0A6308` BodenGreen | `#F6F2E8` BodenPaper |

The sell app inverts the pair so the two apps are told apart at a glance on a device
that has both installed. The colours are declared as `icon_background` in each
flavor's `colors.xml`; the mark colour is baked into the generated PNGs and set at
the top of `generate-icons.sh`.

## iOS

App Store and springboard icons are written opaque, square and with no alpha
channel — iOS applies its own corner mask, and an alpha channel is grounds for
rejection at submission.

## The wordmark vector

`androidApp/src/main/res/drawable/ic_bodenschatz.xml` is generated from
`wordmark.svg` rather than drawn by hand:

```bash
python3 art/logo/mkword.py art/logo/wordmark.svg \
        androidApp/src/main/res/drawable/ic_bodenschatz.xml
```

Each path in the SVG carries a `transform="matrix(…)"`. The matrix here is a uniform
scale plus a translate, with no shear, so it maps onto a VectorDrawable `<group>`;
`mkword.py` asserts that rather than assuming it. The mark is *not* shipped as a
VectorDrawable — it is roughly 3000 bezier segments, about 100 KB of path data, so
PNGs are both smaller and cheaper to draw.
