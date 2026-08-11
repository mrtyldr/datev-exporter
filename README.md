# datev-exporter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mrtyldr/datev-exporter-plain?label=Maven%20Central)](https://central.sonatype.com/search?namespace=io.github.mrtyldr)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

> I wrote this because I have already spent enough of my life counting semicolons, and I would
> rather you did not have to. The `plain` module is a copy of the exporter I built for my own
> work. The other modules, the Javadoc and this README were produced with Opus 5 and GPT-5.6-Sol.

`datev-exporter` is a Java 17 multi-module library for creating DATEV Buchungsstapel CSV files. All exporters use the current v13/125-column or legacy v12/124-column schema, semicolon delimiters and CRLF records; their byte-stream APIs emit Windows-1252. The implementation follows DATEV's [booking-batch](https://developer.datev.de/en/file-format/details/datev-format/format-description/booking-batch), [header](https://developer.datev.de/de/file-format/details/datev-format/format-description/header), [technical structure](https://developer.datev.de/en/file-format/details/datev-format/getting-started), and [character-set](https://developer.datev.de/de/file-format/details/datev-format/character-set) documentation.

## Choose an artifact

| Artifact | Java package | Runtime dependencies | Intended use |
| --- | --- | --- | --- |
| `datev-exporter` | — | — | Bill of Materials aligning every module on one version |
| `datev-exporter-core` | `io.github.mrtyldr.datev.core` | None | Canonical schema, field definitions, EXTF metadata, the CSV codec and the validation engine |
| `datev-exporter-plain` | `io.github.mrtyldr.datev.plain` | `core` only | Fixed v13/v12 schemas, buffered `DatevFile` and forward-only `DatevStreamWriter` |
| `datev-exporter-advanced` | `io.github.mrtyldr.datev.advanced` | `core` only | Custom headers, rename/reorder and built-in validation modes |
| `datev-exporter-field-validator` | `io.github.mrtyldr.datev.validation` | `core` only | Optional semantic validator for the plain exporter |
| `datev-exporter-advanced-univocity` | `io.github.mrtyldr.datev.advanced.univocity` | `advanced` + Univocity | Univocity `CsvWriter` interoperability for the advanced exporter |

**No exporter has a third-party runtime dependency.** `datev-exporter-core` holds the single
canonical copy of DATEV's 125/124-column table, the field specifications, `DatevSchema`,
`DatevColumn`, the optional `DatevField` column enum, the `DatevInfoBlock` slot helper,
`DatevMetadata`, `DatevHeader`, the `DatevCsv` record codec and the semantic validation engine.
Every other module builds on it, so all modules share one verified schema, one serializer and one
set of validation rules.

### Which exporter?

Start with `datev-exporter-plain`. It writes both fixed official schemas, produces a complete
Buchungsstapel file when a `DatevMetadata` is attached, and is the only module offering
`DatevStreamWriter` for writing rows without retaining them.

Add `datev-exporter-advanced` when the output must use custom, renamed or reordered headings, or
when validation strictness has to be selected per file through `DatevValidationMode`. DATEV fixes
the names and order of its official schemas, so renaming or reordering creates a custom downstream
CSV contract and is intentionally incompatible with `DatevMetadata`.

Add `datev-exporter-advanced-univocity` only if the surrounding application already routes CSV
output through a Univocity `CsvWriter` and that writer has to produce the DATEV file. It changes no
bytes the exporters would otherwise write; it only lets an existing Univocity pipeline emit them.

## Requirements

- Java 17 or newer
- Gradle Wrapper (included)

## Install

Released to Maven Central. `io.github.mrtyldr:datev-exporter` is a Bill of Materials, not a
library: import it once and then declare the modules without versions.

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation platform('io.github.mrtyldr:datev-exporter:0.1.0')

    // Fixed schemas, EXTF metadata and streaming; pulls in datev-exporter-core transitively:
    implementation 'io.github.mrtyldr:datev-exporter-plain'

    // Optional semantic validator for the plain exporter:
    implementation 'io.github.mrtyldr:datev-exporter-field-validator'

    // Add only for custom, renamed or reordered headings:
    // implementation 'io.github.mrtyldr:datev-exporter-advanced'

    // Add only to write through an existing Univocity CsvWriter:
    // implementation 'io.github.mrtyldr:datev-exporter-advanced-univocity'
}
```

Maven:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.mrtyldr</groupId>
      <artifactId>datev-exporter</artifactId>
      <version>0.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.github.mrtyldr</groupId>
    <artifactId>datev-exporter-plain</artifactId>
  </dependency>
</dependencies>
```

Without the BOM, declare each module's version explicitly, for example
`implementation 'io.github.mrtyldr:datev-exporter-plain:0.1.0'`.

## Build and test

```shell
./gradlew clean build
```

The default build is deterministic and does not download or launch the external DATEV checker. See [Official DATEV checker compatibility](#official-datev-checker-compatibility) for the opt-in verification.

To try unreleased changes against a local project, install them into the local Maven repository
and add `mavenLocal()` to that project's repositories:

```shell
./gradlew publishToMavenLocal
```

## The plain fixed-schema exporter

The plain exporter creates a current v13 file by default. `DatevFile` retains its rows so they can be inspected, iterated and written more than once:

```java
import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.plain.DatevFile;

DatevFile file = DatevFile.withDefaults();
file.append(Map.of(
        "Umsatz (ohne Soll/Haben-Kz)", "1250,00",
        "Soll/Haben-Kennzeichen", "S",
        "Konto", "1000",
        "Gegenkonto (ohne BU-Schlüssel)", "8400",
        "Belegdatum", "1008",
        "Buchungstext", "Invoice 42"
));

file.append(
        DatevColumn.amount(
                "Umsatz (ohne Soll/Haben-Kz)",
                new BigDecimal("25.50")),
        DatevColumn.of("Soll/Haben-Kennzeichen", "H"),
        DatevColumn.account("Konto", 1200),
        DatevColumn.account("Gegenkonto (ohne BU-Schlüssel)", 1800),
        DatevColumn.documentDate(LocalDate.of(2026, 8, 11)),
        DatevColumn.of("Buchungstext", "Bank fee")
);

try (OutputStream output = Files.newOutputStream(Path.of("booking-rows.csv"))) {
    file.writeTo(output);
}
```

Official headings are exact strings, but they do not have to be typed by hand; see
[Readable column names](#readable-column-names) for the equivalent `DatevField` enum.

Use `DatevFile.legacyV12()` for the fixed 124-column schema. The complete-row overloads are `append(String)`, `append(String[])`, `append(Collection<String>)` and `appendValues(Object...)`; sparse rows use `append(Map<String, ?>)`, `append(DatevColumn<?>...)`, `append(Iterable<? extends DatevColumn<?>>)` or `appendColumns(...)`. Every append is atomic and requires exact official header names.

Without metadata the output begins with the column-heading record and does not contain the mandatory EXTF management record. That is useful when another component supplies that record or a downstream system expects only headings and rows. Attach a `DatevMetadata` — see [Create a complete Buchungsstapel file](#create-a-complete-buchungsstapel-file) — for a file DATEV can import directly.

`writeTo(OutputStream)` and `writeTo(Writer)` write the official heading unquoted and apply DATEV text quoting only to data rows. Serialization goes through `DatevCsv` in the core module, so every exporter emits byte-identical records for the same input.

### Optional semantic validation

The plain exporter performs structural CSV checks without pulling semantic rules into the base artifact. Add `datev-exporter-field-validator` and pass its implementation-neutral validator explicitly:

```java
import io.github.mrtyldr.datev.core.DatevValidationContext;
import io.github.mrtyldr.datev.plain.DatevFile;
import io.github.mrtyldr.datev.validation.DatevValidator;

DatevValidationContext context = DatevValidationContext.builder()
        .accountLength(4)
        .fiscalYearStart(LocalDate.of(2026, 1, 1))
        .period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        .build();

DatevValidator validator = DatevValidator.builder()
        .context(context)
        .build();
DatevFile validated = DatevFile.withDefaults(validator);
```

The same `DatevValidator` instance can be passed to `DatevStreamWriter`. It implements a JDK-only callback receiving the format version and immutable row, so the validator JAR does not link to any exporter. Use `strict()`, `fieldLevel()` or the contextual builder. Adding the validator dependency alone does not change exporter behavior; validation is enabled only when a validator is passed to a factory or builder. The advanced exporter does not take this callback — it selects strictness through `DatevValidationMode` instead.

`STRICT` is calibrated against the pinned DATEV checker schema and all 54 rows in DATEV's official v13 sample. Where a portal expression is narrower than the official sample, the sample-compatible representation is accepted instead of inventing a rule that rejects DATEV's own data. The validator checks deterministic technical semantics; a real checker and product import remain the final boundary for client-specific accounting rules.

### Stream plain rows without retaining them

Use `DatevStreamWriter` when rows can be produced and written once. It writes the fixed heading at construction and discards each successfully written booking row:

```java
import io.github.mrtyldr.datev.plain.DatevStreamWriter;

List<Map<String, ?>> bookingRows = List.of(
        Map.of(
                "Umsatz (ohne Soll/Haben-Kz)", "1250,00",
                "Soll/Haben-Kennzeichen", "S",
                "Konto", "1000",
                "Gegenkonto (ohne BU-Schlüssel)", "8400",
                "Belegdatum", "1008",
                "Buchungstext", "Invoice 42"
        ),
        Map.of(
                "Umsatz (ohne Soll/Haben-Kz)", "25,50",
                "Soll/Haben-Kennzeichen", "H",
                "Konto", "1200",
                "Gegenkonto (ohne BU-Schlüssel)", "1800",
                "Belegdatum", "1108",
                "Buchungstext", "Bank fee"
        )
);

try (OutputStream output = Files.newOutputStream(Path.of("booking-rows.csv"));
     DatevStreamWriter writer = DatevStreamWriter.withDefaults(output)) {
    for (Map<String, ?> row : bookingRows) {
        writer.append(row);
    }
}
```

The streaming writer exposes the same append overloads as the plain `DatevFile`. Use `legacyV12(output)` for v12, or enable the optional semantic validator with `withDefaults(output, DatevValidator.strict())`. `rowCount()` reports successfully handed-off booking rows, and the same 99,999-row DATEV limit applies.

`DatevStreamWriter.builder()` combines schema, validator and EXTF metadata without a factory per combination. Nothing is written until `build(...)` is called; the management record and heading are then emitted immediately:

```java
try (OutputStream output = Files.newOutputStream(Path.of("EXTF_Buchungsstapel.csv"));
     DatevStreamWriter writer = DatevStreamWriter.builder()
             .metadata(metadata)
             .validator(DatevValidator.strict())
             .build(output)) {
    for (Map<String, ?> row : bookingRows) {
        writer.append(row);
    }
}
```

Successfully written rows are not retained. Library-managed working memory is proportional to one aligned 124/125-cell row plus that row's encoded CSV record—`O(largest row)`, rather than `O(total rows)`. This does not prevent a validator or caller-supplied destination such as `ByteArrayOutputStream` or `StringWriter` from retaining rows or the complete output; use a file, network stream or another genuinely streaming destination when heap usage matters. Prefer the `OutputStream` factories for canonical Windows-1252 bytes; a caller-supplied `Writer` remains responsible for its eventual byte encoding. The buffered `DatevFile.writeTo(...)` now also serializes its stored records one at a time, but the `DatevFile` itself continues to retain every appended row.

Formatting, structural, Windows-1252 and optional semantic validation finish before a row is sent to the destination. Those failures do not change the output and the writer remains usable. An I/O failure may happen after the destination accepted part of a record, so physical rollback cannot be guaranteed; after such a failure the writer becomes terminal. During normal completion, `close()` flushes but never closes the caller-owned stream or writer. After a destination failure it does not retry flushing; the caller decides whether to flush or discard the potentially partial output.

## Create a complete Buchungsstapel file

A DATEV-Format file has three parts: the EXTF management record, the column-heading record and the booking records. `DatevMetadata` lives in `datev-exporter-core`, so both the plain and the advanced exporter can emit a complete file. Build the required v13 metadata explicitly and attach it through the builder:

```java
LocalDate periodStart = LocalDate.of(2026, 8, 1);
LocalDate periodEnd = LocalDate.of(2026, 8, 31);

DatevMetadata metadata = DatevMetadata.bookingBatchV13()
        .createdAt(LocalDateTime.now())
        .origin("RE")
        .exportedBy("my_application")
        .advisorNumber(1001)
        .clientNumber(1)
        .fiscalYearStart(LocalDate.of(2026, 1, 1))
        .accountLength(4)
        .period(periodStart, periodEnd)
        .description("August bookings")
        .dictationCode("WD")
        .fixed(false)
        .currency(Currency.getInstance("EUR"))
        .chartOfAccounts("03")
        .applicationInformation("my-application")
        .build();

// io.github.mrtyldr.datev.plain.DatevFile
DatevFile file = DatevFile.builder().metadata(metadata).build();
file.append(Map.of(
        "Umsatz (ohne Soll/Haben-Kz)", "1250,00",
        "Soll/Haben-Kennzeichen", "S",
        "Konto", "1000",
        "Gegenkonto (ohne BU-Schlüssel)", "8400",
        "Belegdatum", "1008",
        "Buchungstext", "Invoice 42"
));

try (OutputStream output = Files.newOutputStream(Path.of("EXTF_Buchungsstapel.csv"))) {
    file.writeTo(output);
}
```

`DatevMetadata` fixes the format identifier to external `EXTF`, header version 700, format category 21, `Buchungsstapel`, and format version 13. Its builder validates the timestamp, adviser/client numbers, fiscal year, account length, period and optional metadata fields. The plain builder additionally rejects metadata whose format version differs from the selected schema, so a file cannot declare a version it does not contain.

`DatevFile.withDefaults()` remains available for a heading-and-rows-only v13 document. Use the builder when the result must contain the mandatory first record. The advanced exporter accepts the same metadata through `DatevFile.withDefaults(metadata)` and `DatevFile.builder().metadata(metadata)`; there it can only be combined with the exact official header, strict validation and the DATEV Windows-1252 output profile.

## Semantic validation

The sections below describe `datev-exporter-advanced`, whose `DatevFile` selects strictness per file. The plain exporter instead takes an optional validator callback; see [Optional semantic validation](#optional-semantic-validation).

Official schemas default to `DatevValidationMode.STRICT`. Strict mode validates required fields, DATEV amount/number/date/account representations, text lengths and documented cross-field dependencies before a row is appended. The append is atomic: any `DatevValidationException` leaves the file unchanged and exposes every structured `DatevValidationError` through `errors()`.

The mode can be selected explicitly:

```java
DatevFile strict = DatevFile.builder()
        .validationMode(DatevValidationMode.STRICT)
        .build();

DatevFile fieldLevel = DatevFile.builder()
        .validationMode(DatevValidationMode.FIELD_LEVEL)
        .build();

DatevFile structuralOnly = DatevFile.builder()
        .validationMode(DatevValidationMode.NONE)
        .build();
```

- `STRICT` checks supplied values, mandatory fields and field dependencies.
- `FIELD_LEVEL` checks each supplied non-empty known field but permits an incomplete row.
- `NONE` performs only structural CSV/header checks.

Custom headers default to `NONE` because their domain semantics are unknown. Validation confirms DATEV syntax and documented structural rules; it does not decide the accounting or tax correctness of a booking.

## Custom headers

Custom, renamed and reordered headings are `datev-exporter-advanced` features; the plain exporter deliberately supports the fixed official schemas only.

Custom headers can be supplied in three equivalent forms:

```java
DatevFile fromText = DatevFile.withHeader("amount;account;text");
DatevFile fromArray = DatevFile.withHeader(
        new String[]{"amount", "account", "text"});
DatevFile fromList = DatevFile.withHeader(
        List.of("amount", "account", "text"));
```

The legacy schema can be selected explicitly:

```java
DatevFile legacy = DatevFile.withHeader(DatevHeader.legacyV12());
```

## Rename and reorder headers

The builder starts from the default header. Rename only the labels that need to differ:

```java
DatevFile localized = DatevFile.builder()
        .renameHeader("Konto", "Account")
        .renameHeader("Buchungstext", "Description")
        .build();
```

Ordering is configurable for default and custom schemas. The order must be a complete permutation:

```java
List<String> order = new ArrayList<>(DatevHeader.current().names());
Collections.swap(order, 0, 1);

DatevFile reordered = DatevFile.builder()
        .headerOrder(order)
        .build();
```

For a different base schema:

```java
DatevHeader custom = DatevHeader.of(List.of("amount", "account", "text"));
DatevFile reorderedCustom = DatevFile.builder(custom)
        .headerOrder("account", "text", "amount")
        .build();
```

DATEV defines fixed names and ordering for its official schemas. Renaming or reordering creates a custom downstream CSV contract and is intentionally incompatible with `DatevMetadata`.

## Append rows

Positional input is useful for small custom schemas. Each positional row must contain exactly as many values as the configured header:

```java
DatevFile file = DatevFile.withHeader("amount;account;text");

file.append("1250,00;1000;Invoice 42");
file.append(new String[]{"25,50", "1200", "Bank fee"});
file.append(List.of("99,90", "1400", "Office supplies"));
```

The string overload parses one semicolon-delimited CSV record, including quoted semicolons and trailing empty values.

Named values do not depend on map iteration order. Omitted fields become empty cells:

```java
Map<String, Object> row = new HashMap<>();
row.put("Umsatz (ohne Soll/Haben-Kz)", "1250,00");
row.put("Soll/Haben-Kennzeichen", "S");
row.put("Konto", 1000);
row.put("Gegenkonto (ohne BU-Schlüssel)", 8400);
row.put("Belegdatum", "1008"); // DDMM
row.put("Buchungstext", "Invoice 42");

DatevFile file = DatevFile.withDefaults();
file.append(row);
```

Map values use `String.valueOf`; they are validated but not locale-converted. The typed helpers produce DATEV amount, account and date representations:

```java
file.append(
        DatevColumn.amount(
                "Umsatz (ohne Soll/Haben-Kz)",
                new BigDecimal("1250.00")),
        DatevColumn.of("Soll/Haben-Kennzeichen", "S"),
        DatevColumn.account("Konto", 1000),
        DatevColumn.account("Gegenkonto (ohne BU-Schlüssel)", 8400),
        DatevColumn.documentDate(LocalDate.of(2026, 8, 10)),
        DatevColumn.of("Buchungstext", "Invoice 42")
);
```

A typed collection can be passed as an `Iterable` or through the named helper:

```java
List<DatevColumn<?>> columns = List.of(
        DatevColumn.of("Konto", 1000),
        DatevColumn.of("Buchungstext", "Invoice 43")
);

DatevFile fieldLevel = DatevFile.builder()
        .validationMode(DatevValidationMode.FIELD_LEVEL)
        .build();
fieldLevel.append(columns);
fieldLevel.appendColumns(columns);
```

Java erases both `Collection<String>` and `Collection<DatevColumn<?>>` to the same runtime method signature. Ordered strings therefore use `append(Collection<String>)`; columns use `append(Iterable<? extends DatevColumn<?>>)` or `appendColumns(...)`.

## Readable column names

An unknown heading is only rejected when the row is appended, as an `IllegalArgumentException`. `DatevField` is an optional enum in `datev-exporter-core` with a readable English constant for each of the 125 official columns, so a misspelled heading becomes a compile error instead:

```java
import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevField;

file.append(
        DatevColumn.amount(DatevField.AMOUNT, new BigDecimal("1250.00")),
        DatevColumn.of(DatevField.DEBIT_CREDIT_FLAG, "S"),
        DatevColumn.account(DatevField.ACCOUNT, 1000),
        DatevColumn.account(DatevField.CONTRA_ACCOUNT, 8400),
        DatevColumn.documentDate(LocalDate.of(2026, 8, 10)),
        DatevColumn.of(DatevField.POSTING_TEXT, "Invoice 42")
);
```

Every `DatevColumn` factory — `of`, `formatted`, `amount`, `account` and `date` — has both a `String` and a `DatevField` form. Neither is deprecated and both produce an identical column, so the enum can be adopted gradually. Passing an untyped `null` to a two-argument factory is ambiguous and has to be cast, for example `DatevColumn.of((String) null, value)`.

Constants are declared in official output order and carry the exact heading. `heading()` returns it, `fieldNumber()` the one-based DATEV field number, `spec()` the official definition and `isPresentIn(DatevSchema)` whether a schema contains the field — only `DIFFERING_CASH_DISCOUNT_ACCOUNT` (`Abw. Skontokonto`, field 125) is missing from v12. `DatevField.fromHeading(String)` resolves the other direction. A static check on class initialization fails the enum if its headings ever diverge from the canonical table.

### Repeating Beleginfo and Zusatzinformation groups

DATEV repeats two groups as numbered `Art`/`Inhalt` pairs: `Beleginfo` with eight slots and `Zusatzinformation` with twenty. Their headings are spelled inconsistently — `"Zusatzinformation - Art 1"` has spaces around the dash while `"Zusatzinformation- Inhalt 1"` does not. `DatevInfoBlock` takes labelled entries and assigns slots in insertion order, so neither the numbering nor the spelling has to be repeated:

```java
import io.github.mrtyldr.datev.core.DatevInfoBlock;

DatevInfoBlock extras = DatevInfoBlock.additionalInfo()
        .put("Auftragsnr", "A-4711")
        .put("Kostenträger", "KT-9");

file.appendColumns(extras.toColumns());
```

`put` rejects a blank or duplicate label, a value longer than the official field length — 20 characters for `Art`, 210 for `Inhalt` — and an entry beyond the last free slot, so those failures surface at the call site instead of during row validation. Unoccupied slots produce no columns and stay empty in the exported row. `toColumns()` and `entries()` return independent snapshots, so one block can be reused across rows; instances are mutable and not thread-safe.

Use `DatevInfoBlock.documentInfo()` for `Beleginfo`, or `DatevInfoBlock.of(DatevField.Group)` for an explicit group. Slot positions are also readable from the enum: `DatevField.ADDITIONAL_INFO_CONTENT_3.slot()` returns the group, slot number and part, while `DatevField.Group.ADDITIONAL_INFO.field(3, DatevField.Part.CONTENT)` resolves the same position back to a field.

## Writer interoperability

`writeTo(OutputStream)` and `writeTo(Writer)` emit the management record first when metadata is configured, followed by the heading and rows. They flush but do not close caller-owned output. Every exporter serializes through `DatevCsv`, so no third-party CSV library is involved.

### Univocity

Add `datev-exporter-advanced-univocity` when an existing Univocity pipeline has to produce the file. `DatevUnivocityWriters` builds writers and settings configured for a given advanced `DatevFile`, which itself implements `Iterable<List<String>>`:

```java
import io.github.mrtyldr.datev.advanced.univocity.DatevUnivocityWriters;

CsvWriter writer = DatevUnivocityWriters.newCsvWriter(file, outputStream);
writer.writeHeaders();
writer.writeRows(file);
writer.flush();
```

A `CsvWriter` emits one uniformly shaped record type and cannot produce the differently shaped 31-field EXTF management record. `DatevUnivocityWriters.writeTo(file, writer)` therefore rejects a metadata-backed file; use `writeDataTo(file, writer)` when heading-and-rows output is intentional, or `DatevFile.writeTo(OutputStream)` for the complete file.

A single `CsvWriter` also cannot apply one quote policy to the heading and another to the rows, so it quotes the heading's text columns as well. The booking rows are byte-identical to the built-in writer; `DatevFile.writeTo(OutputStream/Writer)` remains the canonical unquoted-heading path.

`newCsvWriter(file, OutputStream)` reports unmappable characters instead of silently replacing them and does not close the caller-owned stream. Avoid Univocity's raw `(OutputStream, Charset, settings)` constructor for DATEV output because Java's default encoder replacement can silently turn unsupported characters into `?`.

`csvWriterSettings(file)` returns a fresh object. Official v12/v13 schemas quote their defined text columns. A custom header uses generic CSV quoting; configure custom text fields explicitly:

```java
DatevFile customFile = DatevFile.withHeader("text;number");
customFile.append(List.of("description", "1000"));

CsvWriterSettings settings = DatevUnivocityWriters.csvWriterSettings(customFile);
settings.quoteFields("text");
CsvWriter writer = DatevUnivocityWriters.newCsvWriter(customFile, outputStream, settings);
DatevUnivocityWriters.writeTo(customFile, writer);
```

## Official DATEV checker compatibility

The opt-in checker test downloads DATEV's official [Prüfprogramm DATEV-Format](https://developer.datev.de/de/file-format/details/datev-format/tools) 2.2.3.0 and [official sample files](https://developer.datev.de/de/file-format/details/datev-format/format-description/sample-data) at runtime, verifies both pinned archive SHA-256 values, and never stores those external artifacts in Git. It compares all 125 v13 field types, lengths, decimal-place values, necessary flags and applicable labels/aliases in `datev-exporter-core` with the checker schema, pins that every module really consumes that one verified table, verifies the exact heading against DATEV's official sample, independently inspects a deterministic complete fixture, and runs strict semantic validation over all 54 official Buchungsstapel rows.

```shell
./gradlew datevCheckerTest
./gradlew generateDatevCheckerFixture
```

`datevCheckerTest` requires network access and is deliberately not part of the default `check` task. Its fixture is written to `build/datev-checker/fixture/EXTF_Buchungsstapel.csv`.

The supplied DATEV checker is a Windows GUI application. It has no documented headless report or validation exit-code contract. On an interactive Windows machine, open the generated fixture with:

```powershell
.\scripts\run-datev-checker.ps1
# or
.\gradlew runDatevChecker
```

Set `DATEV_CHECKER_EXE` to an existing checker executable to skip the launcher download. Otherwise the script downloads and checksum-verifies the pinned official archive. A successful process launch is not treated as a compatibility result: inspect the GUI report manually.

An actual import into DATEV Rechnungswesen additionally requires a licensed, configured Windows installation and suitable test client data. This repository cannot honestly automate or claim that product-level import on a public GitHub runner; record that acceptance separately in the target DATEV environment before production use.

## Project status and compatibility

This is an early `0.x` release. The public API may still change between minor versions; see the [CHANGELOG](CHANGELOG.md) for what each version altered. Validate generated files against the DATEV format version and import product used by your organization before relying on them in production.

DATEV is a trademark of DATEV eG. This independent project is not affiliated with, endorsed by, or sponsored by DATEV eG. The library does not provide tax, accounting or legal advice, and no compatibility or regulatory compliance is guaranteed.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
