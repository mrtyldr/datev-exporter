package io.github.mrtyldr.datev.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the canonical key indexing of {@link DatevRowValidation} and the fused Windows-1252 scan
 * against regressions in the error contract they must preserve.
 *
 * <p>Rows validated through the header list of a {@link DatevSchema} or through a
 * {@link DatevHeader} reuse the immutable index map that owner built once. Rows validated through
 * any other key list are re-indexed on every call, which is what keeps null and duplicate keys
 * rejected.
 */
class DatevRowValidationIndexTest {

    /** U+0001, an ISO control character. */
    private static final String CONTROL = String.valueOf((char) 0x01);

    /** U+2028, a line separator that is not an ISO control character. */
    private static final String LINE_SEPARATOR = String.valueOf((char) 0x2028);

    /** A supplementary code point outside the Windows-1252 profile. */
    private static final String UNMAPPABLE = "🙂";

    @Test
    void duplicateAndNullKeysAreRejectedInEveryModeAndOnEveryCall() {
        for (DatevValidationMode mode : DatevValidationMode.values()) {
            for (int attempt = 0; attempt < 3; attempt++) {
                assertThrows(IllegalArgumentException.class,
                        () -> DatevRowValidation.validate(List.of("Konto", "Konto"),
                                List.of("1000", "1001"), mode,
                                DatevValidationContext.empty(), false));
                assertThrows(NullPointerException.class,
                        () -> DatevRowValidation.validate(Arrays.asList("Konto", null),
                                Arrays.asList("1000", "1001"), mode,
                                DatevValidationContext.empty(), false));
            }
        }
    }

    @Test
    void aMutableKeyListIsReindexedAfterItChanges() {
        List<String> keys = new ArrayList<>(DatevSchema.CURRENT_V13.headers());
        List<String> values = new ArrayList<>(Collections.nCopies(keys.size(), null));

        assertTrue(DatevRowValidation.validate(keys, values, DatevValidationMode.FIELD_LEVEL,
                DatevValidationContext.empty(), true).isEmpty());

        keys.set(1, keys.get(0));

        assertThrows(IllegalArgumentException.class,
                () -> DatevRowValidation.validate(keys, values, DatevValidationMode.FIELD_LEVEL,
                        DatevValidationContext.empty(), true));
    }

    @Test
    void theOfficialHeaderListIsRecognizedByIdentityAndReusesTheSchemaIndexMap() {
        for (DatevSchema schema : DatevSchema.values()) {
            // The whole fast path rests on this: one list instance per version, for the JVM's life.
            assertSame(schema.headers(), schema.headers());
            assertSame(schema.headers(), schema == DatevSchema.CURRENT_V13
                    ? DatevFieldSpecs.headers13() : DatevFieldSpecs.headers12());
            assertTrue(DatevFieldSpecs.isOfficialSchema(schema.headers()));

            // A copy is a different instance, so it takes the re-indexing path and must agree.
            List<String> copy = new ArrayList<>(schema.headers());
            List<String> values = dependencyBreakingRow(schema);
            for (DatevValidationMode mode : DatevValidationMode.values()) {
                assertEquals(
                        DatevRowValidation.validate(copy, values, mode,
                                DatevValidationContext.empty(), true),
                        DatevRowValidation.validate(schema.headers(), values, mode,
                                DatevValidationContext.empty(), true),
                        () -> "Schema path diverged for " + schema + " in mode " + mode);
            }
        }
    }

    @Test
    void theSchemaHeaderListStillCarriesTheFieldSpecificationNamesInOrder() {
        for (DatevSchema schema : DatevSchema.values()) {
            assertEquals(
                    schema.fieldSpecs().stream().map(DatevFieldSpec::canonicalKey).toList(),
                    schema.headers(),
                    () -> "Heading order drifted from the field specifications for " + schema);
            assertEquals(schema.columnCount(), schema.headers().size());
        }
    }

    @Test
    void modeNoneReturnsNoErrorsOnBothTheIndexedAndTheKeyListPath() {
        DatevSchema schema = DatevSchema.CURRENT_V13;
        List<String> broken = new ArrayList<>(Collections.nCopies(schema.columnCount(), CONTROL));

        assertTrue(DatevRowValidation.validate(schema.headers(), broken,
                DatevValidationMode.NONE, DatevValidationContext.empty(), true).isEmpty());
        assertTrue(DatevRowValidation.validate(new ArrayList<>(schema.headers()), broken,
                DatevValidationMode.NONE, DatevValidationContext.empty(), true).isEmpty());
        assertTrue(DatevRowValidator.validate(DatevHeader.current(), broken,
                DatevValidationMode.NONE, null).isEmpty());

        // The width check still runs before the mode check on every path.
        assertThrows(IllegalArgumentException.class,
                () -> DatevRowValidation.validate(schema.headers(), List.of("1000"),
                        DatevValidationMode.NONE, DatevValidationContext.empty(), true));
        assertThrows(IllegalArgumentException.class,
                () -> DatevRowValidator.validate(DatevHeader.current(), List.of("1000"),
                        DatevValidationMode.NONE, null));
    }

    @Test
    void theHeaderIndexMapProducesExactlyTheSameErrorsAsAFreshlyIndexedKeyList() {
        DatevHeader header = DatevHeader.current();
        List<String> values = dependencyBreakingRow(DatevSchema.CURRENT_V13);

        for (DatevValidationMode mode : DatevValidationMode.values()) {
            assertEquals(
                    DatevRowValidation.validate(header.keys(), values, mode,
                            DatevValidationContext.empty(), true),
                    DatevRowValidator.validate(header, values, mode, null),
                    () -> "Header path diverged in mode " + mode);
        }

        // A renamed official header keeps its schema identity and its index map stays aligned.
        DatevHeader renamed = header.renamed("Konto", "Sachkonto");
        assertEquals(
                DatevRowValidator.validate(header, values, DatevValidationMode.STRICT, null),
                DatevRowValidator.validate(renamed, values, DatevValidationMode.STRICT, null));
    }

    @Test
    void aReorderedOfficialHeaderKeepsStrictDependencyIndexesAligned() {
        DatevHeader official = DatevHeader.current();
        List<String> reverseOrder = new ArrayList<>(official.keys());
        Collections.reverse(reverseOrder);
        DatevHeader reordered = official.reordered(reverseOrder);

        List<String> officialValues = dependencyBreakingRow(DatevSchema.CURRENT_V13);
        List<String> reorderedValues = new ArrayList<>(official.size());
        for (String key : reordered.keys()) {
            reorderedValues.add(officialValues.get(official.indexOf(key)));
        }

        List<DatevValidationError> freshlyIndexed = DatevRowValidator.validate(
                new ArrayList<>(reordered.keys()), reorderedValues,
                DatevValidationMode.STRICT, null);
        List<DatevValidationError> headerIndexed = DatevRowValidator.validate(
                reordered, reorderedValues, DatevValidationMode.STRICT, null);

        assertEquals(1, freshlyIndexed.size(), () -> "Unexpected errors: " + freshlyIndexed);
        assertEquals(DatevValidationError.Code.DEPENDENT_FIELD_MISSING,
                freshlyIndexed.get(0).code());
        assertEquals("WKZ Basis-Umsatz", freshlyIndexed.get(0).canonicalKey());
        assertEquals(freshlyIndexed, headerIndexed);
    }

    @Test
    void aCustomHeaderIsStillValidatedAgainstItsOwnIndexMap() {
        DatevHeader custom = DatevHeader.of(List.of("Konto", "Basis-Umsatz", "Freitext"));
        List<DatevValidationError> errors = DatevRowValidator.validate(
                custom, Arrays.asList("0", "10,00", "anything"),
                DatevValidationMode.STRICT, null);

        // A custom header is not an official schema, so only field-level rules apply: the zero
        // account is reported, the missing "WKZ Basis-Umsatz" pair partner is not.
        assertEquals(1, errors.size(), () -> "Unexpected errors: " + errors);
        assertEquals(DatevValidationError.Code.VALUE_OUT_OF_RANGE, errors.get(0).code());
    }

    @Test
    void theSharedSchemaIndexMapStaysCorrectUnderConcurrentValidation() throws Exception {
        List<String> v13Values = dependencyBreakingRow(DatevSchema.CURRENT_V13);
        List<String> v12Values = dependencyBreakingRow(DatevSchema.LEGACY_V12);
        List<DatevValidationError> expectedV13 = strictErrors(DatevSchema.CURRENT_V13, v13Values);
        List<DatevValidationError> expectedV12 = strictErrors(DatevSchema.LEGACY_V12, v12Values);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                boolean current = thread % 2 == 0;
                DatevSchema schema = current
                        ? DatevSchema.CURRENT_V13 : DatevSchema.LEGACY_V12;
                List<String> values = current ? v13Values : v12Values;
                List<DatevValidationError> expected = current ? expectedV13 : expectedV12;
                tasks.add(() -> {
                    for (int round = 0; round < 500; round++) {
                        if (!strictErrors(schema, values).equals(expected)) {
                            return false;
                        }
                    }
                    return true;
                });
            }
            for (Future<Boolean> result : pool.invokeAll(tasks)) {
                assertTrue(result.get());
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }

        assertEquals(1, expectedV13.size());
        assertEquals(expectedV13, expectedV12);
        assertEquals(DatevValidationError.Code.DEPENDENT_FIELD_MISSING, expectedV13.get(0).code());
    }

    @Test
    void controlCharactersAreReportedBeforeUnmappableOnesRegardlessOfPosition() {
        assertSingleErrorCode(CONTROL + UNMAPPABLE, DatevValidationError.Code.INVALID_FORMAT);
        assertSingleErrorCode(UNMAPPABLE + CONTROL, DatevValidationError.Code.INVALID_FORMAT);
        assertSingleErrorCode(UNMAPPABLE + LINE_SEPARATOR,
                DatevValidationError.Code.INVALID_FORMAT);
        assertSingleErrorCode(UNMAPPABLE, DatevValidationError.Code.UNMAPPABLE_CHARACTER);
        // An unpaired surrogate is unmappable, exactly as CharsetEncoder.canEncode reports it.
        assertSingleErrorCode("\uD83D", DatevValidationError.Code.UNMAPPABLE_CHARACTER);
    }

    @Test
    void theOfficialWindows1252ProfileIsAcceptedUnchanged() {
        assertTrue(DatevRowValidation.validate(
                List.of("Buchungstext"),
                List.of("Müller & Co. – 100 € „Test“ š ž Œ"),
                DatevValidationMode.FIELD_LEVEL,
                DatevValidationContext.empty(),
                false).isEmpty());
    }

    private static void assertSingleErrorCode(String value, DatevValidationError.Code expected) {
        List<DatevValidationError> errors = DatevRowValidation.validate(
                List.of("Buchungstext"),
                List.of(value),
                DatevValidationMode.FIELD_LEVEL,
                DatevValidationContext.empty(),
                false);

        assertEquals(1, errors.size(), () -> "Unexpected errors: " + errors);
        assertEquals(expected, errors.get(0).code());
    }

    private static List<DatevValidationError> strictErrors(
            DatevSchema schema,
            List<String> values
    ) {
        return DatevRowValidation.validate(schema.headers(), values,
                DatevValidationMode.STRICT, DatevValidationContext.empty(), true);
    }

    /** A strictly valid row whose only defect is a documented cross-field dependency. */
    private static List<String> dependencyBreakingRow(DatevSchema schema) {
        List<String> headers = schema.headers();
        List<String> row = new ArrayList<>(Collections.nCopies(schema.columnCount(), null));
        row.set(headers.indexOf("Umsatz (ohne Soll/Haben-Kz)"), "100,00");
        row.set(headers.indexOf("Soll/Haben-Kennzeichen"), "S");
        row.set(headers.indexOf("Konto"), "1000");
        row.set(headers.indexOf("Gegenkonto (ohne BU-Schlüssel)"), "8400");
        row.set(headers.indexOf("Belegdatum"), "0101");
        // Leaves "WKZ Basis-Umsatz" empty, so exactly one dependency error is expected.
        row.set(headers.indexOf("Basis-Umsatz"), "10,00");
        return Collections.unmodifiableList(row);
    }
}
