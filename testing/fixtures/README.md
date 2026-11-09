# Test fixture generation

Every PDF the end to end suite reads is produced or copied by a script in this
directory. Nothing under `out/` is committed. Run the generator and the whole
fixture tree appears; delete it and rerun and you get byte identical files back.

```
python3 generate.py                build everything into ./out
python3 generate.py --list         show the fixture groups
python3 generate.py --only core    build one group
python3 generate.py --skip-slow    skip the multi hundred megabyte fixtures
python3 generate.py --check        assert every produced PDF parses
python3 generate.py --verify       build twice and prove the bytes match
python3 generate.py --clean        wipe out/ first
```

No third party Python packages are required to build. `approved_arabic` requires
native graphics libraries and a pinned font (see below). `--check` uses `pikepdf`
if it is installed and skips itself if not.

## What gets built

The tree unrolls to roughly 100,700 files (about 1 GB on disk), but only about
175 are designed fixtures that tests reference by name. The rest are three
bulk populations, each a single manifest entry with a generation rule:

- `perf-library-100k/`: 100,000 tiny files for the macrobenchmark, so its
  library benchmarks measure the list path under genuine stress. No E2E test
  uses it. `python3 testing/runner/run.py --with-perf-library` places it on the
  device in about 5 minutes, using exactly four parallel tar extractions. A
  later run without that flag removes it, and `--sweep-perf-library` also
  removes its MediaStore rows.
- `scan/huge/`: 500 one page files so a scan pass genuinely cannot finish
  before the first poll. Grow it rather than adding a sleep.
- `fifty-distinct/`: 50 distinct documents for home grid scroll and sort.

## Why generated rather than committed

The test plan asserts fixture contents literally: page counts, search hit
counts, outline destinations, link targets, highlight colours and creation
dates. A committed binary nobody can regenerate is a fixture nobody can safely
change, and every screenshot golden is downstream of one. Generating them means a fixture is a readable script, a
change to one is a reviewable diff, and the numbers the tests assert are
recorded automatically in `out/MANIFEST.md` rather than by hand.

## Determinism

Output is byte identical across runs, and across machines that have the same
DejaVu font files under `/usr/share/fonts/truetype/dejavu`. That is a hard requirement,
not a nicety, because the pixel baselines compare against images recorded from
these exact files. The rules that make it hold:

- No wall clock anywhere. Document dates are the fixed `D:20240101000000Z`.
- The document id is an MD5 of the content, not random.
- Dictionary keys serialise in insertion order, object numbers in creation order.
- Font resource names are positional (`F0`, `F1`), never derived from `hash()`,
  which Python salts per process.
- Deflate runs at a fixed level, and the font checksum adjustment is zeroed.

`python3 generate.py --verify` builds the tree twice and compares, so a
regression in any of this fails loudly.

## Layout

```
generate.py          CLI entry point
lib/pdfwriter.py     deterministic low level PDF object and xref writer
lib/ttf.py           TrueType reader and glyph blanking subsetter
lib/arabic.py        Arabic shaping and visual reordering
lib/doc.py           pages, text, images, annotations, outlines, encryption
lib/manifest.py      records what each fixture contains
lib/registry.py      generator groups and device push targets
generators/*.py      one module per fixture family
assets/              committed source material that cannot be generated
out/                 generated output, gitignored
out/MANIFEST.md      what every fixture contains, regenerated on each build
out/MANIFEST.json    the same, for machines
```

## Fonts

Fixtures embed their fonts. pdfium falls back to the system font set for a non
embedded font, and the emulator's fonts are not part of the APK, so a non
embedded font would make a golden depend on the system image.

The generator uses only DejaVu, so every fixture uses DejaVu Serif,
Sans or Sans Mono. Embedding a whole 760 KB face into each of two hundred
fixtures would be absurd, so `lib/ttf.py` blanks the outlines of glyphs the
fixture never draws and keeps the table layout and every glyph id exactly as the
source font has them. Flate then compresses the emptied region to nothing. A
twenty page document embeds its font in roughly 40 KB.

### Characters no installed font has

`search_digits.pdf` needs fullwidth digits, which DejaVu does not provide. The fixture draws the ASCII digit glyph but declares the fullwidth
codepoint in `ToUnicode`, so extraction yields `１２３４５` while the page shows
`12345`. Search asserts on extracted text, so this is the right shape, and it is
what `Document.alias_unicode` exists for. Use it sparingly and only where a test
asserts on extraction rather than on pixels.

### Arabic

`lib/arabic.py` converts logical Arabic into positional presentation forms and
reverses the run, which is what a real Arabic PDF contains. The `ToUnicode` map
takes each presentation form back to its base letter, and the lam alef ligature
back to the two characters it stands for, so extraction yields ordinary Arabic.

Known limitation: bidi handling is a simple segment reversal, so a Latin word or
a trailing period inside an Arabic line can sit at the wrong end. It is correct
for pure Arabic runs, which is what the fixtures use. Mixed direction fixtures
draw their Latin and Arabic parts as separate positioned runs rather than
relying on the reordering.

### Approved Arabic (independent)

`generators/approved_arabic.py` bypasses the custom Arabic, font and PDF writers.
It passes the five approved strings unchanged to installed Pango/HarfBuzz/FriBidi
via `ctypes`, then writes `arabic-approved-sentences.pdf` with native Cairo: five
612 x 400 pt pages, one 24 pt sentence per page, right aligned with a baseline
120 pt from the top, no headings. The same scenes also produce single-page
`arabic-approved-sentence-1.pdf` through `-5.pdf` alongside the combined file,
with the same embedded font and dates. Each sentence is page 1 in its own file,
so observation runs need no in-document navigation. Generate just this group:

```sh
python3 testing/fixtures/generate.py --only approved_arabic --out /tmp/approved-arabic
```

To add only these files to existing `out/` without replacing its corpus manifest:

```sh
PYTHONPATH=testing/fixtures python3 -c 'from generators.approved_arabic import build; build("testing/fixtures/out")'
```

`arabic-approved-sentences.expected.json` (schema 2) contains the five exact
strings, UTF-8 source hashes, native/font provenance, page size and Pango ink
bounds, plus output filenames/hashes. Unused character/glyph/gesture metadata
is omitted. Bounds are top-left page points; multiply by 2 for preview pixels,
or use `400-y` for bottom-left PDF coordinates. These are source expectations,
not extraction goldens. `ApprovedArabicSentencesTest` (SEL-62 through SEL-66)
compares Copy Page Text and Text Mode with these approved strings after manual
inspection.

The five `arabic-approved-sentence-1.png` through `-5.png` previews are outside the
fixture output at `<out>-previews/approved_arabic/` (for the second command,
`testing/fixtures/out-previews/approved_arabic/`). They are 1224 x 800 native Cairo
image-surface replays of the **same recording scene** sent to the PDF backend,
not rasterizations of the saved PDF or app screenshots. Inspect these before
adding assertion tests; PNG and PDF hashes are recorded in the expected JSON.

The generator needs Pango >= 1.50, PangoCairo/PangoFT2, HarfBuzz, Cairo >= 1.16,
Fontconfig, FreeType, GLib/GObject, FriBidi, Pixman and zlib. It restricts Fontconfig
to `/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf`, requires SHA-256
`abdc775b21b1bc470d50c97e790d276f2054b7504e56e5bd3e64f48d68582322`, disables fallback,
and rejects unknown glyphs or overflowing/multiline layouts. Native versions,
font checksum and layout options are recorded; both PDF dates are fixed to
2024-01-01 UTC. Unlike the custom writer's cross-machine promise, byte/pixel
determinism here requires the same native stack. Compare fresh group-only builds
**and their sibling preview trees**; the generic `--verify` hashes only `<out>`.

Do not alter source strings or PDF text mappings to match app or parser output.

## Adding a fixture

1. Write it in the right module under `generators/`, or add a module and list it
   in `GENERATOR_MODULES` in `generate.py`.
2. Call `manifest.register(...)` with every fact a test will assert literally.
   If a test needs to know it, it belongs in the manifest, not in a comment.
3. Run `python3 generate.py --only <group> --check --verify`.
4. If the fixture is one a golden is recorded against, say so in the manifest,
   because regenerating it invalidates that golden.

Fixture names are owned by the test plan. Do not invent a
second name for a file that already exists under another one.

## Committed assets

Some fixtures cannot honestly be generated: a real scanned document with real
scanner artefacts, or a real world paper whose layout exercises a complex
structure. Those live in `assets/` and the `assets` generator copies them into
`out/` so every fixture, generated or not, ends up in one place with one
manifest. See `assets/README.md`.

## Device layout

`lib/registry.py` records where each family is pushed on the device. The runner
re pushes the tree before every test class, because tests mutate and delete
fixtures and the orchestrator's data clear does not restore shared storage. See
the shared storage contract in the test plan.
