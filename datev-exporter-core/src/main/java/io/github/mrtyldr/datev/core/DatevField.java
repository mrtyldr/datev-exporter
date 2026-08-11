package io.github.mrtyldr.datev.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Readable English aliases for the official German DATEV Buchungsstapel column headings.
 *
 * <p>Constants are declared in official output order, so {@code ordinal() + 1} is the DATEV field
 * number. Each constant carries the exact heading string from {@link DatevFieldSpecs}, which lets
 * callers write {@code DatevColumn.of(DatevField.ACCOUNT, 1200L)} instead of repeating
 * {@code "Konto"} and risking a typo in a heading such as
 * {@code "Zusatzinformation- Inhalt 1"} (note DATEV's inconsistent spacing around the dash).
 *
 * <p>Using this enum is optional. Every {@code DatevColumn} factory keeps an equivalent
 * {@code String} overload, and both produce identical output.
 *
 * <p>Field 125 ({@link #DIFFERING_CASH_DISCOUNT_ACCOUNT}) exists only in format version 13; see
 * {@link #isPresentIn(DatevSchema)}.
 *
 * @see DatevFieldSpecs
 * @see DatevColumn
 */
public enum DatevField {
    /** Field 1, {@code Umsatz (ohne Soll/Haben-Kz)}. */
    AMOUNT("Umsatz (ohne Soll/Haben-Kz)"),

    /** Field 2, {@code Soll/Haben-Kennzeichen}. */
    DEBIT_CREDIT_FLAG("Soll/Haben-Kennzeichen"),

    /** Field 3, {@code WKZ Umsatz}. */
    CURRENCY("WKZ Umsatz"),

    /** Field 4, {@code Kurs}. */
    EXCHANGE_RATE("Kurs"),

    /** Field 5, {@code Basis-Umsatz}. */
    BASE_AMOUNT("Basis-Umsatz"),

    /** Field 6, {@code WKZ Basis-Umsatz}. */
    BASE_CURRENCY("WKZ Basis-Umsatz"),

    /** Field 7, {@code Konto}. */
    ACCOUNT("Konto"),

    /** Field 8, {@code Gegenkonto (ohne BU-Schlüssel)}. */
    CONTRA_ACCOUNT("Gegenkonto (ohne BU-Schlüssel)"),

    /** Field 9, {@code BU-Schlüssel}. */
    POSTING_KEY("BU-Schlüssel"),

    /** Field 10, {@code Belegdatum}. */
    DOCUMENT_DATE("Belegdatum"),

    /** Field 11, {@code Belegfeld 1}. */
    DOCUMENT_FIELD_1("Belegfeld 1"),

    /** Field 12, {@code Belegfeld 2}. */
    DOCUMENT_FIELD_2("Belegfeld 2"),

    /** Field 13, {@code Skonto}. */
    CASH_DISCOUNT("Skonto"),

    /** Field 14, {@code Buchungstext}. */
    POSTING_TEXT("Buchungstext"),

    /** Field 15, {@code Postensperre}. */
    ITEM_BLOCK("Postensperre"),

    /** Field 16, {@code Diverse Adressnummer}. */
    MISC_ADDRESS_NUMBER("Diverse Adressnummer"),

    /** Field 17, {@code Geschäftspartnerbank}. */
    PARTNER_BANK("Geschäftspartnerbank"),

    /** Field 18, {@code Sachverhalt}. */
    MATTER_CODE("Sachverhalt"),

    /** Field 19, {@code Zinssperre}. */
    INTEREST_BLOCK("Zinssperre"),

    /** Field 20, {@code Beleglink}. */
    DOCUMENT_LINK("Beleglink"),

    /** Field 21, {@code Beleginfo - Art 1}. */
    DOCUMENT_INFO_TYPE_1("Beleginfo - Art 1"),

    /** Field 22, {@code Beleginfo - Inhalt 1}. */
    DOCUMENT_INFO_CONTENT_1("Beleginfo - Inhalt 1"),

    /** Field 23, {@code Beleginfo - Art 2}. */
    DOCUMENT_INFO_TYPE_2("Beleginfo - Art 2"),

    /** Field 24, {@code Beleginfo - Inhalt 2}. */
    DOCUMENT_INFO_CONTENT_2("Beleginfo - Inhalt 2"),

    /** Field 25, {@code Beleginfo - Art 3}. */
    DOCUMENT_INFO_TYPE_3("Beleginfo - Art 3"),

    /** Field 26, {@code Beleginfo - Inhalt 3}. */
    DOCUMENT_INFO_CONTENT_3("Beleginfo - Inhalt 3"),

    /** Field 27, {@code Beleginfo - Art 4}. */
    DOCUMENT_INFO_TYPE_4("Beleginfo - Art 4"),

    /** Field 28, {@code Beleginfo - Inhalt 4}. */
    DOCUMENT_INFO_CONTENT_4("Beleginfo - Inhalt 4"),

    /** Field 29, {@code Beleginfo - Art 5}. */
    DOCUMENT_INFO_TYPE_5("Beleginfo - Art 5"),

    /** Field 30, {@code Beleginfo - Inhalt 5}. */
    DOCUMENT_INFO_CONTENT_5("Beleginfo - Inhalt 5"),

    /** Field 31, {@code Beleginfo - Art 6}. */
    DOCUMENT_INFO_TYPE_6("Beleginfo - Art 6"),

    /** Field 32, {@code Beleginfo - Inhalt 6}. */
    DOCUMENT_INFO_CONTENT_6("Beleginfo - Inhalt 6"),

    /** Field 33, {@code Beleginfo - Art 7}. */
    DOCUMENT_INFO_TYPE_7("Beleginfo - Art 7"),

    /** Field 34, {@code Beleginfo - Inhalt 7}. */
    DOCUMENT_INFO_CONTENT_7("Beleginfo - Inhalt 7"),

    /** Field 35, {@code Beleginfo - Art 8}. */
    DOCUMENT_INFO_TYPE_8("Beleginfo - Art 8"),

    /** Field 36, {@code Beleginfo - Inhalt 8}. */
    DOCUMENT_INFO_CONTENT_8("Beleginfo - Inhalt 8"),

    /** Field 37, {@code KOST1 - Kostenstelle}. */
    COST_CENTER_1("KOST1 - Kostenstelle"),

    /** Field 38, {@code KOST2 - Kostenstelle}. */
    COST_CENTER_2("KOST2 - Kostenstelle"),

    /** Field 39, {@code Kost-Menge}. */
    COST_QUANTITY("Kost-Menge"),

    /** Field 40, {@code EU-Land u. UStID (Bestimmung)}. */
    EU_COUNTRY_VAT_ID_DESTINATION("EU-Land u. UStID (Bestimmung)"),

    /** Field 41, {@code EU-Steuersatz (Bestimmung)}. */
    EU_TAX_RATE_DESTINATION("EU-Steuersatz (Bestimmung)"),

    /** Field 42, {@code Abw. Versteuerungsart}. */
    DIFFERING_TAXATION_TYPE("Abw. Versteuerungsart"),

    /** Field 43, {@code Sachverhalt L+L}. */
    MATTER_CODE_LL("Sachverhalt L+L"),

    /** Field 44, {@code Funktionsergänzung L+L}. */
    FUNCTION_SUPPLEMENT_LL("Funktionsergänzung L+L"),

    /** Field 45, {@code BU 49 Hauptfunktionstyp}. */
    BU49_MAIN_FUNCTION_TYPE("BU 49 Hauptfunktionstyp"),

    /** Field 46, {@code BU 49 Hauptfunktionsnummer}. */
    BU49_MAIN_FUNCTION_NUMBER("BU 49 Hauptfunktionsnummer"),

    /** Field 47, {@code BU 49 Funktionsergänzung}. */
    BU49_FUNCTION_SUPPLEMENT("BU 49 Funktionsergänzung"),

    /** Field 48, {@code Zusatzinformation - Art 1}. */
    ADDITIONAL_INFO_TYPE_1("Zusatzinformation - Art 1"),

    /** Field 49, {@code Zusatzinformation- Inhalt 1}. */
    ADDITIONAL_INFO_CONTENT_1("Zusatzinformation- Inhalt 1"),

    /** Field 50, {@code Zusatzinformation - Art 2}. */
    ADDITIONAL_INFO_TYPE_2("Zusatzinformation - Art 2"),

    /** Field 51, {@code Zusatzinformation- Inhalt 2}. */
    ADDITIONAL_INFO_CONTENT_2("Zusatzinformation- Inhalt 2"),

    /** Field 52, {@code Zusatzinformation - Art 3}. */
    ADDITIONAL_INFO_TYPE_3("Zusatzinformation - Art 3"),

    /** Field 53, {@code Zusatzinformation- Inhalt 3}. */
    ADDITIONAL_INFO_CONTENT_3("Zusatzinformation- Inhalt 3"),

    /** Field 54, {@code Zusatzinformation - Art 4}. */
    ADDITIONAL_INFO_TYPE_4("Zusatzinformation - Art 4"),

    /** Field 55, {@code Zusatzinformation- Inhalt 4}. */
    ADDITIONAL_INFO_CONTENT_4("Zusatzinformation- Inhalt 4"),

    /** Field 56, {@code Zusatzinformation - Art 5}. */
    ADDITIONAL_INFO_TYPE_5("Zusatzinformation - Art 5"),

    /** Field 57, {@code Zusatzinformation- Inhalt 5}. */
    ADDITIONAL_INFO_CONTENT_5("Zusatzinformation- Inhalt 5"),

    /** Field 58, {@code Zusatzinformation - Art 6}. */
    ADDITIONAL_INFO_TYPE_6("Zusatzinformation - Art 6"),

    /** Field 59, {@code Zusatzinformation- Inhalt 6}. */
    ADDITIONAL_INFO_CONTENT_6("Zusatzinformation- Inhalt 6"),

    /** Field 60, {@code Zusatzinformation - Art 7}. */
    ADDITIONAL_INFO_TYPE_7("Zusatzinformation - Art 7"),

    /** Field 61, {@code Zusatzinformation- Inhalt 7}. */
    ADDITIONAL_INFO_CONTENT_7("Zusatzinformation- Inhalt 7"),

    /** Field 62, {@code Zusatzinformation - Art 8}. */
    ADDITIONAL_INFO_TYPE_8("Zusatzinformation - Art 8"),

    /** Field 63, {@code Zusatzinformation- Inhalt 8}. */
    ADDITIONAL_INFO_CONTENT_8("Zusatzinformation- Inhalt 8"),

    /** Field 64, {@code Zusatzinformation - Art 9}. */
    ADDITIONAL_INFO_TYPE_9("Zusatzinformation - Art 9"),

    /** Field 65, {@code Zusatzinformation- Inhalt 9}. */
    ADDITIONAL_INFO_CONTENT_9("Zusatzinformation- Inhalt 9"),

    /** Field 66, {@code Zusatzinformation - Art 10}. */
    ADDITIONAL_INFO_TYPE_10("Zusatzinformation - Art 10"),

    /** Field 67, {@code Zusatzinformation- Inhalt 10}. */
    ADDITIONAL_INFO_CONTENT_10("Zusatzinformation- Inhalt 10"),

    /** Field 68, {@code Zusatzinformation - Art 11}. */
    ADDITIONAL_INFO_TYPE_11("Zusatzinformation - Art 11"),

    /** Field 69, {@code Zusatzinformation- Inhalt 11}. */
    ADDITIONAL_INFO_CONTENT_11("Zusatzinformation- Inhalt 11"),

    /** Field 70, {@code Zusatzinformation - Art 12}. */
    ADDITIONAL_INFO_TYPE_12("Zusatzinformation - Art 12"),

    /** Field 71, {@code Zusatzinformation- Inhalt 12}. */
    ADDITIONAL_INFO_CONTENT_12("Zusatzinformation- Inhalt 12"),

    /** Field 72, {@code Zusatzinformation - Art 13}. */
    ADDITIONAL_INFO_TYPE_13("Zusatzinformation - Art 13"),

    /** Field 73, {@code Zusatzinformation- Inhalt 13}. */
    ADDITIONAL_INFO_CONTENT_13("Zusatzinformation- Inhalt 13"),

    /** Field 74, {@code Zusatzinformation - Art 14}. */
    ADDITIONAL_INFO_TYPE_14("Zusatzinformation - Art 14"),

    /** Field 75, {@code Zusatzinformation- Inhalt 14}. */
    ADDITIONAL_INFO_CONTENT_14("Zusatzinformation- Inhalt 14"),

    /** Field 76, {@code Zusatzinformation - Art 15}. */
    ADDITIONAL_INFO_TYPE_15("Zusatzinformation - Art 15"),

    /** Field 77, {@code Zusatzinformation- Inhalt 15}. */
    ADDITIONAL_INFO_CONTENT_15("Zusatzinformation- Inhalt 15"),

    /** Field 78, {@code Zusatzinformation - Art 16}. */
    ADDITIONAL_INFO_TYPE_16("Zusatzinformation - Art 16"),

    /** Field 79, {@code Zusatzinformation- Inhalt 16}. */
    ADDITIONAL_INFO_CONTENT_16("Zusatzinformation- Inhalt 16"),

    /** Field 80, {@code Zusatzinformation - Art 17}. */
    ADDITIONAL_INFO_TYPE_17("Zusatzinformation - Art 17"),

    /** Field 81, {@code Zusatzinformation- Inhalt 17}. */
    ADDITIONAL_INFO_CONTENT_17("Zusatzinformation- Inhalt 17"),

    /** Field 82, {@code Zusatzinformation - Art 18}. */
    ADDITIONAL_INFO_TYPE_18("Zusatzinformation - Art 18"),

    /** Field 83, {@code Zusatzinformation- Inhalt 18}. */
    ADDITIONAL_INFO_CONTENT_18("Zusatzinformation- Inhalt 18"),

    /** Field 84, {@code Zusatzinformation - Art 19}. */
    ADDITIONAL_INFO_TYPE_19("Zusatzinformation - Art 19"),

    /** Field 85, {@code Zusatzinformation- Inhalt 19}. */
    ADDITIONAL_INFO_CONTENT_19("Zusatzinformation- Inhalt 19"),

    /** Field 86, {@code Zusatzinformation - Art 20}. */
    ADDITIONAL_INFO_TYPE_20("Zusatzinformation - Art 20"),

    /** Field 87, {@code Zusatzinformation- Inhalt 20}. */
    ADDITIONAL_INFO_CONTENT_20("Zusatzinformation- Inhalt 20"),

    /** Field 88, {@code Stück}. */
    PIECES("Stück"),

    /** Field 89, {@code Gewicht}. */
    WEIGHT("Gewicht"),

    /** Field 90, {@code Zahlweise}. */
    PAYMENT_METHOD("Zahlweise"),

    /** Field 91, {@code Forderungsart}. */
    RECEIVABLE_TYPE("Forderungsart"),

    /** Field 92, {@code Veranlagungsjahr}. */
    ASSESSMENT_YEAR("Veranlagungsjahr"),

    /** Field 93, {@code Zugeordnete Fälligkeit}. */
    ASSIGNED_DUE_DATE("Zugeordnete Fälligkeit"),

    /** Field 94, {@code Skontotyp}. */
    CASH_DISCOUNT_TYPE("Skontotyp"),

    /** Field 95, {@code Auftragsnummer}. */
    ORDER_NUMBER("Auftragsnummer"),

    /** Field 96, {@code Buchungstyp}. */
    POSTING_TYPE("Buchungstyp"),

    /** Field 97, {@code USt-Schlüssel (Anzahlungen)}. */
    VAT_KEY_PREPAYMENT("USt-Schlüssel (Anzahlungen)"),

    /** Field 98, {@code EU-Land (Anzahlungen)}. */
    EU_COUNTRY_PREPAYMENT("EU-Land (Anzahlungen)"),

    /** Field 99, {@code Sachverhalt L+L (Anzahlungen)}. */
    MATTER_CODE_LL_PREPAYMENT("Sachverhalt L+L (Anzahlungen)"),

    /** Field 100, {@code EU-Steuersatz (Anzahlungen)}. */
    EU_TAX_RATE_PREPAYMENT("EU-Steuersatz (Anzahlungen)"),

    /** Field 101, {@code Erlöskonto (Anzahlungen)}. */
    REVENUE_ACCOUNT_PREPAYMENT("Erlöskonto (Anzahlungen)"),

    /** Field 102, {@code Herkunft-Kz}. */
    ORIGIN_CODE("Herkunft-Kz"),

    /** Field 103, {@code Buchungs GUID}. */
    POSTING_GUID("Buchungs GUID"),

    /** Field 104, {@code KOST-Datum}. */
    COST_DATE("KOST-Datum"),

    /** Field 105, {@code SEPA-Mandatsreferenz}. */
    SEPA_MANDATE_REFERENCE("SEPA-Mandatsreferenz"),

    /** Field 106, {@code Skontosperre}. */
    CASH_DISCOUNT_BLOCK("Skontosperre"),

    /** Field 107, {@code Gesellschaftername}. */
    SHAREHOLDER_NAME("Gesellschaftername"),

    /** Field 108, {@code Beteiligtennummer}. */
    PARTICIPANT_NUMBER("Beteiligtennummer"),

    /** Field 109, {@code Identifikationsnummer}. */
    IDENTIFICATION_NUMBER("Identifikationsnummer"),

    /** Field 110, {@code Zeichnernummer}. */
    SUBSCRIBER_NUMBER("Zeichnernummer"),

    /** Field 111, {@code Postensperre bis}. */
    ITEM_BLOCK_UNTIL("Postensperre bis"),

    /** Field 112, {@code Bezeichnung SoBil-Sachverhalt}. */
    SOBIL_MATTER_LABEL("Bezeichnung SoBil-Sachverhalt"),

    /** Field 113, {@code Kennzeichen SoBil-Buchung}. */
    SOBIL_POSTING_FLAG("Kennzeichen SoBil-Buchung"),

    /** Field 114, {@code Festschreibung}. */
    FINAL_POSTING_FLAG("Festschreibung"),

    /** Field 115, {@code Leistungsdatum}. */
    SERVICE_DATE("Leistungsdatum"),

    /** Field 116, {@code Datum Zuord. Steuerperiode}. */
    TAX_PERIOD_DATE("Datum Zuord. Steuerperiode"),

    /** Field 117, {@code Fälligkeit}. */
    DUE_DATE("Fälligkeit"),

    /** Field 118, {@code Generalumkehr (GU)}. */
    GENERAL_REVERSAL("Generalumkehr (GU)"),

    /** Field 119, {@code Steuersatz}. */
    TAX_RATE("Steuersatz"),

    /** Field 120, {@code Land}. */
    COUNTRY("Land"),

    /** Field 121, {@code Abrechnungsreferenz}. */
    SETTLEMENT_REFERENCE("Abrechnungsreferenz"),

    /** Field 122, {@code BVV-Position}. */
    BVV_POSITION("BVV-Position"),

    /** Field 123, {@code EU-Land u. UStID (Ursprung)}. */
    EU_COUNTRY_VAT_ID_ORIGIN("EU-Land u. UStID (Ursprung)"),

    /** Field 124, {@code EU-Steuersatz (Ursprung)}. */
    EU_TAX_RATE_ORIGIN("EU-Steuersatz (Ursprung)"),

    /** Field 125, {@code Abw. Skontokonto}. */
    DIFFERING_CASH_DISCOUNT_ACCOUNT("Abw. Skontokonto");

    private static final DatevField[] VALUES = values();
    private static final Map<String, DatevField> BY_HEADING = indexByHeading();

    private final String heading;

    DatevField(String heading) {
        this.heading = heading;
    }

    /**
     * Returns the exact official German heading of this field.
     *
     * @return the heading as it appears in {@link DatevSchema#headers()}
     */
    public String heading() {
        return heading;
    }

    /**
     * Returns the one-based official DATEV field number.
     *
     * @return a number between 1 and 125
     */
    public int fieldNumber() {
        return ordinal() + 1;
    }

    /**
     * Returns the official technical definition of this field.
     *
     * @return the matching v13 field definition
     */
    public DatevFieldSpec spec() {
        return DatevFieldSpecs.version13().get(ordinal());
    }

    /**
     * Returns whether the supplied schema contains this field.
     *
     * @param schema the schema to test
     * @return {@code false} only for field 125 in {@link DatevSchema#LEGACY_V12}
     */
    public boolean isPresentIn(DatevSchema schema) {
        Objects.requireNonNull(schema, "schema");
        return fieldNumber() <= schema.columnCount();
    }

    /**
     * Returns this field's position inside a repeating {@code Art}/{@code Inhalt} group.
     *
     * @return the slot, or an empty optional for the 79 non-repeating fields
     */
    public Optional<Slot> slot() {
        Group group = Group.containing(fieldNumber());
        if (group == null) {
            return Optional.empty();
        }
        int offset = fieldNumber() - group.firstFieldNumber;
        return Optional.of(new Slot(group, offset / 2 + 1, Part.atOffset(offset % 2)));
    }

    /**
     * Returns whether this field belongs to a repeating {@code Art}/{@code Inhalt} group.
     *
     * @return {@code true} for the 16 {@code Beleginfo} and 40 {@code Zusatzinformation} fields
     */
    public boolean isRepeating() {
        return Group.containing(fieldNumber()) != null;
    }

    /**
     * A repeating DATEV field group made of numbered {@code Art}/{@code Inhalt} pairs.
     *
     * <p>DATEV spells the two groups inconsistently — {@code "Zusatzinformation - Art 1"} has
     * spaces around the dash while {@code "Zusatzinformation- Inhalt 1"} does not. Resolving
     * fields through this enum avoids reproducing that quirk by hand.
     */
    public enum Group {
        /** {@code Beleginfo}, DATEV fields 21-36: eight slots. */
        DOCUMENT_INFO(21, 8),

        /** {@code Zusatzinformation}, DATEV fields 48-87: twenty slots. */
        ADDITIONAL_INFO(48, 20);

        private final int firstFieldNumber;
        private final int slotCount;

        Group(int firstFieldNumber, int slotCount) {
            this.firstFieldNumber = firstFieldNumber;
            this.slotCount = slotCount;
        }

        /**
         * Returns how many {@code Art}/{@code Inhalt} slots this group offers.
         *
         * @return 8 for {@link #DOCUMENT_INFO}, 20 for {@link #ADDITIONAL_INFO}
         */
        public int slotCount() {
            return slotCount;
        }

        /**
         * Resolves one half of a numbered slot.
         *
         * @param slotNumber one-based slot number
         * @param part which half of the slot to resolve
         * @return the matching field
         * @throws IllegalArgumentException if the slot number is outside this group
         */
        public DatevField field(int slotNumber, Part part) {
            Objects.requireNonNull(part, "part");
            if (slotNumber < 1 || slotNumber > slotCount) {
                throw new IllegalArgumentException("Slot number " + slotNumber + " is outside "
                        + name() + ", which has " + slotCount + " slots.");
            }
            return VALUES[firstFieldNumber - 1 + 2 * (slotNumber - 1) + part.offset];
        }

        /**
         * Returns every field of this group in official output order.
         *
         * @return a new immutable list of {@code 2 * slotCount()} fields
         */
        public List<DatevField> fields() {
            var fields = new ArrayList<DatevField>(2 * slotCount);
            for (int index = 0; index < 2 * slotCount; index++) {
                fields.add(VALUES[firstFieldNumber - 1 + index]);
            }
            return List.copyOf(fields);
        }

        private static Group containing(int fieldNumber) {
            for (Group group : values()) {
                if (fieldNumber >= group.firstFieldNumber
                        && fieldNumber < group.firstFieldNumber + 2 * group.slotCount) {
                    return group;
                }
            }
            return null;
        }
    }

    /** The role a field plays inside a {@link Group} slot. */
    public enum Part {
        /** DATEV {@code Art}: the label naming what the slot holds. */
        TYPE(0),

        /** DATEV {@code Inhalt}: the value the slot holds. */
        CONTENT(1);

        private final int offset;

        Part(int offset) {
            this.offset = offset;
        }

        private static Part atOffset(int offset) {
            return offset == 0 ? TYPE : CONTENT;
        }
    }

    /**
     * The position of a field inside a repeating group.
     *
     * @param group the owning group
     * @param number one-based slot number within the group
     * @param part which half of the slot the field is
     */
    public record Slot(Group group, int number, Part part) {

        /**
         * Validates this slot position.
         *
         * @param group the owning group
         * @param number one-based slot number within the group
         * @param part which half of the slot the field is
         */
        public Slot {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(part, "part");
            if (number < 1 || number > group.slotCount()) {
                throw new IllegalArgumentException("Slot number " + number + " is outside "
                        + group.name() + ", which has " + group.slotCount() + " slots.");
            }
        }

        /**
         * Returns the field occupying this position.
         *
         * @return the matching field
         */
        public DatevField field() {
            return group.field(number, part);
        }
    }

    /**
     * Finds the field carrying an official German heading.
     *
     * @param heading heading to look up; normalized to Unicode NFC before matching
     * @return the matching field, if any
     */
    public static Optional<DatevField> fromHeading(String heading) {
        if (heading == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_HEADING.get(Normalizer.normalize(heading, Normalizer.Form.NFC)));
    }

    private static Map<String, DatevField> indexByHeading() {
        var result = new LinkedHashMap<String, DatevField>();
        for (DatevField field : values()) {
            if (result.put(field.heading, field) != null) {
                throw new ExceptionInInitializerError("Duplicate DATEV heading: " + field.heading);
            }
        }
        var expected = DatevFieldSpecs.headers13();
        if (expected.size() != result.size()) {
            throw new ExceptionInInitializerError(
                    "DatevField declares " + result.size() + " headings but DatevFieldSpecs has "
                            + expected.size() + '.');
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!expected.get(index).equals(values()[index].heading)) {
                throw new ExceptionInInitializerError(
                        "DatevField." + values()[index].name() + " must map to '"
                                + expected.get(index) + "' but maps to '"
                                + values()[index].heading + "'.");
            }
        }
        return Map.copyOf(result);
    }
}
