package io.github.mrtyldr.datev.advanced;

import io.github.mrtyldr.datev.core.DatevValidationError;
import io.github.mrtyldr.datev.core.DatevValidationException;
import io.github.mrtyldr.datev.core.DatevValidationMode;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

class DatevRowValidatorTest {

    @Test
    void noneModeSkipsSemanticChecks() {
        List<String> row = emptyRow(DatevHeader.current());
        set(DatevHeader.current(), row, "Soll/Haben-Kennzeichen", "invalid");

        assertTrue(DatevRowValidator.validate(
                DatevHeader.current(), row, DatevValidationMode.NONE, null).isEmpty());
    }

    @Test
    void fieldLevelChecksSuppliedValuesButDoesNotRequireMissingFields() {
        List<String> row = emptyRow(DatevHeader.current());
        set(DatevHeader.current(), row, "Soll/Haben-Kennzeichen", "X");

        List<DatevValidationError> errors = DatevRowValidator.validate(
                DatevHeader.current(), row, DatevValidationMode.FIELD_LEVEL, null);

        assertEquals(1, errors.size());
        assertEquals(DatevValidationError.Code.INVALID_FORMAT, errors.get(0).code());
        assertEquals(2, errors.get(0).fieldNumber());
    }

    @Test
    void strictModeCollectsExactlyTheFiveMissingNecessaryFields() {
        List<DatevValidationError> errors = DatevRowValidator.validate(
                DatevHeader.current(),
                emptyRow(DatevHeader.current()),
                DatevValidationMode.STRICT,
                null
        );

        assertEquals(5, errors.size());
        assertEquals(
                List.of(
                        "Umsatz (ohne Soll/Haben-Kz)",
                        "Soll/Haben-Kennzeichen",
                        "Konto",
                        "Gegenkonto (ohne BU-Schlüssel)",
                        "Belegdatum"
                ),
                errors.stream().map(DatevValidationError::canonicalKey).toList()
        );
        assertTrue(errors.stream().allMatch(
                error -> error.code() == DatevValidationError.Code.REQUIRED_FIELD));
    }

    @Test
    void customHeaderDoesNotAcquireOfficialRequiredRulesByNameAlone() {
        DatevHeader customCopy = DatevHeader.of(DatevHeader.current().names());

        assertTrue(DatevRowValidator.validate(
                customCopy,
                emptyRow(customCopy),
                DatevValidationMode.STRICT,
                null
        ).isEmpty());
    }

    @Test
    void acceptsDocumentedCheckerSampleNumericRepresentations() {
        List<String> row = emptyRow(DatevHeader.current());
        set(DatevHeader.current(), row, "Umsatz (ohne Soll/Haben-Kz)", "64083");
        set(DatevHeader.current(), row, "Kurs", "0,830000");
        set(DatevHeader.current(), row, "Kost-Menge", "5");

        assertTrue(DatevRowValidator.validate(
                DatevHeader.current(), row, DatevValidationMode.FIELD_LEVEL, null).isEmpty());
    }

    @Test
    void validatesAmountAndRateRangesWithoutRequiringCanonicalFormatting() {
        assertNoFieldErrors("Umsatz (ohne Soll/Haben-Kz)", "0,01");
        assertNoFieldErrors("Umsatz (ohne Soll/Haben-Kz)", "1234567890,12");
        assertFieldError("Umsatz (ohne Soll/Haben-Kz)", "0,00",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertFieldError("Umsatz (ohne Soll/Haben-Kz)", "1,2",
                DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Umsatz (ohne Soll/Haben-Kz)", "12345678901,00",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);

        assertNoFieldErrors("Kurs", "0,830000");
        assertNoFieldErrors("Kurs", "9999,12");
        assertFieldError("Kurs", "0,000000", DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertFieldError("Kurs", "1,1", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Kurs", "10000,00", DatevValidationError.Code.VALUE_OUT_OF_RANGE);
    }

    @Test
    void genericNumbersUseConfiguredIntegralAndFractionalLimits() {
        assertNoFieldErrors("Kost-Menge", "5");
        assertNoFieldErrors("Kost-Menge", "123456789012,1234");
        assertNoFieldErrors("Geschäftspartnerbank", "1");
        assertFieldError("Kost-Menge", "1234567890123",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertFieldError("Kost-Menge", "1,12345",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertFieldError("Kost-Menge", "1.25",
                DatevValidationError.Code.INVALID_FORMAT);
    }

    @Test
    void validatesAccountsAndUsesMetadataAccountLength() {
        assertNoFieldErrors("Konto", "123456789");
        assertFieldError("Konto", "0000", DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertFieldError("Konto", "12A4", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Konto", "1234567890", DatevValidationError.Code.VALUE_OUT_OF_RANGE);

        DatevMetadata metadata = metadata(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                4
        );
        assertNoFieldErrors("Konto", "12345", metadata);
        assertFieldError("Konto", "123456", metadata, DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertNoFieldErrors("Erlöskonto (Anzahlungen)", "1234", metadata);
        assertFieldError("Erlöskonto (Anzahlungen)", "123", metadata,
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertFieldError("Erlöskonto (Anzahlungen)", "12345", metadata,
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertFieldError("Abw. Skontokonto", "12345", metadata,
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertNoFieldErrors("Erlöskonto (Anzahlungen)", "0", metadata);
        assertNoFieldErrors("Abw. Skontokonto", "0", metadata);
    }

    @Test
    void acceptsOfficialOneToFourDigitBuKeys() {
        assertNoFieldErrors("BU-Schlüssel", "3");
        assertNoFieldErrors("BU-Schlüssel", "40");
        assertNoFieldErrors("BU-Schlüssel", "501");
        assertNoFieldErrors("BU-Schlüssel", "6501");
        assertFieldError("BU-Schlüssel", "12345", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("BU-Schlüssel", "A1", DatevValidationError.Code.INVALID_FORMAT);
    }

    @Test
    void validatesActualDocumentAndFullCalendarDates() {
        DatevMetadata february = metadata(
                LocalDate.of(2024, 2, 1),
                LocalDate.of(2024, 2, 29),
                4
        );

        assertNoFieldErrors("Belegdatum", "2902", february);
        assertFieldError("Belegdatum", "3104", february,
                DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Belegdatum", "0101", february,
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
        assertNoFieldErrors("Belegdatum", "2902");

        assertNoFieldErrors("Leistungsdatum", "29022024");
        assertFieldError("Leistungsdatum", "29022023",
                DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Leistungsdatum", "01011999",
                DatevValidationError.Code.VALUE_OUT_OF_RANGE);
    }

    @Test
    void validatesRestrictedTextCharactersAndCodePointLengths() {
        assertNoFieldErrors("Belegfeld 1", "Rg_32029/2024");
        assertFieldError("Belegfeld 1", "Rg 1", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Belegfeld 1", "Übertrag", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Belegfeld 1", "a".repeat(37),
                DatevValidationError.Code.TEXT_TOO_LONG);
        assertNoFieldErrors("Belegfeld 2", "a".repeat(12));
        assertFieldError("Belegfeld 2", "a".repeat(13),
                DatevValidationError.Code.TEXT_TOO_LONG);

        assertNoFieldErrors("Buchungstext", "ä".repeat(60));
        assertFieldError("Buchungstext", "ä".repeat(61),
                DatevValidationError.Code.TEXT_TOO_LONG);

        // The shared core rejects anything DATEV's Windows-1252 output profile cannot encode,
        // before any length rule runs.
        assertFieldError("Buchungstext", "🙂",
                DatevValidationError.Code.UNMAPPABLE_CHARACTER);
    }

    @Test
    void validatesHighValueEnumsCurrencyAndCountryCodes() {
        assertNoFieldErrors("Soll/Haben-Kennzeichen", "S");
        assertNoFieldErrors("WKZ Umsatz", "EUR");
        assertNoFieldErrors("Land", "DE");
        assertNoFieldErrors("Land", "EL");
        assertNoFieldErrors("Land", "XI");
        assertFieldError("Soll/Haben-Kennzeichen", "s",
                DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("WKZ Umsatz", "ZZZ", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Land", "XX", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Festschreibung", "2", DatevValidationError.Code.INVALID_FORMAT);
        assertFieldError("Buchungstyp", "XX", DatevValidationError.Code.INVALID_FORMAT);
    }

    @Test
    void strictModeCollectsCheckerCompatibleFieldDependencies() {
        DatevHeader header = DatevHeader.current();
        List<String> row = validStrictRow(header);
        set(header, row, "Basis-Umsatz", "10,00");
        set(header, row, "Beleginfo - Art 1", "Bank");
        set(header, row, "Zusatzinformation- Inhalt 1", "Value");

        List<DatevValidationError> errors = DatevRowValidator.validate(
                header, row, DatevValidationMode.STRICT, null);

        assertEquals(3, errors.size());
        assertEquals(Set.of(
                        "WKZ Basis-Umsatz",
                        "Beleginfo - Inhalt 1",
                        "Zusatzinformation - Art 1"
                ),
                errors.stream().map(DatevValidationError::canonicalKey).collect(Collectors.toSet()));
        assertTrue(errors.stream().allMatch(
                error -> error.code() == DatevValidationError.Code.DEPENDENT_FIELD_MISSING));
    }

    @Test
    void strictModeAcceptsCompleteDocumentedFieldPairs() {
        DatevHeader header = DatevHeader.current();
        List<String> row = validStrictRow(header);
        set(header, row, "Basis-Umsatz", "10,00");
        set(header, row, "WKZ Basis-Umsatz", "EUR");
        set(header, row, "Beleginfo - Art 1", "Bank");
        set(header, row, "Beleginfo - Inhalt 1", "Value");
        set(header, row, "Zusatzinformation - Art 1", "Note");
        set(header, row, "Zusatzinformation- Inhalt 1", "Value");
        set(header, row, "Geschäftspartnerbank", "001");
        set(header, row, "SEPA-Mandatsreferenz", "mandate");
        set(header, row, "Leistungsdatum", "01012024");
        set(header, row, "Datum Zuord. Steuerperiode", "01012024");

        assertTrue(DatevRowValidator.validate(
                header, row, DatevValidationMode.STRICT, null).isEmpty());
    }

    @Test
    void strictModeAcceptsDependencyExceptionsPresentInOfficialSample() {
        DatevHeader header = DatevHeader.current();
        List<String> row = validStrictRow(header);
        set(header, row, "Geschäftspartnerbank", "1");
        set(header, row, "Leistungsdatum", "10052018");

        assertTrue(DatevRowValidator.validate(
                header, row, DatevValidationMode.STRICT, null).isEmpty());
    }

    @Test
    void officialSemanticsSurviveRenameAndReorder() {
        List<String> reverseOrder = new ArrayList<>(DatevHeader.current().keys());
        Collections.reverse(reverseOrder);
        DatevHeader configured = DatevHeader.current()
                .renamed("Soll/Haben-Kennzeichen", "Side")
                .reordered(reverseOrder);
        List<String> row = emptyRow(configured);
        set(configured, row, "Soll/Haben-Kennzeichen", "X");

        List<DatevValidationError> errors = DatevRowValidator.validate(
                configured, row, DatevValidationMode.FIELD_LEVEL, null);

        assertEquals(1, errors.size());
        assertEquals("Soll/Haben-Kennzeichen", errors.get(0).canonicalKey());
        assertEquals(2, errors.get(0).fieldNumber());
    }

    @Test
    void aggregateExceptionExposesAnImmutableErrorList() {
        DatevHeader header = DatevHeader.current();
        List<String> row = emptyRow(header);
        set(header, row, "Soll/Haben-Kennzeichen", "X");
        set(header, row, "WKZ Umsatz", "ZZZ");

        DatevValidationException exception = assertThrows(
                DatevValidationException.class,
                () -> DatevRowValidator.validateOrThrow(
                        header, row, DatevValidationMode.FIELD_LEVEL, null)
        );

        assertEquals(2, exception.errors().size());
        assertTrue(exception.getMessage().contains("2 validation errors"));
        assertThrows(UnsupportedOperationException.class, () -> exception.errors().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new DatevValidationException(List.of()));
    }

    @Test
    void errorsRemainInOfficialFieldOrderAfterDependencyChecks() {
        DatevHeader header = DatevHeader.current();
        List<String> row = validStrictRow(header);
        set(header, row, "Basis-Umsatz", "10,00");
        set(header, row, "Abw. Skontokonto", "invalid");

        List<DatevValidationError> errors = DatevRowValidator.validate(
                header, row, DatevValidationMode.STRICT, null);

        assertEquals(List.of(6, 125),
                errors.stream().map(DatevValidationError::fieldNumber).toList());
    }

    @Test
    void rejectsStructuralApiMisuseAndControlCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> DatevRowValidator.validate(List.of("A"), List.of(),
                        DatevValidationMode.FIELD_LEVEL));
        assertThrows(IllegalArgumentException.class,
                () -> DatevRowValidator.validate(List.of("A", "A"), List.of("1", "2"),
                        DatevValidationMode.FIELD_LEVEL));
        assertFieldError("Buchungstext", "line\nbreak",
                DatevValidationError.Code.INVALID_FORMAT);
    }

    private static void assertNoFieldErrors(String key, String value) {
        assertNoFieldErrors(key, value, null);
    }

    private static void assertNoFieldErrors(String key, String value, DatevMetadata metadata) {
        DatevHeader header = DatevHeader.current();
        List<String> row = emptyRow(header);
        set(header, row, key, value);

        assertDoesNotThrow(() -> DatevRowValidator.validateOrThrow(
                header, row, DatevValidationMode.FIELD_LEVEL, metadata));
    }

    private static void assertFieldError(
            String key,
            String value,
            DatevValidationError.Code expectedCode
    ) {
        assertFieldError(key, value, null, expectedCode);
    }

    private static void assertFieldError(
            String key,
            String value,
            DatevMetadata metadata,
            DatevValidationError.Code expectedCode
    ) {
        DatevHeader header = DatevHeader.current();
        List<String> row = emptyRow(header);
        set(header, row, key, value);

        List<DatevValidationError> errors = DatevRowValidator.validate(
                header, row, DatevValidationMode.FIELD_LEVEL, metadata);

        assertFalse(errors.isEmpty(), () -> "Expected error for " + key + " value " + value);
        assertTrue(errors.stream().anyMatch(error -> error.code() == expectedCode),
                () -> "Expected " + expectedCode + " but got " + errors);
    }

    private static List<String> validStrictRow(DatevHeader header) {
        List<String> row = emptyRow(header);
        set(header, row, "Umsatz (ohne Soll/Haben-Kz)", "100,00");
        set(header, row, "Soll/Haben-Kennzeichen", "S");
        set(header, row, "Konto", "1000");
        set(header, row, "Gegenkonto (ohne BU-Schlüssel)", "8400");
        set(header, row, "Belegdatum", "0101");
        return row;
    }

    private static List<String> emptyRow(DatevHeader header) {
        return new ArrayList<>(Collections.nCopies(header.size(), null));
    }

    private static void set(DatevHeader header, List<String> row, String canonicalKey, String value) {
        int index = header.keys().indexOf(canonicalKey);
        if (index < 0) {
            throw new AssertionError("Unknown test field: " + canonicalKey);
        }
        row.set(index, value);
    }

    private static DatevMetadata metadata(LocalDate periodStart, LocalDate periodEnd, int accountLength) {
        return DatevMetadata.bookingBatchV13()
                .createdAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                .advisorNumber(1001)
                .clientNumber(1)
                .fiscalYearStart(LocalDate.of(2024, 1, 1))
                .accountLength(accountLength)
                .period(periodStart, periodEnd)
                .build();
    }
}
