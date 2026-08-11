package io.github.mrtyldr.datev.validation;

import io.github.mrtyldr.datev.core.DatevSchema;
import io.github.mrtyldr.datev.core.DatevValidationContext;
import io.github.mrtyldr.datev.core.DatevValidationError;
import io.github.mrtyldr.datev.core.DatevValidationException;
import io.github.mrtyldr.datev.core.DatevValidationMode;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevValidatorTest {

    @Test
    void noneModeSkipsSemanticsButStillChecksSchemaWidth() {
        DatevValidator validator = DatevValidator.builder()
                .mode(DatevValidationMode.NONE)
                .build();
        List<String> row = emptyRow(DatevSchema.CURRENT_V13);
        set(row, DatevSchema.CURRENT_V13, "Soll/Haben-Kennzeichen", "invalid");

        assertTrue(validator.validate(DatevSchema.CURRENT_V13, row).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(DatevSchema.CURRENT_V13, List.of()));
    }

    @Test
    void fieldLevelValidatesOnlySuppliedCells() {
        List<String> row = emptyRow(DatevSchema.CURRENT_V13);
        set(row, DatevSchema.CURRENT_V13, "Soll/Haben-Kennzeichen", "X");

        List<DatevValidationError> errors =
                DatevValidator.fieldLevel().validate(DatevSchema.CURRENT_V13, row);

        assertEquals(1, errors.size());
        assertEquals(DatevValidationError.Code.INVALID_FORMAT, errors.get(0).code());
        assertEquals(2, errors.get(0).fieldNumber());
    }

    @Test
    void strictModeRequiresSameFiveFieldsForBothSchemas() {
        for (DatevSchema schema : DatevSchema.values()) {
            List<DatevValidationError> errors =
                    DatevValidator.strict().validate(schema, emptyRow(schema));

            assertEquals(List.of(1, 2, 7, 8, 10),
                    errors.stream().map(DatevValidationError::fieldNumber).toList());
            assertTrue(errors.stream().allMatch(
                    error -> error.code() == DatevValidationError.Code.REQUIRED_FIELD));
        }
    }

    @Test
    void validatesAmountsRatesAndGenericNumbers() {
        assertNoFieldErrors("Umsatz (ohne Soll/Haben-Kz)", "0,01");
        assertNoFieldErrors("Umsatz (ohne Soll/Haben-Kz)", "1234567890,12");
        assertFieldError("Umsatz (ohne Soll/Haben-Kz)", "0,00",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertFieldError("Umsatz (ohne Soll/Haben-Kz)", "1,2",
                DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Umsatz (ohne Soll/Haben-Kz)", "12345678901,00",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertNoFieldErrors("Umsatz (ohne Soll/Haben-Kz)", "64083");
        assertNoFieldErrors("Basis-Umsatz", "1,00");
        assertFieldError("Basis-Umsatz", "1", DatevValidationError.Code.INVALID_FORMAT);
        assertNoFieldErrors("Skonto", "2,38");
        assertFieldError("Skonto", "1", DatevValidationError.Code.INVALID_FORMAT);

        assertNoFieldErrors("Kurs", "0,830000");
        assertNoFieldErrors("Kurs", "9999,12");
        assertFieldError("Kurs", "0,000000", DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertFieldError("Kurs", "1,1", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Kost-Menge", "1.25", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Kost-Menge", "1234567890123",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
    }

    @Test
    void validatesAccountRulesWithAndWithoutContext() {
        assertNoFieldErrors("Konto", "123456789");
        assertFieldError("Konto", "0000", DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertFieldError("Konto", "12A4", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Konto", "1234567890", DatevValidationError.Code.VALUE_OUT_OF_RANGE);

        DatevValidator contextual = DatevValidator.builder()
                .mode(DatevValidationMode.FIELD_LEVEL)
                .context(DatevValidationContext.builder().accountLength(4).build())
                .build();
        assertNoFieldErrors(contextual, "Konto", "12345");
        assertFieldError(contextual, "Konto", "123456",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertNoFieldErrors(contextual, "Erlöskonto (Anzahlungen)", "1234");
        assertFieldError(contextual, "Erlöskonto (Anzahlungen)", "12345",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertNoFieldErrors(contextual, "Erlöskonto (Anzahlungen)", "0");
    }

    @Test
    void validatesDocumentAndFullCalendarDates() {
        DatevValidationContext february = DatevValidationContext.builder()
                .period(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29))
                .build();
        DatevValidator contextual = DatevValidator.builder()
                .mode(DatevValidationMode.FIELD_LEVEL)
                .context(february)
                .build();

        assertNoFieldErrors(contextual, "Belegdatum", "2902");
        assertFieldError(contextual, "Belegdatum", "3104",
                DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError(contextual, "Belegdatum", "0101",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertNoFieldErrors("Belegdatum", "2902");
        assertNoFieldErrors("Leistungsdatum", "29022024");
        assertFieldError("Leistungsdatum", "29022023",
                DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Leistungsdatum", "01011999",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
    }

    @Test
    void validatesRestrictedTextLengthEnumsCurrencyAndCountry() {
        assertNoFieldErrors("Belegfeld 1", "Rg_32029/2024");
        assertFieldError("Belegfeld 1", "Rg 1", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Belegfeld 2", "a".repeat(13),
                DatevValidationError.Code.TEXT_TOO_LONG);
        assertFieldError("Buchungstext", "🙂",
                DatevValidationError.Code.UNMAPPABLE_CHARACTER);
        assertFieldError("Buchungstext", "a".repeat(61),
                DatevValidationError.Code.TEXT_TOO_LONG);
        assertNoFieldErrors("WKZ Umsatz", "EUR");
        assertFieldError("WKZ Umsatz", "ZZZ", DatevValidationError.Code.INVALID_FORMAT);
        assertNoFieldErrors("Land", "DE");
        assertNoFieldErrors("Land", "EL");
        assertNoFieldErrors("Land", "XI");
        assertFieldError("Land", "XX", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Festschreibung", "2", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Buchungstyp", "XX", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Buchungstext", "line\nbreak",
                DatevValidationError.Code.INVALID_FORMAT);
        assertNoFieldErrors("Geschäftspartnerbank", "1");
        assertFieldError("Sachverhalt L+L", "0",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
    }

    @Test
    void strictModeChecksDocumentedPairs() {
        List<String> row = validStrictRow(DatevSchema.CURRENT_V13);
        set(row, DatevSchema.CURRENT_V13, "Basis-Umsatz", "10,00");
        set(row, DatevSchema.CURRENT_V13, "Beleginfo - Art 1", "Bank");
        set(row, DatevSchema.CURRENT_V13, "Zusatzinformation- Inhalt 1", "Value");

        List<DatevValidationError> errors =
                DatevValidator.strict().validate(DatevSchema.CURRENT_V13, row);

        assertEquals(Set.of("WKZ Basis-Umsatz", "Beleginfo - Inhalt 1",
                        "Zusatzinformation - Art 1"),
                errors.stream().map(DatevValidationError::canonicalKey)
                        .collect(Collectors.toSet()));
        assertTrue(errors.stream().allMatch(error ->
                error.code() == DatevValidationError.Code.DEPENDENT_FIELD_MISSING));
    }

    @Test
    void strictModeAcceptsCompletePairs() {
        List<String> row = validStrictRow(DatevSchema.CURRENT_V13);
        set(row, DatevSchema.CURRENT_V13, "Basis-Umsatz", "10,00");
        set(row, DatevSchema.CURRENT_V13, "WKZ Basis-Umsatz", "EUR");
        set(row, DatevSchema.CURRENT_V13, "Beleginfo - Art 1", "Bank");
        set(row, DatevSchema.CURRENT_V13, "Beleginfo - Inhalt 1", "Value");
        set(row, DatevSchema.CURRENT_V13, "Zusatzinformation - Art 1", "Note");
        set(row, DatevSchema.CURRENT_V13, "Zusatzinformation- Inhalt 1", "Value");

        assertTrue(DatevValidator.strict().validate(DatevSchema.CURRENT_V13, row).isEmpty());
    }

    @Test
    void validatorIsDirectlyUsableAsBothLeanExportersStandardCallback() {
        DatevValidator validator = DatevValidator.fieldLevel();

        assertDoesNotThrow(() -> validator.accept(13, emptyRow(DatevSchema.CURRENT_V13)));
        assertDoesNotThrow(() -> validator.accept(12, emptyRow(DatevSchema.LEGACY_V12)));
        assertThrows(IllegalArgumentException.class,
                () -> validator.accept(11, List.of()));
        assertEquals(DatevSchema.CURRENT_V13, DatevSchema.fromFormatVersion(13));
        assertEquals(DatevSchema.LEGACY_V12, DatevSchema.fromFormatVersion(12));
    }

    @Test
    void errorsRemainInFieldOrder() {
        List<String> row = validStrictRow(DatevSchema.CURRENT_V13);
        set(row, DatevSchema.CURRENT_V13, "Basis-Umsatz", "10,00");
        set(row, DatevSchema.CURRENT_V13, "Abw. Skontokonto", "invalid");

        assertEquals(List.of(6, 125), DatevValidator.strict()
                .validate(DatevSchema.CURRENT_V13, row)
                .stream().map(DatevValidationError::fieldNumber).toList());
    }

    @Test
    void aggregateExceptionCarriesImmutableErrors() {
        List<String> row = emptyRow(DatevSchema.CURRENT_V13);
        set(row, DatevSchema.CURRENT_V13, "Soll/Haben-Kennzeichen", "X");
        set(row, DatevSchema.CURRENT_V13, "WKZ Umsatz", "ZZZ");

        DatevValidationException exception = assertThrows(DatevValidationException.class,
                () -> DatevValidator.fieldLevel()
                        .validateOrThrow(DatevSchema.CURRENT_V13, row));

        assertEquals(2, exception.errors().size());
        assertTrue(exception.getMessage().contains("2 validation errors"));
        assertThrows(UnsupportedOperationException.class, () -> exception.errors().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new DatevValidationException(List.of()));
    }

    private static void assertNoFieldErrors(String key, String value) {
        assertNoFieldErrors(DatevValidator.fieldLevel(), key, value);
    }

    private static void assertNoFieldErrors(DatevValidator validator, String key, String value) {
        List<String> row = emptyRow(DatevSchema.CURRENT_V13);
        set(row, DatevSchema.CURRENT_V13, key, value);
        assertDoesNotThrow(() -> validator.validateOrThrow(DatevSchema.CURRENT_V13, row));
    }

    private static void assertFieldError(
            String key,
            String value,
            DatevValidationError.Code expectedCode
    ) {
        assertFieldError(DatevValidator.fieldLevel(), key, value, expectedCode);
    }

    private static void assertFieldError(
            DatevValidator validator,
            String key,
            String value,
            DatevValidationError.Code expectedCode
    ) {
        List<String> row = emptyRow(DatevSchema.CURRENT_V13);
        set(row, DatevSchema.CURRENT_V13, key, value);
        List<DatevValidationError> errors = validator.validate(DatevSchema.CURRENT_V13, row);
        assertFalse(errors.isEmpty(), () -> "Expected error for " + key + " value " + value);
        assertTrue(errors.stream().anyMatch(error -> error.code() == expectedCode),
                () -> "Expected " + expectedCode + " but got " + errors);
    }

    private static List<String> validStrictRow(DatevSchema schema) {
        List<String> row = emptyRow(schema);
        set(row, schema, "Umsatz (ohne Soll/Haben-Kz)", "100,00");
        set(row, schema, "Soll/Haben-Kennzeichen", "S");
        set(row, schema, "Konto", "1000");
        set(row, schema, "Gegenkonto (ohne BU-Schlüssel)", "8400");
        set(row, schema, "Belegdatum", "0101");
        return row;
    }

    private static List<String> emptyRow(DatevSchema schema) {
        return new ArrayList<>(Collections.nCopies(schema.columnCount(), null));
    }

    private static void set(List<String> row, DatevSchema schema, String key, String value) {
        int index = schema.headers().indexOf(key);
        if (index < 0) {
            throw new AssertionError("Unknown test field: " + key);
        }
        row.set(index, value);
    }
}
