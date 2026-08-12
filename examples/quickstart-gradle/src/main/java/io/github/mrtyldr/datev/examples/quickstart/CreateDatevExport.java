package io.github.mrtyldr.datev.examples.quickstart;

import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevField;
import io.github.mrtyldr.datev.core.DatevMetadata;
import io.github.mrtyldr.datev.core.DatevValidationContext;
import io.github.mrtyldr.datev.plain.DatevFile;
import io.github.mrtyldr.datev.validation.DatevValidator;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;

/** Generates and verifies one deterministic, complete DATEV Buchungsstapel v13 export. */
public final class CreateDatevExport {
    private static final String EXPECTED_SHA_256 =
            "1b8fc7512decfb98381204d3d6d0ee65750c542225fd76fcbc5f5dd88feb6cef";

    private CreateDatevExport() {
    }

    public static void main(String[] args) throws IOException {
        Path output = args.length == 0
                ? Path.of("build", "quickstart", "EXTF_Buchungsstapel.csv")
                : Path.of(args[0]);

        LocalDate fiscalYearStart = LocalDate.of(2026, 1, 1);
        LocalDate periodStart = LocalDate.of(2026, 8, 1);
        LocalDate periodEnd = LocalDate.of(2026, 8, 31);

        DatevMetadata metadata = DatevMetadata.bookingBatchV13()
                .createdAt(LocalDateTime.of(2026, 8, 12, 9, 30, 15, 123_000_000))
                .origin("RE")
                .exportedBy("quickstart")
                .advisorNumber(1001)
                .clientNumber(1)
                .fiscalYearStart(fiscalYearStart)
                .accountLength(4)
                .period(periodStart, periodEnd)
                .description("August 2026")
                .applicationInformation("datev-exporter")
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
        file.append(
                DatevColumn.amount(DatevField.AMOUNT, new BigDecimal("1250.00")),
                DatevColumn.of(DatevField.DEBIT_CREDIT_FLAG, "S"),
                DatevColumn.of(DatevField.CURRENCY, "EUR"),
                DatevColumn.account(DatevField.ACCOUNT, 1000),
                DatevColumn.account(DatevField.CONTRA_ACCOUNT, 8400),
                DatevColumn.documentDate(LocalDate.of(2026, 8, 10)),
                DatevColumn.of(DatevField.DOCUMENT_FIELD_1, "RE-42"),
                DatevColumn.of(DatevField.POSTING_TEXT, "Rechnung 42; \"München\"")
        );

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream stream = Files.newOutputStream(output)) {
            file.writeTo(stream);
        }

        byte[] exportedBytes = Files.readAllBytes(output);
        String actualSha256 = sha256(exportedBytes);
        if (!EXPECTED_SHA_256.equals(actualSha256)) {
            throw new IllegalStateException("Quickstart output changed: expected SHA-256 "
                    + EXPECTED_SHA_256 + " but was " + actualSha256 + '.');
        }

        System.out.println("Created a complete DATEV v13 EXTF file with "
                + file.rowCount() + " booking row at " + output.toAbsolutePath());
        System.out.println("Verified SHA-256: " + actualSha256);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("This Java runtime does not provide SHA-256.", exception);
        }
    }
}
