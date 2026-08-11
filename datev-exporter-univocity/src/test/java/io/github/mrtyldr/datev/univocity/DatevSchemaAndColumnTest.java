package io.github.mrtyldr.datev.univocity;

import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevSchema;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatevSchemaAndColumnTest {
    @Test
    void exposesImmutableOfficialV12AndV13Schemas() {
        assertSame(DatevSchema.CURRENT_V13, DatevSchema.current());
        assertEquals(13, DatevSchema.CURRENT_V13.formatVersion());
        assertEquals(125, DatevSchema.CURRENT_V13.columnCount());
        assertEquals(12, DatevSchema.LEGACY_V12.formatVersion());
        assertEquals(124, DatevSchema.LEGACY_V12.columnCount());
        assertEquals("Umsatz (ohne Soll/Haben-Kz)", DatevSchema.current().headers().get(0));
        assertEquals("EU-Steuersatz (Ursprung)", DatevSchema.LEGACY_V12.headers().get(123));
        assertEquals("Abw. Skontokonto", DatevSchema.CURRENT_V13.headers().get(124));
        assertThrows(
                UnsupportedOperationException.class,
                () -> DatevSchema.current().headers().add("custom")
        );
    }

    @Test
    void identifiesDatevTextColumnsAndChecksBounds() {
        assertEquals(false, DatevSchema.current().isTextColumn(0));
        assertEquals(true, DatevSchema.current().isTextColumn(1));
        assertEquals(true, DatevSchema.current().isTextColumn(13));
        assertEquals(false, DatevSchema.current().isTextColumn(124));
        assertThrows(IndexOutOfBoundsException.class, () -> DatevSchema.current().isTextColumn(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> DatevSchema.current().isTextColumn(125));
    }

    @Test
    void typedColumnsFormatCanonicalDatevValues() {
        DecimalFormat decimal = new DecimalFormat(
                "0.00",
                DecimalFormatSymbols.getInstance(Locale.GERMANY)
        );

        assertEquals("42", DatevColumn.of("Konto", 42).formattedValue());
        assertEquals(
                "12,50",
                DatevColumn.formatted("Umsatz (ohne Soll/Haben-Kz)", new BigDecimal("12.5"), decimal)
                        .formattedValue()
        );
        assertEquals(
                "1234,50",
                DatevColumn.amount("Umsatz (ohne Soll/Haben-Kz)", new BigDecimal("1234.50"))
                        .formattedValue()
        );
        assertEquals("1000", DatevColumn.account("Konto", 1000).formattedValue());
        assertEquals("2902", DatevColumn.documentDate(LocalDate.of(2024, 2, 29)).formattedValue());
        assertEquals(
                "31012024",
                DatevColumn.date("Leistungsdatum", LocalDate.of(2024, 1, 31)).formattedValue()
        );
        assertNull(DatevColumn.formatted("Konto", null, ignored -> "called").formattedValue());
    }

    @Test
    void columnsValidateNamesAndWrapFormatterFailures() {
        assertEquals(
                "Fälligkeit",
                DatevColumn.of("Fa\u0308lligkeit", "x").header()
        );
        assertThrows(NullPointerException.class, () -> DatevColumn.of((String) null, "x"));
        for (String invalid : List.of(" ", " A", "A ", "A;B", "A\nB", "A\u2028B", "A\u2029B")) {
            assertThrows(IllegalArgumentException.class, () -> DatevColumn.of(invalid, "x"));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> DatevColumn.formatted("Konto", 1, value -> {
                    throw new IllegalStateException("broken");
                }).formattedValue()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DatevColumn.amount("Umsatz (ohne Soll/Haben-Kz)", BigDecimal.ZERO)
                        .formattedValue()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DatevColumn.amount("Umsatz (ohne Soll/Haben-Kz)", new BigDecimal("1.001"))
                        .formattedValue()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DatevColumn.account("Konto", 0).formattedValue()
        );
    }
}
