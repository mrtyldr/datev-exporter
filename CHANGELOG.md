# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Until the first `1.0.0` release the public API may change between minor versions.

## [Unreleased]

Nothing yet.

## [0.1.0] - 2026-08-11

The first published version. There is no previous release to compare against, so this entry
describes what ships rather than what changed.

### Modules

- `datev-exporter` — a Bill of Materials aligning every module on one version.
- `datev-exporter-core` — the single canonical copy of DATEV's 125/124-column table, the field
  specifications, `DatevSchema`, `DatevColumn`, `DatevField`, `DatevInfoBlock`, `DatevMetadata`,
  `DatevHeader`, the `DatevCsv` record codec and the semantic validation engine.
- `datev-exporter-plain` — the fixed official schemas, the buffered `DatevFile` and the
  forward-only `DatevStreamWriter`.
- `datev-exporter-advanced` — custom, renamed and reordered headings plus per-file
  `DatevValidationMode` selection.
- `datev-exporter-field-validator` — the optional implementation-neutral semantic validator.
- `datev-exporter-advanced-univocity` — `DatevUnivocityWriters`, for applications whose existing
  Univocity `CsvWriter` pipeline has to emit the file.

No exporter has a third-party runtime dependency; only the Univocity interoperability module does.

### Highlights

- Both exporters can produce a complete, importable Buchungsstapel file. Attach a `DatevMetadata`
  through `DatevFile.builder()` or `DatevStreamWriter.builder()`; the builders reject metadata whose
  format version differs from the selected schema.
- `DatevField` gives every one of the 125 official columns a readable English constant carrying the
  exact German heading, checked against the canonical table on class initialization. Every
  `DatevColumn` factory accepts either a `DatevField` or the raw heading `String`.
- `DatevInfoBlock` fills the repeating `Beleginfo` (8 slots) and `Zusatzinformation` (20 slots)
  groups from labelled entries, assigning slots in insertion order and enforcing the official field
  lengths at the call site, so DATEV's inconsistent `Art`/`Inhalt` heading spelling never has to be
  typed by hand.
- Every module serializes and parses through one `DatevCsv` implementation, so the exporters cannot
  drift apart in their reading of a DATEV record.
- `DatevMetadata`, `DatevValidationContext`, `DatevValidator` and `DatevHeader` are value types with
  `equals`, `hashCode` and `toString`.
- Published jars carry an `Automatic-Module-Name` and are built reproducibly: normalized timestamps
  and fixed entry order, so identical sources produce byte-identical artifacts.
- The schema, field types, lengths, decimal places and necessary flags are verified against DATEV's
  official Format-Pruefprogramm 2.2.3.0 and all 54 rows of DATEV's official v13 sample through the
  opt-in `datevCheckerTest` task.

### Known gaps

- `DatevColumn` equality depends on formatter identity, because `Function` has no value equality.
  Compare `header()` and `formattedValue()` instead of whole formatted columns.
- The exporters are single-threaded by design and their thread-safety is documented per class, but
  no concurrency audit has been completed.
