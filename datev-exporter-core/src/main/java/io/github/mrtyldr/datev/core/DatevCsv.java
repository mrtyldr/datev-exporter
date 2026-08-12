package io.github.mrtyldr.datev.core;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
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

    /** What a decoder substitutes for a byte the charset leaves unassigned: U+FFFD. */
    private static final char REPLACEMENT_CHARACTER = (char) 0xFFFD;

    private static final long[] ENCODABLE_CODE_POINTS = encodableCodePoints();
    private static final int TABLE_LIMIT = ENCODABLE_CODE_POINTS.length * Long.SIZE - 1;

    private DatevCsv() {
    }

    /**
     * How a value fares against the two constraints every exported DATEV cell must satisfy.
     *
     * <p>Deliberately package-private: it is the shared vocabulary of the scan below and of the
     * validation engine next to it, not public API. An enum rather than {@code int} constants
     * because the two in-package call sites switch on it, and enum constants are singletons, so
     * the type safety costs nothing on the row hot path.
     */
    enum OutputSafety {
        /** The value can be written unchanged. */
        SAFE,

        /** The value contains a control, line- or paragraph-separator character. */
        CONTROL_CHARACTER,

        /** The value contains a code point Windows-1252 cannot represent. */
        UNMAPPABLE_CHARACTER
    }

    /**
     * Rejects a value that no DATEV exporter could write out unchanged.
     *
     * <p>Every exported cell must satisfy two independent constraints: it must not contain control
     * or line-separator characters, and it must be representable in {@link #CHARSET}. This method
     * checks both in a single pass and reports the first violated one, with a control character
     * always winning over an unmappable one no matter which appears first.
     *
     * @param value the logical, unquoted value to check
     * @param description how the value is named in the failure message
     * @throws IllegalArgumentException if the value contains a control or line-separator character,
     *     or a character Windows-1252 cannot encode
     */
    public static void requireExportable(String value, String description) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(description, "description");
        switch (inspectOutputSafety(value)) {
            case CONTROL_CHARACTER -> throw new IllegalArgumentException(
                    description + " must not contain control or line-separator characters.");
            // The encoding failure has always been phrased as a sentence about the value rather
            // than as a continuation of the description, so the subject is capitalized here to keep
            // both messages byte-identical to the ones exporters have reported since 0.1.0.
            case UNMAPPABLE_CHARACTER -> throw new IllegalArgumentException(
                    capitalized(description) + " cannot be encoded as Windows-1252.");
            case SAFE -> {
                // Nothing to reject.
            }
        }
    }

    private static String capitalized(String description) {
        if (description.isEmpty() || Character.isUpperCase(description.charAt(0))) {
            return description;
        }
        return Character.toUpperCase(description.charAt(0)) + description.substring(1);
    }

    /**
     * Inspects one logical value against both DATEV output constraints in a single pass.
     *
     * <p>Checking both constraints in one scan avoids walking each value twice, and the
     * precomputed encodability table removes the buffer allocations that
     * {@code CharsetEncoder.canEncode(CharSequence)} performs per call.
     *
     * <p>A control character always wins over an unmappable one, no matter which appears first, so
     * exporters keep reporting the same failure for a value that violates both constraints.
     *
     * <p>The table is derived at class-initialization time from the JDK's own {@code windows-1252}
     * charset and pinned to {@link CharsetEncoder#canEncode(CharSequence)} by a test that walks
     * the whole Basic Multilingual Plane, so it cannot drift away from the JDK's charset
     * implementation. Code points outside the table, including unpaired surrogates and every
     * supplementary code point, are reported as unmappable, which matches {@code canEncode} too.
     *
     * <p>Package-private because callers outside this package must go through
     * {@link #requireExportable(String, String)}; the validation engine in this package needs the
     * outcome itself, because it reports two distinct {@link DatevValidationError}s instead of
     * throwing.
     *
     * @param value the logical, unquoted value to inspect
     * @return how the value fares against both constraints
     */
    static OutputSafety inspectOutputSafety(String value) {
        Objects.requireNonNull(value, "value");
        boolean unmappable = false;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                return OutputSafety.CONTROL_CHARACTER;
            }
            if (!unmappable && !canEncode(codePoint)) {
                unmappable = true;
            }
        }
        return unmappable ? OutputSafety.UNMAPPABLE_CHARACTER : OutputSafety.SAFE;
    }

    private static boolean canEncode(int codePoint) {
        if (codePoint < 0 || codePoint > TABLE_LIMIT) {
            return false;
        }
        return (ENCODABLE_CODE_POINTS[codePoint >>> 6] & 1L << codePoint) != 0L;
    }

    /**
     * Builds the encodability bit set by decoding, rather than by encoding.
     *
     * <p>Windows-1252 is a single-byte charset, so it can represent at most 256 code points and a
     * code point is encodable exactly when some byte decodes to it. Decoding the 256 possible bytes
     * once therefore yields the same table as probing all 65 536 BMP code points against the
     * encoder, at roughly 1/250 of the class-initialization cost.
     *
     * <p>The five unassigned Windows-1252 positions decode to {@code U+FFFD}, which the encoder
     * cannot represent; they are filtered out explicitly so the table keeps agreeing with
     * {@code CharsetEncoder.canEncode}. The round trip through the decoder replaces the
     * unassigned byte rather than reporting it, so the filter is on the decoded character.
     */
    private static long[] encodableCodePoints() {
        CharsetDecoder decoder = CHARSET.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        byte[] allBytes = new byte[256];
        for (int value = 0; value < allBytes.length; value++) {
            allBytes[value] = (byte) value;
        }
        CharBuffer decoded;
        try {
            decoded = decoder.decode(ByteBuffer.wrap(allBytes));
        } catch (CharacterCodingException exception) {
            throw new IllegalStateException("Could not derive the " + CHARSET + " table.", exception);
        }

        long[] bits = new long[(Character.MAX_VALUE + 1) / Long.SIZE];
        int highest = 0;
        for (int index = 0; index < decoded.length(); index++) {
            char character = decoded.charAt(index);
            if (character == REPLACEMENT_CHARACTER || Character.isSurrogate(character)) {
                continue;
            }
            bits[character >>> 6] |= 1L << character;
            highest = Math.max(highest, character);
        }
        long[] trimmed = new long[(highest >>> 6) + 1];
        System.arraycopy(bits, 0, trimmed, 0, trimmed.length);
        return trimmed;
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
     * <p>Unlike {@link #requireExportable(String, String)} this checks the record structure only,
     * so a value that Windows-1252 cannot encode passes. Callers that serialize a value into an
     * exported cell want {@code requireExportable}; callers that check something which is not a
     * cell, such as a raw CSV row, want this method.
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
