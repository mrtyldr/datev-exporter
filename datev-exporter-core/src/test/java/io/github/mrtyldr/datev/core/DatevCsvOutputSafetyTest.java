package io.github.mrtyldr.datev.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins {@link DatevCsv#requireExportable(String, String)}, the package-private scan behind it and
 * the precomputed encodability table to the JDK charset they replace. Any divergence would
 * silently change which values every exporter accepts.
 *
 * <p>The table is derived from 256 decoded bytes rather than from 65 536 encoder probes, so this
 * test walking the whole Basic Multilingual Plane is what proves the two routes agree.
 */
class DatevCsvOutputSafetyTest {

    @Test
    void theTableAgreesWithTheJdkEncoderForEveryBasicMultilingualPlaneCodePoint() {
        CharsetEncoder encoder = DatevCsv.CHARSET.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        for (int codePoint = 0; codePoint <= Character.MAX_VALUE; codePoint++) {
            String character = String.valueOf((char) codePoint);
            DatevCsv.OutputSafety expected;
            if (isControlOrLineSeparator(codePoint)) {
                // Windows-1252 encodes these, but a DATEV record cannot carry them.
                expected = DatevCsv.OutputSafety.CONTROL_CHARACTER;
            } else {
                expected = encoder.canEncode(character)
                        ? DatevCsv.OutputSafety.SAFE
                        : DatevCsv.OutputSafety.UNMAPPABLE_CHARACTER;
            }
            int inspected = codePoint;
            assertEquals(expected, DatevCsv.inspectOutputSafety(character),
                    () -> "Mismatch at code point " + Integer.toHexString(inspected));
        }
    }

    @Test
    void supplementaryCodePointsAreNeverEncodable() {
        assertEquals(DatevCsv.OutputSafety.UNMAPPABLE_CHARACTER, DatevCsv.inspectOutputSafety(
                new String(Character.toChars(Character.MIN_SUPPLEMENTARY_CODE_POINT))));
        assertEquals(DatevCsv.OutputSafety.UNMAPPABLE_CHARACTER,
                DatevCsv.inspectOutputSafety("🙂"));
        assertEquals(DatevCsv.OutputSafety.UNMAPPABLE_CHARACTER, DatevCsv.inspectOutputSafety(
                new String(Character.toChars(Character.MAX_CODE_POINT))));
        // An unpaired surrogate is unmappable too, exactly as CharsetEncoder.canEncode reports it.
        assertEquals(DatevCsv.OutputSafety.UNMAPPABLE_CHARACTER,
                DatevCsv.inspectOutputSafety("\uD83D"));
    }

    @Test
    void controlCharactersAreReportedBeforeUnmappableOnes() {
        assertEquals(DatevCsv.OutputSafety.SAFE, DatevCsv.inspectOutputSafety(""));
        assertEquals(DatevCsv.OutputSafety.SAFE, DatevCsv.inspectOutputSafety("Müller €"));
        assertEquals(DatevCsv.OutputSafety.UNMAPPABLE_CHARACTER,
                DatevCsv.inspectOutputSafety("🙂"));
        assertEquals(DatevCsv.OutputSafety.CONTROL_CHARACTER,
                DatevCsv.inspectOutputSafety("🙂" + (char) 0x01));
        assertEquals(DatevCsv.OutputSafety.CONTROL_CHARACTER,
                DatevCsv.inspectOutputSafety((char) 0x01 + "🙂"));
        assertEquals(DatevCsv.OutputSafety.CONTROL_CHARACTER,
                DatevCsv.inspectOutputSafety("a" + (char) 0x2028));
    }

    @Test
    void requireExportableReportsTheExporterMessagesInTheDocumentedOrder() {
        String description = "value for DATEV header 'Buchungstext'";

        assertDoesNotThrow(() -> DatevCsv.requireExportable("Müller €", description));
        assertDoesNotThrow(() -> DatevCsv.requireExportable("", description));

        assertEquals("value for DATEV header 'Buchungstext' must not contain control or"
                        + " line-separator characters.",
                assertThrows(IllegalArgumentException.class,
                        () -> DatevCsv.requireExportable("a" + (char) 0x01, description))
                        .getMessage());
        assertEquals("Value for DATEV header 'Buchungstext' cannot be encoded as Windows-1252.",
                assertThrows(IllegalArgumentException.class,
                        () -> DatevCsv.requireExportable("🙂", description)).getMessage());
        // A value violating both is reported as a control character, whichever comes first.
        assertEquals("value for DATEV header 'Buchungstext' must not contain control or"
                        + " line-separator characters.",
                assertThrows(IllegalArgumentException.class,
                        () -> DatevCsv.requireExportable("🙂" + (char) 0x01, description))
                        .getMessage());

        // rejectControlCharacters keeps its narrower contract: unmappable is not its business.
        assertDoesNotThrow(() -> DatevCsv.rejectControlCharacters("🙂", description));
    }

    @Test
    void aNullValueIsRejected() {
        assertThrows(NullPointerException.class, () -> DatevCsv.inspectOutputSafety(null));
        assertThrows(NullPointerException.class, () -> DatevCsv.requireExportable(null, "value"));
        assertThrows(NullPointerException.class, () -> DatevCsv.requireExportable("a", null));
    }

    private static boolean isControlOrLineSeparator(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }
}
