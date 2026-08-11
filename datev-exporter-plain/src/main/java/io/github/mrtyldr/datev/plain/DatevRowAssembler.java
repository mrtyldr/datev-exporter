package io.github.mrtyldr.datev.plain;

import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevSchema;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
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
    private final CharsetEncoder cellEncoder = DatevFile.DEFAULT_CHARSET.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
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
        rejectControlCharacters(semicolonSeparatedRow, "CSV row");
        return fromOrdered(parseCsvRow(semicolonSeparatedRow));
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
            rejectControlCharacters(value, "value for DATEV header '" + headerIdentifier + "'");
            if (!cellEncoder.canEncode(value)) {
                throw new IllegalArgumentException(
                        "Value for DATEV header '" + headerIdentifier
                                + "' cannot be encoded as Windows-1252."
                );
            }
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

    private static void rejectControlCharacters(String value, String description) {
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException(
                        description + " must not contain control or line-separator characters."
                );
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static List<String> parseCsvRow(String input) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean fieldStart = true;
        boolean quoted = false;
        boolean quoteClosed = false;

        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);

            if (quoted) {
                if (character == '"') {
                    if (index + 1 < input.length() && input.charAt(index + 1) == '"') {
                        value.append('"');
                        index++;
                    } else {
                        quoted = false;
                        quoteClosed = true;
                    }
                } else {
                    value.append(character);
                }
                continue;
            }

            if (quoteClosed) {
                if (character != DatevFile.DEFAULT_DELIMITER) {
                    throw malformedCsvRow("Only a delimiter may follow a closing quote", index);
                }
                values.add(value.toString());
                value.setLength(0);
                fieldStart = true;
                quoteClosed = false;
                continue;
            }

            if (character == DatevFile.DEFAULT_DELIMITER) {
                values.add(value.toString());
                value.setLength(0);
                fieldStart = true;
            } else if (character == '"') {
                if (!fieldStart) {
                    throw malformedCsvRow(
                            "A quoted value must start at the beginning of a field",
                            index
                    );
                }
                quoted = true;
                fieldStart = false;
            } else {
                value.append(character);
                fieldStart = false;
            }
        }

        if (quoted) {
            throw malformedCsvRow("Quoted value is not closed", input.length());
        }
        values.add(value.toString());
        return values;
    }

    private static IllegalArgumentException malformedCsvRow(String reason, int index) {
        return new IllegalArgumentException(
                "Malformed CSV row at character " + index + ": " + reason + '.'
        );
    }
}
