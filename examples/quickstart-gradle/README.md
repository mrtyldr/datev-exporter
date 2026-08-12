# Complete DATEV v13 quickstart

This small Gradle application generates a complete DATEV Buchungsstapel v13 EXTF file. It shows
the recommended fixed-schema path with metadata, readable `DatevField` names, typed `DatevColumn`
formatters and strict metadata-aware row validation.

Run it from the repository root:

```shell
./gradlew :examples:quickstart-gradle:run
```

The generated Windows-1252/CRLF file is written to
`examples/quickstart-gradle/build/quickstart/EXTF_Buchungsstapel.csv`. The application verifies the
SHA-256 of the complete output, including the management record, all 125 official v13 headings and
the booking row. A format or serialization change therefore makes the example fail until its
expected output is deliberately reviewed and updated.

The example uses deterministic dates and metadata so the verification result is independent of
the machine and execution time. Replace the sample adviser number, client number, dates, account
mapping and posting data with values from your own accounting domain before using the pattern in a
real export.
