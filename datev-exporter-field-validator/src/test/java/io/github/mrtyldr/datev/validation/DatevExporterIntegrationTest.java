package io.github.mrtyldr.datev.validation;

import io.github.mrtyldr.datev.core.DatevRowSamples;
import io.github.mrtyldr.datev.core.DatevSchema;
import io.github.mrtyldr.datev.core.DatevValidationContext;
import io.github.mrtyldr.datev.core.DatevValidationError;
import io.github.mrtyldr.datev.core.DatevValidationException;
import io.github.mrtyldr.datev.core.DatevValidationMode;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevExporterIntegrationTest {

    @Test
    void bothLeanExportersWriteTheSharedCoreHeaderLine() {
        var plain = io.github.mrtyldr.datev.plain.DatevFile.withDefaults();
        var advanced = io.github.mrtyldr.datev.advanced.DatevFile.withDefaults();

        assertArrayEquals(plain.toByteArray(), advanced.toByteArray());
        assertEquals(125, DatevSchema.CURRENT_V13.columnCount());
        assertEquals(124, DatevSchema.LEGACY_V12.columnCount());
        assertEquals(DatevSchema.LEGACY_V12.headers(),
                DatevSchema.CURRENT_V13.headers().subList(0, 124));
    }

    @Test
    void commonValidatorAcceptsPlainRowsAndRejectsInvalidAppendAtomically() {
        io.github.mrtyldr.datev.plain.DatevFile file = io.github.mrtyldr.datev.plain.DatevFile.withDefaults(
                DatevValidator.strict());

        file.append(validRequiredValues());
        assertEquals(1, file.rowCount());

        DatevValidationException exception = assertThrows(DatevValidationException.class,
                () -> file.append(invalidSideValues()));
        assertEquals(1, file.rowCount());
        assertTrue(exception.errors().stream().anyMatch(error -> error.fieldNumber() == 2));
    }

    @Test
    void builtInStrictModeMatchesTheOptionalValidatorOnAdvanced() {
        io.github.mrtyldr.datev.advanced.DatevFile file =
                io.github.mrtyldr.datev.advanced.DatevFile.withDefaults();

        file.append(validRequiredValues());
        assertEquals(1, file.rowCount());

        DatevValidationException exception = assertThrows(DatevValidationException.class,
                () -> file.append(invalidSideValues()));
        assertEquals(1, file.rowCount());
        assertTrue(exception.errors().stream().anyMatch(error -> error.fieldNumber() == 2));
    }

    @Test
    void commonValidatorRunsBeforePlainStreamingOutput() {
        StringWriter output = new StringWriter();
        io.github.mrtyldr.datev.plain.DatevStreamWriter writer =
                io.github.mrtyldr.datev.plain.DatevStreamWriter.withDefaults(
                        output,
                        DatevValidator.strict()
                );
        String heading = output.toString();

        DatevValidationException exception = assertThrows(
                DatevValidationException.class,
                () -> writer.append(invalidSideValues())
        );

        assertEquals(heading, output.toString());
        assertEquals(0, writer.rowCount());
        assertTrue(exception.errors().stream().anyMatch(error -> error.fieldNumber() == 2));

        writer.append(validRequiredValues());
        assertEquals(1, writer.rowCount());
        writer.close();
    }

    @Test
    void oneImplementationNeutralValidatorMapsLegacyV12AndFactoryModes() {
        DatevValidator validator = DatevValidator.fieldLevel();

        assertEquals(DatevValidationMode.FIELD_LEVEL, validator.mode());
        io.github.mrtyldr.datev.plain.DatevFile.legacyV12(validator).append(Map.of());
        io.github.mrtyldr.datev.plain.DatevStreamWriter
                .legacyV12(new StringWriter(), validator)
                .append(Map.of());
    }

    @Test
    void contextualValidatorConstrainsBufferedAndStreamingOutputEqually() {
        DatevValidationContext context = DatevValidationContext.builder()
                .accountLength(4)
                .period(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
                .build();
        DatevValidator validator = DatevValidator.builder().context(context).build();
        Map<String, Object> invalidAccount = validRequiredValues();
        invalidAccount.put("Konto", "123456");
        invalidAccount.put("Belegdatum", "0101");

        DatevValidationException plainError = assertThrows(DatevValidationException.class,
                () -> io.github.mrtyldr.datev.plain.DatevFile.withDefaults(validator)
                        .append(invalidAccount));
        DatevValidationException streamingError = assertThrows(DatevValidationException.class,
                () -> io.github.mrtyldr.datev.plain.DatevStreamWriter
                        .withDefaults(new StringWriter(), validator)
                        .append(invalidAccount));

        assertEquals(plainError.errors(), streamingError.errors());
        assertEquals(java.util.List.of(7, 10),
                plainError.errors().stream().map(DatevValidationError::fieldNumber).toList());
    }

    @Test
    void validatedPlainAndAdvancedExportsAreByteIdenticalForBothSchemas() {
        DatevValidator validator = DatevValidator.strict();
        var plainV13 = io.github.mrtyldr.datev.plain.DatevFile.withDefaults(validator);
        var advancedV13 = io.github.mrtyldr.datev.advanced.DatevFile.withDefaults();
        plainV13.append(validRequiredValues());
        advancedV13.append(validRequiredValues());
        assertArrayEquals(plainV13.toByteArray(), advancedV13.toByteArray());

        var plainV12 = io.github.mrtyldr.datev.plain.DatevFile.legacyV12(validator);
        var advancedV12 = io.github.mrtyldr.datev.advanced.DatevFile.withHeader(
                io.github.mrtyldr.datev.core.DatevHeader.legacyV12());
        plainV12.append(validRequiredValues());
        advancedV12.append(validRequiredValues());
        assertArrayEquals(plainV12.toByteArray(), advancedV12.toByteArray());
    }

    private static Map<String, Object> validRequiredValues() {
        return DatevRowSamples.requiredFieldsRow();
    }

    private static Map<String, Object> invalidSideValues() {
        return DatevRowSamples.requiredFieldsRowWith("Soll/Haben-Kennzeichen", "X");
    }
}
