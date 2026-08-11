package io.github.mrtyldr.datev.core;

import java.util.List;

/** Supported fixed DATEV Buchungsstapel schemas. */
public enum DatevSchema {
    /** Current 125-column format version 13. */
    CURRENT_V13(13, DatevFieldSpecs.version13()),

    /** Legacy 124-column format version 12. */
    LEGACY_V12(12, DatevFieldSpecs.version12());

    private final int formatVersion;
    private final List<DatevFieldSpec> fieldSpecs;
    private final List<String> headers;

    DatevSchema(int formatVersion, List<DatevFieldSpec> fieldSpecs) {
        this.formatVersion = formatVersion;
        this.fieldSpecs = fieldSpecs;
        this.headers = fieldSpecs.stream().map(DatevFieldSpec::canonicalKey).toList();
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
