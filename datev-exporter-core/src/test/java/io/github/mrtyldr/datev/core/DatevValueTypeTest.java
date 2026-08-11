package io.github.mrtyldr.datev.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the value-equality contract of the immutable types in this module. */
class DatevValueTypeTest {

    @Test
    void metadataComparesByValueAndWorksAsAMapKey() {
        DatevMetadata first = metadata().build();
        DatevMetadata second = metadata().build();

        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals("value", Map.of(first, "value").get(second));
        assertEquals(1, Set.copyOf(List.of(first, second)).size());
    }

    @Test
    void metadataDistinguishesEveryBusinessField() {
        DatevMetadata base = metadata().build();

        assertNotEquals(base, metadata().advisorNumber(1002).build());
        assertNotEquals(base, metadata().clientNumber(2).build());
        assertNotEquals(base, metadata().accountLength(5).build());
        assertNotEquals(base, metadata().fixed(false).build());
        assertNotEquals(base, metadata().origin("XX").build());
        assertNotEquals(base, metadata().description("Other").build());
        assertNotEquals(base, metadata()
                .createdAt(LocalDateTime.of(2026, 8, 12, 9, 30)).build());
        assertNotEquals(base, metadata()
                .period(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 31)).build());
        assertNotEquals(base, null);
        assertNotEquals(base, "DatevMetadata");
    }

    @Test
    void metadataToStringIdentifiesTheClientAndPeriodWithoutDumpingTheRecord() {
        String description = metadata().build().toString();

        assertTrue(description.contains("advisor=1001"), description);
        assertTrue(description.contains("client=1"), description);
        assertTrue(description.contains("2026-08-01..2026-08-31"), description);
        assertTrue(description.length() < 120, description);
    }

    @Test
    void validationContextComparesByValue() {
        DatevValidationContext first = context().build();
        DatevValidationContext second = context().build();

        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, context().accountLength(5).build());
        assertNotEquals(first, DatevValidationContext.empty());
        assertEquals(DatevValidationContext.empty(), DatevValidationContext.builder().build());
        assertEquals("DatevValidationContext[empty]", DatevValidationContext.empty().toString());
        assertTrue(first.toString().contains("accountLength=4"), first.toString());
    }

    @Test
    void headerAlreadyComparesByValue() {
        assertEquals(DatevHeader.current(), DatevHeader.current().renamed(Map.of()));
        assertEquals(DatevHeader.parse("A;B"), DatevHeader.of(List.of("A", "B")));
        assertEquals(DatevHeader.parse("A;B").hashCode(), DatevHeader.of(List.of("A", "B")).hashCode());
        assertNotEquals(DatevHeader.current(), DatevHeader.legacyV12());
        // A parsed custom header has no official format version and no text columns, so it is not
        // equal to the official header even with identical names.
        assertNotEquals(DatevHeader.current(),
                DatevHeader.parse(String.join(";", DatevFieldSpecs.headers13())));
    }

    @Test
    void columnEqualityIgnoresValuesOnlyWhenAFormatterIsInvolved() {
        assertEquals(DatevColumn.of(DatevField.POSTING_TEXT, "Miete"),
                DatevColumn.of(DatevField.POSTING_TEXT, "Miete"));
        assertNotEquals(DatevColumn.of(DatevField.POSTING_TEXT, "Miete"),
                DatevColumn.of(DatevField.POSTING_TEXT, "Strom"));

        // Documented caveat: equality depends on formatter identity. Separately written lambdas
        // are never equal, so callers must not rely on comparing formatted columns.
        DatevColumn<String> viaOwnLambda = DatevColumn.formatted(
                DatevField.POSTING_TEXT, "x", value -> value);
        DatevColumn<String> viaEqualLambda = DatevColumn.formatted(
                DatevField.POSTING_TEXT, "x", value -> value);

        assertNotEquals(viaOwnLambda, viaEqualLambda);
        assertEquals(viaOwnLambda.header(), viaEqualLambda.header());
        assertEquals(viaOwnLambda.formattedValue(), viaEqualLambda.formattedValue());

        DatevColumn<BigDecimal> first = DatevColumn.amount(DatevField.AMOUNT, new BigDecimal("1.00"));
        DatevColumn<BigDecimal> second = DatevColumn.amount(DatevField.AMOUNT, new BigDecimal("1.00"));
        assertEquals(first.formattedValue(), second.formattedValue());
    }

    private static DatevMetadata.Builder metadata() {
        return DatevMetadata.bookingBatchV13()
                .createdAt(LocalDateTime.of(2026, 8, 11, 9, 30))
                .origin("RE")
                .exportedBy("test")
                .advisorNumber(1001)
                .clientNumber(1)
                .fiscalYearStart(LocalDate.of(2026, 1, 1))
                .accountLength(4)
                .period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .description("August");
    }

    private static DatevValidationContext.Builder context() {
        return DatevValidationContext.builder()
                .accountLength(4)
                .fiscalYearStart(LocalDate.of(2026, 1, 1))
                .period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }
}
