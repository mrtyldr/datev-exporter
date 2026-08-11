package io.github.mrtyldr.datev.plain;

import io.github.mrtyldr.datev.core.DatevSchema;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevFileWriterTest {

    @Test
    void emptyFileWritesTheExactUnquotedFixedHeaderAndCrLf() {
        DatevFile file = DatevFile.withDefaults();

        assertEquals(
                String.join(";", DatevSchema.CURRENT_V13.headers()) + "\r\n",
                file.toCsvString()
        );
    }

    @Test
    void alwaysQuotesTextFieldsAndGenericallyEscapesOtherFields() {
        DatevFile file = DatevFile.withDefaults();
        String[] row = new String[DatevSchema.CURRENT_V13.columnCount()];
        row[0] = "12,50";
        row[1] = "S";
        row[2] = "EUR";
        row[3] = "a;b";
        row[6] = "1000";
        row[8] = "12\"34";
        row[9] = "1108";
        row[13] = "Invoice; \"42\"";
        file.append(row);

        String dataLine = file.toCsvString().split("\r\n", -1)[1];

        assertTrue(dataLine.startsWith(
                "12,50;\"S\";\"EUR\";\"a;b\";;\"\";1000;;\"12\"\"34\";1108;\"\";\"\";;"
                        + "\"Invoice; \"\"42\"\"\";"
        ));
        assertTrue(dataLine.endsWith(";"));
    }

    @Test
    void nullAndEmptyTextCellsAreQuotedWhileNumericCellsRemainEmpty() {
        DatevFile file = DatevFile.withDefaults();
        String[] row = new String[DatevSchema.CURRENT_V13.columnCount()];
        row[1] = "";
        file.append(row);

        String dataLine = file.toCsvString().split("\r\n", -1)[1];

        assertTrue(dataLine.startsWith(";\"\";\"\";;;\"\";"));
    }

    @Test
    void v12AndV13EmitTheirExactNumberOfColumns() {
        DatevFile v12 = DatevFile.legacyV12();
        DatevFile v13 = DatevFile.withDefaults();
        v12.append(Map.of("Konto", "1000"));
        v13.append(Map.of("Konto", "1000"));

        String[] v12Lines = v12.toCsvString().split("\r\n", -1);
        String[] v13Lines = v13.toCsvString().split("\r\n", -1);

        assertEquals(123, count(v12Lines[0], ';'));
        assertEquals(123, count(v12Lines[1], ';'));
        assertEquals(124, count(v13Lines[0], ';'));
        assertEquals(124, count(v13Lines[1], ';'));
    }

    @Test
    void byteOutputIsExactWindows1252AndCallerStreamRemainsOpen() throws IOException {
        DatevFile file = DatevFile.withDefaults();
        file.append(Map.of("Buchungstext", "Müller"));
        CloseTrackingOutputStream output = new CloseTrackingOutputStream();

        file.write(output);
        output.write('!');

        byte[] expected = (file.toCsvString() + "!").getBytes(DatevFile.DEFAULT_CHARSET);
        assertArrayEquals(expected, output.delegate.toByteArray());
        assertFalse(output.closed);
        assertArrayEquals(
                file.toCsvString().getBytes(DatevFile.DEFAULT_CHARSET),
                file.toByteArray()
        );
    }

    @Test
    void characterWriterIsFlushedButNeverClosed() {
        DatevFile file = DatevFile.legacyV12();
        file.append(Map.of("Konto", "1000"));
        CloseTrackingWriter writer = new CloseTrackingWriter();

        file.write(writer);
        writer.write(new char[]{'!'}, 0, 1);

        assertEquals(file.toCsvString() + "!", writer.delegate.toString());
        assertTrue(writer.flushed);
        assertFalse(writer.closed);
    }

    @Test
    void ioFailuresAreReportedAsUncheckedIoExceptions() {
        DatevFile file = DatevFile.withDefaults();
        Writer broken = new Writer() {
            @Override
            public void write(char[] buffer, int offset, int length) throws IOException {
                throw new IOException("broken");
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        assertThrows(java.io.UncheckedIOException.class, () -> file.writeTo(broken));
    }

    @Test
    void outputAlwaysEndsEveryRecordWithCrLf() {
        DatevFile file = DatevFile.withDefaults();
        file.append(Map.of());
        file.append(Map.of("Konto", "1000"));

        String csv = file.toCsvString();

        assertTrue(csv.endsWith("\r\n"));
        assertEquals(3, countOccurrences(csv, "\r\n"));
        assertFalse(csv.replace("\r\n", "").contains("\n"));
        assertFalse(csv.replace("\r\n", "").contains("\r"));
    }

    @Test
    void writesStoredRecordsIndividuallyInsteadOfMaterializingTheWholeDocument() {
        DatevFile file = DatevFile.withDefaults();
        file.append(Map.of("Konto", "1000"));
        file.append(Map.of("Konto", "2000"));
        CountingWriter output = new CountingWriter();

        file.writeTo(output);

        assertEquals(3, output.writeCalls);
        assertEquals(file.toCsvString(), output.delegate.toString());
    }

    private static int count(String value, char searched) {
        int result = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == searched) {
                result++;
            }
        }
        return result;
    }

    private static int countOccurrences(String value, String searched) {
        return (value.length() - value.replace(searched, "").length()) / searched.length();
    }

    private static final class CloseTrackingOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private boolean closed;

        @Override
        public void write(int value) {
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            delegate.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class CloseTrackingWriter extends Writer {
        private final StringWriter delegate = new StringWriter();
        private boolean flushed;
        private boolean closed;

        @Override
        public void write(char[] buffer, int offset, int length) {
            delegate.write(buffer, offset, length);
        }

        @Override
        public void flush() {
            flushed = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class CountingWriter extends Writer {
        private final StringWriter delegate = new StringWriter();
        private int writeCalls;

        @Override
        public void write(char[] buffer, int offset, int length) {
            writeCalls++;
            delegate.write(buffer, offset, length);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
