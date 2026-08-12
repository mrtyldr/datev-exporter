package io.github.mrtyldr.datev.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevMetadataTest {

    @Test
    void serializesTheOfficialBuchungsstapelV13ExampleExactly() {
        DatevMetadata metadata = DatevMetadata.bookingBatchV13()
                .createdAt(LocalDateTime.of(2024, 1, 30, 14, 4, 40, 439_000_000))
                .origin("RE")
                .advisorNumber(29098)
                .clientNumber(55003)
                .fiscalYearStart(LocalDate.of(2024, 1, 1))
                .accountLength(4)
                .period(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 8, 31))
                .description("Buchungsstapel")
                .dictationCode("WD")
                .fixed(false)
                .chartOfAccounts("03")
                .build();

        assertEquals(
                "\"EXTF\";700;21;\"Buchungsstapel\";13;20240130140440439;;\"RE\";\"\";\"\";"
                        + "29098;55003;20240101;4;20240101;20240831;\"Buchungsstapel\";\"WD\";"
                        + "1;0;0;\"EUR\";;\"\";;;\"03\";;;\"\";\"\"",
                metadata.toCsvLine()
        );
        assertFalse(metadata.toCsvLine().contains("\r"));
        assertFalse(metadata.toCsvLine().contains("\n"));
    }

    @Test
    void suppliesDocumentedDefaultsAndExactEmptyFieldQuoting() {
        DatevMetadata metadata = minimalBuilder().build();
        String[] fields = metadata.toCsvLine().split(";", -1);

        assertAll(
                () -> assertEquals(31, fields.length),
                () -> assertEquals("\"EXTF\"", fields[0]),
                () -> assertEquals("700", fields[1]),
                () -> assertEquals("21", fields[2]),
                () -> assertEquals("\"Buchungsstapel\"", fields[3]),
                () -> assertEquals("13", fields[4]),
                () -> assertEquals("", fields[6]),
                () -> assertEquals("\"\"", fields[7]),
                () -> assertEquals("\"\"", fields[8]),
                () -> assertEquals("\"\"", fields[9]),
                () -> assertEquals("\"\"", fields[16]),
                () -> assertEquals("\"\"", fields[17]),
                () -> assertEquals("1", fields[18]),
                () -> assertEquals("0", fields[19]),
                () -> assertEquals("1", fields[20]),
                () -> assertEquals("\"EUR\"", fields[21]),
                () -> assertEquals("", fields[22]),
                () -> assertEquals("\"\"", fields[23]),
                () -> assertEquals("", fields[24]),
                () -> assertEquals("", fields[25]),
                () -> assertEquals("\"\"", fields[26]),
                () -> assertEquals("", fields[27]),
                () -> assertEquals("", fields[28]),
                () -> assertEquals("\"\"", fields[29]),
                () -> assertEquals("\"\"", fields[30])
        );
    }

    @Test
    void exposesTypedMetadataValues() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 2, 3, 4, 5, 6, 987_654_321);
        LocalDate fiscalStart = LocalDate.of(2024, 1, 1);
        LocalDate periodStart = LocalDate.of(2024, 2, 1);
        LocalDate periodEnd = LocalDate.of(2024, 2, 29);
        Currency usd = Currency.getInstance("USD");

        DatevMetadata metadata = DatevMetadata.bookingBatchV13()
                .createdAt(timestamp)
                .origin("RE")
                .exportedBy("Exporter_1")
                .importedBy("Admin")
                .advisorNumber(1234)
                .clientNumber(42)
                .fiscalYearStart(fiscalStart)
                .accountLength(8)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .description("Batch 02/2024")
                .dictationCode("ABCD")
                .bookingType(DatevMetadata.BookingType.ANNUAL_FINANCIAL_STATEMENTS)
                .accountingPurpose(DatevMetadata.AccountingPurpose.IFRS)
                .fixed(false)
                .currency(usd)
                .chartOfAccounts("0304")
                .industrySolutionId("0012")
                .applicationInformation("app-1")
                .build();

        assertAll(
                () -> assertEquals(700, metadata.headerVersion()),
                () -> assertEquals(21, metadata.formatCategory()),
                () -> assertEquals("Buchungsstapel", metadata.formatName()),
                () -> assertEquals(13, metadata.formatVersion()),
                () -> assertEquals(timestamp.withNano(987_000_000), metadata.createdAt()),
                () -> assertEquals("RE", metadata.origin()),
                () -> assertEquals("Exporter_1", metadata.exportedBy()),
                () -> assertEquals("Admin", metadata.importedBy()),
                () -> assertEquals(1234, metadata.advisorNumber()),
                () -> assertEquals(42, metadata.clientNumber()),
                () -> assertEquals(fiscalStart, metadata.fiscalYearStart()),
                () -> assertEquals(8, metadata.accountLength()),
                () -> assertEquals(periodStart, metadata.periodStart()),
                () -> assertEquals(periodEnd, metadata.periodEnd()),
                () -> assertEquals("Batch 02/2024", metadata.description()),
                () -> assertEquals("ABCD", metadata.dictationCode()),
                () -> assertEquals(DatevMetadata.BookingType.ANNUAL_FINANCIAL_STATEMENTS,
                        metadata.bookingType()),
                () -> assertEquals(DatevMetadata.AccountingPurpose.IFRS,
                        metadata.accountingPurpose()),
                () -> assertFalse(metadata.fixed()),
                () -> assertEquals(usd, metadata.currency()),
                () -> assertEquals("0304", metadata.chartOfAccounts()),
                () -> assertEquals("0012", metadata.industrySolutionId()),
                () -> assertEquals("app-1", metadata.applicationInformation())
        );

        String[] fields = metadata.toCsvLine().split(";", -1);
        assertAll(
                () -> assertEquals("2", fields[18]),
                () -> assertEquals("64", fields[19]),
                () -> assertEquals("0", fields[20]),
                () -> assertEquals("\"USD\"", fields[21])
        );
    }

    @Test
    void reportsAllMissingRequiredFields() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DatevMetadata.bookingBatchV13().build()
        );

        assertAll(
                () -> assertTrue(exception.getMessage().contains("createdAt")),
                () -> assertTrue(exception.getMessage().contains("advisorNumber")),
                () -> assertTrue(exception.getMessage().contains("clientNumber")),
                () -> assertTrue(exception.getMessage().contains("fiscalYearStart")),
                () -> assertTrue(exception.getMessage().contains("accountLength")),
                () -> assertTrue(exception.getMessage().contains("periodStart")),
                () -> assertTrue(exception.getMessage().contains("periodEnd"))
        );
    }

    @Test
    void acceptsDocumentedNumericBoundaries() {
        DatevMetadata lower = minimalBuilder()
                .advisorNumber(1001)
                .clientNumber(1)
                .accountLength(4)
                .build();
        DatevMetadata upper = minimalBuilder()
                .advisorNumber(9_999_999)
                .clientNumber(99_999)
                .accountLength(8)
                .build();

        assertAll(
                () -> assertEquals(1001, lower.advisorNumber()),
                () -> assertEquals(1, lower.clientNumber()),
                () -> assertEquals(4, lower.accountLength()),
                () -> assertEquals(9_999_999, upper.advisorNumber()),
                () -> assertEquals(99_999, upper.clientNumber()),
                () -> assertEquals(8, upper.accountLength())
        );
    }

    @Test
    void rejectsNumericValuesOutsideDocumentedRanges() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().advisorNumber(1000)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().advisorNumber(10_000_000)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().clientNumber(0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().clientNumber(100_000)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().accountLength(3)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().accountLength(9))
        );
    }

    @Test
    void validatesIndividualDateRanges() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().createdAt(LocalDateTime.of(1999, 12, 31, 0, 0))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().fiscalYearStart(LocalDate.of(2100, 1, 1))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().periodStart(LocalDate.of(1999, 1, 1))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().periodEnd(LocalDate.of(2100, 1, 1)))
        );
    }

    @Test
    void validatesPeriodOrderingAndFiscalYearMembership() {
        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> minimalBuilder()
                        .period(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 31))
                        .build()),
                () -> assertThrows(IllegalStateException.class, () -> minimalBuilder()
                        .fiscalYearStart(LocalDate.of(2024, 2, 1))
                        .period(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 29))
                        .build()),
                () -> assertThrows(IllegalStateException.class, () -> minimalBuilder()
                        .period(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1))
                        .build())
        );

        DatevMetadata metadata = minimalBuilder()
                .period(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31))
                .build();
        assertEquals(LocalDate.of(2024, 12, 31), metadata.periodEnd());
    }

    @Test
    void validatesOfficialOptionalFieldPatterns() {
        assertEquals("Max Mustermann", minimalBuilder()
                .exportedBy("Max Mustermann")
                .build()
                .exportedBy());
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().origin("ABC")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().origin("Ä")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().exportedBy("user-name")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().importedBy("a".repeat(26))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().description("a".repeat(31))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().description("Übertrag")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().dictationCode("A")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().dictationCode("ab")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().chartOfAccounts("123")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().industrySolutionId("12345")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().industrySolutionId("12A"))
        );
    }

    @Test
    void validatesApplicationInformationAndEscapesCsvQuotes() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().applicationInformation("a".repeat(17))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().applicationInformation("line\nbreak")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().applicationInformation("line\u2028break")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().applicationInformation("🙂"))
        );

        DatevMetadata metadata = minimalBuilder()
                .applicationInformation("Müller €;\"b")
                .build();

        assertTrue(metadata.toCsvLine().endsWith(";\"Müller €;\"\"b\""));
    }

    @Test
    void rejectsNullsInsteadOfTreatingThemAsOptionalEmptyValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().createdAt(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().origin(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().bookingType(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().accountingPurpose(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().currency(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> minimalBuilder().applicationInformation(null))
        );
    }

    @Test
    void enumCodesMatchTheDatevContract() {
        assertAll(
                () -> assertEquals(1, DatevMetadata.BookingType.FINANCIAL_ACCOUNTING.code()),
                () -> assertEquals(2,
                        DatevMetadata.BookingType.ANNUAL_FINANCIAL_STATEMENTS.code()),
                () -> assertEquals(0, DatevMetadata.AccountingPurpose.INDEPENDENT.code()),
                () -> assertEquals(30, DatevMetadata.AccountingPurpose.TAX_LAW.code()),
                () -> assertEquals(40, DatevMetadata.AccountingPurpose.CALCULATION.code()),
                () -> assertEquals(50, DatevMetadata.AccountingPurpose.COMMERCIAL_LAW.code()),
                () -> assertEquals(64, DatevMetadata.AccountingPurpose.IFRS.code())
        );
    }

    private static DatevMetadata.Builder minimalBuilder() {
        return DatevMetadata.bookingBatchV13()
                .createdAt(LocalDateTime.of(2024, 1, 1, 12, 30, 45, 123_000_000))
                .advisorNumber(1001)
                .clientNumber(1)
                .fiscalYearStart(LocalDate.of(2024, 1, 1))
                .accountLength(4)
                .period(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
    }
}
