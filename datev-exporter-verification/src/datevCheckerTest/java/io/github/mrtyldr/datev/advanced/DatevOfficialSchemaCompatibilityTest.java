package io.github.mrtyldr.datev.advanced;

import io.github.mrtyldr.datev.core.DatevFieldSpec;
import io.github.mrtyldr.datev.core.DatevFieldSpecs;
import io.github.mrtyldr.datev.core.DatevSchema;
import io.github.mrtyldr.datev.core.DatevFieldType;
import io.github.mrtyldr.datev.core.DatevValidationContext;
import io.github.mrtyldr.datev.core.DatevValidationError;
import io.github.mrtyldr.datev.core.DatevValidationMode;

import io.github.mrtyldr.datev.validation.DatevValidator;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in compatibility checks against the schema shipped with DATEV's official checker. */
class DatevOfficialSchemaCompatibilityTest {
    private static final String EXPECTED_SCHEMA_SHA256 =
            "ff7ed8c3612ac12201508ced21e3aff5215fd898ad91a6bb95f6f407021f1956";
    private static final Map<Integer, String> EXTERNAL_HEADER_NAMES = Map.of(
            7, "Konto",
            37, "KOST1 - Kostenstelle",
            38, "KOST2 - Kostenstelle",
            96, "Buchungstyp",
            104, "KOST-Datum"
    );
    private static final Pattern INTEGER = Pattern.compile("[0-9]+");
    private static final DateTimeFormatter BASIC_DATE =
            DateTimeFormatter.BASIC_ISO_DATE.withResolverStyle(ResolverStyle.STRICT);

    @Test
    void libraryFieldDefinitionsMatchAllOfficialCheckerFields() throws Exception {
        Document document = readOfficialSchema();
        assertEquals("13", directText(firstDirectChild(document.getDocumentElement(), "Format"),
                "Version"));

        List<CheckerField> checkerFields = checkerFields(document);
        List<DatevFieldSpec> libraryFields = DatevFieldSpecs.version13();

        assertEquals(125, checkerFields.size());
        assertEquals(125, libraryFields.size());
        assertEquals(EXPECTED_SCHEMA_SHA256, schemaFingerprint(checkerFields));

        for (int index = 0; index < checkerFields.size(); index++) {
            CheckerField checker = checkerFields.get(index);
            DatevFieldSpec library = libraryFields.get(index);
            int fieldNumber = index + 1;

            assertEquals(fieldNumber, checker.ordinal() + 1, "checker ordinal");
            assertEquals(fieldNumber, library.fieldNumber(), "library field number");
            assertEquals(
                    EXTERNAL_HEADER_NAMES.getOrDefault(fieldNumber, checker.label()),
                    library.canonicalKey(),
                    "field " + fieldNumber + " header"
            );
            assertEquals(checker.type(), library.type().checkerName(),
                    "field " + fieldNumber + " type");
            assertEquals(checker.maxLength(), library.maxLength(),
                    "field " + fieldNumber + " length");
            assertEquals(checker.decimalPlaces(), library.decimalPlaces(),
                    "field " + fieldNumber + " decimal places");
            assertEquals(checker.required(), library.required(),
                    "field " + fieldNumber + " necessary flag");
        }

        assertEquals(
                DatevHeader.current().names(),
                libraryFields.stream().map(DatevFieldSpec::canonicalKey).toList()
        );
    }

    /**
     * Closes the verification gap that used to exist while the header table and the field-spec
     * table were duplicated per module: the loop above verifies exactly one table against the
     * official XML, so this test pins that every module really consumes that same table.
     */
    @Test
    void everyModuleDerivesItsSchemaFromTheSingleVerifiedCoreTable() {
        List<DatevFieldSpec> core = DatevFieldSpecs.version13();
        List<String> canonicalKeys = core.stream().map(DatevFieldSpec::canonicalKey).toList();

        assertEquals(core, DatevSchema.CURRENT_V13.fieldSpecs());
        assertEquals(canonicalKeys, DatevSchema.CURRENT_V13.headers());
        assertEquals(canonicalKeys, DatevFieldSpecs.headers13());
        assertEquals(canonicalKeys.subList(0, 124), DatevFieldSpecs.headers12());
        assertEquals(DatevFieldSpecs.version12(), DatevSchema.LEGACY_V12.fieldSpecs());

        assertEquals(canonicalKeys, DatevHeader.current().names());
        assertEquals(canonicalKeys, DatevHeader.current().keys());
        assertEquals(canonicalKeys.subList(0, 124), DatevHeader.legacyV12().names());

        assertTrue(DatevFieldSpecs.isOfficialSchema(canonicalKeys));
        assertTrue(DatevFieldSpecs.isOfficialSchema(canonicalKeys.subList(0, 124)));
    }

    @Test
    void generatedCompleteFileMatchesOfficialSchemaAndCsvContract() throws Exception {
        Path fixture = requiredPath("datev.checker.fixture");
        assertEquals("EXTF_Buchungsstapel.csv", fixture.getFileName().toString());

        String csv = decodeWindows1252Strict(Files.readAllBytes(fixture));
        assertTrue(csv.endsWith("\r\n"), "DATEV records must end in CRLF");
        String withoutCrLf = csv.replace("\r\n", "");
        assertFalse(withoutCrLf.contains("\r"), "lone CR is not allowed");
        assertFalse(withoutCrLf.contains("\n"), "lone LF is not allowed");

        String[] records = csv.substring(0, csv.length() - 2).split("\r\n", -1);
        assertEquals(3, records.length, "metadata, heading and one booking record expected");

        List<CsvCell> metadata = parseCsvRecord(records[0]);
        assertMetadata(metadata);

        List<CsvCell> heading = parseCsvRecord(records[1]);
        assertEquals(DatevHeader.current().names(), values(heading));
        assertTrue(heading.stream().noneMatch(CsvCell::quoted),
                "official DATEV heading labels must remain unquoted");

        List<CsvCell> booking = parseCsvRecord(records[2]);
        List<DatevFieldSpec> specs = DatevFieldSpecs.version13();
        assertEquals(specs.size(), booking.size());
        for (int index = 0; index < specs.size(); index++) {
            assertCellMatches(specs.get(index), booking.get(index));
        }

        assertEquals("1234,56", booking.get(0).value());
        assertEquals("S", booking.get(1).value());
        assertEquals("1000", booking.get(6).value());
        assertEquals("8400", booking.get(7).value());
        assertEquals("1008", booking.get(9).value());
        LocalDate documentDate = LocalDate.parse("10082026",
                DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT));
        assertFalse(documentDate.isBefore(LocalDate.of(2026, 8, 1)));
        assertFalse(documentDate.isAfter(LocalDate.of(2026, 8, 31)));

        Element csvProperties = firstDirectChild(readOfficialSchema().getDocumentElement(),
                "CsvFormatProperties");
        assertEquals(";", directText(csvProperties, "SeperatorField"));
        assertEquals("\"", directText(csvProperties, "SeperatorText"));
        assertEquals("ANSI", directText(csvProperties, "Coding"));
        assertEquals("1", directText(csvProperties, "Headline"));
    }

    @Test
    void allRowsInThePinnedOfficialBookingSamplePassStrictValidation() throws Exception {
        Path sample = requiredPath("datev.official.bookingSample");
        String csv = decodeUtf8Strict(Files.readAllBytes(sample));
        assertTrue(csv.endsWith("\r\n"));
        String[] records = csv.substring(0, csv.length() - 2).split("\r\n", -1);

        assertEquals(56, records.length, "metadata, heading and 54 official rows expected");
        assertEquals(DatevHeader.current().names(), values(parseCsvRecord(records[1])));

        DatevMetadata metadata = DatevMetadata.bookingBatchV13()
                .createdAt(LocalDateTime.of(2024, 1, 30, 14, 4, 40, 439_000_000))
                .origin("RE")
                .advisorNumber(29098)
                .clientNumber(55003)
                .fiscalYearStart(LocalDate.of(2024, 1, 1))
                .accountLength(4)
                .period(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 8, 31))
                .description("Buchungsstapel")
                .dictationCode("WD")
                .fixed(false)
                .chartOfAccounts("03")
                .build();
        assertEquals(metadata.toCsvLine(), records[0]);
        DatevValidator leanValidator = DatevValidator.builder()
                .context(DatevValidationContext.builder()
                        .accountLength(metadata.accountLength())
                        .fiscalYearStart(metadata.fiscalYearStart())
                        .period(metadata.periodStart(), metadata.periodEnd())
                        .build())
                .build();

        for (int record = 2; record < records.length; record++) {
            List<String> row = values(parseCsvRecord(records[record]));
            assertEquals(125, row.size(), "official row " + (record - 1) + " width");
            List<DatevValidationError> errors = DatevRowValidator.validate(
                    DatevHeader.current(),
                    row,
                    DatevValidationMode.STRICT,
                    metadata
            );
            int bookingNumber = record - 1;
            assertTrue(errors.isEmpty(),
                    () -> "official booking row " + bookingNumber + " failed: " + errors);
            List<io.github.mrtyldr.datev.core.DatevValidationError> leanErrors =
                    leanValidator.validate(
                            io.github.mrtyldr.datev.core.DatevSchema.CURRENT_V13,
                            row
                    );
            assertTrue(leanErrors.isEmpty(),
                    () -> "official booking row " + bookingNumber
                            + " failed optional lean validation: " + leanErrors);
        }
    }

    private static void assertMetadata(List<CsvCell> metadata) {
        assertEquals(31, metadata.size());
        assertEquals("EXTF", metadata.get(0).value());
        assertEquals("700", metadata.get(1).value());
        assertEquals("21", metadata.get(2).value());
        assertEquals("Buchungsstapel", metadata.get(3).value());
        assertEquals("13", metadata.get(4).value());
        assertEquals("20260810123456789", metadata.get(5).value());
        assertEquals("1001", metadata.get(10).value());
        assertEquals("1", metadata.get(11).value());
        assertEquals("20260101", metadata.get(12).value());
        assertEquals("4", metadata.get(13).value());
        assertEquals("20260801", metadata.get(14).value());
        assertEquals("20260831", metadata.get(15).value());
        assertEquals("EUR", metadata.get(21).value());

        LocalDate fiscalYearStart = LocalDate.parse(metadata.get(12).value(), BASIC_DATE);
        LocalDate periodStart = LocalDate.parse(metadata.get(14).value(), BASIC_DATE);
        LocalDate periodEnd = LocalDate.parse(metadata.get(15).value(), BASIC_DATE);
        assertFalse(periodStart.isBefore(fiscalYearStart));
        assertFalse(periodEnd.isBefore(periodStart));
    }

    private static void assertCellMatches(DatevFieldSpec spec, CsvCell cell) {
        String message = "field " + spec.fieldNumber() + " (" + spec.canonicalKey() + ")";
        if (spec.required()) {
            assertFalse(cell.value().isEmpty(), message + " is required");
        }
        if (spec.type() == DatevFieldType.TEXT) {
            assertTrue(cell.quoted(), message + " must be quoted");
        } else {
            assertFalse(cell.quoted(), message + " must not be quoted");
        }
        if (cell.value().isEmpty()) {
            return;
        }

        switch (spec.type()) {
            case TEXT -> assertTrue(
                    cell.value().codePointCount(0, cell.value().length()) <= spec.maxLength(),
                    message + " exceeds its text length"
            );
            case ACCOUNT -> {
                assertTrue(INTEGER.matcher(cell.value()).matches(), message + " is not an account");
                assertTrue(cell.value().length() <= spec.maxLength(), message + " is too long");
            }
            case AMOUNT -> {
                String expression = "[0-9]{1," + spec.maxLength() + "},[0-9]{"
                        + spec.decimalPlaces() + "}";
                assertTrue(cell.value().matches(expression), message + " is not an amount");
                assertFalse(cell.value().matches("0+,0+"), message + " must be non-zero");
            }
            case NUMBER -> {
                String expression = spec.decimalPlaces() == 0
                        ? "[0-9]{1," + spec.maxLength() + "}"
                        : "[0-9]{1," + spec.maxLength() + "}(?:,[0-9]{1,"
                        + spec.decimalPlaces() + "})?";
                assertTrue(cell.value().matches(expression), message + " is not numeric");
            }
            case DATE -> {
                assertTrue(cell.value().matches("[0-9]{4}|[0-9]{8}"),
                        message + " is not a DATEV date");
                assertTrue(cell.value().length() <= spec.maxLength(), message + " is too long");
            }
        }
    }

    private static Document readOfficialSchema() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(requiredPath("datev.checker.schema").toFile());
    }

    private static List<CheckerField> checkerFields(Document document) {
        NodeList nodes = document.getDocumentElement().getChildNodes();
        List<CheckerField> result = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (!(node instanceof Element field) || !"Field".equals(field.getTagName())) {
                continue;
            }
            result.add(new CheckerField(
                    integer(field, "OrdinalNumber"),
                    integer(field, "FieldId"),
                    directText(field, "Label"),
                    directText(field, "FormatType"),
                    integer(field, "Length"),
                    integer(field, "DecimalPlaces"),
                    "1".equals(directText(field, "Necessary"))
            ));
        }
        result.sort(Comparator.comparingInt(CheckerField::ordinal));
        return List.copyOf(result);
    }

    private static String schemaFingerprint(List<CheckerField> fields) throws Exception {
        StringBuilder canonical = new StringBuilder();
        for (CheckerField field : fields) {
            canonical.append(field.ordinal()).append('|')
                    .append(field.fieldId()).append('|')
                    .append(field.label()).append('|')
                    .append(field.type()).append('|')
                    .append(field.maxLength()).append('|')
                    .append(field.decimalPlaces()).append('|')
                    .append(field.required() ? '1' : '0').append('\n');
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static Element firstDirectChild(Element parent, String name) {
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        throw new IllegalArgumentException("Missing XML element: " + name);
    }

    private static String directText(Element parent, String name) {
        return firstDirectChild(parent, name).getTextContent().trim();
    }

    private static int integer(Element parent, String name) {
        return Integer.parseInt(directText(parent, name));
    }

    private static Path requiredPath(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing system property: " + property);
        }
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Missing file for " + property + ": " + path);
        }
        return path;
    }

    private static String decodeWindows1252Strict(byte[] bytes) throws CharacterCodingException {
        return DatevFile.DEFAULT_CHARSET.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static String decodeUtf8Strict(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static List<String> values(List<CsvCell> cells) {
        return cells.stream().map(CsvCell::value).toList();
    }

    private static List<CsvCell> parseCsvRecord(String record) {
        List<CsvCell> cells = new ArrayList<>();
        int offset = 0;
        while (true) {
            if (offset == record.length()) {
                cells.add(new CsvCell("", false));
                break;
            }

            boolean quoted = record.charAt(offset) == '"';
            StringBuilder value = new StringBuilder();
            if (quoted) {
                offset++;
                boolean closed = false;
                while (offset < record.length()) {
                    char character = record.charAt(offset++);
                    if (character != '"') {
                        value.append(character);
                    } else if (offset < record.length() && record.charAt(offset) == '"') {
                        value.append('"');
                        offset++;
                    } else {
                        closed = true;
                        break;
                    }
                }
                if (!closed) {
                    throw new IllegalArgumentException("Unterminated quoted CSV field.");
                }
                if (offset < record.length() && record.charAt(offset) != ';') {
                    throw new IllegalArgumentException("Unexpected character after quoted CSV field.");
                }
            } else {
                while (offset < record.length() && record.charAt(offset) != ';') {
                    char character = record.charAt(offset++);
                    if (character == '"') {
                        throw new IllegalArgumentException("Quote inside unquoted CSV field.");
                    }
                    value.append(character);
                }
            }

            cells.add(new CsvCell(value.toString(), quoted));
            if (offset == record.length()) {
                break;
            }
            offset++;
            if (offset == record.length()) {
                cells.add(new CsvCell("", false));
                break;
            }
        }
        return List.copyOf(cells);
    }

    private record CheckerField(
            int ordinal,
            int fieldId,
            String label,
            String type,
            int maxLength,
            int decimalPlaces,
            boolean required
    ) {
    }

    private record CsvCell(String value, boolean quoted) {
    }
}
