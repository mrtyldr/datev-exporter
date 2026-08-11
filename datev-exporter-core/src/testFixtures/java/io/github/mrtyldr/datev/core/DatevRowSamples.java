package io.github.mrtyldr.datev.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared DATEV row scenarios used by every exporter module's tests.
 *
 * <p>Keeping these in one place means a change to DATEV's required fields or to the canonical
 * header table only has to be reflected once, instead of in the near-identical copies each
 * exporter's {@code DatevFileWriterTest} and {@code DatevFileAppendTest} used to carry.
 */
public final class DatevRowSamples {

    /** A value outside DATEV's Windows-1252 output profile. */
    public static final String UNMAPPABLE_VALUE = "🙂";

    /** Values that must be rejected in every append shape. */
    public static final List<String> CONTROL_CHARACTER_VALUES =
            List.of("line\nbreak", "carriage\rreturn", "bell", "line separator");

    private DatevRowSamples() {
    }

    /**
     * Returns a sparse map holding exactly DATEV's five necessary fields.
     *
     * @return mutable copy of the minimal valid row
     */
    public static Map<String, Object> requiredFieldsRow() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("Umsatz (ohne Soll/Haben-Kz)", "100,00");
        values.put("Soll/Haben-Kennzeichen", "S");
        values.put("Konto", "1000");
        values.put("Gegenkonto (ohne BU-Schlüssel)", "8400");
        values.put("Belegdatum", "0101");
        return values;
    }

    /**
     * Returns a sparse map that also exercises CSV quoting and escaping.
     *
     * @return mutable copy of a valid row with a delimiter- and quote-bearing text field
     */
    public static Map<String, Object> escapingRow() {
        Map<String, Object> values = requiredFieldsRow();
        values.put("Belegdatum", "1008");
        values.put("Umsatz (ohne Soll/Haben-Kz)", "1250,00");
        values.put("Buchungstext", "Invoice; August \"2026\"");
        return values;
    }

    /**
     * Returns the same row with one field replaced.
     *
     * @param canonicalKey official column name
     * @param value replacement value
     * @return mutable copy of {@link #requiredFieldsRow()} with the override applied
     */
    public static Map<String, Object> requiredFieldsRowWith(String canonicalKey, Object value) {
        Map<String, Object> values = requiredFieldsRow();
        values.put(canonicalKey, value);
        return values;
    }

    /**
     * Expands a sparse row into a complete positional row for the supplied schema.
     *
     * @param schema fixed DATEV schema
     * @param sparse sparse values keyed by official column name
     * @return mutable list with exactly {@link DatevSchema#columnCount()} cells
     */
    public static List<String> positionalRow(DatevSchema schema, Map<String, Object> sparse) {
        List<String> row = new ArrayList<>(
                Collections.nCopies(schema.columnCount(), ""));
        List<String> headers = schema.headers();
        for (Map.Entry<String, Object> entry : sparse.entrySet()) {
            int index = headers.indexOf(entry.getKey());
            if (index < 0) {
                throw new IllegalArgumentException("Unknown DATEV column: " + entry.getKey());
            }
            row.set(index, String.valueOf(entry.getValue()));
        }
        return row;
    }

    /**
     * Returns a complete positional row holding only DATEV's necessary fields.
     *
     * @param schema fixed DATEV schema
     * @return mutable list with exactly {@link DatevSchema#columnCount()} cells
     */
    public static List<String> requiredFieldsPositionalRow(DatevSchema schema) {
        return positionalRow(schema, requiredFieldsRow());
    }

    /**
     * Returns the exact unquoted heading record DATEV expects, without its trailing CRLF.
     *
     * @param schema fixed DATEV schema
     * @return semicolon-separated official column names
     */
    public static String headerLine(DatevSchema schema) {
        return String.join(";", schema.headers());
    }
}
