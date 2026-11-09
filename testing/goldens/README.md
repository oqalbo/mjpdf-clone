# Rendering goldens

This tree owns reviewed screenshot baselines independently of instrumentation
source. `app/build.gradle` adds this directory as an androidTest asset source, so
files under `baselines/` are packaged at the runtime path
`assets/baselines/<profile>/`. Review reports, tools, and this README are excluded
from the APK.

## Ownership and profiles

Each profile directory is one indivisible approved set. Its name pins every
environment property that can move pixels, and its `manifest.json` records those
pins, provenance, approval, and the SHA-256 and dimensions of every PNG. Do not
mix images from different profiles or update one without reviewing the complete
resulting profile.

The current approved profile is
`api36-1080x2400-440dpi-3button-en-US-swiftshader`: API 36, 1080x2400, 440 dpi,
three-button navigation, `en-US`, UTC, font scale 1.0, animations off, system
night mode off, no display cutout, and Google SwiftShader.

Run the durable audit before and after packaging:

```text
python3 testing/goldens/tools/check.py
python3 testing/goldens/tools/check.py --apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

The checker requires exactly 62 source-referenced PNGs, decodes every image,
checks dimensions and hashes against the approved manifest, verifies the TLCL
source and generated output bytes, and checks that the test APK contains the
runtime profile but no review material.

## Approval and regeneration

Record mode is enabled only with instrumentation argument
`-e baseline.record true`. It writes review inputs to the runner's
`baseline-staging/<profile>/` directory and deliberately fails; it never updates
this tree. Review every staged image and diff, preserve the capture evidence
under `reviews/`, which is local and not published, then move a complete
approved profile here and set
`approvalStatus` only after explicit human approval. Never regenerate or replace
approved pixels silently.

The 2026-09-16 TLCL set was explicitly approved by the maintainer as all 60 reviewed
images, including the visually odd scanned-image captures. `VIS-56` and
`VIS-57` were separately approved on 2026-09-17. Review evidence is kept outside the
repository.

On 2026-10-01 the maintainer approved correcting preview leakage at page and tile
bitmap edges and updating affected goldens. Twelve images changed at those
edges; layout, text, and highlight content remain the same. The original
profile, changed images, differences, and capture provenance are kept with the
review evidence outside the repository. Comparison tolerances remain unchanged.

## TLCL provenance

The general-document baseline uses the unchanged `TLCL-25.12A.pdf`, *The Linux
Command Line, 7th Internet Edition* by William Shotts, from
<https://sourceforge.net/projects/linuxcommand/files/TLCL/25.12/TLCL-25.12A.pdf/download>.
It is redistributed unchanged under CC BY-NC-ND 3.0 US. The file is 6,519,800
bytes, has SHA-256
`c6c564db237ea1d5f5758c5a0df5d8c804041ef483585259687d04d9ecf2cd05`,
and contains 597 pages. The committed source and generated fixture output must
remain byte-identical.
