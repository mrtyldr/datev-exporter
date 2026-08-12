#!/usr/bin/env python3
"""Generate the four deep-dive reference pages in English and German.

The field table, the EXTF management record samples and the codec constants are read from data
dumped by the library itself (``scripts/DumpFieldSpecs.java`` and ``scripts/DumpHeaderRecord.java``),
so the published tables cannot drift away from the code.

Regenerate after a schema or metadata change::

    ./gradlew :datev-exporter-core:classes
    javac -d build/dump -cp datev-exporter-core/build/classes/java/main \
        scripts/DumpFieldSpecs.java scripts/DumpHeaderRecord.java
    java -cp build/dump:datev-exporter-core/build/classes/java/main DumpFieldSpecs > build/fields.tsv
    java -cp build/dump:datev-exporter-core/build/classes/java/main DumpHeaderRecord > build/header.tsv
    python3 scripts/generate-reference-pages.py
"""

from __future__ import annotations

import argparse
import html
import json
from pathlib import Path
from typing import NamedTuple

BASE = "https://mrtyldr.github.io/datev-exporter/"
VERSION = "0.2.0"

# Official DATEV text columns, one-based. Mirrors DatevFieldSpecs.createTextColumnIndexes().
ALWAYS_QUOTED = (
    {2, 3, 6, 9, 11, 12, 14, 16, 20, 40, 42, 91, 95, 96, 98, 102, 103, 105, 107, 109, 110,
     112, 118, 120, 121, 123}
    | set(range(21, 39))
    | set(range(48, 88))
)


class Field(NamedTuple):
    number: int
    heading: str
    constant: str
    type_name: str
    checker_type: str
    max_length: int
    decimals: int
    required: bool
    in_v12: bool


def read_fields(path: Path) -> list[Field]:
    fields: list[Field] = []
    for line in path.read_text(encoding="utf-8").splitlines()[1:]:
        if line.startswith("#"):
            continue
        parts = line.split("\t")
        fields.append(
            Field(
                int(parts[0]), parts[1], parts[2], parts[3], parts[4],
                int(parts[5]), int(parts[6]), parts[7] == "true", parts[8] == "true",
            )
        )
    if len(fields) != 125:
        raise SystemExit(f"expected 125 fields, dumped {len(fields)}")
    return fields


def read_header(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        key, _, value = line.partition("\t")
        values[key] = value
    return values


def esc(value: object) -> str:
    return html.escape(str(value))


def attr(value: object) -> str:
    return html.escape(str(value), quote=True)


# --------------------------------------------------------------------------------------------
# Localised strings
# --------------------------------------------------------------------------------------------

STRINGS = {
    "en": {
        "lang": "en",
        "locale": "en_US",
        "nav": [
            ("./", "Overview"),
            ("getting-started.html", "Get started"),
            ("compatibility.html", "Compatibility"),
            ("reference.html", "Reference"),
            ("fields.html", "Fields"),
            ("benchmarks.html", "Benchmarks"),
        ],
        "section_root": "Documentation",
        "toc": "On this page",
        "footer_note": "<strong>Independent open-source project.</strong> Not affiliated with, "
                       "endorsed by or supported by DATEV. DATEV is a trademark of DATEV eG. "
                       "Output is an import candidate, not a promise of acceptance.",
        "footer_nav": "Footer navigation",
        "primary_nav": "Primary navigation",
        "skip": "Skip to content",
        "brand_label": "datev-exporter home",
        "related": "Related references",
        "yes": "yes",
        "no": "no",
    },
    "de": {
        "lang": "de",
        "locale": "de_DE",
        "nav": [
            ("./", "Überblick"),
            ("getting-started.html", "Erste Schritte"),
            ("compatibility.html", "Kompatibilität"),
            ("reference.html", "Referenz"),
            ("fields.html", "Felder"),
            ("benchmarks.html", "Benchmarks"),
        ],
        "section_root": "Dokumentation",
        "toc": "Auf dieser Seite",
        "footer_note": "<strong>Unabhängiges Open-Source-Projekt.</strong> Keine Verbindung zu, "
                       "Unterstützung durch oder Empfehlung von DATEV. DATEV ist eine Marke der "
                       "DATEV eG. Die Ausgabe ist ein Importkandidat, keine Zusage der Annahme.",
        "footer_nav": "Fußnavigation",
        "primary_nav": "Hauptnavigation",
        "skip": "Zum Inhalt springen",
        "brand_label": "datev-exporter Startseite",
        "related": "Verwandte Referenzen",
        "yes": "ja",
        "no": "nein",
    },
}

RELATED_LABELS = {
    "en": {
        "fields.html": "Field reference — all 125 Buchungsstapel columns",
        "validation-errors.html": "Validation errors — the six error codes explained",
        "extf-header.html": "EXTF header — the 31-field management record",
        "encoding.html": "Encoding — Windows-1252, CRLF, quoting and umlauts",
    },
    "de": {
        "fields.html": "Feldreferenz — alle 125 Buchungsstapel-Spalten",
        "validation-errors.html": "Validierungsfehler — die sechs Fehlercodes erklärt",
        "extf-header.html": "EXTF-Header — der Verwaltungssatz mit 31 Feldern",
        "encoding.html": "Kodierung — Windows-1252, CRLF, Quoting und Umlaute",
    },
}


def page_shell(
    language: str,
    slug: str,
    title: str,
    description: str,
    eyebrow: str,
    heading: str,
    lede: str,
    toc: list[tuple[str, str]],
    body: str,
    breadcrumb: str,
    extra_ld: list[dict] | None = None,
) -> str:
    strings = STRINGS[language]
    other = "de" if language == "en" else "en"
    canonical = f"{BASE}{language}/{slug}"
    nav_items = "\n".join(
        '            <a href="{0}"{2}>{1}</a>'.format(
            attr(href), esc(label), ' aria-current="page"' if href == slug else ""
        )
        for href, label in strings["nav"]
    )
    toc_items = "\n".join(
        f'                <a href="#{attr(anchor)}">{esc(label)}</a>' for anchor, label in toc
    )
    blocks = [
        {
            "@context": "https://schema.org",
            "@type": "BreadcrumbList",
            "itemListElement": [
                {
                    "@type": "ListItem",
                    "position": 1,
                    "name": strings["section_root"],
                    "item": f"{BASE}{language}/",
                },
                {"@type": "ListItem", "position": 2, "name": breadcrumb, "item": canonical},
            ],
        },
        *(extra_ld or []),
    ]
    structured = "\n".join(
        '    <script type="application/ld+json">\n'
        + "\n".join("    " + line for line in json.dumps(block, indent=2, ensure_ascii=False).splitlines())
        + "\n    </script>"
        for block in blocks
    )
    related = "\n".join(
        f'            <li><a href="{attr(target)}">{esc(label)}</a></li>'
        for target, label in RELATED_LABELS[language].items()
        if target != slug
    )
    return f"""<!doctype html>
<html lang="{attr(strings['lang'])}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>{esc(title)}</title>
    <meta name="description" content="{attr(description)}">
    <meta name="robots" content="index,follow">
    <meta property="og:type" content="article">
    <meta property="og:site_name" content="datev-exporter">
    <meta property="og:locale" content="{attr(strings['locale'])}">
    <meta property="og:locale:alternate" content="{attr(STRINGS[other]['locale'])}">
    <meta property="og:title" content="{attr(title)}">
    <meta property="og:description" content="{attr(description)}">
    <meta property="og:url" content="{attr(canonical)}">
    <meta property="og:image" content="{attr(BASE)}assets/social-preview.png">
    <meta property="og:image:width" content="1280">
    <meta property="og:image:height" content="640">
    <meta property="og:image:alt" content="datev-exporter — DATEV Buchungsstapel for Java">
    <meta name="twitter:card" content="summary_large_image">
    <meta name="twitter:title" content="{attr(title)}">
    <meta name="twitter:description" content="{attr(description)}">
    <meta name="twitter:image" content="{attr(BASE)}assets/social-preview.png">
    <meta name="twitter:image:alt" content="datev-exporter — DATEV Buchungsstapel for Java">
    <link rel="canonical" href="{attr(canonical)}">
    <link rel="alternate" hreflang="en" href="{attr(BASE)}en/{attr(slug)}">
    <link rel="alternate" hreflang="de" href="{attr(BASE)}de/{attr(slug)}">
    <link rel="alternate" hreflang="x-default" href="{attr(BASE)}en/{attr(slug)}">
    <link rel="stylesheet" href="../assets/site.css">
    <link rel="icon" href="../assets/favicon.svg" type="image/svg+xml">
{structured}
</head>
<body>
<a class="skip-link" href="#content">{esc(strings['skip'])}</a>
<header class="site-header">
    <div class="nav-shell">
        <a class="brand" href="./" aria-label="{attr(strings['brand_label'])}"><span class="brand-mark" aria-hidden="true">d/x</span>datev-exporter</a>
        <nav class="site-nav" aria-label="{attr(strings['primary_nav'])}">
{nav_items}
            <a class="language-link" href="../{other}/{attr(slug)}" lang="{other}" hreflang="{other}">{other.upper()}</a>
        </nav>
    </div>
</header>

<main id="content">
    <header class="page-hero shell">
        <p class="eyebrow">{esc(eyebrow)}</p>
        <h1>{esc(heading)}</h1>
        <p class="lede">{esc(lede)}</p>
    </header>

    <div class="section compact">
        <div class="shell page-layout">
            <nav class="toc" aria-label="{attr(strings['toc'])}">
                <strong>{esc(strings['toc'])}</strong>
{toc_items}
            </nav>

            <article class="prose">
{body}
                <section id="related">
                    <h2>{esc(strings['related'])}</h2>
                    <ul>
{related}
                    </ul>
                </section>
            </article>
        </div>
    </div>
</main>

<footer class="site-footer">
    <div class="shell footer-grid">
        <p>{strings['footer_note']}</p>
        <nav class="footer-links" aria-label="{attr(strings['footer_nav'])}">
            <a href="https://github.com/mrtyldr/datev-exporter">GitHub</a>
            <a href="reference.html">{esc(strings['nav'][3][1])}</a>
            <a href="/datev-exporter/api/{VERSION}/">API {VERSION}</a>
            <a href="/datev-exporter/llms.txt">llms.txt</a>
        </nav>
    </div>
</footer>
</body>
</html>
"""


# --------------------------------------------------------------------------------------------
# Page: field reference
# --------------------------------------------------------------------------------------------

FIELDS_TEXT = {
    "en": {
        "title": "DATEV Buchungsstapel field reference (v13 and v12) — datev-exporter",
        "description": "Every DATEV Buchungsstapel column: field number, official German heading, "
                       "checker type, maximum length, decimal places, whether it is required and "
                       "whether format version 12 contains it.",
        "eyebrow": f"Field reference · schema v13 and v12 · {VERSION}",
        "heading": "All 125 Buchungsstapel columns, in official output order.",
        "lede": "Field numbers, exact German headings, checker types and lengths for format "
                "version 13, with the four differences that matter when you target version 12.",
        "breadcrumb": "Field reference",
        "toc": [
            ("overview", "What this table is"),
            ("summary", "Shape of the schema"),
            ("required", "Required fields"),
            ("repeating", "Repeating groups"),
            ("table", "Full field table"),
            ("usage", "Addressing a field from Java"),
        ],
        "overview_h": "What this table is",
        "overview_p1": "A DATEV Buchungsstapel booking row has a fixed number of columns in a fixed "
                       "order. Format version 13 defines 125 columns; version 12 defines the first "
                       "124 and omits <code>Abw. Skontokonto</code>. Column order carries meaning: "
                       "a row is positional, so field 7 is <code>Konto</code> whether or not you "
                       "supplied field 6.",
        "overview_p2": "The table below is generated from <code>DatevFieldSpecs</code>, the single "
                       "canonical copy of the schema that every module in this library derives "
                       "from. It is not a hand-maintained transcription, so it cannot drift away "
                       "from what the exporters actually write.",
        "overview_note_t": "This is a technical schema, not accounting guidance",
        "overview_note_p": "Knowing that field 9 is <code>BU-Schlüssel</code> does not tell you "
                           "which posting key your case needs. Account mapping, tax treatment and "
                           "posting logic stay with your application and your tax adviser.",
        "summary_h": "Shape of the schema",
        "summary_labels": ["columns in version 13", "columns in version 12",
                           "fields the checker marks required", "logical field types"],
        "types_caption": "Field types across the 125 version 13 columns",
        "types_head": ["Type", "Checker name", "Columns", "Meaning"],
        "type_meaning": {
            "TEXT": "Quoted textual value; the maximum length counts characters.",
            "NUMBER": "Unquoted numeric value with an optional comma decimal separator.",
            "AMOUNT": "Positive monetary value; the sign lives in Soll/Haben-Kennzeichen.",
            "ACCOUNT": "Numeric account identifier, narrowed further by the account length.",
            "DATE": "DATEV-formatted date.",
        },
        "required_h": "The five required fields",
        "required_p": "Strict validation reports an empty value in these columns as "
                      "<code>REQUIRED_FIELD</code>. Every other column may be left empty.",
        "repeating_h": "Repeating groups and one spelling trap",
        "repeating_p": "Two column families repeat as type/content pairs. Supplying one half "
                       "without the other is reported as <code>DEPENDENT_FIELD_MISSING</code> in "
                       "strict mode.",
        "repeating_items": [
            "<code>Beleginfo - Art 1</code> … <code>Beleginfo - Inhalt 8</code> — eight pairs, "
            "fields 21–36.",
            "<code>Zusatzinformation - Art 1</code> … <code>Zusatzinformation- Inhalt 20</code> — "
            "twenty pairs, fields 48–87.",
        ],
        "repeating_note_t": "Mind the space",
        "repeating_note_p": "DATEV spells the pair inconsistently: <code>Zusatzinformation - Art "
                            "1</code> has spaces around the dash, but <code>Zusatzinformation- "
                            "Inhalt 1</code> has none. Both spellings are reproduced exactly. Use "
                            "the <code>DatevField</code> constants and a typo becomes a compile "
                            "error instead of a rejected file.",
        "table_h": "Full field table",
        "table_caption": "DATEV Buchungsstapel columns, format version 13, in output order",
        "table_head": ["#", "Official heading", "DatevField constant", "Type", "Max. length",
                       "Decimals", "Required", "In v12"],
        "usage_h": "Addressing a field from Java",
        "usage_p": "Every <code>DatevColumn</code> factory accepts either the enum constant or the "
                   "raw heading string, and both produce identical output. The constant is "
                   "checked at compile time.",
        "usage_after": "<code>DatevField</code> constants are declared in output order, so "
                       "<code>ordinal() + 1</code> is the DATEV field number, and "
                       "<code>isPresentIn(DatevSchema.LEGACY_V12)</code> answers the version-12 "
                       "question for any field.",
    },
    "de": {
        "title": "DATEV-Buchungsstapel Feldreferenz (v13 und v12) — datev-exporter",
        "description": "Alle Spalten des DATEV-Buchungsstapels: Feldnummer, amtliche Überschrift, "
                       "Prüfprogramm-Typ, maximale Länge, Nachkommastellen, Pflichtfeld und "
                       "Vorhandensein in Formatversion 12.",
        "eyebrow": f"Feldreferenz · Schema v13 und v12 · {VERSION}",
        "heading": "Alle 125 Buchungsstapel-Spalten in amtlicher Reihenfolge.",
        "lede": "Feldnummern, exakte Überschriften, Prüfprogramm-Typen und Längen für "
                "Formatversion 13 — mit den Unterschieden, die bei Formatversion 12 zählen.",
        "breadcrumb": "Feldreferenz",
        "toc": [
            ("overview", "Was diese Tabelle ist"),
            ("summary", "Aufbau des Schemas"),
            ("required", "Pflichtfelder"),
            ("repeating", "Wiederholende Gruppen"),
            ("table", "Vollständige Feldtabelle"),
            ("usage", "Ein Feld aus Java ansprechen"),
        ],
        "overview_h": "Was diese Tabelle ist",
        "overview_p1": "Eine Buchungszeile im DATEV-Buchungsstapel hat eine feste Anzahl Spalten in "
                       "fester Reihenfolge. Formatversion 13 definiert 125 Spalten, Version 12 die "
                       "ersten 124 und lässt <code>Abw. Skontokonto</code> weg. Die Reihenfolge "
                       "trägt Bedeutung: eine Zeile ist positionsbasiert, Feld 7 ist "
                       "<code>Konto</code> — unabhängig davon, ob Feld 6 gefüllt wurde.",
        "overview_p2": "Die Tabelle wird aus <code>DatevFieldSpecs</code> erzeugt, der einzigen "
                       "kanonischen Schemakopie, aus der sich jedes Modul dieser Bibliothek "
                       "ableitet. Sie ist keine handgepflegte Abschrift und kann daher nicht von "
                       "dem abweichen, was die Exporter tatsächlich schreiben.",
        "overview_note_t": "Ein technisches Schema, keine buchhalterische Anleitung",
        "overview_note_p": "Zu wissen, dass Feld 9 <code>BU-Schlüssel</code> heißt, sagt nichts "
                           "darüber, welcher Buchungsschlüssel im konkreten Fall richtig ist. "
                           "Kontenzuordnung, steuerliche Behandlung und Buchungslogik bleiben bei "
                           "der aufrufenden Anwendung und der steuerlichen Beratung.",
        "summary_h": "Aufbau des Schemas",
        "summary_labels": ["Spalten in Version 13", "Spalten in Version 12",
                           "vom Prüfprogramm als notwendig markiert", "logische Feldtypen"],
        "types_caption": "Feldtypen über die 125 Spalten der Version 13",
        "types_head": ["Typ", "Name im Prüfprogramm", "Spalten", "Bedeutung"],
        "type_meaning": {
            "TEXT": "Gequoteter Textwert; die maximale Länge zählt Zeichen.",
            "NUMBER": "Ungequoteter numerischer Wert mit optionalem Komma als Dezimaltrenner.",
            "AMOUNT": "Positiver Betrag; das Vorzeichen steht im Soll/Haben-Kennzeichen.",
            "ACCOUNT": "Numerische Kontonummer, zusätzlich durch die Sachkontenlänge eingegrenzt.",
            "DATE": "Datum im DATEV-Format.",
        },
        "required_h": "Die fünf Pflichtfelder",
        "required_p": "Die strikte Validierung meldet einen leeren Wert in diesen Spalten als "
                      "<code>REQUIRED_FIELD</code>. Jede andere Spalte darf leer bleiben.",
        "repeating_h": "Wiederholende Gruppen und eine Schreibweisen-Falle",
        "repeating_p": "Zwei Spaltenfamilien wiederholen sich als Art-/Inhalt-Paare. Wird nur eine "
                       "Hälfte gefüllt, meldet der strikte Modus "
                       "<code>DEPENDENT_FIELD_MISSING</code>.",
        "repeating_items": [
            "<code>Beleginfo - Art 1</code> … <code>Beleginfo - Inhalt 8</code> — acht Paare, "
            "Felder 21–36.",
            "<code>Zusatzinformation - Art 1</code> … <code>Zusatzinformation- Inhalt 20</code> — "
            "zwanzig Paare, Felder 48–87.",
        ],
        "repeating_note_t": "Auf das Leerzeichen achten",
        "repeating_note_p": "DATEV schreibt das Paar uneinheitlich: <code>Zusatzinformation - Art "
                            "1</code> mit Leerzeichen um den Bindestrich, <code>Zusatzinformation- "
                            "Inhalt 1</code> ohne. Beide Schreibweisen werden exakt reproduziert. "
                            "Mit den <code>DatevField</code>-Konstanten wird ein Tippfehler zum "
                            "Compile-Fehler statt zu einer abgelehnten Datei.",
        "table_h": "Vollständige Feldtabelle",
        "table_caption": "Spalten des DATEV-Buchungsstapels, Formatversion 13, in Ausgabereihenfolge",
        "table_head": ["#", "Amtliche Überschrift", "DatevField-Konstante", "Typ", "Max. Länge",
                       "Nachkommastellen", "Pflicht", "In v12"],
        "usage_h": "Ein Feld aus Java ansprechen",
        "usage_p": "Jede <code>DatevColumn</code>-Factory akzeptiert entweder die Enum-Konstante "
                   "oder die Überschrift als Zeichenkette; beide erzeugen identische Ausgabe. Die "
                   "Konstante wird zur Übersetzungszeit geprüft.",
        "usage_after": "Die <code>DatevField</code>-Konstanten sind in Ausgabereihenfolge "
                       "deklariert, daher ist <code>ordinal() + 1</code> die DATEV-Feldnummer. "
                       "<code>isPresentIn(DatevSchema.LEGACY_V12)</code> beantwortet die "
                       "Version-12-Frage für jedes Feld.",
    },
}

USAGE_SNIPPET = """<pre><code>import io.github.mrtyldr.datev.core.DatevColumn;
import io.github.mrtyldr.datev.core.DatevField;
import io.github.mrtyldr.datev.core.DatevSchema;

// Field 7, "Konto" — compile-checked.
DatevColumn account = DatevColumn.account(DatevField.ACCOUNT, 1000);

// Identical output, but a typo would only surface at runtime.
DatevColumn same = DatevColumn.account("Konto", 1000);

int number = DatevField.ACCOUNT.fieldNumber();                     // 7
String heading = DatevField.ACCOUNT.heading();                     // "Konto"
boolean inLegacy = DatevField.DIFFERING_CASH_DISCOUNT_ACCOUNT
        .isPresentIn(DatevSchema.LEGACY_V12);                      // false</code></pre>"""


def build_fields_page(language: str, fields: list[Field]) -> str:
    text = FIELDS_TEXT[language]
    strings = STRINGS[language]
    yes, no = strings["yes"], strings["no"]

    type_counts: dict[str, int] = {}
    checker_names: dict[str, str] = {}
    for field in fields:
        type_counts[field.type_name] = type_counts.get(field.type_name, 0) + 1
        checker_names[field.type_name] = field.checker_type
    required = [field for field in fields if field.required]

    metrics = "\n".join(
        f'                    <div class="metric"><span class="metric-value">{esc(value)}</span>'
        f'<span class="metric-label">{esc(label)}</span></div>'
        for value, label in zip(
            [len(fields), sum(1 for f in fields if f.in_v12), len(required), len(type_counts)],
            text["summary_labels"],
        )
    )

    type_rows = "\n".join(
        f"                        <tr><th scope=\"row\"><code>{esc(name)}</code></th>"
        f"<td>{esc(checker_names[name])}</td><td>{count}</td>"
        f"<td>{text['type_meaning'][name]}</td></tr>"
        for name, count in sorted(type_counts.items(), key=lambda item: -item[1])
    )
    type_head = "".join(f"<th scope=\"col\">{esc(label)}</th>" for label in text["types_head"])

    required_rows = "\n".join(
        f"                        <tr><th scope=\"row\">{field.number}</th>"
        f"<td><code>{esc(field.heading)}</code></td>"
        f"<td><code>{esc(field.constant)}</code></td>"
        f"<td>{esc(field.checker_type)}</td></tr>"
        for field in required
    )

    field_rows = "\n".join(
        f"                        <tr><th scope=\"row\">{field.number}</th>"
        f"<td><code>{esc(field.heading)}</code></td>"
        f"<td><code>{esc(field.constant)}</code></td>"
        f"<td>{esc(field.checker_type)}</td>"
        f"<td>{field.max_length}</td>"
        f"<td>{field.decimals if field.decimals else '—'}</td>"
        f"<td>{yes if field.required else no}</td>"
        f"<td>{yes if field.in_v12 else no}</td></tr>"
        for field in fields
    )
    field_head = "".join(f"<th scope=\"col\">{esc(label)}</th>" for label in text["table_head"])
    repeating_items = "\n".join(
        f"                        <li>{item}</li>" for item in text["repeating_items"]
    )

    body = f"""                <section id="overview">
                    <h2>{esc(text['overview_h'])}</h2>
                    <p>{text['overview_p1']}</p>
                    <p>{text['overview_p2']}</p>
                    <div class="callout warning">
                        <strong>{esc(text['overview_note_t'])}</strong>
                        <p>{text['overview_note_p']}</p>
                    </div>
                </section>

                <section id="summary">
                    <h2>{esc(text['summary_h'])}</h2>
                    <div class="metrics">
{metrics}
                    </div>
                    <div class="table-wrap">
                        <table>
                            <caption>{esc(text['types_caption'])}</caption>
                            <thead><tr>{type_head}</tr></thead>
                            <tbody>
{type_rows}
                            </tbody>
                        </table>
                    </div>
                </section>

                <section id="required">
                    <h2>{esc(text['required_h'])}</h2>
                    <p>{text['required_p']}</p>
                    <div class="table-wrap">
                        <table>
                            <thead><tr><th scope="col">#</th><th scope="col">{esc(text['table_head'][1])}</th><th scope="col">{esc(text['table_head'][2])}</th><th scope="col">{esc(text['table_head'][3])}</th></tr></thead>
                            <tbody>
{required_rows}
                            </tbody>
                        </table>
                    </div>
                </section>

                <section id="repeating">
                    <h2>{esc(text['repeating_h'])}</h2>
                    <p>{text['repeating_p']}</p>
                    <ul>
{repeating_items}
                    </ul>
                    <div class="callout tip">
                        <strong>{esc(text['repeating_note_t'])}</strong>
                        <p>{text['repeating_note_p']}</p>
                    </div>
                </section>

                <section id="table">
                    <h2>{esc(text['table_h'])}</h2>
                    <div class="table-wrap">
                        <table>
                            <caption>{esc(text['table_caption'])}</caption>
                            <thead><tr>{field_head}</tr></thead>
                            <tbody>
{field_rows}
                            </tbody>
                        </table>
                    </div>
                </section>

                <section id="usage">
                    <h2>{esc(text['usage_h'])}</h2>
                    <p>{text['usage_p']}</p>
{USAGE_SNIPPET}
                    <p>{text['usage_after']}</p>
                </section>
"""
    return page_shell(
        language, "fields.html", text["title"], text["description"], text["eyebrow"],
        text["heading"], text["lede"], text["toc"], body, text["breadcrumb"],
    )


# --------------------------------------------------------------------------------------------
# Simple content pages
# --------------------------------------------------------------------------------------------

def render_sections(items: list[tuple[str, str, str]]) -> str:
    return "\n\n".join(
        f"""                <section id="{attr(anchor)}">
                    <h2>{esc(title)}</h2>
{content}
                </section>"""
        for anchor, title, content in items
    )


def table(caption: str, headings: list[str], rows: list[list[str]]) -> str:
    head = "".join(f'<th scope="col">{esc(label)}</th>' for label in headings)
    body = "\n".join(
        "                        <tr>"
        + f'<th scope="row">{row[0]}</th>'
        + "".join(f"<td>{cell}</td>" for cell in row[1:])
        + "</tr>"
        for row in rows
    )
    return f"""                    <div class="table-wrap">
                        <table>
                            <caption>{esc(caption)}</caption>
                            <thead><tr>{head}</tr></thead>
                            <tbody>
{body}
                            </tbody>
                        </table>
                    </div>"""


def build_simple_page(language: str, slug: str, spec: dict, sections: list[tuple[str, str, str]],
                      extra_ld: list[dict] | None = None) -> str:
    return page_shell(
        language, slug, spec["title"], spec["description"], spec["eyebrow"], spec["heading"],
        spec["lede"], [(anchor, title) for anchor, title, _ in sections],
        render_sections(sections), spec["breadcrumb"], extra_ld,
    )


ERROR_CODES = ["REQUIRED_FIELD", "INVALID_FORMAT", "VALUE_OUT_OF_RANGE", "TEXT_TOO_LONG",
               "UNMAPPABLE_CHARACTER", "DEPENDENT_FIELD_MISSING"]

ERRORS_TEXT = {
    "en": {
        "title": "DATEV Buchungsstapel validation errors explained — datev-exporter",
        "description": "What each of the six datev-exporter validation error codes means, when it "
                       "is raised, and how to resolve it before writing a DATEV Buchungsstapel "
                       "file.",
        "eyebrow": f"Validation reference · {VERSION}",
        "heading": "Six error codes, and what each one is telling you.",
        "lede": "Validation failures carry a stable machine-readable code, the DATEV field number "
                "and the official column name. Here is what triggers each code and what to change.",
        "breadcrumb": "Validation errors",
        "modes_h": "Three validation depths",
        "modes_p": "Depth is a deliberate choice per file. A stricter mode never changes the "
                   "bytes that are written; it only changes what is rejected before writing.",
        "modes_caption": "Validation modes and the codes each one can produce",
        "modes_head": ["Mode", "What it checks", "Possible codes"],
        "modes_rows": [
            ["<code>NONE</code>", "Nothing semantic. The exporter's structural checks — row width, "
             "control characters, encodability — still apply.", "—"],
            ["<code>FIELD_LEVEL</code>", "Each supplied non-empty cell against its official field "
             "definition.", "<code>INVALID_FORMAT</code>, <code>VALUE_OUT_OF_RANGE</code>, "
             "<code>TEXT_TOO_LONG</code>, <code>UNMAPPABLE_CHARACTER</code>"],
            ["<code>STRICT</code>", "Everything above, plus required fields and cross-field "
             "dependencies.", "all six"],
        ],
        "codes_h": "The six codes",
        "codes_caption": "Stable validation error categories",
        "codes_head": ["Code", "Raised when", "Usual fix"],
        "codes_rows": [
            ["<code>REQUIRED_FIELD</code>",
             "A field the official checker marks as necessary is empty, in strict mode on an "
             "official schema.",
             "Populate the field. Only five columns are affected: Umsatz, Soll/Haben-Kennzeichen, "
             "Konto, Gegenkonto and Belegdatum."],
            ["<code>INVALID_FORMAT</code>",
             "A value does not use DATEV's required representation — a malformed number, a date "
             "that is not a real calendar date, a flag outside its allowed set, or a control or "
             "line-separator character inside a cell.",
             "Format the value the way DATEV expects rather than the way your locale prints it. "
             "Strip newlines and tabs from free text."],
            ["<code>VALUE_OUT_OF_RANGE</code>",
             "The value is syntactically valid but exceeds its range — too many integral digits, "
             "too many decimals, or an account number wider than the configured account length.",
             "Check the field's maximum length and decimal places in the field reference, and "
             "check that the account length in your metadata matches the client."],
            ["<code>TEXT_TOO_LONG</code>",
             "A text value exceeds the field's maximum character count.",
             "Truncate deliberately in your own mapping. Silent truncation is not performed for "
             "you, because losing part of a Buchungstext is a business decision."],
            ["<code>UNMAPPABLE_CHARACTER</code>",
             "The value contains a code point that Windows-1252 cannot represent.",
             "Transliterate or replace the character before export. See the encoding reference for "
             "which characters survive."],
            ["<code>DEPENDENT_FIELD_MISSING</code>",
             "One half of a paired field group was supplied without the other, in strict mode.",
             "Supply both halves, or neither."],
        ],
        "deps_h": "The paired fields",
        "deps_p": "Strict mode enforces these pairs. Supplying either side alone produces "
                  "<code>DEPENDENT_FIELD_MISSING</code> on the missing side.",
        "deps_items": [
            "<code>Basis-Umsatz</code> ↔ <code>WKZ Basis-Umsatz</code> (fields 5 and 6).",
            "<code>Beleginfo - Art n</code> ↔ <code>Beleginfo - Inhalt n</code> for n = 1…8.",
            "<code>Zusatzinformation - Art n</code> ↔ <code>Zusatzinformation- Inhalt n</code> for "
            "n = 1…20.",
        ],
        "context_h": "Context sharpens the checks",
        "context_p": "Some rules cannot be evaluated from the schema alone. A validation context "
                     "carries the facts that make them decidable; without it, those specific "
                     "checks are simply skipped rather than guessed.",
        "context_items": [
            "<strong>Account length</strong> narrows how wide <code>Konto</code> and "
            "<code>Gegenkonto</code> may be.",
            "<strong>Fiscal year start</strong> and <strong>posting period</strong> let the "
            "four-digit <code>Belegdatum</code> be resolved against real calendar dates.",
        ],
        "context_note_t": "Passing validation is not import certification",
        "context_note_p": "These checks confirm the file matches the technical schema. They say "
                          "nothing about whether the bookings are correct, or whether a specific "
                          "DATEV product and configuration will accept the file.",
        "handling_h": "Reading errors in code",
        "handling_p": "A rejected row raises <code>DatevValidationException</code>, which carries "
                      "the full list of errors. Each error keeps its code, field number and "
                      "official column name, so failures can be logged or mapped without parsing "
                      "message text.",
    },
    "de": {
        "title": "DATEV-Buchungsstapel Validierungsfehler erklärt — datev-exporter",
        "description": "Was die sechs Validierungsfehlercodes von datev-exporter bedeuten, wann "
                       "sie ausgelöst werden und wie sie sich vor dem Schreiben einer "
                       "Buchungsstapel-Datei beheben lassen.",
        "eyebrow": f"Validierungsreferenz · {VERSION}",
        "heading": "Sechs Fehlercodes und was sie jeweils aussagen.",
        "lede": "Jeder Validierungsfehler trägt einen stabilen maschinenlesbaren Code, die "
                "DATEV-Feldnummer und die amtliche Spaltenbezeichnung. Hier steht, was den Code "
                "auslöst und was zu ändern ist.",
        "breadcrumb": "Validierungsfehler",
        "modes_h": "Drei Prüftiefen",
        "modes_p": "Die Tiefe ist eine bewusste Entscheidung je Datei. Ein strikterer Modus ändert "
                   "nie die geschriebenen Bytes, sondern nur, was vorher abgelehnt wird.",
        "modes_caption": "Validierungsmodi und die jeweils möglichen Codes",
        "modes_head": ["Modus", "Was geprüft wird", "Mögliche Codes"],
        "modes_rows": [
            ["<code>NONE</code>", "Keine semantische Prüfung. Die strukturellen Prüfungen des "
             "Exporters — Zeilenbreite, Steuerzeichen, Kodierbarkeit — gelten weiterhin.", "—"],
            ["<code>FIELD_LEVEL</code>", "Jede gefüllte Zelle gegen ihre amtliche Felddefinition.",
             "<code>INVALID_FORMAT</code>, <code>VALUE_OUT_OF_RANGE</code>, "
             "<code>TEXT_TOO_LONG</code>, <code>UNMAPPABLE_CHARACTER</code>"],
            ["<code>STRICT</code>", "Alles davon, zusätzlich Pflichtfelder und "
             "Feldabhängigkeiten.", "alle sechs"],
        ],
        "codes_h": "Die sechs Codes",
        "codes_caption": "Stabile Kategorien von Validierungsfehlern",
        "codes_head": ["Code", "Wird ausgelöst, wenn", "Übliche Behebung"],
        "codes_rows": [
            ["<code>REQUIRED_FIELD</code>",
             "Ein vom amtlichen Prüfprogramm als notwendig markiertes Feld ist leer — im strikten "
             "Modus auf einem amtlichen Schema.",
             "Feld füllen. Betroffen sind nur fünf Spalten: Umsatz, Soll/Haben-Kennzeichen, Konto, "
             "Gegenkonto und Belegdatum."],
            ["<code>INVALID_FORMAT</code>",
             "Ein Wert entspricht nicht der von DATEV geforderten Darstellung — eine fehlerhafte "
             "Zahl, ein Datum ohne realen Kalenderbezug, ein Kennzeichen außerhalb der erlaubten "
             "Menge oder ein Steuer- bzw. Zeilentrennzeichen innerhalb einer Zelle.",
             "Den Wert so formatieren, wie DATEV ihn erwartet, nicht wie das eigene Locale ihn "
             "ausgibt. Zeilenumbrüche und Tabulatoren aus Freitext entfernen."],
            ["<code>VALUE_OUT_OF_RANGE</code>",
             "Der Wert ist syntaktisch gültig, überschreitet aber seinen Bereich — zu viele "
             "Vorkommastellen, zu viele Nachkommastellen oder eine Kontonummer breiter als die "
             "konfigurierte Sachkontenlänge.",
             "Maximale Länge und Nachkommastellen in der Feldreferenz prüfen und sicherstellen, "
             "dass die Sachkontenlänge in den Metadaten zum Mandanten passt."],
            ["<code>TEXT_TOO_LONG</code>",
             "Ein Textwert überschreitet die maximale Zeichenzahl des Feldes.",
             "Bewusst in der eigenen Zuordnung kürzen. Es wird nicht still gekürzt, weil der "
             "Verlust eines Teils des Buchungstexts eine fachliche Entscheidung ist."],
            ["<code>UNMAPPABLE_CHARACTER</code>",
             "Der Wert enthält ein Zeichen, das Windows-1252 nicht darstellen kann.",
             "Das Zeichen vor dem Export transliterieren oder ersetzen. Die Kodierungsreferenz "
             "zeigt, welche Zeichen erhalten bleiben."],
            ["<code>DEPENDENT_FIELD_MISSING</code>",
             "Im strikten Modus wurde eine Hälfte eines Feldpaares ohne die andere gefüllt.",
             "Beide Hälften füllen — oder keine."],
        ],
        "deps_h": "Die Feldpaare",
        "deps_p": "Der strikte Modus erzwingt diese Paare. Wird nur eine Seite gefüllt, entsteht "
                  "<code>DEPENDENT_FIELD_MISSING</code> auf der fehlenden Seite.",
        "deps_items": [
            "<code>Basis-Umsatz</code> ↔ <code>WKZ Basis-Umsatz</code> (Felder 5 und 6).",
            "<code>Beleginfo - Art n</code> ↔ <code>Beleginfo - Inhalt n</code> für n = 1…8.",
            "<code>Zusatzinformation - Art n</code> ↔ <code>Zusatzinformation- Inhalt n</code> für "
            "n = 1…20.",
        ],
        "context_h": "Kontext schärft die Prüfungen",
        "context_p": "Manche Regeln lassen sich nicht allein aus dem Schema entscheiden. Ein "
                     "Validierungskontext trägt die Angaben, die sie entscheidbar machen; ohne ihn "
                     "werden genau diese Prüfungen übersprungen statt geraten.",
        "context_items": [
            "Die <strong>Sachkontenlänge</strong> begrenzt die Breite von <code>Konto</code> und "
            "<code>Gegenkonto</code>.",
            "<strong>Wirtschaftsjahresbeginn</strong> und <strong>Buchungszeitraum</strong> "
            "erlauben es, das vierstellige <code>Belegdatum</code> gegen reale Kalenderdaten "
            "aufzulösen.",
        ],
        "context_note_t": "Bestandene Validierung ist keine Importzertifizierung",
        "context_note_p": "Diese Prüfungen bestätigen, dass die Datei dem technischen Schema "
                          "entspricht. Sie sagen nichts darüber aus, ob die Buchungen fachlich "
                          "richtig sind oder ob ein bestimmtes DATEV-Produkt in einer bestimmten "
                          "Konfiguration die Datei annimmt.",
        "handling_h": "Fehler im Code auswerten",
        "handling_p": "Eine abgelehnte Zeile löst eine <code>DatevValidationException</code> aus, "
                      "die die vollständige Fehlerliste trägt. Jeder Fehler behält Code, "
                      "Feldnummer und amtliche Spaltenbezeichnung, sodass sich Fehler protokollieren "
                      "oder abbilden lassen, ohne Meldungstexte zu parsen.",
    },
}

ERROR_SNIPPET = """<pre><code>import io.github.mrtyldr.datev.core.DatevValidationError;
import io.github.mrtyldr.datev.core.DatevValidationException;

try {
    file.append(columns);
} catch (DatevValidationException failure) {
    for (DatevValidationError error : failure.errors()) {
        log.warn("field {} ({}): {} — {}",
                error.fieldNumber(),
                error.canonicalKey(),
                error.code(),
                error.message());
    }
}</code></pre>"""


def build_errors_page(language: str) -> str:
    text = ERRORS_TEXT[language]
    question_template = (
        "What does the {} validation error mean in a DATEV Buchungsstapel export?"
        if language == "en"
        else "Was bedeutet der Validierungsfehler {} beim DATEV-Buchungsstapel-Export?"
    )
    faq = [
        (
            question_template.format(row[0].replace("<code>", "").replace("</code>", "")),
            row[1] + " " + row[2],
        )
        for row in text["codes_rows"]
    ]
    sections = [
        ("modes", text["modes_h"],
         f"                    <p>{text['modes_p']}</p>\n"
         + table(text["modes_caption"], text["modes_head"], text["modes_rows"])),
        ("codes", text["codes_h"],
         table(text["codes_caption"], text["codes_head"], text["codes_rows"])),
        ("dependencies", text["deps_h"],
         f"                    <p>{text['deps_p']}</p>\n                    <ul>\n"
         + "\n".join(f"                        <li>{item}</li>" for item in text["deps_items"])
         + "\n                    </ul>"),
        ("context", text["context_h"],
         f"                    <p>{text['context_p']}</p>\n                    <ul>\n"
         + "\n".join(f"                        <li>{item}</li>" for item in text["context_items"])
         + "\n                    </ul>\n"
         + f"""                    <div class="callout warning">
                        <strong>{esc(text['context_note_t'])}</strong>
                        <p>{text['context_note_p']}</p>
                    </div>"""),
        ("handling", text["handling_h"],
         f"                    <p>{text['handling_p']}</p>\n{ERROR_SNIPPET}"),
    ]
    faq_ld = {
        "@context": "https://schema.org",
        "@type": "FAQPage",
        "inLanguage": language,
        "url": f"{BASE}{language}/validation-errors.html",
        "mainEntity": [
            {
                "@type": "Question",
                "name": question,
                "acceptedAnswer": {
                    "@type": "Answer",
                    "text": html.unescape(
                        answer.replace("<code>", "").replace("</code>", "")
                    ),
                },
            }
            for question, answer in faq
        ],
    }
    return build_simple_page(language, "validation-errors.html", text, sections, [faq_ld])


HEADER_TEXT = {
    "en": {
        "title": "EXTF header: the DATEV Buchungsstapel management record — datev-exporter",
        "description": "The 31-field EXTF management record that opens every DATEV Buchungsstapel "
                       "file: fixed constants, date and timestamp formats, quoting rules and the "
                       "v13/v12 difference.",
        "eyebrow": f"Format reference · EXTF · {VERSION}",
        "heading": "The line before the headings.",
        "lede": "A Buchungsstapel file opens with a management record whose shape has nothing to "
                "do with the booking rows beneath it. Getting it wrong is the most common reason "
                "an otherwise correct export is rejected.",
        "breadcrumb": "EXTF header",
        "shape_h": "Three records, two shapes",
        "shape_p1": "A complete file is three layers: the EXTF management record, the exact "
                    "versioned column heading, and the booking rows. Only the last two share a "
                    "shape. The management record always has 31 fields, whichever format version "
                    "you target.",
        "shape_p2": "The first five fields are fixed identifiers. They are what a reader uses to "
                    "recognise the file at all:",
        "constants_caption": "Fixed identifying fields of the management record",
        "constants_head": ["Field", "Value", "Meaning"],
        "sample_h": "A real management record",
        "sample_p": "These lines are produced by the library itself, from metadata with a fixed "
                    "timestamp so the output is reproducible.",
        "sample_v13": "Format version 13",
        "sample_v12": "Format version 12 — identical except field 5",
        "sample_note_t": "Empty is not the same as absent",
        "sample_note_p": "Reserved fields are written as empty cells, and text fields stay quoted "
                         "even when empty. Dropping a field instead of emptying it shifts every "
                         "later field by one position.",
        "formats_h": "Formats that trip people up",
        "formats_items": [
            "<strong>Created-at timestamp</strong> uses <code>yyyyMMddHHmmssSSS</code> — 17 digits "
            "including milliseconds, unquoted.",
            "<strong>Fiscal year start and posting period</strong> use <code>yyyyMMdd</code>, "
            "unquoted.",
            "<strong>Text fields are quoted</strong>, including designated empty ones. A quote "
            "inside application information is escaped by doubling it.",
            "<strong>Numeric fields are unquoted</strong>, including the adviser and client "
            "numbers.",
        ],
        "enums_h": "The coded fields",
        "enums_p": "Four fields take values from a closed set rather than free input.",
        "booking_caption": "Booking type",
        "purpose_caption": "Accounting purpose",
        "enum_head": ["Constant", "DATEV value"],
        "builder_h": "Building it from Java",
        "builder_p": "Metadata is built once per file and validated on construction, so an "
                     "impossible combination fails before any row is written. The builder chooses "
                     "the format version, and the exporter refuses a management record whose "
                     "version does not match its heading.",
        "builder_note_t": "One version per file",
        "builder_note_p": "A v12 management record cannot be combined with the 125-column v13 "
                          "heading. The builder rejects the mismatch rather than writing a file "
                          "that no importer can interpret.",
    },
    "de": {
        "title": "EXTF-Header: der Verwaltungssatz des DATEV-Buchungsstapels — datev-exporter",
        "description": "Der EXTF-Verwaltungssatz mit 31 Feldern am Anfang jeder "
                       "DATEV-Buchungsstapel-Datei: feste Konstanten, Datums- und Zeitformate, "
                       "Quoting-Regeln und der Unterschied zwischen v13 und v12.",
        "eyebrow": f"Formatreferenz · EXTF · {VERSION}",
        "heading": "Die Zeile vor den Überschriften.",
        "lede": "Eine Buchungsstapel-Datei beginnt mit einem Verwaltungssatz, dessen Aufbau nichts "
                "mit den darunter liegenden Buchungszeilen zu tun hat. Fehler hier sind der "
                "häufigste Grund, warum ein sonst korrekter Export abgelehnt wird.",
        "breadcrumb": "EXTF-Header",
        "shape_h": "Drei Sätze, zwei Formen",
        "shape_p1": "Eine vollständige Datei besteht aus drei Schichten: dem "
                    "EXTF-Verwaltungssatz, der exakten versionierten Spaltenüberschrift und den "
                    "Buchungszeilen. Nur die letzten beiden teilen ihre Form. Der Verwaltungssatz "
                    "hat immer 31 Felder, unabhängig von der Formatversion.",
        "shape_p2": "Die ersten fünf Felder sind feste Kennungen. An ihnen erkennt ein Leser die "
                    "Datei überhaupt erst:",
        "constants_caption": "Feste Kennfelder des Verwaltungssatzes",
        "constants_head": ["Feld", "Wert", "Bedeutung"],
        "sample_h": "Ein echter Verwaltungssatz",
        "sample_p": "Diese Zeilen erzeugt die Bibliothek selbst, aus Metadaten mit festem "
                    "Zeitstempel, damit die Ausgabe reproduzierbar ist.",
        "sample_v13": "Formatversion 13",
        "sample_v12": "Formatversion 12 — identisch bis auf Feld 5",
        "sample_note_t": "Leer ist nicht dasselbe wie fehlend",
        "sample_note_p": "Reservierte Felder werden als leere Zellen geschrieben, und Textfelder "
                         "bleiben auch leer gequotet. Ein Feld wegzulassen statt es zu leeren "
                         "verschiebt jedes folgende Feld um eine Position.",
        "formats_h": "Formate, über die man stolpert",
        "formats_items": [
            "Der <strong>Erstellungszeitstempel</strong> nutzt <code>yyyyMMddHHmmssSSS</code> — 17 "
            "Ziffern inklusive Millisekunden, ungequotet.",
            "<strong>Wirtschaftsjahresbeginn und Buchungszeitraum</strong> nutzen "
            "<code>yyyyMMdd</code>, ungequotet.",
            "<strong>Textfelder werden gequotet</strong>, auch die vorgesehenen leeren. Ein "
            "Anführungszeichen in der Anwendungsinformation wird durch Verdopplung maskiert.",
            "<strong>Numerische Felder bleiben ungequotet</strong>, einschließlich Berater- und "
            "Mandantennummer.",
        ],
        "enums_h": "Die kodierten Felder",
        "enums_p": "Vier Felder nehmen Werte aus einer geschlossenen Menge statt freier Eingabe.",
        "booking_caption": "Buchungstyp",
        "purpose_caption": "Rechnungslegungszweck",
        "enum_head": ["Konstante", "DATEV-Wert"],
        "builder_h": "Aufbau aus Java",
        "builder_p": "Die Metadaten werden einmal je Datei gebaut und bei der Konstruktion "
                     "validiert, sodass eine unmögliche Kombination scheitert, bevor eine Zeile "
                     "geschrieben wird. Der Builder wählt die Formatversion, und der Exporter "
                     "lehnt einen Verwaltungssatz ab, dessen Version nicht zur Überschrift passt.",
        "builder_note_t": "Eine Version je Datei",
        "builder_note_p": "Ein v12-Verwaltungssatz lässt sich nicht mit der 125-spaltigen "
                          "v13-Überschrift kombinieren. Der Builder weist die Abweichung zurück, "
                          "statt eine Datei zu schreiben, die kein Importeur deuten kann.",
    },
}

CONSTANT_MEANINGS = {
    "en": [
        ("1", "Identifies an EXTF export file."),
        ("2", "Header version."),
        ("3", "Format category for Buchungsstapel."),
        ("4", "Format name."),
        ("5", "Data format version: 13 or 12."),
    ],
    "de": [
        ("1", "Kennzeichnet eine EXTF-Exportdatei."),
        ("2", "Header-Version."),
        ("3", "Formatkategorie für Buchungsstapel."),
        ("4", "Formatname."),
        ("5", "Datenformatversion: 13 oder 12."),
    ],
}

BOOKING_TYPES = [("FINANCIAL_ACCOUNTING", 1), ("ANNUAL_FINANCIAL_STATEMENTS", 2)]
ACCOUNTING_PURPOSES = [("INDEPENDENT", 0), ("TAX_LAW", 30), ("CALCULATION", 40),
                       ("COMMERCIAL_LAW", 50), ("IFRS", 64)]

HEADER_SNIPPET = """<pre><code>import io.github.mrtyldr.datev.core.DatevMetadata;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;

DatevMetadata metadata = DatevMetadata.bookingBatchV13()
        .createdAt(LocalDateTime.now())
        .origin("RE")
        .exportedBy("my_application")
        .advisorNumber(1001)
        .clientNumber(1)
        .fiscalYearStart(LocalDate.of(2026, 1, 1))
        .accountLength(4)
        .period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        .description("August 2026")
        .currency(Currency.getInstance("EUR"))
        .applicationInformation("my-application")
        .build();

String record = metadata.toCsvLine();   // the 31-field management record</code></pre>"""


def build_header_page(language: str, header: dict[str, str]) -> str:
    text = HEADER_TEXT[language]
    constant_values = ["EXTF", header["headerVersion"], header["formatCategory"],
                       header["formatName"], f"{header['formatVersion']} / "
                                              f"{header['legacyFormatVersion']}"]
    constant_rows = [
        [number, f"<code>{esc(value)}</code>", meaning]
        for (number, meaning), value in zip(CONSTANT_MEANINGS[language], constant_values)
    ]
    booking_rows = [[f"<code>{esc(name)}</code>", str(code)] for name, code in BOOKING_TYPES]
    purpose_rows = [[f"<code>{esc(name)}</code>", str(code)] for name, code in ACCOUNTING_PURPOSES]
    format_items = "\n".join(
        f"                        <li>{item}</li>" for item in text["formats_items"]
    )
    sections = [
        ("shape", text["shape_h"],
         f"                    <p>{text['shape_p1']}</p>\n"
         f"                    <p>{text['shape_p2']}</p>\n"
         + table(text["constants_caption"], text["constants_head"], constant_rows)),
        ("sample", text["sample_h"],
         f"                    <p>{text['sample_p']}</p>\n"
         f"                    <p><strong>{esc(text['sample_v13'])}</strong></p>\n"
         f"<pre><code>{esc(header['v13Record'])}</code></pre>\n"
         f"                    <p><strong>{esc(text['sample_v12'])}</strong></p>\n"
         f"<pre><code>{esc(header['v12Record'])}</code></pre>\n"
         + f"""                    <div class="callout warning">
                        <strong>{esc(text['sample_note_t'])}</strong>
                        <p>{text['sample_note_p']}</p>
                    </div>"""),
        ("formats", text["formats_h"],
         f"                    <ul>\n{format_items}\n                    </ul>"),
        ("coded", text["enums_h"],
         f"                    <p>{text['enums_p']}</p>\n"
         + table(text["booking_caption"], text["enum_head"], booking_rows) + "\n"
         + table(text["purpose_caption"], text["enum_head"], purpose_rows)),
        ("builder", text["builder_h"],
         f"                    <p>{text['builder_p']}</p>\n{HEADER_SNIPPET}\n"
         + f"""                    <div class="callout warning">
                        <strong>{esc(text['builder_note_t'])}</strong>
                        <p>{text['builder_note_p']}</p>
                    </div>"""),
    ]
    return build_simple_page(language, "extf-header.html", text, sections)


ENCODABLE = "ä ö ü ß Ä Ö Ü € § µ ° á é í ó ú ñ ç å ø æ š ž"
UNENCODABLE = "ı ğ ş ł ą č ř ő ū 日 😀"

ENCODING_TEXT = {
    "en": {
        "title": "Windows-1252, CRLF and umlauts in DATEV exports — datev-exporter",
        "description": "How DATEV Buchungsstapel files are encoded: Windows-1252 bytes, CRLF "
                       "records, semicolon delimiters, quoting rules, and which characters "
                       "survive the conversion.",
        "eyebrow": f"Codec reference · {VERSION}",
        "heading": "Windows-1252 is not a detail you can postpone.",
        "lede": "A Buchungsstapel file is bytes, not text. Umlauts survive; a Turkish dotless i "
                "does not. One careless UTF-8 step after export undoes everything.",
        "breadcrumb": "Encoding",
        "contract_h": "The byte contract",
        "contract_caption": "Codec constants shared by every exporter",
        "contract_head": ["Property", "Value"],
        "contract_rows_labels": ["Character encoding", "Record separator", "Field delimiter",
                                 "Quote character", "Escaped quote"],
        "quoting_h": "When a value is quoted",
        "quoting_p": "Two independent rules decide quoting, and both are applied.",
        "quoting_items": [
            "<strong>Official text columns are always quoted</strong>, even when empty. 84 of the "
            "125 version 13 columns are text columns.",
            "<strong>Any other value is quoted only when it must be</strong> — that is, when it "
            "contains a semicolon or a quote character.",
            "<strong>An embedded quote is doubled</strong>, never backslash-escaped.",
        ],
        "chars_h": "Which characters survive",
        "chars_p1": "Windows-1252 is a single-byte encoding, so it can represent at most 256 code "
                    "points. German text is comfortably inside that range; a lot of other text is "
                    "not.",
        "chars_ok": "Encodable — written unchanged",
        "chars_bad": "Not encodable — rejected as UNMAPPABLE_CHARACTER",
        "chars_p2": "A value containing an unencodable character is refused rather than silently "
                    "replaced with a question mark, because a corrupted Buchungstext is harder to "
                    "notice later than a failed export now. Transliterate deliberately in your own "
                    "mapping if your source data can contain such characters.",
        "control_h": "Control characters",
        "control_p": "Control, line-separator and paragraph-separator characters are rejected "
                     "anywhere in a cell, reported as <code>INVALID_FORMAT</code>. A newline "
                     "inside a Buchungstext would otherwise split one booking row into two "
                     "unusable records.",
        "pitfalls_h": "How exports get corrupted after they are written",
        "pitfalls_p": "The library controls the bytes it writes. Everything downstream of that is "
                      "yours to protect.",
        "pitfalls_items": [
            "A text editor that opens the file and saves it back as UTF-8 — every umlaut becomes "
            "two bytes and the file is no longer valid.",
            "A transfer or archiving step configured for text mode that rewrites CRLF to LF.",
            "Reading the file back with the platform default charset instead of Windows-1252.",
            "A templating or logging layer that normalises the output to Unicode NFD, splitting "
            "umlauts into base letter plus combining mark.",
        ],
        "pitfalls_note_t": "Verify the bytes, not the rendering",
        "pitfalls_note_p": "A file that looks right in an editor may already be broken. Check the "
                           "byte length and the encoding, and keep the generated file untouched "
                           "between export and import.",
    },
    "de": {
        "title": "Windows-1252, CRLF und Umlaute im DATEV-Export — datev-exporter",
        "description": "Wie DATEV-Buchungsstapel-Dateien kodiert werden: Windows-1252-Bytes, "
                       "CRLF-Sätze, Semikolon als Trennzeichen, Quoting-Regeln und welche Zeichen "
                       "die Umwandlung überstehen.",
        "eyebrow": f"Codec-Referenz · {VERSION}",
        "heading": "Windows-1252 ist kein Detail, das warten kann.",
        "lede": "Eine Buchungsstapel-Datei besteht aus Bytes, nicht aus Text. Umlaute überstehen "
                "das; ein türkisches punktloses i nicht. Ein unbedachter UTF-8-Schritt nach dem "
                "Export macht alles zunichte.",
        "breadcrumb": "Kodierung",
        "contract_h": "Der Byte-Vertrag",
        "contract_caption": "Codec-Konstanten, die alle Exporter teilen",
        "contract_head": ["Eigenschaft", "Wert"],
        "contract_rows_labels": ["Zeichenkodierung", "Satztrenner", "Feldtrenner",
                                 "Anführungszeichen", "Maskiertes Anführungszeichen"],
        "quoting_h": "Wann ein Wert gequotet wird",
        "quoting_p": "Zwei unabhängige Regeln entscheiden über das Quoting, beide werden "
                     "angewendet.",
        "quoting_items": [
            "<strong>Amtliche Textspalten werden immer gequotet</strong>, auch wenn sie leer sind. "
            "84 der 125 Spalten in Version 13 sind Textspalten.",
            "<strong>Jeder andere Wert wird nur gequotet, wenn es nötig ist</strong> — also wenn er "
            "ein Semikolon oder ein Anführungszeichen enthält.",
            "<strong>Ein enthaltenes Anführungszeichen wird verdoppelt</strong>, nie mit Backslash "
            "maskiert.",
        ],
        "chars_h": "Welche Zeichen überstehen",
        "chars_p1": "Windows-1252 ist eine Ein-Byte-Kodierung und kann höchstens 256 Zeichen "
                    "darstellen. Deutscher Text liegt bequem darin, viel anderer Text nicht.",
        "chars_ok": "Kodierbar — werden unverändert geschrieben",
        "chars_bad": "Nicht kodierbar — abgelehnt als UNMAPPABLE_CHARACTER",
        "chars_p2": "Ein Wert mit einem nicht kodierbaren Zeichen wird abgelehnt statt still durch "
                    "ein Fragezeichen ersetzt, weil ein beschädigter Buchungstext später schwerer "
                    "auffällt als ein jetzt fehlgeschlagener Export. Wenn die Quelldaten solche "
                    "Zeichen enthalten können, sollte in der eigenen Zuordnung bewusst "
                    "transliteriert werden.",
        "control_h": "Steuerzeichen",
        "control_p": "Steuer-, Zeilen- und Absatztrennzeichen werden an jeder Stelle einer Zelle "
                     "abgelehnt und als <code>INVALID_FORMAT</code> gemeldet. Ein Zeilenumbruch "
                     "in einem Buchungstext würde sonst eine Buchungszeile in zwei unbrauchbare "
                     "Sätze zerlegen.",
        "pitfalls_h": "Wie Exporte nach dem Schreiben beschädigt werden",
        "pitfalls_p": "Die Bibliothek kontrolliert die Bytes, die sie schreibt. Alles danach liegt "
                      "in eigener Verantwortung.",
        "pitfalls_items": [
            "Ein Texteditor, der die Datei öffnet und als UTF-8 zurückspeichert — jeder Umlaut "
            "wird zu zwei Bytes und die Datei ist ungültig.",
            "Ein Übertragungs- oder Archivierungsschritt im Textmodus, der CRLF zu LF umschreibt.",
            "Das Zurücklesen der Datei mit dem Plattform-Standardzeichensatz statt Windows-1252.",
            "Eine Template- oder Logging-Schicht, die die Ausgabe auf Unicode NFD normalisiert und "
            "Umlaute in Grundbuchstabe plus kombinierendes Zeichen zerlegt.",
        ],
        "pitfalls_note_t": "Die Bytes prüfen, nicht die Darstellung",
        "pitfalls_note_p": "Eine Datei, die im Editor richtig aussieht, kann bereits defekt sein. "
                           "Bytelänge und Kodierung prüfen und die erzeugte Datei zwischen Export "
                           "und Import unangetastet lassen.",
    },
}


def build_encoding_page(language: str, header: dict[str, str]) -> str:
    text = ENCODING_TEXT[language]
    values = [
        f"<code>{esc(header['charset'])}</code>",
        f"<code>{esc(header['lineSeparator'])}</code> (CRLF)",
        f"<code>{esc(header['delimiter'])}</code>",
        "<code>\"</code>",
        "<code>\"\"</code>",
    ]
    rows = [[esc(label), value] for label, value in zip(text["contract_rows_labels"], values)]
    quoting_items = "\n".join(
        f"                        <li>{item}</li>" for item in text["quoting_items"]
    )
    pitfall_items = "\n".join(
        f"                        <li>{item}</li>" for item in text["pitfalls_items"]
    )
    sections = [
        ("contract", text["contract_h"],
         table(text["contract_caption"], text["contract_head"], rows)),
        ("quoting", text["quoting_h"],
         f"                    <p>{text['quoting_p']}</p>\n                    <ul>\n"
         f"{quoting_items}\n                    </ul>"),
        ("characters", text["chars_h"],
         f"                    <p>{text['chars_p1']}</p>\n"
         f"                    <p><strong>{esc(text['chars_ok'])}</strong></p>\n"
         f"<pre><code>{esc(ENCODABLE)}</code></pre>\n"
         f"                    <p><strong>{esc(text['chars_bad'])}</strong></p>\n"
         f"<pre><code>{esc(UNENCODABLE)}</code></pre>\n"
         f"                    <p>{text['chars_p2']}</p>"),
        ("control", text["control_h"], f"                    <p>{text['control_p']}</p>"),
        ("pitfalls", text["pitfalls_h"],
         f"                    <p>{text['pitfalls_p']}</p>\n                    <ul>\n"
         f"{pitfall_items}\n                    </ul>\n"
         + f"""                    <div class="callout warning">
                        <strong>{esc(text['pitfalls_note_t'])}</strong>
                        <p>{text['pitfalls_note_p']}</p>
                    </div>"""),
    ]
    return build_simple_page(language, "encoding.html", text, sections)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fields", type=Path, default=Path("build/fields.tsv"))
    parser.add_argument("--header", type=Path, default=Path("build/header.tsv"))
    parser.add_argument("--website", type=Path, default=Path("website"))
    args = parser.parse_args()

    fields = read_fields(args.fields)
    header = read_header(args.header)

    written = 0
    for language in ("en", "de"):
        pages = {
            "fields.html": build_fields_page(language, fields),
            "validation-errors.html": build_errors_page(language),
            "extf-header.html": build_header_page(language, header),
            "encoding.html": build_encoding_page(language, header),
        }
        for name, markup in pages.items():
            target = args.website / language / name
            target.write_text(markup, encoding="utf-8")
            print(f"wrote {target}")
            written += 1
    print(f"{written} page(s) generated from {len(fields)} field specs")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
