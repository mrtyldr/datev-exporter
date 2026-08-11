# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Until the first `1.0.0` release the public API may change between minor versions.

## [Unreleased]

Nothing has been released yet. This section collects the changes that will make up the first
published version.

### Added

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

- Published jars are now built reproducibly: file timestamps are normalized and entry order is
  fixed, so identical sources produce byte-identical artifacts.

### Notes

- `DatevColumn.of(null, value)` is now ambiguous between the `String` and `DatevField` overloads
  and has to be cast, for example `DatevColumn.of((String) null, value)`. This affects source
  compatibility only, and only for that specific untyped-null call.
