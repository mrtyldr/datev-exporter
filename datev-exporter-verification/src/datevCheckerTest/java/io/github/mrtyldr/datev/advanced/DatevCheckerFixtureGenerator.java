package io.github.mrtyldr.datev.advanced;

import io.github.mrtyldr.datev.core.DatevMetadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** Generates the deterministic complete file used by the opt-in DATEV checker verification. */
public final class DatevCheckerFixtureGenerator {

    private DatevCheckerFixtureGenerator() {
    }

    /**
     * Writes one complete Buchungsstapel v13 file to the path supplied by Gradle.
     *
     * @param args exactly one output-file path
     * @throws Exception when the fixture cannot be created
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one fixture output path.");
        }

        DatevMetadata metadata = DatevMetadata.bookingBatchV13()
                .createdAt(LocalDateTime.of(2026, 8, 10, 12, 34, 56, 789_000_000))
                .origin("RE")
                .exportedBy("datevexporter")
                .advisorNumber(1001)
                .clientNumber(1)
                .fiscalYearStart(LocalDate.of(2026, 1, 1))
                .accountLength(4)
                .period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .description("Compatibility fixture")
                .dictationCode("WD")
                .fixed(false)
                .chartOfAccounts("03")
                .applicationInformation("datev-exporter")
                .build();

        DatevFile file = DatevFile.withDefaults(metadata);
        file.append(Map.of(
                "Umsatz (ohne Soll/Haben-Kz)", "1234,56",
                "Soll/Haben-Kennzeichen", "S",
                "Konto", "1000",
                "Gegenkonto (ohne BU-Schlüssel)", "8400",
                "Belegdatum", "1008",
                "Buchungstext", "Compatibility fixture"
        ));

        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.write(output, file.toByteArray());
    }
}
