package io.github.mrtyldr.datev.plain;

import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevCsv;
import io.github.mrtyldr.datev.core.DatevSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Shared row normalization for the buffered and forward-only plain exporters. */
final class DatevRowAssembler {

    private final DatevSchema schema;
    private final BiConsumer<Integer, List<String>> validator;
    private final Map<String, Integer> headerIndexes;

    DatevRowAssembler(
            DatevSchema schema,
            BiConsumer<Integer, List<String>> validator
    ) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.validator = validator;
        this.headerIndexes = indexHeaders(schema.headers());
    }

    BiConsumer<Integer, List<String>> validator() {
        return validator;
    }

    List<String> fromCsv(String semicolonSeparatedRow) {
        Objects.requireNonNull(semicolonSeparatedRow, "semicolonSeparatedRow");
        DatevCsv.rejectControlCharacters(semicolonSeparatedRow, "CSV row");
        return fromOrdered(DatevCsv.parseRecord(semicolonSeparatedRow));
    }

    List<String> fromArray(String[] orderedValues) {
        Objects.requireNonNull(orderedValues, "orderedValues");
        return fromOrdered(Arrays.asList(orderedValues.clone()));
    }

    List<String> fromCollection(Collection<String> orderedValues) {
        Objects.requireNonNull(orderedValues, "orderedValues");
        return fromOrdered(new ArrayList<>(orderedValues));
    }

    List<String> fromValues(Object... orderedValues) {
        Objects.requireNonNull(orderedValues, "orderedValues");
        validateRowWidth(orderedValues.length);
        List<String> converted = new ArrayList<>(orderedValues.length);
        for (Object value : orderedValues) {
            converted.add(value == null ? null : String.valueOf(value));
        }
        return fromOrdered(converted);
    }

    List<String> fromMap(Map<String, ?> valuesByHeader) {
        Objects.requireNonNull(valuesByHeader, "valuesByHeader");

        List<String> identifiers = new ArrayList<>(valuesByHeader.size());
        List<Object> values = new ArrayList<>(valuesByHeader.size());
        for (Map.Entry<String, ?> entry : valuesByHeader.entrySet()) {
            identifiers.add(entry.getKey());
            values.add(entry.getValue());
        }

        int[] indexes = new int[identifiers.size()];
        for (int position = 0; position < identifiers.size(); position++) {
            indexes[position] = resolveHeader(identifiers.get(position));
        }

        String[] row = new String[schema.columnCount()];
        for (int position = 0; position < values.size(); position++) {
            Object value = values.get(position);
            row[indexes[position]] = validateCell(
                    value == null ? null : String.valueOf(value),
                    identifiers.get(position)
            );
        }
        return validateAndFreeze(row);
    }

    List<String> fromColumns(Collection<? extends DatevColumn<?>> columns) {
        Objects.requireNonNull(columns, "columns");
        List<? extends DatevColumn<?>> snapshot = new ArrayList<>(columns);
        int[] indexes = new int[snapshot.size()];
        boolean[] assigned = new boolean[schema.columnCount()];

        for (int position = 0; position < snapshot.size(); position++) {
            DatevColumn<?> column = Objects.requireNonNull(
                    snapshot.get(position),
                    "columns must not contain null"
            );
            int index = resolveHeader(column.header());
            if (assigned[index]) {
                throw new IllegalArgumentException(
                        "Multiple values were supplied for DATEV header '" + column.header() + "'."
                );
            }
            assigned[index] = true;
            indexes[position] = index;
        }

        String[] row = new String[schema.columnCount()];
        for (int position = 0; position < snapshot.size(); position++) {
            DatevColumn<?> column = snapshot.get(position);
            row[indexes[position]] = validateCell(column.formattedValue(), column.header());
        }
        return validateAndFreeze(row);
    }

    private List<String> fromOrdered(List<String> orderedValues) {
        validateRowWidth(orderedValues.size());
        String[] row = new String[orderedValues.size()];
        for (int index = 0; index < orderedValues.size(); index++) {
            row[index] = validateCell(orderedValues.get(index), schema.headers().get(index));
        }
        return validateAndFreeze(row);
    }

    private List<String> validateAndFreeze(String[] row) {
        List<String> immutable = immutableRow(row);
        if (validator != null) {
            validator.accept(schema.formatVersion(), immutable);
        }
        return immutable;
    }

    private void validateRowWidth(int valueCount) {
        if (valueCount != schema.columnCount()) {
            throw new IllegalArgumentException(
                    "Row contains " + valueCount + " values but header contains "
                            + schema.columnCount() + '.'
            );
        }
    }

    private int resolveHeader(String identifier) {
        if (identifier == null) {
            throw new NullPointerException("DATEV header identifier must not be null.");
        }
        Integer index = headerIndexes.get(identifier);
        if (index == null) {
            throw new IllegalArgumentException(
                    "Unknown DATEV header for schema " + schema.name() + ": '" + identifier + "'."
            );
        }
        return index;
    }

    private String validateCell(String value, String headerIdentifier) {
        if (value != null) {
            // One pass replaces the former control-character scan followed by an independent
            // canEncode scan. Messages and their precedence are unchanged.
            DatevCsv.requireExportable(value,
                    "value for DATEV header '" + headerIdentifier + "'");
        }
        return value;
    }

    private static Map<String, Integer> indexHeaders(List<String> headers) {
        Map<String, Integer> indexes = new LinkedHashMap<>(headers.size());
        for (int index = 0; index < headers.size(); index++) {
            Integer previous = indexes.put(headers.get(index), index);
            if (previous != null) {
                throw new IllegalStateException("Duplicate fixed DATEV heading: " + headers.get(index));
            }
        }
        return Collections.unmodifiableMap(indexes);
    }

    private static List<String> immutableRow(String[] row) {
        List<String> copy = new ArrayList<>(row.length);
        Collections.addAll(copy, row);
        return Collections.unmodifiableList(copy);
    }



}
