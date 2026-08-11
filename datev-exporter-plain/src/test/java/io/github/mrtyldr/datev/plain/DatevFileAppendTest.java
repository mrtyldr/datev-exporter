package io.github.mrtyldr.datev.plain;

import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevSchema;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevFileAppendTest {

    @Test
    void factoriesSelectFixedSchemasAndRequireExplicitValidators() {
        DatevFile unvalidated = DatevFile.withDefaults();
        BiConsumer<Integer, List<String>> validator = (formatVersion, values) -> { };
        DatevFile validated = DatevFile.withDefaults(validator);
        DatevFile validatedLegacy = DatevFile.legacyV12(validator);
        DatevFile validatedBySchema = DatevFile.forSchema(DatevSchema.LEGACY_V12, validator);

        assertSame(DatevSchema.CURRENT_V13, unvalidated.schema());
        assertTrue(unvalidated.validator().isEmpty());
        assertSame(validator, validated.validator().orElseThrow());
        assertSame(validator, validatedLegacy.validator().orElseThrow());
        assertSame(validator, validatedBySchema.validator().orElseThrow());
        assertSame(DatevSchema.LEGACY_V12, DatevFile.legacyV12().schema());
        assertSame(
                DatevSchema.LEGACY_V12,
                DatevFile.forSchema(DatevSchema.LEGACY_V12).schema()
        );
        assertThrows(NullPointerException.class, () -> DatevFile.forSchema(null));
        assertThrows(
                NullPointerException.class,
                () -> DatevFile.withDefaults((BiConsumer<Integer, List<String>>) null)
        );
    }

    @Test
    void appendsArraysCollectionsAndObjectsUsingDefensiveCopies() {
        DatevFile file = DatevFile.withDefaults();
        String[] array = fullRow(DatevSchema.CURRENT_V13, null);
        array[0] = "1,00";
        List<String> collection = new ArrayList<>(Arrays.asList(
                fullRow(DatevSchema.CURRENT_V13, null)
        ));
        collection.set(6, "1000");
        Object[] objects = new Object[DatevSchema.CURRENT_V13.columnCount()];
        objects[0] = new BigDecimal("12.50");
        objects[9] = 1108;

        file.append(array);
        file.append(collection);
        file.appendValues(objects);
        array[0] = "changed";
        collection.set(6, "changed");

        assertEquals("1,00", file.rows().get(0).get(0));
        assertEquals("1000", file.rows().get(1).get(6));
        assertEquals("12.50", file.rows().get(2).get(0));
        assertEquals("1108", file.rows().get(2).get(9));
    }

    @Test
    void rejectsWrongPositionalWidthWithoutConvertingOrChangingState() {
        DatevFile file = DatevFile.withDefaults();
        AtomicInteger conversions = new AtomicInteger();
        Object value = new Object() {
            @Override
            public String toString() {
                conversions.incrementAndGet();
                return "x";
            }
        };

        assertThrows(IllegalArgumentException.class, () -> file.append(new String[]{"x"}));
        assertThrows(IllegalArgumentException.class, () -> file.append(List.of("x")));
        assertThrows(IllegalArgumentException.class, () -> file.appendValues(value));

        assertEquals(0, conversions.get());
        assertTrue(file.isEmpty());
    }

    @Test
    void strictStringParserSupportsCsvQuotesAndTrailingEmptyCells() {
        DatevFile file = DatevFile.legacyV12();
        String[] values = fullRow(DatevSchema.LEGACY_V12, "");
        values[0] = "one";
        values[1] = "two;inner";
        values[2] = "a\"b";
        values[123] = "";
        String row = "one;\"two;inner\";\"a\"\"b\";"
                + ";".repeat(DatevSchema.LEGACY_V12.columnCount() - 4);

        file.append(row);

        assertEquals(Arrays.asList(values), file.rows().get(0));
    }

    @Test
    void strictStringParserTreatsCommentMarkersAsOrdinaryData() {
        DatevFile file = DatevFile.withDefaults();

        file.append("#value" + ";".repeat(DatevSchema.CURRENT_V13.columnCount() - 1));

        assertEquals("#value", file.rows().get(0).get(0));
    }

    @Test
    void strictStringParserRejectsMalformedQuotesAndMultilineInputAtomically() {
        DatevFile file = DatevFile.withDefaults();
        String padding = ";".repeat(DatevSchema.CURRENT_V13.columnCount() - 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> file.append("a;\"unterminated" + padding)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append("a;b\"c" + padding)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append("a;\"b\"suffix" + padding)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append("a;b\n" + padding)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append("a;\"b\"\"" + padding)
        );

        assertTrue(file.isEmpty());
    }

    @Test
    void sparseMapUsesOnlyExactFixedHeadingNames() {
        DatevFile file = DatevFile.withDefaults();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("Buchungstext", "Invoice 42");
        values.put("Konto", 1000);
        values.put("Umsatz (ohne Soll/Haben-Kz)", null);

        file.append(values);

        assertNull(file.rows().get(0).get(0));
        assertEquals("1000", file.rows().get(0).get(6));
        assertEquals("Invoice 42", file.rows().get(0).get(13));
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(Map.of("konto", "1000"))
        );
        Map<String, Object> nullKey = new HashMap<>();
        nullKey.put(null, "x");
        assertThrows(NullPointerException.class, () -> file.append(nullKey));
        assertEquals(1, file.rowCount());
    }

    @Test
    void formattedColumnsAlignByHeadingAcrossAllOverloads() {
        DatevFile file = DatevFile.withDefaults();

        file.append(
                DatevColumn.account("Konto", 1000),
                DatevColumn.amount(
                        "Umsatz (ohne Soll/Haben-Kz)",
                        new BigDecimal("12.50")
                ),
                DatevColumn.documentDate(LocalDate.of(2026, 8, 11))
        );
        List<DatevColumn<?>> iterable = List.of(
                DatevColumn.of("Buchungstext", "Second"),
                DatevColumn.account("Konto", 2000)
        );
        file.append(iterable);
        file.appendColumns(List.of(DatevColumn.of("Soll/Haben-Kennzeichen", "S")));

        assertEquals("12,50", file.rows().get(0).get(0));
        assertEquals("1000", file.rows().get(0).get(6));
        assertEquals("1108", file.rows().get(0).get(9));
        assertEquals("2000", file.rows().get(1).get(6));
        assertEquals("Second", file.rows().get(1).get(13));
        assertEquals("S", file.rows().get(2).get(1));
    }

    @Test
    void detectsStructuralColumnErrorsBeforeFormattingAndKeepsAppendAtomic() {
        DatevFile file = DatevFile.withDefaults();
        AtomicInteger formatterCalls = new AtomicInteger();
        DatevColumn<Integer> first = DatevColumn.formatted("Konto", 1000, value -> {
            formatterCalls.incrementAndGet();
            return value.toString();
        });

        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(first, DatevColumn.of("Konto", "2000"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(first, DatevColumn.of("Not a heading", "x"))
        );
        assertThrows(
                NullPointerException.class,
                () -> file.appendColumns(Arrays.asList(first, null))
        );

        assertEquals(0, formatterCalls.get());
        assertTrue(file.isEmpty());
    }

    @Test
    void formatterRunsExactlyOnceDuringAppendAndNeverDuringWriting() {
        DatevFile file = DatevFile.withDefaults();
        AtomicInteger calls = new AtomicInteger();

        file.append(DatevColumn.formatted("Konto", 1000, value -> {
            calls.incrementAndGet();
            return value.toString();
        }));
        file.toCsvString();
        file.toByteArray();

        assertEquals(1, calls.get());
    }

    @Test
    void rejectsControlsAndUnicodeLineSeparatorsInEveryAppendShape() {
        DatevFile file = DatevFile.withDefaults();
        String[] positional = fullRow(DatevSchema.CURRENT_V13, null);
        positional[0] = "a\tb";

        assertThrows(IllegalArgumentException.class, () -> file.append(positional));
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(Map.of("Konto", "a\rb"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(DatevColumn.of("Konto", "a\nb"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(Map.of("Konto", "a\u2028b"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(Map.of("Konto", "a\u2029b"))
        );
        assertTrue(file.isEmpty());
    }

    @Test
    void rejectsWindows1252IncompatibleValuesAtomicallyAcrossEveryAppendShape() {
        DatevFile file = DatevFile.withDefaults();
        String[] positional = fullRow(DatevSchema.CURRENT_V13, null);
        positional[0] = "🙂";
        Object[] objects = new Object[DatevSchema.CURRENT_V13.columnCount()];
        objects[0] = "🙂";
        List<DatevColumn<?>> iterable = List.of(DatevColumn.of("Konto", "🙂"));

        assertThrows(
                IllegalArgumentException.class,
                () -> file.append("🙂" + ";".repeat(DatevSchema.CURRENT_V13.columnCount() - 1))
        );
        assertThrows(IllegalArgumentException.class, () -> file.append(positional));
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(new ArrayList<>(Arrays.asList(positional)))
        );
        assertThrows(IllegalArgumentException.class, () -> file.appendValues(objects));
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(Map.of("Buchungstext", "🙂"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(DatevColumn.of("Konto", "🙂"))
        );
        assertThrows(IllegalArgumentException.class, () -> file.append(iterable));
        assertThrows(IllegalArgumentException.class, () -> file.appendColumns(iterable));

        assertTrue(file.isEmpty());
    }

    @Test
    void explicitValidatorSeesImmutableAlignedRowAndFailureIsAtomic() {
        AtomicInteger calls = new AtomicInteger();
        BiConsumer<Integer, List<String>> validator = (formatVersion, row) -> {
            assertEquals(13, formatVersion);
            assertEquals(DatevSchema.CURRENT_V13.columnCount(), row.size());
            assertThrows(UnsupportedOperationException.class, () -> row.set(0, "changed"));
            calls.incrementAndGet();
            if ("reject".equals(row.get(13))) {
                throw new IllegalArgumentException("rejected");
            }
        };
        DatevFile file = DatevFile.withDefaults(validator);

        file.append(Map.of("Buchungstext", "accept"));
        assertThrows(
                IllegalArgumentException.class,
                () -> file.append(Map.of("Buchungstext", "reject"))
        );

        assertEquals(2, calls.get());
        assertEquals(1, file.rowCount());
        assertEquals("accept", file.rows().get(0).get(13));
    }

    @Test
    void snapshotsRowsAndIteratorState() {
        DatevFile file = DatevFile.withDefaults();
        file.append(Map.of("Konto", "1000"));
        List<List<String>> snapshot = file.rows();
        Iterator<List<String>> iterator = file.iterator();
        file.append(Map.of("Konto", "2000"));

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(List.of()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.get(0).set(6, "changed")
        );
        assertTrue(iterator.hasNext());
        assertEquals("1000", iterator.next().get(6));
        assertFalse(iterator.hasNext());
        assertEquals(2, file.rowCount());
    }

    @Test
    void enforcesDatevRowLimitBoundary() {
        DatevFile.ensureCanAppendRow(DatevFile.MAX_DATA_ROWS - 1);
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> DatevFile.ensureCanAppendRow(DatevFile.MAX_DATA_ROWS)
        );
        assertTrue(error.getMessage().contains("99999"));
        assertThrows(IllegalArgumentException.class, () -> DatevFile.ensureCanAppendRow(-1));
    }

    private static String[] fullRow(DatevSchema schema, String fill) {
        String[] row = new String[schema.columnCount()];
        Arrays.fill(row, fill);
        return row;
    }
}
