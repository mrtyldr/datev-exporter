package io.github.mrtyldr.datev.core;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatevHeaderTest {
    private static final String VERSION_12_SNAPSHOT =
            "Umsatz (ohne Soll/Haben-Kz);Soll/Haben-Kennzeichen;WKZ Umsatz;Kurs;Basis-Umsatz;"
                    + "WKZ Basis-Umsatz;Konto;Gegenkonto (ohne BU-Schlüssel);BU-Schlüssel;Belegdatum;"
                    + "Belegfeld 1;Belegfeld 2;Skonto;Buchungstext;Postensperre;Diverse Adressnummer;"
                    + "Geschäftspartnerbank;Sachverhalt;Zinssperre;Beleglink;Beleginfo - Art 1;"
                    + "Beleginfo - Inhalt 1;Beleginfo - Art 2;Beleginfo - Inhalt 2;Beleginfo - Art 3;"
                    + "Beleginfo - Inhalt 3;Beleginfo - Art 4;Beleginfo - Inhalt 4;Beleginfo - Art 5;"
                    + "Beleginfo - Inhalt 5;Beleginfo - Art 6;Beleginfo - Inhalt 6;Beleginfo - Art 7;"
                    + "Beleginfo - Inhalt 7;Beleginfo - Art 8;Beleginfo - Inhalt 8;KOST1 - Kostenstelle;"
                    + "KOST2 - Kostenstelle;Kost-Menge;EU-Land u. UStID (Bestimmung);"
                    + "EU-Steuersatz (Bestimmung);Abw. Versteuerungsart;Sachverhalt L+L;"
                    + "Funktionsergänzung L+L;BU 49 Hauptfunktionstyp;BU 49 Hauptfunktionsnummer;"
                    + "BU 49 Funktionsergänzung;Zusatzinformation - Art 1;Zusatzinformation- Inhalt 1;"
                    + "Zusatzinformation - Art 2;Zusatzinformation- Inhalt 2;Zusatzinformation - Art 3;"
                    + "Zusatzinformation- Inhalt 3;Zusatzinformation - Art 4;Zusatzinformation- Inhalt 4;"
                    + "Zusatzinformation - Art 5;Zusatzinformation- Inhalt 5;Zusatzinformation - Art 6;"
                    + "Zusatzinformation- Inhalt 6;Zusatzinformation - Art 7;Zusatzinformation- Inhalt 7;"
                    + "Zusatzinformation - Art 8;Zusatzinformation- Inhalt 8;Zusatzinformation - Art 9;"
                    + "Zusatzinformation- Inhalt 9;Zusatzinformation - Art 10;Zusatzinformation- Inhalt 10;"
                    + "Zusatzinformation - Art 11;Zusatzinformation- Inhalt 11;Zusatzinformation - Art 12;"
                    + "Zusatzinformation- Inhalt 12;Zusatzinformation - Art 13;Zusatzinformation- Inhalt 13;"
                    + "Zusatzinformation - Art 14;Zusatzinformation- Inhalt 14;Zusatzinformation - Art 15;"
                    + "Zusatzinformation- Inhalt 15;Zusatzinformation - Art 16;Zusatzinformation- Inhalt 16;"
                    + "Zusatzinformation - Art 17;Zusatzinformation- Inhalt 17;Zusatzinformation - Art 18;"
                    + "Zusatzinformation- Inhalt 18;Zusatzinformation - Art 19;Zusatzinformation- Inhalt 19;"
                    + "Zusatzinformation - Art 20;Zusatzinformation- Inhalt 20;Stück;Gewicht;Zahlweise;"
                    + "Forderungsart;Veranlagungsjahr;Zugeordnete Fälligkeit;Skontotyp;Auftragsnummer;"
                    + "Buchungstyp;USt-Schlüssel (Anzahlungen);EU-Land (Anzahlungen);"
                    + "Sachverhalt L+L (Anzahlungen);EU-Steuersatz (Anzahlungen);Erlöskonto (Anzahlungen);"
                    + "Herkunft-Kz;Buchungs GUID;KOST-Datum;SEPA-Mandatsreferenz;Skontosperre;"
                    + "Gesellschaftername;Beteiligtennummer;Identifikationsnummer;Zeichnernummer;"
                    + "Postensperre bis;Bezeichnung SoBil-Sachverhalt;Kennzeichen SoBil-Buchung;"
                    + "Festschreibung;Leistungsdatum;Datum Zuord. Steuerperiode;Fälligkeit;"
                    + "Generalumkehr (GU);Steuersatz;Land;Abrechnungsreferenz;BVV-Position;"
                    + "EU-Land u. UStID (Ursprung);EU-Steuersatz (Ursprung)";

    private static final String VERSION_13_SNAPSHOT = VERSION_12_SNAPSHOT + ";Abw. Skontokonto";

    @Test
    void currentMatchesTheCompleteOfficialVersion13Snapshot() {
        DatevHeader header = DatevHeader.current();

        assertAll(
                () -> assertEquals(VERSION_13_SNAPSHOT, header.toString()),
                () -> assertEquals(List.of(VERSION_13_SNAPSHOT.split(";", -1)), header.names()),
                () -> assertEquals(header.names(), header.keys()),
                () -> assertEquals(125, header.size()),
                () -> assertEquals(125, new HashSet<>(header.names()).size()),
                () -> assertEquals("Abw. Skontokonto", header.names().get(124))
        );
    }

    @Test
    void legacyVersion12MatchesTheCompleteCorrectedSnapshot() {
        DatevHeader legacy = DatevHeader.legacyV12();

        assertAll(
                () -> assertEquals(VERSION_12_SNAPSHOT, legacy.toString()),
                () -> assertEquals(List.of(VERSION_12_SNAPSHOT.split(";", -1)), legacy.names()),
                () -> assertEquals(124, legacy.size()),
                () -> assertEquals(legacy.names(), DatevHeader.current().names().subList(0, 124))
        );
    }

    @Test
    void officialNamesAreUtf8CorrectAndPreserveSignificantInternalSpacing() {
        String joined = DatevHeader.current().toString();

        assertAll(
                () -> assertTrue(joined.contains("Gegenkonto (ohne BU-Schlüssel)")),
                () -> assertTrue(joined.contains("Geschäftspartnerbank")),
                () -> assertTrue(joined.contains("Funktionsergänzung L+L")),
                () -> assertTrue(joined.contains("Zusatzinformation- Inhalt 1")),
                () -> assertTrue(joined.contains("Stück")),
                () -> assertTrue(joined.contains("Fälligkeit")),
                () -> assertTrue(joined.contains("Erlöskonto (Anzahlungen)")),
                () -> assertFalse(joined.contains("Ã")),
                () -> assertFalse(joined.contains("Â")),
                () -> assertFalse(joined.contains("�"))
        );
    }

    @Test
    void officialSchemasRetainTextQuotingMetadataAcrossRenameAndReorder() {
        Set<Integer> expected = new HashSet<>(Set.of(
                1, 2, 5, 8, 10, 11, 13, 15, 19, 39, 41,
                90, 94, 95, 97, 101, 102, 104, 106, 108, 109, 111, 117, 119, 120, 122
        ));
        for (int index = 20; index <= 37; index++) {
            expected.add(index);
        }
        for (int index = 47; index <= 86; index++) {
            expected.add(index);
        }

        DatevHeader renamed = DatevHeader.current().renamed("Soll/Haben-Kennzeichen", "Side");
        List<String> reversedOrder = new ArrayList<>(renamed.keys());
        java.util.Collections.reverse(reversedOrder);
        DatevHeader reversed = renamed.reordered(reversedOrder);
        Set<Integer> actual = Set.copyOf(DatevHeader.current().quotedColumnIndexes());
        Set<Integer> legacy = Set.copyOf(DatevHeader.legacyV12().quotedColumnIndexes());
        Set<Integer> reversedIndexes = Set.copyOf(reversed.quotedColumnIndexes());
        Set<Integer> expectedReversed = expected.stream()
                .map(index -> DatevHeader.current().size() - 1 - index)
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(expected, actual),
                () -> assertEquals(expected, legacy),
                () -> assertEquals(expectedReversed, reversedIndexes),
                () -> assertEquals("Side", renamed.names().get(1)),
                () -> assertEquals(0, DatevHeader.parse("A;B").quotedColumnIndexes().size())
        );
    }

    @Test
    void allCustomFactoriesCreateEquivalentHeadersAndNormalizeUnicodeToNfc() {
        String decomposed = "Gescha\u0308ft";
        String composed = Normalizer.normalize(decomposed, Normalizer.Form.NFC);
        String[] array = {decomposed, "Konto"};
        var list = new ArrayList<>(List.of(decomposed, "Konto"));

        DatevHeader parsed = DatevHeader.parse(decomposed + ";Konto");
        DatevHeader fromArray = DatevHeader.of(array);
        DatevHeader fromList = DatevHeader.of(list);

        array[0] = "Changed";
        list.set(0, "Changed");

        assertAll(
                () -> assertEquals(List.of(composed, "Konto"), parsed.names()),
                () -> assertEquals(parsed, fromArray),
                () -> assertEquals(parsed, fromList),
                () -> assertEquals(parsed.names(), parsed.keys()),
                () -> assertEquals(composed + ";Konto", parsed.toString())
        );
    }

    @Test
    void returnedCollectionsAndArraysCannotMutateAHeaderOrGlobalDefaults() {
        DatevHeader custom = DatevHeader.parse("A;B");
                DatevHeader renamed = DatevHeader.current().renamed("Konto", "Sachkonto");

        assertAll(
                () -> assertThrows(UnsupportedOperationException.class, () -> custom.names().add("C")),
                () -> assertThrows(UnsupportedOperationException.class, () -> custom.keys().set(0, "C")),
                () -> assertEquals(List.of("A", "B"), custom.names()),
                () -> assertEquals("A", custom.names().get(0)),
                () -> assertEquals("Konto", DatevHeader.current().names().get(6)),
                () -> assertEquals("Sachkonto", renamed.names().get(6)),
                () -> assertNotEquals(DatevHeader.current(), renamed)
        );
    }

    @Test
    void renameChangesOnlyOutputNamesAndCanResolveCanonicalOrCurrentNames() {
        DatevHeader original = DatevHeader.parse("Amount;Account;Text");
        DatevHeader once = original.renamed("Amount", "Umsatz");
        DatevHeader twice = once.renamed("Umsatz", "Revenue");
        DatevHeader several = twice.renamed(Map.of("Account", "Konto", "Text", "Buchungstext"));

        assertAll(
                () -> assertEquals(List.of("Amount", "Account", "Text"), original.names()),
                () -> assertEquals(List.of("Amount", "Account", "Text"), several.keys()),
                () -> assertEquals(List.of("Revenue", "Konto", "Buchungstext"), several.names()),
                () -> assertEquals(0, several.resolve("Amount")),
                () -> assertEquals(0, several.resolve("Revenue")),
                () -> assertEquals(1, several.resolve("Account")),
                () -> assertEquals(1, several.resolve("Konto")),
                () -> assertEquals(several, several.renamed(Map.of())),
                () -> assertEquals(several, several.renamed("Revenue", "Revenue"))
        );
    }

    @Test
    void reorderRequiresAndPreservesAFullExactPermutation() {
        DatevHeader header = DatevHeader.parse("Amount;Account;Text").renamed("Amount", "Umsatz");

        DatevHeader byArray = header.reordered("Text", "Umsatz", "Account");
        DatevHeader byList = header.reordered(List.of("Account", "Amount", "Text"));

        assertAll(
                () -> assertEquals(List.of("Text", "Amount", "Account"), byArray.keys()),
                () -> assertEquals(List.of("Text", "Umsatz", "Account"), byArray.names()),
                () -> assertEquals(List.of("Account", "Amount", "Text"), byList.keys()),
                () -> assertEquals(List.of("Account", "Umsatz", "Text"), byList.names()),
                () -> assertEquals(List.of("Amount", "Account", "Text"), header.keys())
        );
    }

    @Test
    void factoriesRejectNullEmptyMalformedAndDuplicateNames() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> DatevHeader.parse(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> DatevHeader.of((String[]) null)),
                () -> assertThrows(IllegalArgumentException.class, () -> DatevHeader.of((List<String>) null)),
                () -> assertThrows(IllegalArgumentException.class, () -> DatevHeader.of(new String[0])),
                () -> assertThrows(IllegalArgumentException.class, () -> DatevHeader.of(List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> DatevHeader.of(new String[]{"A", null})),
                () -> assertThrows(IllegalArgumentException.class, () -> DatevHeader.parse("")),
                () -> assertThrows(IllegalArgumentException.class, () -> DatevHeader.parse("A;")),
                () -> assertThrows(IllegalArgumentException.class, () -> DatevHeader.parse(";A")),
                () -> assertThrows(IllegalArgumentException.class, () -> DatevHeader.parse("A;;B")),
                () -> assertThrows(IllegalArgumentException.class, () -> DatevHeader.of(new String[]{"A", "A"})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> DatevHeader.of(new String[]{"é", "e\u0301"})),
                () -> assertEquals(List.of("A", "a"), DatevHeader.parse("A;a").names())
        );

        for (String invalid : List.of(
                " ", "\t", " padded", "padded ", "\u00a0padded", "padded\u00a0",
                "semi;colon", "line\nbreak", "carriage\rreturn", "control\u0000character",
                "line\u2028separator", "paragraph\u2029separator")) {
            assertThrows(IllegalArgumentException.class, () -> DatevHeader.of(new String[]{invalid}), invalid);
        }
    }

    @Test
    void renameRejectsUnknownMalformedAmbiguousAndCollidingChanges() {
        DatevHeader header = DatevHeader.parse("A;B;C");
        DatevHeader withAlias = header.renamed("A", "Amount");
        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "X");
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("A", null);
        Map<String, String> sameColumnTwice = new LinkedHashMap<>();
        sameColumnTwice.put("A", "X");
        sameColumnTwice.put("Amount", "Y");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> header.renamed(null, "X")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.renamed("unknown", "X")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.renamed("a", "X")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.renamed("A", null)),
                () -> assertThrows(IllegalArgumentException.class, () -> header.renamed("A", " ")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.renamed("A", " padded")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.renamed("A", "semi;colon")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.renamed("A", "line\nbreak")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.renamed("A", "B")),
                () -> assertThrows(IllegalArgumentException.class, () -> withAlias.renamed("B", "Amount")),
                () -> assertThrows(IllegalArgumentException.class, () -> withAlias.renamed("B", "A")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.renamed((Map<String, String>) null)),
                () -> assertThrows(IllegalArgumentException.class, () -> header.renamed(nullKey)),
                () -> assertThrows(IllegalArgumentException.class, () -> header.renamed(nullValue)),
                () -> assertThrows(IllegalArgumentException.class, () -> withAlias.renamed(sameColumnTwice)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> header.renamed(Map.of("A", "X", "B", "X")))
        );
    }

    @Test
    void reorderRejectsAnythingOtherThanAFullExactPermutation() {
        DatevHeader header = DatevHeader.parse("A;B;C").renamed("A", "Amount");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> header.reordered((String[]) null)),
                () -> assertThrows(IllegalArgumentException.class, () -> header.reordered((List<String>) null)),
                () -> assertThrows(IllegalArgumentException.class, () -> header.reordered("A", "B")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.reordered("A", "B", "C", "A")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.reordered("A", "B", "B")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.reordered("A", "B", "unknown")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.reordered("A", "B", (String) null)),
                () -> assertThrows(IllegalArgumentException.class, () -> header.reordered("Amount", "A", "B")),
                () -> assertThrows(IllegalArgumentException.class, () -> header.reordered("Amount", "B", "c"))
        );
    }
}
