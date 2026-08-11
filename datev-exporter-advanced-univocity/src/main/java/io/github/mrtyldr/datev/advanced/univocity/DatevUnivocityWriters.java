package io.github.mrtyldr.datev.advanced.univocity;

import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import io.github.mrtyldr.datev.advanced.DatevFile;
import io.github.mrtyldr.datev.core.DatevCsv;
import io.github.mrtyldr.datev.core.DatevHeader;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.CodingErrorAction;
import java.util.List;
import java.util.Objects;

/**
 * Univocity writers configured for a {@link DatevFile}.
 *
 * <p>{@code datev-exporter-advanced} writes DATEV files on its own and pulls in no third-party
 * dependencies. This class exists for applications that already route their CSV output through
 * Univocity and want a {@link CsvWriter} that agrees with DATEV's quoting, delimiter, line
 * separator and charset rules:
 *
 * <pre>{@code
 * CsvWriter writer = DatevUnivocityWriters.newCsvWriter(file, outputStream);
 * DatevUnivocityWriters.writeTo(file, writer);
 * }</pre>
 *
 * <p>A {@link CsvWriter} emits one uniformly shaped record type and therefore cannot produce the
 * differently shaped 31-field EXTF management record. {@link #writeTo(DatevFile, CsvWriter)}
 * refuses a file that carries metadata; use {@link DatevFile#writeTo(OutputStream)} for a complete
 * file, or {@link #writeDataTo(DatevFile, CsvWriter)} when only the heading and booking rows are
 * intended.
 */
public final class DatevUnivocityWriters {

    private DatevUnivocityWriters() {
    }

    /**
     * Creates a fresh Univocity configuration for a file.
     *
     * <p>The returned settings may be customized without affecting the file. A fresh
     * {@link CsvWriter} writes the configured heading automatically before its first data row; for
     * an empty file call {@link CsvWriter#writeHeaders()} explicitly. These settings describe only
     * the heading and booking-row section.
     *
     * @param file the file whose header and quoting rules apply
     * @return a fresh writer configuration
     */
    public static CsvWriterSettings csvWriterSettings(DatevFile file) {
        return writerSettings(Objects.requireNonNull(file, "file").header(), true, true);
    }

    /**
     * Creates a configured writer using the file's charset.
     *
     * <p>Unmappable characters are reported instead of being silently replaced. Closing the
     * returned writer flushes but does not close the supplied stream.
     *
     * @param file the file whose header and charset apply
     * @param output the caller-owned output stream
     * @return a configured writer that writes to {@code output}
     */
    public static CsvWriter newCsvWriter(DatevFile file, OutputStream output) {
        return newCsvWriter(file, output, csvWriterSettings(file));
    }

    /**
     * Creates a writer with caller-customized settings while retaining strict charset handling and
     * caller ownership of the output stream.
     *
     * @param file the file whose charset applies
     * @param output the caller-owned output stream
     * @param settings the writer settings to use
     * @return a writer that writes to {@code output} using {@code settings}
     */
    public static CsvWriter newCsvWriter(
            DatevFile file,
            OutputStream output,
            CsvWriterSettings settings
    ) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(settings, "settings");
        var encoder = file.charset().newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return new CsvWriter(
                new NonClosingWriter(new OutputStreamWriter(output, encoder)),
                settings
        );
    }

    /**
     * Creates a configured writer around a caller-provided character writer.
     *
     * @param file the file whose header and quoting rules apply
     * @param output the caller-owned character writer
     * @return a configured writer that writes to {@code output}
     */
    public static CsvWriter newCsvWriter(DatevFile file, Writer output) {
        return newCsvWriter(file, output, csvWriterSettings(file));
    }

    /**
     * Creates a writer around a caller-provided character writer and customized settings.
     *
     * @param file the file whose header applies
     * @param output the caller-owned character writer
     * @param settings the writer settings to use
     * @return a writer that writes to {@code output} using {@code settings}
     */
    public static CsvWriter newCsvWriter(DatevFile file, Writer output, CsvWriterSettings settings) {
        Objects.requireNonNull(file, "file");
        return new CsvWriter(
                new NonClosingWriter(Objects.requireNonNull(output, "output")),
                Objects.requireNonNull(settings, "settings")
        );
    }

    /**
     * Writes a data-only file to a fresh, compatibly configured writer and flushes it.
     *
     * <p>The writer is not closed. It must not already contain records and should have been
     * created from {@link #csvWriterSettings(DatevFile)}.
     *
     * @param file the file to write
     * @param writer the fresh, compatibly configured destination writer
     * @throws IllegalStateException if the file carries EXTF metadata
     */
    public static void writeTo(DatevFile file, CsvWriter writer) {
        Objects.requireNonNull(file, "file");
        if (file.metadata().isPresent()) {
            throw new IllegalStateException(
                    "A CsvWriter cannot emit the EXTF management record; use "
                            + "DatevFile.writeTo(OutputStream/Writer) for a complete file or "
                            + "writeDataTo(file, writer) explicitly."
            );
        }
        writeDataTo(file, writer);
    }

    /**
     * Writes only the DATEV heading and booking rows through a caller-provided writer.
     *
     * <p>This deliberately omits the EXTF management record even when metadata is configured. The
     * writer is flushed but not closed.
     *
     * @param file the file to write
     * @param writer the fresh, compatibly configured destination writer
     */
    public static void writeDataTo(DatevFile file, CsvWriter writer) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(writer, "writer");
        writer.writeHeaders();
        writer.writeRows(file);
        writer.flush();
    }

    private static CsvWriterSettings writerSettings(
            DatevHeader header,
            boolean writeHeader,
            boolean quoteDatevFields
    ) {
        CsvFormat format = new CsvFormat();
        format.setDelimiter(DatevCsv.DELIMITER);
        format.setLineSeparator(DatevCsv.LINE_SEPARATOR);

        CsvWriterSettings settings = new CsvWriterSettings();
        settings.setFormat(format);
        settings.setHeaders(header.names().toArray(String[]::new));
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
        settings.setMaxColumns(header.size());
        List<Integer> quotedIndexes = header.quotedColumnIndexes();
        if (quoteDatevFields && !quotedIndexes.isEmpty()) {
            settings.quoteIndexes(quotedIndexes.toArray(Integer[]::new));
        }
        return settings;
    }

    /** Flushes on close but leaves the caller-owned destination open. */
    private static final class NonClosingWriter extends FilterWriter {
        private NonClosingWriter(Writer destination) {
            super(destination);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
