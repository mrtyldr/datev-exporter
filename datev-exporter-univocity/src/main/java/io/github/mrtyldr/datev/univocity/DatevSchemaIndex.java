package io.github.mrtyldr.datev.univocity;

import io.github.mrtyldr.datev.core.DatevSchema;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal Univocity-specific projections of the shared {@link DatevSchema}.
 *
 * <p>Kept in this module so the core schema stays free of writer-specific concerns.
 */
final class DatevSchemaIndex {
    private static final Map<DatevSchema, Map<String, Integer>> INDEXES = createIndexes();
    private static final Map<DatevSchema, String[]> HEADER_ARRAYS = createHeaderArrays();
    private static final Map<DatevSchema, Integer[]> QUOTED_INDEXES = createQuotedIndexes();

    private DatevSchemaIndex() {
    }

    static int resolve(DatevSchema schema, String header) {
        Integer index = INDEXES.get(schema).get(header);
        if (index == null) {
            throw new IllegalArgumentException(
                    "Unknown DATEV header for format v" + schema.formatVersion()
                            + ": '" + header + "'."
            );
        }
        return index;
    }

    static String[] headerArray(DatevSchema schema) {
        return HEADER_ARRAYS.get(schema).clone();
    }

    static Integer[] quotedIndexes(DatevSchema schema) {
        return QUOTED_INDEXES.get(schema).clone();
    }

    private static Map<DatevSchema, Map<String, Integer>> createIndexes() {
        var result = new EnumMap<DatevSchema, Map<String, Integer>>(DatevSchema.class);
        for (DatevSchema schema : DatevSchema.values()) {
            List<String> headers = schema.headers();
            var indexes = new LinkedHashMap<String, Integer>(headers.size());
            for (int index = 0; index < headers.size(); index++) {
                indexes.put(headers.get(index), index);
            }
            result.put(schema, Map.copyOf(indexes));
        }
        return result;
    }

    private static Map<DatevSchema, String[]> createHeaderArrays() {
        var result = new EnumMap<DatevSchema, String[]>(DatevSchema.class);
        for (DatevSchema schema : DatevSchema.values()) {
            result.put(schema, schema.headers().toArray(String[]::new));
        }
        return result;
    }

    private static Map<DatevSchema, Integer[]> createQuotedIndexes() {
        var result = new EnumMap<DatevSchema, Integer[]>(DatevSchema.class);
        for (DatevSchema schema : DatevSchema.values()) {
            result.put(schema, java.util.stream.IntStream.range(0, schema.columnCount())
                    .filter(schema::isTextColumn)
                    .boxed()
                    .toArray(Integer[]::new));
        }
        return result;
    }
}
