package io.github.mrtyldr.datev.advanced;

import io.github.mrtyldr.datev.core.DatevFieldSpecs;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable DATEV CSV header.
 *
 * <p>Each column has a stable canonical key and a configurable output name. A
 * rename only changes the output name, so callers can continue to address the
 * column by its original key. All names are case-sensitive and normalized to
 * Unicode NFC.</p>
 */
public final class DatevHeader {
    private static final DatevHeader LEGACY_V12 = fromNames(
            DatevFieldSpecs.headers12(),
            DatevFieldSpecs.textColumnIndexes(),
            12
    );
    private static final DatevHeader CURRENT = fromNames(
            DatevFieldSpecs.headers13(),
            DatevFieldSpecs.textColumnIndexes(),
            13
    );

    private final List<Column> columns;
    private final List<String> keys;
    private final List<String> names;
    private final Map<String, Integer> keyIndexes;
    private final Map<String, Integer> nameIndexes;
    private final Integer bookingBatchFormatVersion;

    private DatevHeader(List<Column> columns, Integer bookingBatchFormatVersion) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("A DATEV header must contain at least one column.");
        }

        var copiedColumns = List.copyOf(columns);
        var copiedKeys = new ArrayList<String>(copiedColumns.size());
        var copiedNames = new ArrayList<String>(copiedColumns.size());
        var keysByName = new HashMap<String, Integer>(copiedColumns.size());
        var outputsByName = new HashMap<String, Integer>(copiedColumns.size());

        for (int index = 0; index < copiedColumns.size(); index++) {
            var column = copiedColumns.get(index);
            Integer previousKey = keysByName.putIfAbsent(column.key(), index);
            if (previousKey != null) {
                throw new IllegalArgumentException("Duplicate canonical header key: " + column.key());
            }
            Integer previousName = outputsByName.putIfAbsent(column.name(), index);
            if (previousName != null) {
                throw new IllegalArgumentException("Duplicate output header name: " + column.name());
            }
            copiedKeys.add(column.key());
            copiedNames.add(column.name());
        }

        for (var output : outputsByName.entrySet()) {
            Integer keyIndex = keysByName.get(output.getKey());
            if (keyIndex != null && !keyIndex.equals(output.getValue())) {
                throw new IllegalArgumentException(
                        "Output header name collides with another canonical key: " + output.getKey());
            }
        }

        this.columns = copiedColumns;
        this.keys = List.copyOf(copiedKeys);
        this.names = List.copyOf(copiedNames);
        this.keyIndexes = Map.copyOf(keysByName);
        this.nameIndexes = Map.copyOf(outputsByName);
        this.bookingBatchFormatVersion = bookingBatchFormatVersion;
    }

    /**
     * Returns the official Buchungsstapel format-version 13 header (125 columns).
     *
     * @return the immutable current default header
     */
    public static DatevHeader current() {
        return CURRENT;
    }

    /**
     * Returns the official Buchungsstapel format-version 12 header (124 columns).
     *
     * @return the immutable legacy version 12 header
     */
    public static DatevHeader legacyV12() {
        return LEGACY_V12;
    }

    /**
     * Creates a custom header from a semicolon-delimited flat string.
     *
     * @param header the complete header, with columns separated by semicolons
     * @return an immutable custom header
     * @throws IllegalArgumentException if the string or any column name is invalid
     */
    public static DatevHeader parse(String header) {
        if (header == null) {
            throw new IllegalArgumentException("Header string must not be null.");
        }
        return of(header.split(";", -1));
    }

    /**
     * Creates a custom header from an array in output order. The array is copied.
     *
     * @param names column names in output order
     * @return an immutable custom header
     * @throws IllegalArgumentException if the array or any column name is invalid
     */
    public static DatevHeader of(String[] names) {
        if (names == null) {
            throw new IllegalArgumentException("Header array must not be null.");
        }
        return fromNames(Arrays.asList(names.clone()));
    }

    /**
     * Creates a custom header from a list in output order. The list is copied.
     *
     * @param names column names in output order
     * @return an immutable custom header
     * @throws IllegalArgumentException if the list or any column name is invalid
     */
    public static DatevHeader of(List<String> names) {
        if (names == null) {
            throw new IllegalArgumentException("Header list must not be null.");
        }
        return fromNames(names);
    }

    /**
     * Returns a copy with one column's output name changed.
     *
     * <p>The column can be identified by either its canonical key or its current
     * output name.</p>
     *
     * @param identifier canonical key or current output name
     * @param newName new output name
     * @return a new immutable header
     * @throws IllegalArgumentException if the identifier is unknown or the name is invalid or colliding
     */
    public DatevHeader renamed(String identifier, String newName) {
        int index = resolve(identifier);
        String normalizedName = validateAndNormalizeName(newName, "Output header name");
        if (columns.get(index).name().equals(normalizedName)) {
            return this;
        }

        var renamed = new ArrayList<>(columns);
        renamed.set(index, new Column(
                columns.get(index).key(),
                normalizedName,
                columns.get(index).quoteValue()
        ));
        return new DatevHeader(renamed, bookingBatchFormatVersion);
    }

    /**
     * Returns a copy with several output names changed atomically.
     *
     * <p>Map keys are canonical keys or current output names; map values are the
     * new output names.</p>
     *
     * @param renames identifiers mapped to their new output names
     * @return a new immutable header
     * @throws IllegalArgumentException if an identifier/name is invalid, a column is targeted twice, or names collide
     */
    public DatevHeader renamed(Map<String, String> renames) {
        if (renames == null) {
            throw new IllegalArgumentException("Rename map must not be null.");
        }
        if (renames.isEmpty()) {
            return this;
        }

        var resolvedRenames = new LinkedHashMap<Integer, String>();
        for (var rename : renames.entrySet()) {
            int index = resolve(rename.getKey());
            String normalizedName = validateAndNormalizeName(rename.getValue(), "Output header name");
            if (resolvedRenames.putIfAbsent(index, normalizedName) != null) {
                throw new IllegalArgumentException(
                        "The same header column is renamed more than once: " + rename.getKey());
            }
        }

        var renamed = new ArrayList<>(columns);
        for (var rename : resolvedRenames.entrySet()) {
            int index = rename.getKey();
            renamed.set(index, new Column(
                    columns.get(index).key(),
                    rename.getValue(),
                    columns.get(index).quoteValue()
            ));
        }
        return new DatevHeader(renamed, bookingBatchFormatVersion);
    }

    /**
     * Returns a copy in the requested order.
     *
     * <p>The arguments must form a full, exact permutation of this header. Each
     * column can be identified by either its canonical key or current output name.</p>
     *
     * @param identifiers all columns in the requested order
     * @return a new immutable header in that order
     * @throws IllegalArgumentException if the arguments are not a full exact permutation
     */
    public DatevHeader reordered(String... identifiers) {
        if (identifiers == null) {
            throw new IllegalArgumentException("Header order array must not be null.");
        }
        return reordered(Arrays.asList(identifiers.clone()));
    }

    /**
     * Returns a copy in the requested order.
     *
     * <p>The list must form a full, exact permutation of this header. Each column
     * can be identified by either its canonical key or current output name.</p>
     *
     * @param identifiers all columns in the requested order
     * @return a new immutable header in that order
     * @throws IllegalArgumentException if the list is not a full exact permutation
     */
    public DatevHeader reordered(List<String> identifiers) {
        if (identifiers == null) {
            throw new IllegalArgumentException("Header order list must not be null.");
        }
        if (identifiers.size() != columns.size()) {
            throw new IllegalArgumentException(
                    "Header order must contain exactly " + columns.size() + " columns, but contained "
                            + identifiers.size() + '.');
        }

        var seenIndexes = new HashSet<Integer>(columns.size());
        var reordered = new ArrayList<Column>(columns.size());
        for (String identifier : identifiers) {
            int index = resolve(identifier);
            if (!seenIndexes.add(index)) {
                throw new IllegalArgumentException("Header order contains a duplicate column: " + identifier);
            }
            reordered.add(columns.get(index));
        }
        return reordered.equals(columns) ? this : new DatevHeader(reordered, bookingBatchFormatVersion);
    }

    /**
     * Returns the stable canonical keys in output order.
     *
     * @return an unmodifiable list of canonical keys
     */
    public List<String> keys() {
        return keys;
    }

    /**
     * Returns the configured CSV column names in output order.
     *
     * @return an unmodifiable list of output names
     */
    public List<String> names() {
        return names;
    }

    /**
     * Returns the number of columns in this header.
     *
     * @return column count
     */
    public int size() {
        return columns.size();
    }

    int resolve(String identifier) {
        String normalizedIdentifier = validateAndNormalizeName(identifier, "Header identifier");
        Integer keyIndex = keyIndexes.get(normalizedIdentifier);
        Integer nameIndex = nameIndexes.get(normalizedIdentifier);
        if (keyIndex != null && nameIndex != null && !keyIndex.equals(nameIndex)) {
            throw new IllegalArgumentException("Ambiguous header identifier: " + identifier);
        }
        Integer resolved = keyIndex != null ? keyIndex : nameIndex;
        if (resolved == null) {
            throw new IllegalArgumentException("Unknown header identifier: " + identifier);
        }
        return resolved;
    }

    String[] namesArray() {
        return names.toArray(String[]::new);
    }

    Integer[] quotedIndexes() {
        var indexes = new ArrayList<Integer>();
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index).quoteValue()) {
                indexes.add(index);
            }
        }
        return indexes.toArray(Integer[]::new);
    }

    Integer bookingBatchFormatVersion() {
        return bookingBatchFormatVersion;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof DatevHeader that
                && Objects.equals(bookingBatchFormatVersion, that.bookingBatchFormatVersion)
                && columns.equals(that.columns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columns, bookingBatchFormatVersion);
    }

    @Override
    public String toString() {
        return String.join(";", names);
    }

    private static DatevHeader fromNames(List<String> rawNames) {
        return fromNames(rawNames, Set.of(), null);
    }

    private static DatevHeader fromNames(
            List<String> rawNames,
            Set<Integer> quotedIndexes,
            Integer bookingBatchFormatVersion
    ) {
        if (rawNames.isEmpty()) {
            throw new IllegalArgumentException("A DATEV header must contain at least one column.");
        }
        var columns = new ArrayList<Column>(rawNames.size());
        for (int index = 0; index < rawNames.size(); index++) {
            String name = validateAndNormalizeName(rawNames.get(index), "Header name at index " + index);
            columns.add(new Column(name, name, quotedIndexes.contains(index)));
        }
        return new DatevHeader(columns, bookingBatchFormatVersion);
    }

    private static String validateAndNormalizeName(String rawName, String description) {
        if (rawName == null) {
            throw new IllegalArgumentException(description + " must not be null.");
        }
        String name = Normalizer.normalize(rawName, Normalizer.Form.NFC);
        if (name.isBlank()) {
            throw new IllegalArgumentException(description + " must not be blank.");
        }
        int first = name.codePointAt(0);
        int last = name.codePointBefore(name.length());
        if (isWhitespace(first) || isWhitespace(last)) {
            throw new IllegalArgumentException(description + " must not have leading or trailing whitespace: " + rawName);
        }
        if (name.indexOf(';') >= 0) {
            throw new IllegalArgumentException(description + " must not contain the semicolon delimiter: " + rawName);
        }
        if (name.codePoints().anyMatch(DatevHeader::isControlOrLineSeparator)) {
            throw new IllegalArgumentException(
                    description + " must not contain control or line-separator characters."
            );
        }
        return name;
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static boolean isControlOrLineSeparator(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }

    private record Column(String key, String name, boolean quoteValue) {
        private Column {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(name, "name");
        }
    }
}
