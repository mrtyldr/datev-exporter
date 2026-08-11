package io.github.mrtyldr.datev.univocity;

import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevSchema;

import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import com.univocity.parsers.csv.UnescapedQuoteHandling;

import java.io.ByteArrayOutputStream;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A dependency-light DATEV row container whose CSV parsing and writing are backed by Univocity.
 *
 * <p>The schema is one of the fixed official headings. Custom headings, renaming, reordering, and
 * built-in semantic validation are deliberately outside this artifact. An optional
 * {@link BiConsumer} receiving the numeric format version and immutable, header-aligned row can be
 * passed explicitly when the file is created.
 *
 * <p>Instances are mutable and are not thread-safe. Iterators and row accessors return immutable
 * snapshots, so later appends do not affect an existing snapshot.
 */
public final class DatevFile implements Iterable<List<String>> {
    /** DATEV CSV field delimiter. */
    public static final char DEFAULT_DELIMITER = ';';

    /** DATEV CSV record separator. */
    public static final String DEFAULT_LINE_SEPARATOR = "\r\n";

    /** DATEV-compatible output encoding used by byte-oriented methods. */
    public static final Charset DEFAULT_CHARSET = Charset.forName("windows-1252");

    /** Maximum booking records in one Buchungsstapel file. */
    public static final int MAX_DATA_ROWS = 99_999;

    private final DatevSchema schema;
    private final BiConsumer<Integer, List<String>> validator;
    private final List<List<String>> rows = new ArrayList<>();

    private DatevFile(DatevSchema schema, BiConsumer<Integer, List<String>> validator) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.validator = validator;
    }

    /**
     * Creates a v13 exporter without semantic validation.
     *
     * @return new v13 file
     */
    public static DatevFile withDefaults() {
        return forSchema(DatevSchema.CURRENT_V13);
    }

    /**
     * Creates a v13 exporter with an explicitly supplied validator.
     *
     * @param validator hook receiving format version 13 and an immutable aligned row
     * @return new validated v13 file
     */
    public static DatevFile withDefaults(BiConsumer<Integer, List<String>> validator) {
        return forSchema(DatevSchema.CURRENT_V13, validator);
    }

    /**
     * Creates a v12 exporter without semantic validation.
     *
     * @return new v12 file
     */
    public static DatevFile legacyV12() {
        return forSchema(DatevSchema.LEGACY_V12);
    }

    /**
     * Creates a v12 exporter with an explicitly supplied validator.
     *
     * @param validator hook receiving format version 12 and an immutable aligned row
     * @return new validated v12 file
     */
    public static DatevFile legacyV12(BiConsumer<Integer, List<String>> validator) {
        return forSchema(DatevSchema.LEGACY_V12, validator);
    }

    /**
     * Creates an exporter for a fixed schema without semantic validation.
     *
     * @param schema fixed schema to use
     * @return new file
     */
    public static DatevFile forSchema(DatevSchema schema) {
        return new DatevFile(schema, null);
    }

    /**
     * Creates an exporter for a fixed schema with an explicitly supplied validator.
     *
     * @param schema fixed schema to use
     * @param validator hook receiving the numeric format version and an immutable aligned row
     * @return new validated file
     */
    public static DatevFile forSchema(
            DatevSchema schema,
            BiConsumer<Integer, List<String>> validator
    ) {
        return new DatevFile(schema, Objects.requireNonNull(validator, "validator"));
    }

    /**
     * Returns the fixed schema used by this file.
     *
     * @return fixed schema
     */
    public DatevSchema schema() {
        return schema;
    }

    /**
     * Returns the immutable official headings in output order.
     *
     * @return immutable heading list
     */
    public List<String> headers() {
        return schema.headers();
    }

    /**
     * Returns the configured optional validation hook.
     *
     * @return configured format-version/row consumer, if any
     */
    public Optional<BiConsumer<Integer, List<String>>> validator() {
        return Optional.ofNullable(validator);
    }

    /**
     * Returns the byte-output charset.
     *
     * @return Windows-1252
     */
    public Charset charset() {
        return DEFAULT_CHARSET;
    }

    /**
     * Returns the number of committed data rows.
     *
     * @return row count
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * Returns whether no data rows have been committed.
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * Returns an immutable snapshot containing immutable rows.
     *
     * @return immutable row snapshot
     */
    public List<List<String>> rows() {
        return List.copyOf(rows);
    }

    /** Iterates over an immutable snapshot of the current rows. */
    @Override
    public Iterator<List<String>> iterator() {
        return rows().iterator();
    }

    /**
     * Returns fresh Univocity settings for booking rows only.
     *
     * <p>Automatic header writing is disabled because the selective DATEV text-column quoting
     * required for data would also quote heading cells. The official headings remain configured
     * for named field selection. Use {@link #writeTo(Writer)} or {@link #writeTo(OutputStream)} to
     * produce the canonical unquoted heading followed by data rows.
     *
     * @return fresh data-row-only writer settings
     */
    public CsvWriterSettings csvWriterSettings() {
        return writerSettings(false, true);
    }

    /**
     * Creates a configured data-row-only writer without taking ownership of the output stream.
     *
     * @param output caller-owned output stream
     * @return configured writer
     */
    public CsvWriter newCsvWriter(OutputStream output) {
        return newCsvWriter(output, csvWriterSettings());
    }

    /**
     * Creates a data-row-only writer using a snapshot of caller-customized settings and strict
     * Windows-1252 encoding. Header writing is disabled on the snapshot. Closing the returned
     * writer flushes but does not close the caller-owned stream.
     *
     * @param output caller-owned output stream
     * @param settings writer settings
     * @return configured writer
     */
    public CsvWriter newCsvWriter(OutputStream output, CsvWriterSettings settings) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(settings, "settings");
        var encoder = DEFAULT_CHARSET.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return new CsvWriter(
                new NonClosingWriter(new OutputStreamWriter(output, encoder)),
                dataOnlySettings(settings)
        );
    }

    /**
     * Creates a configured data-row-only writer without taking ownership of the character writer.
     *
     * @param output caller-owned character writer
     * @return configured writer
     */
    public CsvWriter newCsvWriter(Writer output) {
        return newCsvWriter(output, csvWriterSettings());
    }

    /**
     * Creates a data-row-only writer using a snapshot of caller-customized settings without taking
     * ownership of its output. Header writing is disabled on the snapshot.
     *
     * @param output caller-owned character writer
     * @param settings writer settings
     * @return configured writer
     */
    public CsvWriter newCsvWriter(Writer output, CsvWriterSettings settings) {
        return new CsvWriter(
                new NonClosingWriter(Objects.requireNonNull(output, "output")),
                dataOnlySettings(settings)
        );
    }

    /**
     * Appends one strict, semicolon-delimited CSV record.
     *
     * <p>Quoted fields and doubled quote escapes are supported. Newlines, unclosed quotes, quotes
     * inside unquoted fields, and characters after a closing quote are rejected before Univocity
     * parses the record.
     *
     * @param semicolonSeparatedRow complete CSV record
     */
    public void append(String semicolonSeparatedRow) {
        Objects.requireNonNull(semicolonSeparatedRow, "semicolonSeparatedRow");
        rejectControlCharacters(semicolonSeparatedRow, "CSV row");
        validateCsvRowSyntax(semicolonSeparatedRow);

        CsvParserSettings settings = parserSettings(semicolonSeparatedRow.length());
        String[] parsed;
        try {
            parsed = new CsvParser(settings).parseLine(semicolonSeparatedRow);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed DATEV CSV row.", exception);
        }
        append(parsed == null ? new String[]{""} : parsed);
    }

    /**
     * Appends a complete positional row after defensively copying it.
     *
     * @param orderedValues values in fixed heading order
     */
    public void append(String[] orderedValues) {
        Objects.requireNonNull(orderedValues, "orderedValues");
        appendOrdered(Arrays.asList(orderedValues.clone()));
    }

    /**
     * Appends a complete positional row after defensively copying it.
     *
     * @param orderedValues values in fixed heading order
     */
    public void append(Collection<String> orderedValues) {
        Objects.requireNonNull(orderedValues, "orderedValues");
        appendOrdered(new ArrayList<>(orderedValues));
    }

    /**
     * Appends positional values converted with {@link String#valueOf(Object)}.
     *
     * @param orderedValues values in fixed heading order
     */
    public void appendValues(Object... orderedValues) {
        Objects.requireNonNull(orderedValues, "orderedValues");
        validateRowWidth(orderedValues.length);
        List<String> converted = new ArrayList<>(orderedValues.length);
        for (Object value : orderedValues) {
            converted.add(value == null ? null : String.valueOf(value));
        }
        appendOrdered(converted);
    }

    /**
     * Appends a sparse row keyed by exact official headings. Missing entries become empty cells.
     *
     * @param valuesByHeader values keyed by exact official headings
     */
    public void append(Map<String, ?> valuesByHeader) {
        Objects.requireNonNull(valuesByHeader, "valuesByHeader");
        ensureCanAppendRow();

        List<String> identifiers = new ArrayList<>(valuesByHeader.size());
        List<Object> values = new ArrayList<>(valuesByHeader.size());
        for (Map.Entry<String, ?> entry : valuesByHeader.entrySet()) {
            identifiers.add(Objects.requireNonNull(entry.getKey(), "header key"));
            values.add(entry.getValue());
        }

        String[] row = new String[schema.columnCount()];
        int[] resolvedIndexes = new int[identifiers.size()];
        Set<Integer> assigned = new HashSet<>();
        for (int position = 0; position < identifiers.size(); position++) {
            String identifier = identifiers.get(position);
            int index = DatevSchemaIndex.resolve(schema, identifier);
            ensureUnassigned(assigned, index, identifier);
            resolvedIndexes[position] = index;
        }
        for (int position = 0; position < identifiers.size(); position++) {
            Object value = values.get(position);
            row[resolvedIndexes[position]] = validateCell(
                    value == null ? null : String.valueOf(value),
                    identifiers.get(position)
            );
        }
        commit(row);
    }

    /**
     * Appends sparse, formatted columns addressed by exact official headings.
     *
     * @param columns formatted columns
     */
    public void append(DatevColumn<?>... columns) {
        Objects.requireNonNull(columns, "columns");
        appendColumns(Arrays.asList(columns.clone()));
    }

    /**
     * Appends sparse, formatted columns from any iterable.
     *
     * @param columns formatted columns
     */
    public void append(Iterable<? extends DatevColumn<?>> columns) {
        Objects.requireNonNull(columns, "columns");
        List<DatevColumn<?>> snapshot = new ArrayList<>();
        for (DatevColumn<?> column : columns) {
            snapshot.add(column);
        }
        appendColumns(snapshot);
    }

    /**
     * Appends a collection of sparse, formatted columns atomically.
     *
     * <p>This named overload avoids Java's erasure collision with {@code Collection<String>}.
     *
     * @param columns formatted columns
     */
    public void appendColumns(Collection<? extends DatevColumn<?>> columns) {
        Objects.requireNonNull(columns, "columns");
        ensureCanAppendRow();
        List<? extends DatevColumn<?>> snapshot = new ArrayList<>(columns);

        String[] row = new String[schema.columnCount()];
        int[] resolvedIndexes = new int[snapshot.size()];
        Set<Integer> assigned = new HashSet<>();
        for (int position = 0; position < snapshot.size(); position++) {
            DatevColumn<?> column = Objects.requireNonNull(
                    snapshot.get(position),
                    "columns must not contain null"
            );
            int index = DatevSchemaIndex.resolve(schema, column.header());
            ensureUnassigned(assigned, index, column.header());
            resolvedIndexes[position] = index;
        }
        for (int position = 0; position < snapshot.size(); position++) {
            DatevColumn<?> column = snapshot.get(position);
            row[resolvedIndexes[position]] = validateCell(
                    column.formattedValue(),
                    column.header()
            );
        }
        commit(row);
    }

    /**
     * Writes the unquoted official heading and current rows without closing the stream.
     *
     * @param output caller-owned output stream
     */
    public void writeTo(OutputStream output) {
        Objects.requireNonNull(output, "output");
        var encoder = DEFAULT_CHARSET.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        writeTo(new NonClosingWriter(new OutputStreamWriter(output, encoder)));
    }

    /**
     * Alias for {@link #writeTo(OutputStream)}.
     *
     * @param output caller-owned output stream
     */
    public void write(OutputStream output) {
        writeTo(output);
    }

    /**
     * Alias for {@link #writeTo(Writer)}.
     *
     * @param output caller-owned character writer
     */
    public void write(Writer output) {
        writeTo(output);
    }

    /**
     * Writes the unquoted official heading and current rows without closing the writer.
     *
     * @param output caller-owned character writer
     */
    public void writeTo(Writer output) {
        Objects.requireNonNull(output, "output");
        NonClosingWriter destination = new NonClosingWriter(output);

        CsvWriter headerWriter = new CsvWriter(destination, writerSettings(true, false));
        headerWriter.writeHeaders();
        headerWriter.flush();

        CsvWriter rowWriter = new CsvWriter(destination, writerSettings(false, true));
        rowWriter.writeRows(this);
        rowWriter.flush();
    }

    /**
     * Writes only the current booking rows through a compatible Univocity writer and flushes it.
     *
     * <p>No heading is emitted. Use {@link #writeTo(Writer)} or {@link #writeTo(OutputStream)} for
     * a canonical fixed-heading CSV file. The supplied writer must have automatic header writing
     * disabled; writers created by {@link #newCsvWriter(Writer)} and
     * {@link #newCsvWriter(OutputStream)} satisfy this requirement.
     *
     * @param writer compatible data-row writer
     */
    public void writeDataTo(CsvWriter writer) {
        Objects.requireNonNull(writer, "writer");
        writer.writeRows(this);
        writer.flush();
    }

    /**
     * Returns the fixed-header CSV encoded strictly as Windows-1252.
     *
     * @return encoded CSV bytes
     */
    public byte[] toByteArray() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeTo(output);
        return output.toByteArray();
    }

    /**
     * Returns the fixed-header CSV as characters.
     *
     * @return CSV text
     */
    public String toCsvString() {
        StringWriter output = new StringWriter();
        writeTo(output);
        return output.toString();
    }

    private CsvWriterSettings writerSettings(boolean writeHeader, boolean quoteDatevFields) {
        CsvFormat format = new CsvFormat();
        format.setDelimiter(DEFAULT_DELIMITER);
        format.setLineSeparator(DEFAULT_LINE_SEPARATOR);
        // Univocity otherwise quotes an unquoted first cell beginning with its default '#'
        // comment marker. Structural-only rows must serialize identically to the plain writer.
        format.setComment('\0');

        CsvWriterSettings settings = new CsvWriterSettings();
        settings.setFormat(format);
        settings.setHeaders(DatevSchemaIndex.headerArray(schema));
        settings.setHeaderWritingEnabled(writeHeader);
        settings.setNullValue("");
        settings.setEmptyValue("");
        settings.setQuoteNulls(true);
        settings.setQuoteEscapingEnabled(true);
        settings.setSkipEmptyLines(false);
        settings.setIgnoreLeadingWhitespaces(false);
        settings.setIgnoreTrailingWhitespaces(false);
        settings.setExpandIncompleteRows(false);
        settings.setColumnReorderingEnabled(false);
        settings.setMaxColumns(schema.columnCount());
        if (quoteDatevFields) {
            settings.quoteIndexes(DatevSchemaIndex.quotedIndexes(schema));
        }
        return settings;
    }

    private static CsvWriterSettings dataOnlySettings(CsvWriterSettings settings) {
        CsvWriterSettings snapshot = Objects.requireNonNull(settings, "settings").clone();
        snapshot.setHeaderWritingEnabled(false);
        return snapshot;
    }

    private CsvParserSettings parserSettings(int rowLength) {
        CsvParserSettings settings = new CsvParserSettings();
        settings.getFormat().setDelimiter(DEFAULT_DELIMITER);
        settings.setSkipEmptyLines(false);
        settings.setCommentProcessingEnabled(false);
        settings.setIgnoreLeadingWhitespaces(false);
        settings.setIgnoreTrailingWhitespaces(false);
        settings.setIgnoreLeadingWhitespacesInQuotes(false);
        settings.setIgnoreTrailingWhitespacesInQuotes(false);
        settings.setEmptyValue("");
        settings.setNullValue("");
        settings.setUnescapedQuoteHandling(UnescapedQuoteHandling.RAISE_ERROR);
        settings.setMaxColumns(schema.columnCount());
        settings.setMaxCharsPerColumn(Math.max(1, rowLength));
        return settings;
    }

    private void appendOrdered(List<String> orderedValues) {
        validateRowWidth(orderedValues.size());
        ensureCanAppendRow();
        String[] row = new String[schema.columnCount()];
        for (int index = 0; index < orderedValues.size(); index++) {
            row[index] = validateCell(orderedValues.get(index), schema.headers().get(index));
        }
        commit(row);
    }

    private void commit(String[] row) {
        List<String> immutable = immutableRow(row);
        if (validator != null) {
            validator.accept(schema.formatVersion(), immutable);
        }
        ensureCanAppendRow();
        rows.add(immutable);
    }

    private void ensureCanAppendRow() {
        ensureCanAppendRow(rows.size());
    }

    static void ensureCanAppendRow(int currentRowCount) {
        if (currentRowCount < 0) {
            throw new IllegalArgumentException("Current row count must not be negative.");
        }
        if (currentRowCount >= MAX_DATA_ROWS) {
            throw new IllegalStateException(
                    "A DATEV Buchungsstapel file can contain at most " + MAX_DATA_ROWS
                            + " booking rows."
            );
        }
    }

    private void validateRowWidth(int valueCount) {
        if (valueCount != schema.columnCount()) {
            throw new IllegalArgumentException(
                    "Row contains " + valueCount + " values but the fixed DATEV v"
                            + schema.formatVersion() + " header contains " + schema.columnCount() + "."
            );
        }
    }

    private static List<String> immutableRow(String[] row) {
        List<String> copy = new ArrayList<>(row.length);
        Collections.addAll(copy, row);
        return Collections.unmodifiableList(copy);
    }

    private static void ensureUnassigned(Set<Integer> assigned, int index, String identifier) {
        if (!assigned.add(index)) {
            throw new IllegalArgumentException(
                    "Multiple values were supplied for DATEV header '" + identifier + "'."
            );
        }
    }

    private static String validateCell(String value, String headerIdentifier) {
        if (value != null) {
            String description = "value for DATEV header '" + headerIdentifier + "'";
            rejectControlCharacters(value, description);
            if (!DEFAULT_CHARSET.newEncoder().canEncode(value)) {
                throw new IllegalArgumentException(
                        description + " contains characters that cannot be encoded as Windows-1252."
                );
            }
        }
        return value;
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

    private static void validateCsvRowSyntax(String row) {
        boolean fieldStart = true;
        boolean quoted = false;
        boolean quoteClosed = false;

        for (int index = 0; index < row.length(); index++) {
            char character = row.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < row.length() && row.charAt(index + 1) == '"') {
                        index++;
                    } else {
                        quoted = false;
                        quoteClosed = true;
                    }
                }
                continue;
            }

            if (quoteClosed) {
                if (character != DEFAULT_DELIMITER) {
                    throw malformedCsvRow("Only a delimiter may follow a closing quote", index);
                }
                fieldStart = true;
                quoteClosed = false;
                continue;
            }

            if (character == DEFAULT_DELIMITER) {
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
                fieldStart = false;
            }
        }

        if (quoted) {
            throw malformedCsvRow("Quoted value is not closed", row.length());
        }
    }

    private static IllegalArgumentException malformedCsvRow(String reason, int index) {
        return new IllegalArgumentException(
                "Malformed CSV row at character " + index + ": " + reason + '.');
    }

    private static final class NonClosingWriter extends FilterWriter {
        private NonClosingWriter(Writer delegate) {
            super(delegate);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
