package io.github.mrtyldr.datev.core;


import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevColumnFactoryTest {

    @Test
    void createsPlainAndFunctionFormattedColumns() {
        assertEquals("42", DatevColumn.of("Account", 42).formattedValue());

        DatevColumn<BigDecimal> amount = DatevColumn.formatted(
                "Amount",
                new BigDecimal("12.345"),
                value -> value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
        );

        assertEquals("12.35", amount.formattedValue());
    }

    @Test
    void adaptsJavaTextFormat() {
        DecimalFormat format = new DecimalFormat(
                "0.00",
                DecimalFormatSymbols.getInstance(Locale.GERMANY)
        );

        DatevColumn<BigDecimal> amount = DatevColumn.formatted(
                "Umsatz (ohne Soll/Haben-Kz)",
                new BigDecimal("1234.5"),
                format
        );

        assertEquals("1234,50", amount.formattedValue());
    }

    @Test
    void providesCanonicalAmountAccountAndDateFactories() {
        assertEquals(
                "1234,50",
                DatevColumn.amount("Umsatz (ohne Soll/Haben-Kz)", new BigDecimal("1234.5"))
                        .formattedValue()
        );
        assertEquals("1000", DatevColumn.account("Konto", 1000).formattedValue());
        assertEquals("2902", DatevColumn.documentDate(LocalDate.of(2024, 2, 29)).formattedValue());
        assertEquals(
                "31012024",
                DatevColumn.date("Leistungsdatum", LocalDate.of(2024, 1, 31)).formattedValue()
        );
    }

    @Test
    void typedFactoriesRejectLossyOrNonPositiveValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DatevColumn.amount("Umsatz (ohne Soll/Haben-Kz)", new BigDecimal("1.001"))
                        .formattedValue()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DatevColumn.amount("Umsatz (ohne Soll/Haben-Kz)", BigDecimal.ZERO)
                        .formattedValue()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DatevColumn.account("Konto", 0).formattedValue()
        );
    }

    @Test
    void nullRepresentsAnEmptyCellWithoutCallingFormatter() {
        AtomicBoolean invoked = new AtomicBoolean();
        DatevColumn<String> column = DatevColumn.formatted("A", null, value -> {
            invoked.set(true);
            return value.toUpperCase(Locale.ROOT);
        });

        assertNull(column.formattedValue());
        assertFalse(invoked.get());
    }

    @Test
    void wrapsFormatterFailureWithHeaderContext() {
        IllegalStateException cause = new IllegalStateException("broken");
        DatevColumn<Integer> column = DatevColumn.formatted("Amount", 1, value -> {
            throw cause;
        });

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                column::formattedValue
        );

        assertTrue(exception.getMessage().contains("Amount"));
        assertSame(cause, exception.getCause());
    }

    @Test
    void validatesAndNormalizesHeaderIdentifiers() {
        assertEquals("Fälligkeit", DatevColumn.of("Fa\u0308lligkeit", "x").header());
        assertThrows(NullPointerException.class, () -> DatevColumn.of((String) null, "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of(" ", "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of(" A", "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of("\u00a0A", "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of("A\u00a0", "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of("A;B", "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of("A\nB", "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of("A\u2028B", "x"));
        assertThrows(IllegalArgumentException.class, () -> DatevColumn.of("A\u2029B", "x"));
    }
}
