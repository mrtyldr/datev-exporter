package io.github.mrtyldr.datev.advanced;

import io.github.mrtyldr.datev.core.DatevHeader;
import io.github.mrtyldr.datev.core.DatevRowSamples;
import io.github.mrtyldr.datev.core.DatevSchema;
import io.github.mrtyldr.datev.core.DatevValidationContext;
import io.github.mrtyldr.datev.core.DatevValidationException;
import io.github.mrtyldr.datev.validation.DatevValidator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LeanExporterParityTest {

    @Test
    void advancedHeadersMatchTheSharedCoreSchema() {
        assertEquals(DatevSchema.CURRENT_V13.headers(), DatevHeader.current().names());
        assertEquals(DatevSchema.LEGACY_V12.headers(), DatevHeader.legacyV12().names());
    }

    @Test
    void leanImplementationsProduceIdenticalCurrentAndLegacyBytes() {
        Map<String, Object> row = validSparseRow();

        var plainCurrent = io.github.mrtyldr.datev.plain.DatevFile.withDefaults();
        var advancedCurrent = DatevFile.withDefaults();
        plainCurrent.append(row);
        advancedCurrent.append(row);
        assertArrayEquals(plainCurrent.toByteArray(), advancedCurrent.toByteArray());

        var plainLegacy = io.github.mrtyldr.datev.plain.DatevFile.legacyV12();
        var advancedLegacy = DatevFile.withHeader(DatevHeader.legacyV12());
        plainLegacy.append(row);
        advancedLegacy.append(row);
        assertArrayEquals(plainLegacy.toByteArray(), advancedLegacy.toByteArray());
    }

    @Test
    void structuralModePreservesALeadingHashIdentically() {
        Map<String, Object> row = Map.of("Umsatz (ohne Soll/Haben-Kz)", "#value");
        var plain = io.github.mrtyldr.datev.plain.DatevFile.withDefaults();
        var advanced = DatevFile.builder()
                .validationMode(io.github.mrtyldr.datev.core.DatevValidationMode.NONE)
                .build();

        plain.append(row);
        advanced.append(row);

        assertArrayEquals(plain.toByteArray(), advanced.toByteArray());
    }

    @Test
    void theOptionalValidatorAndBuiltInStrictModeAgree() {
        DatevValidationContext context = DatevValidationContext.builder()
                .accountLength(4)
                .fiscalYearStart(LocalDate.of(2026, 1, 1))
                .period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .build();

        DatevValidator validator = DatevValidator.builder().context(context).build();
        var plain = io.github.mrtyldr.datev.plain.DatevFile.withDefaults(validator);
        var advanced = DatevFile.withDefaults();

        Map<String, Object> valid = validSparseRow();
        plain.append(valid);
        advanced.append(valid);
        assertArrayEquals(plain.toByteArray(), advanced.toByteArray());

        Map<String, Object> invalid = new LinkedHashMap<>(valid);
        invalid.put("Belegdatum", "3102");
        assertThrows(DatevValidationException.class, () -> plain.append(invalid));
        assertThrows(DatevValidationException.class, () -> advanced.append(invalid));
        assertEquals(1, plain.rowCount());
        assertEquals(1, advanced.rowCount());
    }

    private static Map<String, Object> validSparseRow() {
        return DatevRowSamples.escapingRow();
    }
}
