# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Until the first `1.0.0` release the public API may change between minor versions.

## [Unreleased]

Nothing yet.

## [0.2.0] - 2026-08-12

### Added

- A Gradle quickstart application that generates a deterministic complete v13 EXTF file during
  `check` and verifies the management record, official heading and booking row through a pinned
  whole-file SHA-256 value.
- A permanent `datev-exporter-benchmarks` JMH module comparing complete, strictly validated EXTF
  output through the plain retained file, plain forward-only writer and advanced retained file.
  The module is compiled by the normal build but cannot be published and is absent from the BOM.
- `DatevCsv.requireExportable(String value, String description)`, which checks in a single pass
  that a value carries neither a control or line-separator character nor a character Windows-1252
  cannot encode. It reports the control-character violation first, exactly as the exporters always
  did, and throws the same `IllegalArgumentException` messages they used to build themselves. The
  plain exporter now goes through it instead of scanning each cell twice.

### Changed

Except for the `applicationInformation` validation alignment described below, these changes preserve
the accepted rows, reported validation errors and emitted bytes. The validation, schema-lookup and
CSV-codec optimization items reduce allocations or CPU work; byte-for-byte parity tests against the
buffered exporter guard the output contract.

- `DatevStreamWriter`'s existing write-through contract is now explicit: each record is prepared in
  call-local `String`/`byte[]` storage, handed to the destination immediately and then discarded.
  The library does not batch records; callers decide whether to wrap the destination in
  `BufferedOutputStream` or `BufferedWriter` and choose any buffer size.
- `DatevMetadata.Builder.applicationInformation(...)` now rejects characters that Windows-1252
  cannot encode when the value is set. This makes the streaming and buffered paths fail consistently
  before output instead of allowing the streaming path to replace such characters.
- Row validation reuses the canonical-key index map that `DatevSchema` and `DatevHeader` build once
  instead of re-indexing 125 keys per row. A key list that is not one of the two official heading
  lists is still re-indexed on every call, so null and duplicate keys stay rejected.
- `DatevSchema.headers()` returns the same list instance `DatevFieldSpecs.headers13()` and
  `headers12()` return, rather than a separately derived copy with identical contents.
- The Windows-1252 encodability table in `DatevCsv` is derived by decoding 256 bytes instead of
  probing 65 536 code points against a `CharsetEncoder`, which shortens class initialization. The
  full Basic Multilingual Plane parity test against the JDK encoder still guards the result.
- Per-cell `CharsetEncoder`, per-cell `Optional` and per-row `Pattern` compilations were removed
  from the validation and assembly paths.

## [0.1.1] - 2026-08-11

### Added

- `DatevMetadata.bookingBatchV12()`, so the legacy 124-column schema can also carry an EXTF
  management record. `0.1.0` only offered `bookingBatchV13()`, which made
  `DatevFile.builder(DatevSchema.LEGACY_V12).metadata(...)` impossible to satisfy: the builder
  accepted the legacy schema but every metadata instance declared version 13.
- `DatevMetadata.LEGACY_FORMAT_VERSION`.

### Changed

- `DatevMetadata.formatVersion()` now returns the version the instance was built with instead of
  the constant 13, and `toCsvLine()` writes that value into field 5 of the management record.
- The advanced `DatevFile` builder accepts metadata alongside either official header and checks
  that the two versions agree, instead of requiring version 13.

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

The built-in exporters have no third-party runtime dependency; only the optional Univocity
interoperability module adds one.

### Highlights

- Both built-in exporters can produce a format-complete Buchungsstapel import candidate. Attach a
  `DatevMetadata` through `DatevFile.builder()` or `DatevStreamWriter.builder()`; the builders reject
  metadata whose format version differs from the selected schema. Acceptance still depends on the
  licensed, configured target DATEV environment.
- `DatevField` gives every one of the 125 official columns a readable English constant carrying the
  exact German heading, checked against the canonical table on class initialization. Every
  `DatevColumn` factory accepts either a `DatevField` or the raw heading `String`.
- `DatevInfoBlock` fills the repeating `Beleginfo` (8 slots) and `Zusatzinformation` (20 slots)
  groups from labelled entries, assigning slots in insertion order and enforcing the official field
  lengths at the call site, so DATEV's inconsistent `Art`/`Inhalt` heading spelling never has to be
  typed by hand.
- Every built-in exporter serializes and parses through one `DatevCsv` implementation, so those
  exporters cannot drift apart in their reading of a DATEV record. The optional Univocity adapter
  retains its documented heading-quoting difference.
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
