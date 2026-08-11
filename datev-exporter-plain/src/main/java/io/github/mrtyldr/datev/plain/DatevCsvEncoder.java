package io.github.mrtyldr.datev.plain;

import io.github.mrtyldr.datev.core.DatevSchema;

import java.util.List;

/** Shared canonical CSV serialization for the plain exporters. */
final class DatevCsvEncoder {

    private DatevCsvEncoder() {
    }

    static String encodeHeader(DatevSchema schema) {
        StringBuilder csv = new StringBuilder(schema.columnCount() * 16);
        appendHeader(csv, schema);
        return csv.toString();
    }

    static String encodeRow(DatevSchema schema, List<String> row) {
        StringBuilder csv = new StringBuilder(schema.columnCount() * 4);
        appendRow(csv, schema, row);
        return csv.toString();
    }

    static void appendHeader(StringBuilder csv, DatevSchema schema) {
        appendDelimitedValues(csv, schema.headers());
        csv.append(DatevFile.DEFAULT_LINE_SEPARATOR);
    }

    static void appendRow(StringBuilder csv, DatevSchema schema, List<String> row) {
        for (int index = 0; index < row.size(); index++) {
            if (index > 0) {
                csv.append(DatevFile.DEFAULT_DELIMITER);
            }
            String value = row.get(index);
            if (schema.isTextColumn(index)) {
                appendQuoted(csv, value);
            } else {
                appendGeneric(csv, value);
            }
        }
        csv.append(DatevFile.DEFAULT_LINE_SEPARATOR);
    }

    private static void appendDelimitedValues(StringBuilder csv, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(DatevFile.DEFAULT_DELIMITER);
            }
            csv.append(values.get(index));
        }
    }

    private static void appendGeneric(StringBuilder csv, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (value.indexOf(DatevFile.DEFAULT_DELIMITER) >= 0 || value.indexOf('"') >= 0) {
            appendQuoted(csv, value);
        } else {
            csv.append(value);
        }
    }

    private static void appendQuoted(StringBuilder csv, String value) {
        csv.append('"');
        if (value != null) {
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character == '"') {
                    csv.append('"');
                }
                csv.append(character);
            }
        }
        csv.append('"');
    }
}
