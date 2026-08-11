package io.github.mrtyldr.datev.validation;

import io.github.mrtyldr.datev.core.DatevValidationContext;
import io.github.mrtyldr.datev.core.DatevValidationMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevValidatorValueTest {

    @Test
    void validatorsWithTheSameConfigurationAreEqual() {
        DatevValidator first = DatevValidator.strict();
        DatevValidator second = DatevValidator.strict();

        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals("value", Map.of(first, "value").get(second));
        assertEquals(1, Set.copyOf(List.of(first, second)).size());
    }

    @Test
    void modeAndContextBothParticipateInEquality() {
        DatevValidationContext context = DatevValidationContext.builder()
                .accountLength(4)
                .period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .build();

        assertNotEquals(DatevValidator.strict(), DatevValidator.fieldLevel());
        assertNotEquals(DatevValidator.strict(),
                DatevValidator.builder().context(context).build());
        assertEquals(DatevValidator.builder().context(context).build(),
                DatevValidator.builder().context(context).build());
        assertNotEquals(DatevValidator.strict(), null);
        assertNotEquals(DatevValidator.strict(), DatevValidationMode.STRICT);
    }

    @Test
    void toStringNamesTheModeAndContext() {
        String description = DatevValidator.fieldLevel().toString();

        assertTrue(description.contains("FIELD_LEVEL"), description);
        assertTrue(description.contains("DatevValidationContext"), description);
    }
}
