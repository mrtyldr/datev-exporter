package io.github.mrtyldr.datev.core;


import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevColumnTest {

    @Test
    void supportsDefaultFunctionAndFormatConversion() {
        DecimalFormat decimalFormat = new DecimalFormat(
                "0.00",
                DecimalFormatSymbols.getInstance(Locale.GERMANY)
        );

        assertEquals("42", DatevColumn.of("Konto", 42).formattedValue());
        assertEquals(
                "v=42",
                DatevColumn.formatted("Konto", 42, value -> "v=" + value).formattedValue()
        );
        assertEquals(
                "12,50",
                DatevColumn.formatted("Umsatz (ohne Soll/Haben-Kz)", 12.5, decimalFormat)
                        .formattedValue()
        );
        assertNull(DatevColumn.formatted("Konto", null, value -> "never").formattedValue());
    }

    @Test
    void canonicalConveniencesProduceDatevRepresentations() {
        assertEquals(
                "12,50",
                DatevColumn.amount("Umsatz (ohne Soll/Haben-Kz)", new BigDecimal("12.50"))
                        .formattedValue()
        );
        assertEquals("1000", DatevColumn.account("Konto", 1000).formattedValue());
        assertEquals(
                "1108",
                DatevColumn.documentDate(LocalDate.of(2026, 8, 11)).formattedValue()
        );
        assertEquals(
                "11082026",
                DatevColumn.date("Leistungsdatum", LocalDate.of(2026, 8, 11)).formattedValue()
        );
    }

    @Test
    void convenienceFormattingRejectsInvalidNumericValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DatevColumn.amount(
                        "Umsatz (ohne Soll/Haben-Kz)",
                        new BigDecimal("1.001")
                ).formattedValue()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DatevColumn.amount(
                        "Umsatz (ohne Soll/Haben-Kz)",
                        BigDecimal.ZERO
                ).formattedValue()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DatevColumn.account("Konto", 0).formattedValue()
        );
    }

    @Test
    void rejectsUnsafeHeadingIdentifiers() {
        assertThrows(NullPointerException.class, () -> DatevColumn.of((String) null, "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of(" ", "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of(" Konto", "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of("Konto;Other", "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of("Kon\nto", "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of("Kon\u2028to", "x"));
    }

    @Test
    void wrapsFormatterFailuresWithHeadingContextAndRunsOnce() {
        AtomicInteger calls = new AtomicInteger();
        DatevColumn<Integer> column = DatevColumn.formatted("Konto", 42, ignored -> {
            calls.incrementAndGet();
            throw new IllegalStateException("broken");
        });

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                column::formattedValue
        );

        assertTrue(error.getMessage().contains("Konto"));
        assertTrue(error.getCause() instanceof IllegalStateException);
        assertEquals(1, calls.get());
    }
}
