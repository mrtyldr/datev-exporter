package io.github.mrtyldr.datev.core;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * The canonical DATEV CSV record codec shared by every exporter.
 *
 * <p>DATEV files are semicolon-delimited, use CRLF record separators and are encoded as
 * Windows-1252. Values are quoted with {@code "} and an embedded quote is doubled. Every module
 * serializes and parses through this class, so the exporters cannot drift apart in their
 * interpretation of a DATEV record.
 *
 * <p>This is a low-level primitive. Prefer the exporter APIs, which additionally validate cells,
 * row widths and field semantics.
 */
public final class DatevCsv {

    /** The DATEV field delimiter. */
    public static final char DELIMITER = ';';

    /** The DATEV record separator. */
    public static final String LINE_SEPARATOR = "\r\n";

    /** The DATEV output charset. */
    public static final Charset CHARSET = Charset.forName("windows-1252");

    /** Quotes no column beyond what CSV syntax requires. */
    public static final IntPredicate QUOTE_NONE = index -> false;

    private DatevCsv() {
    }

    /**
     * Encodes one record, terminated by {@link #LINE_SEPARATOR}.
     *
     * @param values the record's values; {@code null} is written as an empty cell
     * @param alwaysQuoted columns DATEV defines as text, which are quoted even when empty
     * @return the encoded record
     */
    public static String encodeRecord(List<String> values, IntPredicate alwaysQuoted) {
        StringBuilder csv = new StringBuilder(values.size() * 8);
        appendRecord(csv, values, alwaysQuoted);
        return csv.toString();
    }

    /**
     * Appends one record, terminated by {@link #LINE_SEPARATOR}.
     *
     * @param csv the destination buffer
     * @param values the record's values; {@code null} is written as an empty cell
     * @param alwaysQuoted columns DATEV defines as text, which are quoted even when empty
     */
    public static void appendRecord(
            StringBuilder csv,
            List<String> values,
            IntPredicate alwaysQuoted
    ) {
        Objects.requireNonNull(csv, "csv");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(alwaysQuoted, "alwaysQuoted");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(DELIMITER);
            }
            String value = values.get(index);
            if (alwaysQuoted.test(index)) {
                appendQuoted(csv, value);
            } else {
                appendGeneric(csv, value);
            }
        }
        csv.append(LINE_SEPARATOR);
    }

    /**
     * Parses exactly one DATEV CSV record.
     *
     * <p>Quoted values may contain the delimiter and doubled quotes. A trailing empty value is
     * preserved, so {@code "a;"} parses to two values.
     *
     * @param record one record without its line separator
     * @return the parsed values, at least one
     * @throws IllegalArgumentException if the record is not well-formed
     */
    public static List<String> parseRecord(String record) {
        Objects.requireNonNull(record, "record");
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean fieldStart = true;
        boolean quoted = false;
        boolean quoteClosed = false;

        for (int index = 0; index < record.length(); index++) {
            char character = record.charAt(index);

            if (quoted) {
                if (character == '"') {
                    if (index + 1 < record.length() && record.charAt(index + 1) == '"') {
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
                if (character != DELIMITER) {
                    throw malformedRecord("Only a delimiter may follow a closing quote", index);
                }
                values.add(value.toString());
                value.setLength(0);
                fieldStart = true;
                quoteClosed = false;
                continue;
            }

            if (character == DELIMITER) {
                values.add(value.toString());
                value.setLength(0);
                fieldStart = true;
            } else if (character == '"') {
                if (!fieldStart) {
                    throw malformedRecord(
                            "A quoted value must start at the beginning of a field", index);
                }
                quoted = true;
                fieldStart = false;
            } else {
                value.append(character);
                fieldStart = false;
            }
        }

        if (quoted) {
            throw malformedRecord("Quoted value is not closed", record.length());
        }
        values.add(value.toString());
        return values;
    }

    /**
     * Rejects characters that would break a DATEV record.
     *
     * @param value the value to inspect
     * @param description how the value is named in the failure message
     * @throws IllegalArgumentException if the value contains a control or line-separator character
     */
    public static void rejectControlCharacters(String value, String description) {
        Objects.requireNonNull(value, "value");
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                throw new IllegalArgumentException(
                        description + " must not contain control or line-separator characters.");
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static void appendGeneric(StringBuilder csv, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (value.indexOf(DELIMITER) >= 0 || value.indexOf('"') >= 0) {
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

    private static IllegalArgumentException malformedRecord(String reason, int index) {
        return new IllegalArgumentException(
                "Malformed CSV row at character " + index + ": " + reason + '.');
    }
}
