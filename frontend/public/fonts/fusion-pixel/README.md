# Fusion Pixel (12 px, proportional, Latin)

Self-hosted headings and numerals face. Source: https://github.com/TakWolf/fusion-pixel-font, release 2026.10.16, `fusion-pixel-12px-proportional-latin.ttf.woff2`.

The shipped file is a subset (U+0020-007E, U+00A0-00FF, en and em dashes, curly quotes, bullet, ellipsis, right arrow) made with fontTools (`pyftsubset`) to keep it near 4 kB. The glyph coverage check lives in `src/app/theme/font-coverage.spec.ts`. The face has no U+2212 (minus sign) or U+2713 (check mark): the UI uses ASCII "-" and "+" in this face.

Licence: SIL Open Font License 1.1, in `OFL.txt` (the face embeds glyphs from other OFL fonts, whose licences are in `LICENSES/`). The OFL declares no Reserved Font Name, so the subset keeps the name.
