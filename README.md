# DATEV Buchungsstapel exporter for Java

[![Build](https://github.com/mrtyldr/datev-exporter/actions/workflows/build.yml/badge.svg)](https://github.com/mrtyldr/datev-exporter/actions/workflows/build.yml)
[![Documentation](https://img.shields.io/badge/docs-GitHub%20Pages-0969da)](https://mrtyldr.github.io/datev-exporter/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.mrtyldr/datev-exporter-plain?label=Maven%20Central)](https://central.sonatype.com/search?namespace=io.github.mrtyldr)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

Generate format-complete DATEV Buchungsstapel / EXTF CSV files from booking data your application
has already mapped. The library owns the official v13/v12 column schemas, metadata record,
Windows-1252/CRLF serialization, deterministic technical validation and forward-only streaming.

This is a file-format exporter, not an accounting engine, SKR03/SKR04 mapper, DATEV API client or
import certification.

Read the [documentation](https://mrtyldr.github.io/datev-exporter/) or browse the
[v0.2.0 Javadoc](https://mrtyldr.github.io/datev-exporter/api/0.2.0/).

`datev-exporter` is a Java 17 multi-module library for creating DATEV Buchungsstapel CSV files.
Its fixed-schema exporters support the current v13/125-column and legacy v12/124-column schemas,
semicolon delimiters and CRLF records. Complete metadata-backed EXTF output uses Windows-1252;
metadata-free advanced output may select another charset for a downstream CSV contract. The
implementation follows DATEV's [booking-batch](https://developer.datev.de/en/file-format/details/datev-format/format-description/booking-batch), [header](https://developer.datev.de/de/file-format/details/datev-format/format-description/header), [technical structure](https://developer.datev.de/en/file-format/details/datev-format/getting-started), and [character-set](https://developer.datev.de/de/file-format/details/datev-format/character-set) documentation.

## Choose an artifact

| Artifact | Java package | Runtime dependencies | Intended use |
| --- | --- | --- | --- |
| `datev-exporter` | — | — | Bill of Materials aligning every module on one version |
| `datev-exporter-core` | `io.github.mrtyldr.datev.core` | None | Canonical schema, field definitions, EXTF metadata, the CSV codec and the validation engine |
| `datev-exporter-plain` | `io.github.mrtyldr.datev.plain` | `core` only | Fixed v13/v12 schemas, buffered `DatevFile` and forward-only `DatevStreamWriter` |
| `datev-exporter-advanced` | `io.github.mrtyldr.datev.advanced` | `core` only | Custom headers, rename/reorder and built-in validation modes |
| `datev-exporter-field-validator` | `io.github.mrtyldr.datev.validation` | `core` only | Optional semantic validator for the plain exporter |
| `datev-exporter-advanced-univocity` | `io.github.mrtyldr.datev.advanced.univocity` | `advanced` + Univocity | Univocity `CsvWriter` interoperability for the advanced exporter |

The built-in plain and advanced exporters have no third-party runtime dependency; only the optional
Univocity interoperability adapter adds one. `datev-exporter-core` holds the single canonical copy
of DATEV's 125/124-column table, the field specifications, `DatevSchema`,
`DatevColumn`, the optional `DatevField` column enum, the `DatevInfoBlock` slot helper,
`DatevMetadata`, `DatevHeader`, the `DatevCsv` record codec and the semantic validation engine.
Every other module builds on it, so all modules share one verified schema and one set of validation
rules; the built-in exporters also share the `DatevCsv` serializer.

### Which exporter?

Start with `datev-exporter-plain`. It writes both fixed official schemas, produces a complete
Buchungsstapel file when a `DatevMetadata` is attached, and is the only module offering
`DatevStreamWriter` for writing rows without retaining them.

Add `datev-exporter-advanced` when the output must use custom, renamed or reordered headings, or
when validation strictness has to be selected per file through `DatevValidationMode`. DATEV fixes
the names and order of its official schemas, so renaming or reordering creates a custom downstream
CSV contract and is intentionally incompatible with `DatevMetadata`.

Add `datev-exporter-advanced-univocity` only if the surrounding application already routes CSV
output through a Univocity `CsvWriter`. It intentionally emits heading-and-rows output rather than
the differently shaped EXTF management record. With the adapter's unmodified official v12/v13
settings, its booking rows match the built-in writer, but its text headings are quoted differently;
use the built-in writer for canonical complete-file output.

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
    implementation platform('io.github.mrtyldr:datev-exporter:0.2.0')

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
      <version>0.2.0</version>
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
  <dependency>
    <groupId>io.github.mrtyldr</groupId>
    <artifactId>datev-exporter-field-validator</artifactId>
  </dependency>
</dependencies>
```

Without the BOM, declare each module's version explicitly, for example
`implementation 'io.github.mrtyldr:datev-exporter-plain:0.2.0'`.

## Build and test

```shell
./gradlew clean build
```

The default build is deterministic and does not download or launch the external DATEV checker. See [Official DATEV checker compatibility](#official-datev-checker-compatibility) for the opt-in verification.

Local Maven publication uses the same signing policy as a Central release. With Gradle signing
credentials configured, install unreleased changes locally and add `mavenLocal()` to the consuming
project's repositories:

```shell
./gradlew publishToMavenLocal
```

## Performance benchmarks

The internal `datev-exporter-benchmarks` module contains permanent JMH coverage for complete EXTF
writing through the retained and forward-only exporters. It is compiled by the normal build but is
deliberately absent from the BOM and Maven Central release. Run measurements locally:

```shell
./gradlew :datev-exporter-benchmarks:jmh
```

See the [benchmark module guide](datev-exporter-benchmarks/README.md) for its scenarios and a short
smoke command. Timing results are not used as a CI pass/fail condition.

## The plain fixed-schema exporter

Start with a complete v13 EXTF file and strict semantic validation. `DatevFile` retains its rows so
they can be inspected, iterated and written more than once:

For a complete, deterministic application that is compiled and executed by this build, see the
[Gradle quickstart](examples/quickstart-gradle/README.md) or run
`./gradlew :examples:quickstart-gradle:run`.

```java
import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevMetadata;
import io.github.mrtyldr.datev.core.DatevValidationContext;
import io.github.mrtyldr.datev.plain.DatevFile;
import io.github.mrtyldr.datev.validation.DatevValidator;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

LocalDate fiscalYearStart = LocalDate.of(2026, 1, 1);
LocalDate periodStart = LocalDate.of(2026, 8, 1);
LocalDate periodEnd = LocalDate.of(2026, 8, 31);

DatevMetadata metadata = DatevMetadata.bookingBatchV13()
        .createdAt(LocalDateTime.now())
        .origin("RE")
        .exportedBy("my_application")
        .advisorNumber(1001)
        .clientNumber(1)
        .fiscalYearStart(fiscalYearStart)
        .accountLength(4)
        .period(periodStart, periodEnd)
        .description("August 2026")
        .build();

DatevValidationContext context = DatevValidationContext.builder()
        .accountLength(metadata.accountLength())
        .fiscalYearStart(metadata.fiscalYearStart())
        .period(metadata.periodStart(), metadata.periodEnd())
        .build();
DatevValidator validator = DatevValidator.builder()
        .context(context)
        .build();

DatevFile file = DatevFile.builder()
        .metadata(metadata)
        .validator(validator)
        .build();
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

try (OutputStream output = Files.newOutputStream(Path.of("EXTF_Buchungsstapel.csv"))) {
    file.writeTo(output);
}
```

This is the recommended path for a format-complete DATEV import candidate: metadata writes the
mandatory EXTF management record, and the strict validator rejects invalid booking rows before
serialization. Acceptance still has to be verified in the licensed, configured target DATEV
environment.

Official headings are exact strings, but they do not have to be typed by hand; see
[Readable column names](#readable-column-names) for the equivalent `DatevField` enum.

Use `DatevFile.legacyV12()` for the fixed 124-column schema. The complete-row overloads are `append(String)`, `append(String[])`, `append(Collection<String>)` and `appendValues(Object...)`; sparse rows use `append(Map<String, ?>)`, `append(DatevColumn<?>...)`, `append(Iterable<? extends DatevColumn<?>>)` or `appendColumns(...)`. Every append is atomic and requires exact official header names.

For the narrower headings-and-rows-only contract, `DatevFile.withDefaults()` remains available. Its
output intentionally omits the mandatory EXTF management record and is appropriate only when
another component supplies that record or a downstream system explicitly expects no metadata. See
[Create a complete Buchungsstapel file](#create-a-complete-buchungsstapel-file) for all metadata
options.

The built-in `writeTo(OutputStream)` paths write the official heading unquoted, apply DATEV text
quoting only to data rows and emit byte-identical output for the same schema and input. The
`Writer` paths produce the same record text, while their eventual bytes depend on the caller's
encoding. All built-in paths serialize through `DatevCsv`. The optional Univocity adapter has the
documented heading limitation described under [Univocity](#univocity).

### Validation modes and metadata-dependent context

The golden path above derives a strict `DatevValidationContext` from the same values used for
metadata, so account length, fiscal year and booking period are checked as well. The validator
remains a separate optional artifact, and the plain exporter performs structural CSV checks even
without it. Use `DatevValidator.strict()` only when metadata-dependent constraints are deliberately
out of scope.

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
             .validator(validator)
             .build(output)) {
    for (Map<String, ?> row : bookingRows) {
        writer.append(row);
    }
}
```

Successfully written rows are not retained. Each append assembles its record in row-local temporary
storage, encodes it to a row-local byte array on the `OutputStream` path and hands it to the
destination immediately. The writer retains no reference to those serialization objects after the
append, and the library does not batch records or keep a persistent serialization buffer between
calls. Library-managed working memory is therefore proportional to the row currently being written
rather than the total number of rows. This does not prevent a validator or caller-supplied
destination such as `ByteArrayOutputStream` or `StringWriter` from retaining rows or the complete
output; use a file, network stream or another genuinely streaming destination when heap usage
matters. Prefer the `OutputStream` factories for canonical Windows-1252 bytes; a caller-supplied
`Writer` remains responsible for its eventual byte encoding. The buffered `DatevFile.writeTo(...)`
also serializes its stored records one at a time, but the `DatevFile` itself continues to retain
every appended row.

Each completed record reaches the destination on the append that produced it—nothing is held back
between appends. Whether the destination should buffer those writes is an application decision.
Wrap an otherwise unbuffered file or network destination in `BufferedOutputStream` when exporting
many rows and reducing small operating-system or network writes improves throughput. Use
`BufferedWriter` for the equivalent character path. Buffering is normally unnecessary for
`ByteArrayOutputStream`, `StringWriter`, or a destination that is already buffered; wrapping those
again only adds another layer.

The example below leaves the buffer size at the JDK default. Applications with measured throughput,
latency or memory requirements can choose it explicitly with
`new BufferedOutputStream(file, bufferSize)`:

```java
try (OutputStream file = Files.newOutputStream(Path.of("EXTF_Buchungsstapel.csv"));
     OutputStream output = new BufferedOutputStream(file);
     DatevStreamWriter writer = DatevStreamWriter.builder()
             .metadata(metadata)
             .validator(validator)
             .build(output)) {
    for (Map<String, ?> row : bookingRows) {
        writer.append(row);
    }
}
```

`DatevStreamWriter.close()` flushes but does not close its caller-owned destination. Declare the
destination before the writer in the same try-with-resources statement, as above, so the writer is
flushed first and the buffered destination is then closed. Call `writer.flush()` when already
completed records must become visible before the writer is closed. The application still owns the
destination, controls any additional flushes and remains responsible for closing it.

Formatting, structural, Windows-1252 and optional semantic validation finish before a row is sent
to the destination. Those failures do not change the output and the writer remains usable. An I/O
failure may happen after the destination accepted part of a record, so physical rollback cannot be
guaranteed; after such a failure the writer becomes terminal. After a destination failure `close()`
does not retry flushing; the caller decides whether to flush or discard the potentially partial
output.

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

`DatevMetadata` fixes the format identifier to external `EXTF`, header version 700, format category
21 and `Buchungsstapel`. Its builder validates the timestamp, adviser/client numbers, fiscal year,
account length, period and optional metadata fields. In particular,
`applicationInformation(...)` rejects characters that Windows-1252 cannot encode when the value is
set, so every metadata-capable output path fails before writing instead of substituting an output
character.

Use `DatevMetadata.bookingBatchV12()` for the legacy 124-column schema:

```java
DatevMetadata legacyMetadata = DatevMetadata.bookingBatchV12()
        // ... the same builder methods
        .build();

DatevFile legacy = DatevFile.builder(DatevSchema.LEGACY_V12)
        .metadata(legacyMetadata)
        .build();
```

Both exporters reject metadata whose format version differs from the configured schema or header, so a file cannot declare a version it does not contain.

`DatevFile.withDefaults()` remains available for a heading-and-rows-only v13 document. Use the builder when the result must contain the mandatory first record. The advanced exporter accepts the same metadata through `DatevFile.withDefaults(metadata)` and `DatevFile.builder().metadata(metadata)`; there it can only be combined with the exact official header, strict validation and the DATEV Windows-1252 output profile.

## Semantic validation

The sections below describe `datev-exporter-advanced`, whose `DatevFile` selects strictness per file.
The plain exporter instead takes an optional validator callback; see
[Validation modes and metadata-dependent context](#validation-modes-and-metadata-dependent-context).

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

The built-in `writeTo(OutputStream)` and `writeTo(Writer)` paths emit the management record first
when metadata is configured, followed by the heading and rows. They flush but do not close
caller-owned output and serialize through `DatevCsv`; the optional adapter below deliberately
delegates heading-and-rows serialization to Univocity.

### Univocity

Add `datev-exporter-advanced-univocity` when an existing Univocity pipeline has to produce the file. `DatevUnivocityWriters` builds writers and settings configured for a given advanced `DatevFile`, which itself implements `Iterable<List<String>>`:

```java
import io.github.mrtyldr.datev.advanced.univocity.DatevUnivocityWriters;

CsvWriter writer = DatevUnivocityWriters.newCsvWriter(file, outputStream);
DatevUnivocityWriters.writeTo(file, writer);
```

A `CsvWriter` emits one uniformly shaped record type and cannot produce the differently shaped 31-field EXTF management record. `DatevUnivocityWriters.writeTo(file, writer)` therefore rejects a metadata-backed file; use `writeDataTo(file, writer)` when heading-and-rows output is intentional, or `DatevFile.writeTo(OutputStream)` for the complete file.

A single `CsvWriter` also cannot apply one quote policy to the heading and another to the rows, so
it quotes the heading's text columns as well. With the adapter's unmodified official v12/v13
settings, the booking rows are byte-identical to the built-in `OutputStream` writer;
`DatevFile.writeTo(OutputStream/Writer)` remains the canonical unquoted-heading path.

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

## Development provenance

The `plain` exporter began as code used in the author's own work. Subsequent modules and
documentation were developed with assistance from Opus 5 and GPT-5.6-Sol and are guarded by the
repository's automated tests and pinned DATEV verification suite.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
