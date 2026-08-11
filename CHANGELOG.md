# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Until the first `1.0.0` release the public API may change between minor versions.

## [Unreleased]

Nothing has been released yet. This section collects the changes that will make up the first
published version.

### Added

- EXTF management-record support in `datev-exporter-plain`. `DatevFile.builder()` and
  `DatevStreamWriter.builder()` combine schema, validator and `DatevMetadata`, so the dependency-free
  exporter can now produce a complete, importable Buchungsstapel file. The builders reject metadata
  whose format version differs from the selected schema.
- `datev-exporter-advanced-univocity`, holding the Univocity `CsvWriter` interoperability that
  previously lived on the advanced `DatevFile`, as `DatevUnivocityWriters`.
- `DatevCsv` in core: the canonical DATEV record encoder and strict single-record parser that every
  exporter now shares, replacing two independent implementations.
- `DatevField`, an enum with a readable English constant for each of the 125 official DATEV
  Buchungsstapel columns. Constants are declared in official output order and carry the exact
  German heading, which is checked against the canonical table on class initialization.
- `DatevField.Group`, `DatevField.Part` and `DatevField.Slot`, which describe the position of a
  field inside the repeating `Beleginfo` and `Zusatzinformation` groups and resolve a group slot
  back to a field.
- `DatevInfoBlock`, a slot pool that fills the repeating `Beleginfo` (8 slots) and
  `Zusatzinformation` (20 slots) groups from labelled entries, assigning slots in insertion order
  and enforcing the official field lengths at the call site.
- A `DatevField` overload for every `DatevColumn` factory: `of`, `formatted`, `amount`, `account`
  and `date`. The existing `String` overloads are unchanged and are not deprecated.
- An `Automatic-Module-Name` manifest attribute on each published jar, reserving the JPMS module
  name for consumers on the module path.

### Changed

- `DatevMetadata`, `DatevHeader` and `DatevRowValidator` moved from
  `io.github.mrtyldr.datev.advanced` to `io.github.mrtyldr.datev.core`. They are pure format
  knowledge with no backend dependency, and both exporters need them.
- `datev-exporter-advanced` no longer depends on Univocity. It serializes through `DatevCsv`, so
  no exporter has a third-party runtime dependency any more.
- `DatevHeader.resolve` and the package-private `quotedIndexes`/`namesArray` accessors were
  replaced by the public `indexOf(String)`, `quotedColumnIndexes()`, `isQuotedColumn(int)` and
  `bookingBatchVersion()`.
- Published jars are now built reproducibly: file timestamps are normalized and entry order is
  fixed, so identical sources produce byte-identical artifacts.

### Removed

- `datev-exporter-univocity`. It offered the same fixed-schema API as `datev-exporter-plain` at the
  cost of a third-party dependency, and could not emit the EXTF management record. Use
  `datev-exporter-plain`, or `datev-exporter-advanced-univocity` when an existing Univocity
  `CsvWriter` must produce the file.
- The Univocity-typed methods on the advanced `DatevFile` — `csvWriterSettings()`, the four
  `newCsvWriter(...)` overloads, `writeTo(CsvWriter)` and `writeDataTo(CsvWriter)`. They are now
  static methods on `DatevUnivocityWriters` taking the file as their first argument.

### Notes

- `DatevColumn.of(null, value)` is now ambiguous between the `String` and `DatevField` overloads
  and has to be cast, for example `DatevColumn.of((String) null, value)`. This affects source
  compatibility only, and only for that specific untyped-null call.
