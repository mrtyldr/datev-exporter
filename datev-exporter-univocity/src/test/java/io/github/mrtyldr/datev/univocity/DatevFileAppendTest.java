package io.github.mrtyldr.datev.univocity;

import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevSchema;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevFileAppendTest {
    @Test
    void factoriesSelectFixedSchemasAndOptionalValidatorExplicitly() {
        BiConsumer<Integer, List<String>> validator = (formatVersion, row) -> { };

        assertSame(DatevSchema.CURRENT_V13, DatevFile.withDefaults().schema());
        assertSame(DatevSchema.LEGACY_V12, DatevFile.legacyV12().schema());
        assertSame(
                DatevSchema.LEGACY_V12,
                DatevFile.forSchema(DatevSchema.LEGACY_V12).schema()
        );
        assertTrue(DatevFile.withDefaults(validator).validator().isPresent());
        assertSame(validator, DatevFile.legacyV12(validator).validator().orElseThrow());
        assertTrue(DatevFile.withDefaults().validator().isEmpty());
        assertThrows(NullPointerException.class, () -> DatevFile.forSchema(null));
        assertThrows(NullPointerException.class, () -> DatevFile.withDefaults(null));
    }

    @Test
    void positionalOverloadsCopyAndConvertCompleteRows() {
        DatevFile file = DatevFile.withDefaults();
        String[] array = emptyArray(file.schema());
        array[0] = "1,00";
        List<String> collection = emptyRow(file.schema());
        collection.set(6, "1000");
        Object[] objects = new Object[file.schema().columnCount()];
        objects[6] = 2000;

        file.append(array);
        file.append(collection);
        file.appendValues(objects);
        array[0] = "changed";
        collection.set(6, "changed");

        assertEquals("1,00", file.rows().get(0).get(0));
        assertEquals("1000", file.rows().get(1).get(6));
        assertEquals("2000", file.rows().get(2).get(6));
        assertNull(file.rows().get(2).get(0));
        assertThrows(IllegalArgumentException.class, () -> file.append(List.of("too short")));
        assertEquals(3, file.rowCount());
    }

    @Test
    void strictStringAppendParsesQuotesEscapesCommentsAndTrailingEmpties() {
        DatevFile file = DatevFile.withDefaults();
        List<String> encoded = new ArrayList<>(
                Collections.nCopies(file.schema().columnCount(), "")
        );
        encoded.set(0, "#not-a-comment");
        encoded.set(1, "\"two;inner\"");
        encoded.set(2, "\"a\"\"b\"");

        file.append(String.join(";", encoded));

        assertEquals("#not-a-comment", file.rows().get(0).get(0));
        assertEquals("two;inner", file.rows().get(0).get(1));
        assertEquals("a\"b", file.rows().get(0).get(2));
        assertEquals("", file.rows().get(0).get(124));
    }

    @Test
    void strictStringAppendRejectsMalformedOrMultilineRowsAtomically() {
        DatevFile file = DatevFile.withDefaults();

        for (String invalid : List.of(
                "a;\"unterminated",
                "a;\"b\"\"",
                "a;b\"c",
                "a;\"b\"suffix",
                "a;b\n",
                "a;b\r",
                "a;b\t",
                "a;b\u2028",
                "a;b\u2029"
        )) {
            assertThrows(IllegalArgumentException.class, () -> file.append(invalid));
        }

        assertEquals(0, file.rowCount());
    }

    @Test
    void mapRowsAreSparseExactAndIndependentOfIterationOrder() {
        DatevFile file = DatevFile.withDefaults();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("Konto", 1000);
        values.put("Umsatz (ohne Soll/Haben-Kz)", "12,50");
        values.put("Soll/Haben-Kennzeichen", null);

        file.append(values);

        assertEquals("12,50", file.rows().get(0).get(0));
        assertNull(file.rows().get(0).get(1));
        assertEquals("1000", file.rows().get(0).get(6));
        assertThrows(IllegalArgumentException.class, () -> file.append(Map.of("unknown", "x")));
        assertEquals(1, file.rowCount());
    }

    @Test
    void formattedColumnsResolveBeforeFormattingAndCommitOnce() {
        DatevFile file = DatevFile.withDefaults();
        AtomicInteger invocations = new AtomicInteger();
        DatevColumn<Integer> amount = DatevColumn.formatted(
                "Umsatz (ohne Soll/Haben-Kz)",
                1,
                value -> {
                    invocations.incrementAndGet();
                    return "1,00";
                }
        );

        file.append(
                DatevColumn.account("Konto", 1000),
                amount,
                DatevColumn.of("Soll/Haben-Kennzeichen", "S")
        );
        file.toCsvString();
        file.toCsvString();

        assertEquals(List.of("1,00", "S"), file.rows().get(0).subList(0, 2));
        assertEquals("1000", file.rows().get(0).get(6));
        assertEquals(1, invocations.get());

        DatevColumn<Integer> first = DatevColumn.formatted("Konto", 1, value -> {
            invocations.incrementAndGet();
            return value.toString();
        });
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(first, DatevColumn.of("Konto", 2))
        );
        assertEquals(1, invocations.get());
        assertEquals(1, file.rowCount());
    }

    @Test
    void collectionAndIterableColumnOverloadsAlignSparseRows() {
        DatevFile collectionFile = DatevFile.withDefaults();
        DatevFile iterableFile = DatevFile.withDefaults();
        List<DatevColumn<?>> columns = List.of(
                DatevColumn.of("Konto", 1000),
                DatevColumn.amount("Umsatz (ohne Soll/Haben-Kz)", new BigDecimal("2.00"))
        );

        collectionFile.appendColumns(columns);
        iterableFile.append((Iterable<? extends DatevColumn<?>>) columns);

        assertEquals(collectionFile.rows(), iterableFile.rows());
    }

    @Test
    void validatorReceivesImmutableAlignedRowAndFailureIsAtomic() {
        AtomicReference<Integer> observedFormatVersion = new AtomicReference<>();
        AtomicReference<List<String>> observedRow = new AtomicReference<>();
        DatevFile file = DatevFile.withDefaults((formatVersion, row) -> {
            observedFormatVersion.set(formatVersion);
            observedRow.set(row);
            if (row.get(6) == null) {
                throw new IllegalArgumentException("Konto is required");
            }
        });

        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(Map.of("Umsatz (ohne Soll/Haben-Kz)", "1,00"))
        );
        assertEquals(0, file.rowCount());
        assertThrows(UnsupportedOperationException.class, () -> observedRow.get().set(0, "changed"));

        file.append(Map.of("Konto", "1000"));
        assertEquals(13, observedFormatVersion.get());
        assertEquals("1000", observedRow.get().get(6));
        assertEquals(1, file.rowCount());
    }

    @Test
    void validatorReceivesLegacyNumericFormatVersion() {
        AtomicReference<Integer> observedFormatVersion = new AtomicReference<>();
        DatevFile file = DatevFile.legacyV12(
                (formatVersion, row) -> observedFormatVersion.set(formatVersion)
        );

        file.append(emptyArray(file.schema()));

        assertEquals(12, observedFormatVersion.get());
    }

    @Test
    void rejectsControlCharactersInEveryAppendShape() {
        DatevFile file = DatevFile.withDefaults();
        String[] array = emptyArray(file.schema());
        array[0] = "a\tb";

        assertThrows(IllegalArgumentException.class, () -> file.append(array));
        assertThrows(IllegalArgumentException.class, () -> file.append(Map.of("Konto", "a\rb")));
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(DatevColumn.of("Konto", "a\nb"))
        );
        assertEquals(0, file.rowCount());
    }

    @Test
    void rejectsValuesOutsideWindows1252InEveryAppendShapeAtomically() {
        DatevFile file = DatevFile.withDefaults();
        String[] array = emptyArray(file.schema());
        array[0] = "🙂";
        List<String> encoded = new ArrayList<>(
                Collections.nCopies(file.schema().columnCount(), "")
        );
        encoded.set(0, "🙂");

        assertThrows(IllegalArgumentException.class, () -> file.append(array));
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(Map.of("Buchungstext", "🙂"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(DatevColumn.of("Buchungstext", "🙂"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(String.join(";", encoded))
        );

        assertEquals(0, file.rowCount());
    }

    @Test
    void rowAndIteratorSnapshotsCannotMutateOrObserveLaterAppends() {
        DatevFile file = DatevFile.withDefaults();
        file.append(emptyArray(file.schema()));
        List<List<String>> snapshot = file.rows();
        Iterator<List<String>> iterator = file.iterator();
        file.append(emptyArray(file.schema()));

        assertEquals(1, snapshot.size());
        assertTrue(iterator.hasNext());
        iterator.next();
        assertFalse(iterator.hasNext());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(List.of()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get(0).set(0, "x"));
        assertEquals(2, file.rowCount());
    }

    @Test
    void enforcesDatevBookingRowLimitBoundary() {
        DatevFile.ensureCanAppendRow(DatevFile.MAX_DATA_ROWS - 1);
        assertThrows(IllegalStateException.class, () -> DatevFile.ensureCanAppendRow(99_999));
        assertThrows(IllegalArgumentException.class, () -> DatevFile.ensureCanAppendRow(-1));
    }

    private static List<String> emptyRow(DatevSchema schema) {
        return new ArrayList<>(Collections.nCopies(schema.columnCount(), null));
    }

    private static String[] emptyArray(DatevSchema schema) {
        return new String[schema.columnCount()];
    }
}
