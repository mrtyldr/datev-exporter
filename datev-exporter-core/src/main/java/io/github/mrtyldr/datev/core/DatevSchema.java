package io.github.mrtyldr.datev.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Supported fixed DATEV Buchungsstapel schemas. */
public enum DatevSchema {
    /** Current 125-column format version 13. */
    CURRENT_V13(13, DatevFieldSpecs.version13(), DatevFieldSpecs.headers13()),

    /** Legacy 124-column format version 12. */
    LEGACY_V12(12, DatevFieldSpecs.version12(), DatevFieldSpecs.headers12());

    private final int formatVersion;
    private final List<DatevFieldSpec> fieldSpecs;
    private final List<String> headers;
    private final Map<String, Integer> headerIndexes;

    /**
     * Binds a version to its field specifications and to the official heading list they were
     * derived from.
     *
     * <p>The headings are taken from {@link DatevFieldSpecs} instead of being recomputed from
     * {@code fieldSpecs}. Both routes produce the same names in the same order — the field
     * specifications read their canonical key out of exactly this list — but reusing it means
     * {@link #headers()} and {@link DatevFieldSpecs#headers13()} are the same object, so every
     * identity-based shortcut keyed on the official lists actually fires, and two further
     * 125-element lists are never built.
     */
    DatevSchema(int formatVersion, List<DatevFieldSpec> fieldSpecs, List<String> headers) {
        this.formatVersion = formatVersion;
        this.fieldSpecs = fieldSpecs;
        this.headers = headers;
        this.headerIndexes = indexHeaders(this.headers);
    }

    private static Map<String, Integer> indexHeaders(List<String> headers) {
        var indexes = new HashMap<String, Integer>(headers.size());
        for (int index = 0; index < headers.size(); index++) {
            if (indexes.putIfAbsent(headers.get(index), index) != null) {
                throw new IllegalStateException(
                        "Duplicate official DATEV heading: " + headers.get(index));
            }
        }
        return Map.copyOf(indexes);
    }

    /**
     * Returns the current schema.
     *
     * @return {@link #CURRENT_V13}
     */
    public static DatevSchema current() {
        return CURRENT_V13;
    }

    /**
     * Resolves a supported Buchungsstapel format version.
     *
     * @param formatVersion DATEV format version 12 or 13
     * @return matching fixed schema
     * @throws IllegalArgumentException if the version is unsupported
     */
    public static DatevSchema fromFormatVersion(int formatVersion) {
        return switch (formatVersion) {
            case 12 -> LEGACY_V12;
            case 13 -> CURRENT_V13;
            default -> throw new IllegalArgumentException(
                    "Unsupported DATEV Buchungsstapel format version: " + formatVersion + '.');
        };
    }

    /**
     * Returns the DATEV booking-batch format version.
     *
     * @return 12 or 13
     */
    public int formatVersion() {
        return formatVersion;
    }

    /**
     * Returns official fields in output order.
     *
     * @return immutable definitions
     */
    public List<DatevFieldSpec> fieldSpecs() {
        return fieldSpecs;
    }

    /**
     * Returns official column names in output order.
     *
     * @return immutable header names
     */
    public List<String> headers() {
        return headers;
    }

    /**
     * Returns the immutable canonical-key-to-column-index map of this schema.
     *
     * <p>Computed once when the enum constant is created and validated for duplicates there, so
     * row validation can reuse it instead of re-indexing 125 keys for every row. Deliberately not
     * part of the public surface: it exists only so the validation engine in this package can skip
     * work it already knows the answer to.
     *
     * @return the immutable key index map
     */
    Map<String, Integer> headerIndexes() {
        return headerIndexes;
    }

    /**
     * Returns the exact number of cells expected in a row.
     *
     * @return 124 or 125
     */
    public int columnCount() {
        return fieldSpecs.size();
    }

    /**
     * Returns whether DATEV defines the zero-based column as a text field.
     *
     * <p>Text fields are always quoted on output, including empty values.
     *
     * @param zeroBasedIndex the column index
     * @return {@code true} for a DATEV text field
     * @throws IndexOutOfBoundsException if the index does not exist in this schema
     */
    public boolean isTextColumn(int zeroBasedIndex) {
        if (zeroBasedIndex < 0 || zeroBasedIndex >= fieldSpecs.size()) {
            throw new IndexOutOfBoundsException("Column index " + zeroBasedIndex
                    + " is outside schema " + name() + '.');
        }
        return DatevFieldSpecs.textColumnIndexes().contains(zeroBasedIndex);
    }
}
