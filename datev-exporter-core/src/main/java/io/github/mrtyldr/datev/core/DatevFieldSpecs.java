package io.github.mrtyldr.datev.core;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Official technical field metadata for Buchungsstapel format versions 12 and 13.
 *
 * <p>This is the single canonical copy of DATEV's column names and field definitions. Every
 * {@code datev-exporter} module derives its schema from this class.
 *
 * <p>Version 13 contains 125 fields. Version 12 is identical through field 124 and does not
 * contain {@code Abw. Skontokonto}.
 *
 * @see <a href="https://developer.datev.de/de/file-format/details/datev-format/tools">Official
 *     DATEV-Format checker</a>
 */
public final class DatevFieldSpecs {
    private static final List<String> HEADERS = """
            Umsatz (ohne Soll/Haben-Kz)
            Soll/Haben-Kennzeichen
            WKZ Umsatz
            Kurs
            Basis-Umsatz
            WKZ Basis-Umsatz
            Konto
            Gegenkonto (ohne BU-Schlüssel)
            BU-Schlüssel
            Belegdatum
            Belegfeld 1
            Belegfeld 2
            Skonto
            Buchungstext
            Postensperre
            Diverse Adressnummer
            Geschäftspartnerbank
            Sachverhalt
            Zinssperre
            Beleglink
            Beleginfo - Art 1
            Beleginfo - Inhalt 1
            Beleginfo - Art 2
            Beleginfo - Inhalt 2
            Beleginfo - Art 3
            Beleginfo - Inhalt 3
            Beleginfo - Art 4
            Beleginfo - Inhalt 4
            Beleginfo - Art 5
            Beleginfo - Inhalt 5
            Beleginfo - Art 6
            Beleginfo - Inhalt 6
            Beleginfo - Art 7
            Beleginfo - Inhalt 7
            Beleginfo - Art 8
            Beleginfo - Inhalt 8
            KOST1 - Kostenstelle
            KOST2 - Kostenstelle
            Kost-Menge
            EU-Land u. UStID (Bestimmung)
            EU-Steuersatz (Bestimmung)
            Abw. Versteuerungsart
            Sachverhalt L+L
            Funktionsergänzung L+L
            BU 49 Hauptfunktionstyp
            BU 49 Hauptfunktionsnummer
            BU 49 Funktionsergänzung
            Zusatzinformation - Art 1
            Zusatzinformation- Inhalt 1
            Zusatzinformation - Art 2
            Zusatzinformation- Inhalt 2
            Zusatzinformation - Art 3
            Zusatzinformation- Inhalt 3
            Zusatzinformation - Art 4
            Zusatzinformation- Inhalt 4
            Zusatzinformation - Art 5
            Zusatzinformation- Inhalt 5
            Zusatzinformation - Art 6
            Zusatzinformation- Inhalt 6
            Zusatzinformation - Art 7
            Zusatzinformation- Inhalt 7
            Zusatzinformation - Art 8
            Zusatzinformation- Inhalt 8
            Zusatzinformation - Art 9
            Zusatzinformation- Inhalt 9
            Zusatzinformation - Art 10
            Zusatzinformation- Inhalt 10
            Zusatzinformation - Art 11
            Zusatzinformation- Inhalt 11
            Zusatzinformation - Art 12
            Zusatzinformation- Inhalt 12
            Zusatzinformation - Art 13
            Zusatzinformation- Inhalt 13
            Zusatzinformation - Art 14
            Zusatzinformation- Inhalt 14
            Zusatzinformation - Art 15
            Zusatzinformation- Inhalt 15
            Zusatzinformation - Art 16
            Zusatzinformation- Inhalt 16
            Zusatzinformation - Art 17
            Zusatzinformation- Inhalt 17
            Zusatzinformation - Art 18
            Zusatzinformation- Inhalt 18
            Zusatzinformation - Art 19
            Zusatzinformation- Inhalt 19
            Zusatzinformation - Art 20
            Zusatzinformation- Inhalt 20
            Stück
            Gewicht
            Zahlweise
            Forderungsart
            Veranlagungsjahr
            Zugeordnete Fälligkeit
            Skontotyp
            Auftragsnummer
            Buchungstyp
            USt-Schlüssel (Anzahlungen)
            EU-Land (Anzahlungen)
            Sachverhalt L+L (Anzahlungen)
            EU-Steuersatz (Anzahlungen)
            Erlöskonto (Anzahlungen)
            Herkunft-Kz
            Buchungs GUID
            KOST-Datum
            SEPA-Mandatsreferenz
            Skontosperre
            Gesellschaftername
            Beteiligtennummer
            Identifikationsnummer
            Zeichnernummer
            Postensperre bis
            Bezeichnung SoBil-Sachverhalt
            Kennzeichen SoBil-Buchung
            Festschreibung
            Leistungsdatum
            Datum Zuord. Steuerperiode
            Fälligkeit
            Generalumkehr (GU)
            Steuersatz
            Land
            Abrechnungsreferenz
            BVV-Position
            EU-Land u. UStID (Ursprung)
            EU-Steuersatz (Ursprung)
            Abw. Skontokonto
            """.strip().lines().toList();

    private static final List<DatevFieldSpec> VERSION_13 = List.of(
            spec(1, DatevFieldType.AMOUNT, 10, 2, true),
            spec(2, DatevFieldType.TEXT, 1, 0, true),
            spec(3, DatevFieldType.TEXT, 3, 0, false),
            spec(4, DatevFieldType.NUMBER, 5, 6, false),
            spec(5, DatevFieldType.AMOUNT, 10, 2, false),
            spec(6, DatevFieldType.TEXT, 3, 0, false),
            spec(7, DatevFieldType.ACCOUNT, 9, 0, true),
            spec(8, DatevFieldType.ACCOUNT, 9, 0, true),
            spec(9, DatevFieldType.TEXT, 4, 0, false),
            spec(10, DatevFieldType.DATE, 8, 0, true),
            spec(11, DatevFieldType.TEXT, 36, 0, false),
            spec(12, DatevFieldType.TEXT, 12, 0, false),
            spec(13, DatevFieldType.AMOUNT, 8, 2, false),
            spec(14, DatevFieldType.TEXT, 60, 0, false),
            spec(15, DatevFieldType.NUMBER, 1, 0, false),
            spec(16, DatevFieldType.TEXT, 9, 0, false),
            spec(17, DatevFieldType.NUMBER, 3, 0, false),
            spec(18, DatevFieldType.NUMBER, 2, 0, false),
            spec(19, DatevFieldType.NUMBER, 1, 0, false),
            spec(20, DatevFieldType.TEXT, 210, 0, false),
            spec(21, DatevFieldType.TEXT, 20, 0, false),
            spec(22, DatevFieldType.TEXT, 210, 0, false),
            spec(23, DatevFieldType.TEXT, 20, 0, false),
            spec(24, DatevFieldType.TEXT, 210, 0, false),
            spec(25, DatevFieldType.TEXT, 20, 0, false),
            spec(26, DatevFieldType.TEXT, 210, 0, false),
            spec(27, DatevFieldType.TEXT, 20, 0, false),
            spec(28, DatevFieldType.TEXT, 210, 0, false),
            spec(29, DatevFieldType.TEXT, 20, 0, false),
            spec(30, DatevFieldType.TEXT, 210, 0, false),
            spec(31, DatevFieldType.TEXT, 20, 0, false),
            spec(32, DatevFieldType.TEXT, 210, 0, false),
            spec(33, DatevFieldType.TEXT, 20, 0, false),
            spec(34, DatevFieldType.TEXT, 210, 0, false),
            spec(35, DatevFieldType.TEXT, 20, 0, false),
            spec(36, DatevFieldType.TEXT, 210, 0, false),
            spec(37, DatevFieldType.TEXT, 36, 0, false),
            spec(38, DatevFieldType.TEXT, 36, 0, false),
            spec(39, DatevFieldType.NUMBER, 12, 4, false),
            spec(40, DatevFieldType.TEXT, 15, 0, false),
            spec(41, DatevFieldType.NUMBER, 2, 2, false),
            spec(42, DatevFieldType.TEXT, 1, 0, false),
            spec(43, DatevFieldType.NUMBER, 3, 0, false),
            spec(44, DatevFieldType.NUMBER, 3, 0, false),
            spec(45, DatevFieldType.NUMBER, 1, 0, false),
            spec(46, DatevFieldType.NUMBER, 2, 0, false),
            spec(47, DatevFieldType.NUMBER, 3, 0, false),
            spec(48, DatevFieldType.TEXT, 20, 0, false),
            spec(49, DatevFieldType.TEXT, 210, 0, false),
            spec(50, DatevFieldType.TEXT, 20, 0, false),
            spec(51, DatevFieldType.TEXT, 210, 0, false),
            spec(52, DatevFieldType.TEXT, 20, 0, false),
            spec(53, DatevFieldType.TEXT, 210, 0, false),
            spec(54, DatevFieldType.TEXT, 20, 0, false),
            spec(55, DatevFieldType.TEXT, 210, 0, false),
            spec(56, DatevFieldType.TEXT, 20, 0, false),
            spec(57, DatevFieldType.TEXT, 210, 0, false),
            spec(58, DatevFieldType.TEXT, 20, 0, false),
            spec(59, DatevFieldType.TEXT, 210, 0, false),
            spec(60, DatevFieldType.TEXT, 20, 0, false),
            spec(61, DatevFieldType.TEXT, 210, 0, false),
            spec(62, DatevFieldType.TEXT, 20, 0, false),
            spec(63, DatevFieldType.TEXT, 210, 0, false),
            spec(64, DatevFieldType.TEXT, 20, 0, false),
            spec(65, DatevFieldType.TEXT, 210, 0, false),
            spec(66, DatevFieldType.TEXT, 20, 0, false),
            spec(67, DatevFieldType.TEXT, 210, 0, false),
            spec(68, DatevFieldType.TEXT, 20, 0, false),
            spec(69, DatevFieldType.TEXT, 210, 0, false),
            spec(70, DatevFieldType.TEXT, 20, 0, false),
            spec(71, DatevFieldType.TEXT, 210, 0, false),
            spec(72, DatevFieldType.TEXT, 20, 0, false),
            spec(73, DatevFieldType.TEXT, 210, 0, false),
            spec(74, DatevFieldType.TEXT, 20, 0, false),
            spec(75, DatevFieldType.TEXT, 210, 0, false),
            spec(76, DatevFieldType.TEXT, 20, 0, false),
            spec(77, DatevFieldType.TEXT, 210, 0, false),
            spec(78, DatevFieldType.TEXT, 20, 0, false),
            spec(79, DatevFieldType.TEXT, 210, 0, false),
            spec(80, DatevFieldType.TEXT, 20, 0, false),
            spec(81, DatevFieldType.TEXT, 210, 0, false),
            spec(82, DatevFieldType.TEXT, 20, 0, false),
            spec(83, DatevFieldType.TEXT, 210, 0, false),
            spec(84, DatevFieldType.TEXT, 20, 0, false),
            spec(85, DatevFieldType.TEXT, 210, 0, false),
            spec(86, DatevFieldType.TEXT, 20, 0, false),
            spec(87, DatevFieldType.TEXT, 210, 0, false),
            spec(88, DatevFieldType.NUMBER, 8, 0, false),
            spec(89, DatevFieldType.NUMBER, 8, 2, false),
            spec(90, DatevFieldType.NUMBER, 2, 0, false),
            spec(91, DatevFieldType.TEXT, 10, 0, false),
            spec(92, DatevFieldType.NUMBER, 4, 0, false),
            spec(93, DatevFieldType.DATE, 8, 0, false),
            spec(94, DatevFieldType.NUMBER, 1, 0, false),
            spec(95, DatevFieldType.TEXT, 30, 0, false),
            spec(96, DatevFieldType.TEXT, 2, 0, false),
            spec(97, DatevFieldType.NUMBER, 2, 0, false),
            spec(98, DatevFieldType.TEXT, 2, 0, false),
            spec(99, DatevFieldType.NUMBER, 3, 0, false),
            spec(100, DatevFieldType.NUMBER, 2, 2, false),
            spec(101, DatevFieldType.ACCOUNT, 9, 0, false),
            spec(102, DatevFieldType.TEXT, 2, 0, false),
            spec(103, DatevFieldType.TEXT, 36, 0, false),
            spec(104, DatevFieldType.DATE, 8, 0, false),
            spec(105, DatevFieldType.TEXT, 35, 0, false),
            spec(106, DatevFieldType.NUMBER, 1, 0, false),
            spec(107, DatevFieldType.TEXT, 76, 0, false),
            spec(108, DatevFieldType.NUMBER, 4, 0, false),
            spec(109, DatevFieldType.TEXT, 11, 0, false),
            spec(110, DatevFieldType.TEXT, 20, 0, false),
            spec(111, DatevFieldType.DATE, 8, 0, false),
            spec(112, DatevFieldType.TEXT, 30, 0, false),
            spec(113, DatevFieldType.NUMBER, 2, 0, false),
            spec(114, DatevFieldType.NUMBER, 1, 0, false),
            spec(115, DatevFieldType.DATE, 8, 0, false),
            spec(116, DatevFieldType.DATE, 8, 0, false),
            spec(117, DatevFieldType.DATE, 8, 0, false),
            spec(118, DatevFieldType.TEXT, 1, 0, false),
            spec(119, DatevFieldType.NUMBER, 2, 2, false),
            spec(120, DatevFieldType.TEXT, 2, 0, false),
            spec(121, DatevFieldType.TEXT, 50, 0, false),
            spec(122, DatevFieldType.NUMBER, 1, 0, false),
            spec(123, DatevFieldType.TEXT, 15, 0, false),
            spec(124, DatevFieldType.NUMBER, 2, 2, false),
            spec(125, DatevFieldType.ACCOUNT, 8, 0, false)
    );

    private static final List<DatevFieldSpec> VERSION_12 =
            List.copyOf(VERSION_13.subList(0, 124));
    private static final Map<String, DatevFieldSpec> BY_KEY = indexByKey();
    private static final List<String> HEADERS_12 = List.copyOf(HEADERS.subList(0, 124));
    private static final Set<Integer> TEXT_COLUMN_INDEXES = createTextColumnIndexes();
    private static final Set<String> KEYS_12 = Set.copyOf(HEADERS_12);
    private static final Set<String> KEYS_13 = Set.copyOf(HEADERS);

    private DatevFieldSpecs() {
    }

    /**
     * Returns the 125 official v13 column names in output order.
     *
     * @return immutable v13 column names
     */
    public static List<String> headers13() {
        return HEADERS;
    }

    /**
     * Returns the 124 official v12 column names in output order.
     *
     * @return immutable v12 column names
     */
    public static List<String> headers12() {
        return HEADERS_12;
    }

    /**
     * Returns the zero-based indexes DATEV defines as text fields.
     *
     * <p>Text fields are always quoted on output, including empty values.
     *
     * @return immutable zero-based text-column indexes
     */
    public static Set<Integer> textColumnIndexes() {
        return TEXT_COLUMN_INDEXES;
    }

    /**
     * Returns whether the supplied keys are a complete official v12 or v13 key set.
     *
     * <p>Renamed or reordered official columns still match; a custom schema that merely reuses
     * some official names does not.
     *
     * @param canonicalKeys stable canonical keys in the row's output order
     * @return {@code true} for a complete official key set
     */
    public static boolean isOfficialSchema(List<String> canonicalKeys) {
        if (canonicalKeys == null) {
            return false;
        }
        // The official lists themselves are by far the most common argument, and comparing them
        // by identity avoids building a 125-element HashSet copy for every row.
        if (canonicalKeys == HEADERS || canonicalKeys == HEADERS_12) {
            return true;
        }
        int size = canonicalKeys.size();
        if (size != 124 && size != 125) {
            return false;
        }
        Set<String> expected = size == 124 ? KEYS_12 : KEYS_13;
        return new HashSet<>(canonicalKeys).equals(expected);
    }

    /**
     * Returns the 125 v13 definitions in official order.
     *
     * @return immutable v13 field definitions
     */
    public static List<DatevFieldSpec> version13() {
        return VERSION_13;
    }

    /**
     * Returns the 124 v12 definitions in official order.
     *
     * @return immutable v12 field definitions
     */
    public static List<DatevFieldSpec> version12() {
        return VERSION_12;
    }

    /**
     * Finds a definition by official column name.
     *
     * @param canonicalKey official column name
     * @return matching definition, if any
     */
    public static Optional<DatevFieldSpec> find(String canonicalKey) {
        return Optional.ofNullable(findOrNull(canonicalKey));
    }

    /**
     * Finds a definition by official column name without wrapping the result.
     *
     * <p>The row validation hot path performs this lookup once per cell, which is 125 times per
     * row. Returning the definition directly avoids an {@link Optional} allocation per cell.
     *
     * @param canonicalKey official column name, may be {@code null}
     * @return the matching definition, or {@code null} if there is none
     */
    static DatevFieldSpec findOrNull(String canonicalKey) {
        return canonicalKey == null ? null : BY_KEY.get(canonicalKey);
    }

    private static DatevFieldSpec spec(
            int fieldNumber,
            DatevFieldType type,
            int maxLength,
            int decimalPlaces,
            boolean required
    ) {
        return new DatevFieldSpec(fieldNumber, HEADERS.get(fieldNumber - 1), type,
                maxLength, decimalPlaces, required);
    }

    private static Set<Integer> createTextColumnIndexes() {
        var indexes = new LinkedHashSet<Integer>();
        addOneBased(indexes, 2, 3, 6, 9, 11, 12, 14, 16, 20, 40, 42,
                91, 95, 96, 98, 102, 103, 105, 107, 109, 110, 112, 118, 120, 121, 123);
        addOneBasedRange(indexes, 21, 38);
        addOneBasedRange(indexes, 48, 87);
        return Set.copyOf(indexes);
    }

    private static void addOneBased(Set<Integer> indexes, int... oneBasedIndexes) {
        for (int index : oneBasedIndexes) {
            indexes.add(index - 1);
        }
    }

    private static void addOneBasedRange(Set<Integer> indexes, int first, int last) {
        for (int index = first; index <= last; index++) {
            indexes.add(index - 1);
        }
    }

    private static Map<String, DatevFieldSpec> indexByKey() {
        var result = new LinkedHashMap<String, DatevFieldSpec>();
        for (DatevFieldSpec spec : VERSION_13) {
            if (result.put(spec.canonicalKey(), spec) != null) {
                throw new ExceptionInInitializerError("Duplicate DATEV key: " + spec.canonicalKey());
            }
        }
        return Map.copyOf(result);
    }
}
