package io.github.mrtyldr.datev.core;


import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatevValidationContextTest {

    @Test
    void emptyContextLeavesAllOptionalValuesAbsent() {
        DatevValidationContext context = DatevValidationContext.empty();

        assertFalse(context.accountLength().isPresent());
        assertFalse(context.fiscalYearStart().isPresent());
        assertFalse(context.periodStart().isPresent());
        assertFalse(context.periodEnd().isPresent());
    }

    @Test
    void buildsValidatedFiscalContext() {
        DatevValidationContext context = DatevValidationContext.builder()
                .accountLength(4)
                .fiscalYearStart(LocalDate.of(2024, 1, 1))
                .period(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
                .build();

        assertEquals(4, context.accountLength().orElseThrow());
        assertEquals(LocalDate.of(2024, 2, 1), context.periodStart().orElseThrow());
    }

    @Test
    void rejectsInvalidAccountAndPeriodConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> DatevValidationContext.builder().accountLength(3));
        assertThrows(IllegalArgumentException.class,
                () -> DatevValidationContext.builder().accountLength(9));
        assertThrows(IllegalStateException.class,
                () -> DatevValidationContext.builder()
                        .period(LocalDate.of(2024, 2, 2), LocalDate.of(2024, 2, 1))
                        .build());
        assertThrows(IllegalStateException.class,
                () -> DatevValidationContext.builder()
                        .fiscalYearStart(LocalDate.of(2024, 1, 1))
                        .period(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))
                        .build());
    }
}
