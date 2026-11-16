# Third-party notices

This app bundles one font binary and links a number of Apache-2.0 libraries. Their licences and
copyright notices are reproduced here, which is what the SIL Open Font License requires of anyone
redistributing the font software.

---

## Fonts (bundled binaries redistributed in this repository)

### Google Sans Flex

    Copyright 2015 Google LLC

- **Licence:** SIL Open Font License 1.1. See [`licenses/OFL-1.1.txt`](licenses/OFL-1.1.txt).
- **Where it came from:** `fonts.gstatic.com`, via the Google Fonts CSS API. Google Fonts lists the
  family as open source (`isOpenSource: true` in its own family metadata).
- **Modified: yes.** The per-script woff2 subsets Google serves were downloaded, converted to TTF
  and merged into one variable font, because Android cannot load woff2 and
  no single subset covers this app's needs. Glyph outlines, the design space and all six variable
  axes are unchanged; only the set of glyphs present differs from any one upstream subset.
- **No Reserved Font Name is declared** in the binary's name table, so OFL §3 places no restriction
  on the family name. The modified font therefore keeps its original name, as the licence permits.

---

## Public holiday data

Holidays are fetched at runtime from [Nager.Date](https://date.nager.at) (MIT Licence,
copyright Tino Hager). No holiday data is stored in this repository.

---

## Libraries (linked, not redistributed here)

All under the Apache License 2.0:

- Jetpack Compose and AndroidX: Copyright The Android Open Source Project
- `androidx.compose.material:material-icons-extended` is the Material Symbols icon set as Compose
  `ImageVector`s. Copyright Google LLC / The Android Open Source Project.
- `androidx.graphics:graphics-shapes`: Copyright The Android Open Source Project
- Kotlin and the Kotlin standard library: Copyright JetBrains s.r.o.

Gradle resolves these from Maven Central and Google's Maven repository at build time; no copy of any
of them is stored in this repository.

---

## Trademarks

Google, Google Sans, Material, Material Design, Android and Pixel are trademarks of Google LLC.
This project is not affiliated with, sponsored by, or endorsed by Google.

Google Sans Flex is one of Google's **brand** typefaces (Google Fonts flags it `isBrandFont: true`).
Its *font software* is open-licensed and may be redistributed under the OFL, as above; that licence
grants no rights in the Google name or in any Google trademark, and nothing here should be read as
claiming any.

---

## Design credits

The app's look, its theme code and much of its structure come from **Rayane Bouafia**'s
*Expressive Clock* / Material 3 Expressive design system (MIT Licence; copyright notice kept in
[`LICENSE`](LICENSE)). That design follows Google's unshipped **Material 3 Expressive research
concepts**. Google's concept renders are Google's copyright and are **not** included in this
repository.
