#!/usr/bin/env python3
"""Assemble and validate the static GitHub Pages artifact.

The authored website is copied verbatim. Generated, versioned Javadocs and small discovery files
are then added around it so the deployable directory never has to be committed.
"""

from __future__ import annotations

import argparse
import html
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import shutil
import sys
import tempfile
from urllib.parse import quote, unquote, urljoin, urlparse
from xml.etree import ElementTree


MODULES = (
    (
        "datev-exporter-core",
        "Core schema, metadata, CSV codec and validation contracts",
    ),
    (
        "datev-exporter-plain",
        "Fixed v13/v12 complete-file and forward-only streaming exporters",
    ),
    (
        "datev-exporter-field-validator",
        "Optional semantic field validation",
    ),
    (
        "datev-exporter-advanced",
        "Custom-header exporter with built-in validation modes",
    ),
    (
        "datev-exporter-advanced-univocity",
        "Optional Univocity CsvWriter interoperability",
    ),
)

# Search-engine ownership tokens must be served byte for byte as the provider issued them, so they
# are exempt from the page-quality checks below. They are never linked and carry no content.
VERIFICATION_TOKENS = re.compile(r"google[0-9a-f]+\.html\Z")

SEMVER = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\Z")
INDEXNOW_KEY_PATTERN = re.compile(r"[A-Za-z0-9-]{8,128}\Z")


class ResourceParser(HTMLParser):
    """Collect resource and navigation links without requiring a third-party parser."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.references: list[str] = []
        self.description = ""
        self.has_heading = False
        self.has_meta_refresh = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag in {"a", "link"} and attributes.get("href"):
            self.references.append(attributes["href"])
        if tag in {"img", "script", "source"} and attributes.get("src"):
            self.references.append(attributes["src"])
        if tag == "h1":
            self.has_heading = True
        if tag == "meta":
            if (attributes.get("name") or "").lower() == "description":
                self.description = (attributes.get("content") or "").strip()
            if (attributes.get("http-equiv") or "").lower() == "refresh":
                self.has_meta_refresh = True


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--website", type=Path, default=Path("website"))
    parser.add_argument("--output", type=Path, default=Path("build/pages"))
    parser.add_argument(
        "--javadoc-root",
        type=Path,
        default=Path("."),
        help="Checkout root containing <module>/build/docs/javadoc directories",
    )
    parser.add_argument("--version", required=True)
    parser.add_argument(
        "--base-url",
        default="https://mrtyldr.github.io/datev-exporter/",
        help="Canonical URL ending in a slash",
    )
    return parser.parse_args()


def indexnow_key() -> str:
    key = Path(__file__).with_name("indexnow-key.txt").read_text(encoding="utf-8").strip()
    if not INDEXNOW_KEY_PATTERN.fullmatch(key):
        raise RuntimeError("scripts/indexnow-key.txt must contain 8-128 letters, digits or dashes")
    return key


def page(
    title: str,
    body: str,
    canonical: str,
    base_path: str,
    description: str,
    robots: str = "index,follow",
) -> str:
    escaped_title = html.escape(title)
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="{html.escape(robots, quote=True)}">
  <title>{escaped_title}</title>
  <meta name="description" content="{html.escape(description, quote=True)}">
  <link rel="canonical" href="{html.escape(canonical, quote=True)}">
  <link rel="stylesheet" href="{html.escape(base_path, quote=True)}assets/site.css">
</head>
<body>
  <main>
    {body}
  </main>
</body>
</html>
"""


def write_api_indexes(stage: Path, version: str, base_url: str) -> None:
    api_root = stage / "api"
    version_root = api_root / version
    version_root.mkdir(parents=True, exist_ok=True)

    module_items = "\n".join(
        "      <li><a href=\"{0}/\"><code>{0}</code></a> — {1}</li>".format(
            html.escape(module), html.escape(description)
        )
        for module, description in MODULES
    )
    version_body = f"""<p><a href="../../">DATEV exporter documentation</a></p>
    <h1>DATEV exporter {html.escape(version)} API</h1>
    <p>Javadoc for the five Java modules published to Maven Central.</p>
    <ul>
{module_items}
    </ul>"""
    (version_root / "index.html").write_text(
        page(
            f"DATEV exporter {version} API",
            version_body,
            urljoin(base_url, f"api/{version}/"),
            "../../",
            f"Javadoc for the five DATEV exporter {version} Java modules published to Maven Central: "
            "core schema, plain exporters, field validation, advanced exporter and Univocity "
            "interoperability.",
        ),
        encoding="utf-8",
    )

    api_body = f"""<p><a href="../">DATEV exporter documentation</a></p>
    <h1>Java API documentation</h1>
    <p><a href="{html.escape(version)}/">Version {html.escape(version)}</a> is the current published API.</p>"""
    (api_root / "index.html").write_text(
        page(
            "DATEV exporter Java API",
            api_body,
            urljoin(base_url, "api/"),
            "../",
            "Versioned Javadoc index for the DATEV exporter Java library, which generates, "
            "validates and streams DATEV Buchungsstapel / EXTF v13 and v12 files.",
        ),
        encoding="utf-8",
    )

    latest = api_root / "latest"
    latest.mkdir()
    target = f"../{version}/"
    # A meta refresh is deliberately avoided here: crawlers flag it, and GitHub Pages cannot serve
    # an HTTP redirect. The canonical link plus a single visible link keeps the alias resolvable
    # for people while pointing every crawler at the versioned page.
    latest_body = f"""<p><a href="../../">DATEV exporter documentation</a></p>
    <h1>Latest DATEV exporter API</h1>
    <p>The current published Java API is
    <a href="{html.escape(target, quote=True)}">version {html.escape(version)}</a>.</p>"""
    (latest / "index.html").write_text(
        page(
            "Latest DATEV exporter API",
            latest_body,
            urljoin(base_url, f"api/{version}/"),
            "../../",
            f"Stable alias pointing at the current DATEV exporter Java API, version {version}.",
            robots="noindex,follow",
        ),
        encoding="utf-8",
    )


def copy_javadocs(javadoc_root: Path, stage: Path, version: str) -> None:
    destination_root = stage / "api" / version
    missing: list[str] = []
    for module, _ in MODULES:
        source = javadoc_root / module / "build" / "docs" / "javadoc"
        if not (source / "index.html").is_file():
            missing.append(str(source / "index.html"))
            continue
        shutil.copytree(source, destination_root / module)
    if missing:
        formatted = "\n  - ".join(missing)
        raise RuntimeError(f"Javadoc is missing; run ./gradlew siteJavadocs first:\n  - {formatted}")


JAVADOC_REDIRECT_TARGET = re.compile(
    r'<link rel="canonical" href="([^"]+package-summary\.html)">'
)


def rewrite_javadoc_redirects(stage: Path, version: str, base_url: str) -> set[str]:
    """Replace Javadoc's `IndexRedirectWriter` stubs with crawlable landing pages.

    Javadoc emits a per-module `index.html` that carries a `<noscript><meta http-equiv="Refresh">`
    and no heading. Crawlers report both as defects. The replacement keeps the same script-based
    redirect for people, but points crawlers at the package summary through a canonical link.

    Returns the stage-relative package-summary pages the stubs point at, so the sitemap can
    advertise one stable Javadoc entry point per module.
    """
    version_root = stage / "api" / version
    summaries: set[str] = set()
    for module, description in MODULES:
        document = version_root / module / "index.html"
        if not document.is_file():
            raise RuntimeError(f"Javadoc module index is missing: {document}")
        source = document.read_text(encoding="utf-8")
        match = JAVADOC_REDIRECT_TARGET.search(source)
        if not match:
            # A module with several packages gets a real overview page instead of a redirect stub.
            continue
        target = match.group(1)
        escaped_target = html.escape(target, quote=True)
        body = f"""<p><a href="../">DATEV exporter {html.escape(version)} API</a></p>
    <h1><code>{html.escape(module)}</code> {html.escape(version)} API</h1>
    <p>{html.escape(description)}.</p>
    <p>Continue to the <a href="{escaped_target}">package summary</a>.</p>
    <script>window.location.replace({json.dumps(target)});</script>"""
        document.write_text(
            page(
                f"{module} {version} API",
                body,
                urljoin(base_url, f"api/{version}/{module}/{target}"),
                "../../../",
                f"{description} in the DATEV exporter {version} Java API.",
                robots="noindex,follow",
            ),
            encoding="utf-8",
        )
        summaries.add(f"api/{version}/{module}/{target}")
    return summaries


def canonical_html_urls(
    stage: Path, version: str, base_url: str, package_summaries: set[str]
) -> list[str]:
    urls: list[str] = []
    for document in sorted(stage.rglob("*.html")):
        relative = document.relative_to(stage).as_posix()
        # Ownership tokens are not content and must never be advertised to crawlers.
        if VERIFICATION_TOKENS.fullmatch(relative):
            continue
        # Generated Javadoc holds thousands of implementation pages that would drown the sitemap.
        # Advertise only stable entry points: the API and version indexes, plus one package summary
        # per module, which is where class-name searches usefully land.
        if relative.startswith("api/") and relative not in {
            "api/index.html",
            f"api/{version}/index.html",
        } | package_summaries:
            continue
        if relative.endswith("/index.html"):
            relative = relative[: -len("index.html")]
        elif relative == "index.html":
            relative = ""
        encoded = "/".join(quote(segment) for segment in relative.split("/"))
        urls.append(urljoin(base_url, encoded))
    return urls


class DocumentTextExtractor(HTMLParser):
    """Render the `<main>` region of an authored page as Markdown-ish plain text.

    Large-language-model crawlers consume text far more reliably than styled HTML. Nothing here
    tries to be a general converter; it only has to handle the tags this site actually authors.
    """

    BLOCK_TAGS = {"p", "li", "h1", "h2", "h3", "h4", "pre", "td", "th", "tr"}
    PREFIX = {"h1": "# ", "h2": "## ", "h3": "### ", "h4": "#### ", "li": "- "}
    SKIP_TAGS = {"script", "style", "nav"}

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.blocks: list[str] = []
        self._depth = 0
        self._skip_depth = 0
        self._stack: list[str] = []
        self._buffer: list[str] = []

    def _flush(self) -> None:
        text = "".join(self._buffer)
        self._buffer.clear()
        tag = self._stack[-1] if self._stack else ""
        if tag == "pre":
            body = text.strip("\n")
            if body:
                self.blocks.append("```\n" + body + "\n```")
            return
        collapsed = " ".join(text.split())
        if collapsed:
            self.blocks.append(self.PREFIX.get(tag, "") + collapsed)

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "main":
            self._depth += 1
            return
        if self._depth == 0:
            return
        if tag in self.SKIP_TAGS:
            self._skip_depth += 1
            return
        if self._skip_depth == 0 and tag in self.BLOCK_TAGS:
            self._flush()
            self._stack.append(tag)

    def handle_endtag(self, tag: str) -> None:
        if tag == "main":
            self._flush()
            self._depth = max(self._depth - 1, 0)
            return
        if self._depth == 0:
            return
        if tag in self.SKIP_TAGS:
            self._skip_depth = max(self._skip_depth - 1, 0)
            return
        if self._skip_depth == 0 and tag in self.BLOCK_TAGS and self._stack:
            self._flush()
            self._stack.pop()

    def handle_data(self, data: str) -> None:
        if self._depth and not self._skip_depth and self._stack:
            self._buffer.append(data)


def write_llms_full(stage: Path, version: str, base_url: str) -> None:
    """Concatenate the authored documentation into one plain-text file."""
    sections = [
        "# DATEV exporter for Java — full documentation",
        "",
        f"Release {version}. Source: https://github.com/mrtyldr/datev-exporter",
        "",
        "Java 17 library that generates, validates and streams DATEV Buchungsstapel / EXTF v13 "
        "and v12 files. It is a format exporter, not an accounting engine, DATEV API client, "
        "SKR03/SKR04 mapper or import certification service. Generated files are import "
        "candidates; acceptance must be verified in the licensed, configured target environment.",
        "",
        "This project is independent and is not affiliated with, endorsed by or supported by "
        "DATEV. DATEV is a trademark of DATEV eG.",
        "",
    ]
    pages = [
        f"{language}/{name}"
        for language in ("en", "de")
        for name in (
            "index.html",
            "getting-started.html",
            "compatibility.html",
            "reference.html",
            "fields.html",
            "validation-errors.html",
            "extf-header.html",
            "encoding.html",
            "benchmarks.html",
        )
    ]
    for relative in pages:
        document = stage / relative
        if not document.is_file():
            raise RuntimeError(f"Documentation page is missing: {relative}")
        extractor = DocumentTextExtractor()
        extractor.feed(document.read_text(encoding="utf-8"))
        if not extractor.blocks:
            raise RuntimeError(f"No extractable text in {relative}")
        canonical = urljoin(base_url, relative.replace("/index.html", "/"))
        sections.append("---")
        sections.append("")
        sections.append(f"Source: {canonical}")
        sections.append("")
        sections.extend(extractor.blocks)
        sections.append("")
    (stage / "llms-full.txt").write_text("\n".join(sections) + "\n", encoding="utf-8")


def write_discovery_files(
    stage: Path, version: str, base_url: str, indexnow_key: str, package_summaries: set[str]
) -> None:
    urls = canonical_html_urls(stage, version, base_url, package_summaries)
    namespace = "http://www.sitemaps.org/schemas/sitemap/0.9"
    ElementTree.register_namespace("", namespace)
    urlset = ElementTree.Element(f"{{{namespace}}}urlset")
    for url in urls:
        entry = ElementTree.SubElement(urlset, f"{{{namespace}}}url")
        ElementTree.SubElement(entry, f"{{{namespace}}}loc").text = url
    ElementTree.indent(urlset, space="  ")
    ElementTree.ElementTree(urlset).write(
        stage / "sitemap.xml", encoding="utf-8", xml_declaration=True
    )

    (stage / "llms.txt").write_text(
        f"""# DATEV exporter for Java

> Java 17 library for generating, validating and streaming DATEV Buchungsstapel / EXTF CSV files.

- Documentation: {base_url}
- English guide: {urljoin(base_url, 'en/')}
- German guide: {urljoin(base_url, 'de/')}
- Field reference, all 125 columns: {urljoin(base_url, 'en/fields.html')}
- Validation error codes: {urljoin(base_url, 'en/validation-errors.html')}
- EXTF management record: {urljoin(base_url, 'en/extf-header.html')}
- Windows-1252 encoding rules: {urljoin(base_url, 'en/encoding.html')}
- Java API {version}: {urljoin(base_url, f'api/{version}/')}
- Full documentation text: {urljoin(base_url, 'llms-full.txt')}
- Source: https://github.com/mrtyldr/datev-exporter
- Maven Central: https://central.sonatype.com/artifact/io.github.mrtyldr/datev-exporter

The library implements file-format serialization and deterministic technical validation. It is not
an accounting engine, DATEV API client, SKR03/SKR04 mapper or import certification service.
""",
        encoding="utf-8",
    )
    write_llms_full(stage, version, base_url)
    (stage / ".nojekyll").touch()
    (stage / f"{indexnow_key}.txt").write_text(f"{indexnow_key}\n", encoding="utf-8")


def validate_site(stage: Path, version: str, base_url: str, indexnow_key: str) -> None:
    required = (
        "index.html",
        "api/index.html",
        "api/latest/index.html",
        "sitemap.xml",
        "llms.txt",
        "llms-full.txt",
        ".nojekyll",
        "assets/social-preview.png",
        f"{indexnow_key}.txt",
    )
    for relative in required:
        if not (stage / relative).is_file():
            raise RuntimeError(f"Required Pages file is missing: {relative}")

    for path in stage.rglob("*"):
        if path.is_symlink():
            raise RuntimeError(f"Pages artifacts may not contain symbolic links: {path}")

    parsed_base = urlparse(base_url)
    base_path = parsed_base.path.rstrip("/") + "/"
    broken: list[str] = []
    # Crawler-reported defects, checked across the whole artifact including generated Javadoc.
    missing_description: list[str] = []
    missing_heading: list[str] = []
    meta_refresh: list[str] = []
    for document in sorted(stage.rglob("*.html")):
        parser = ResourceParser()
        try:
            parser.feed(document.read_text(encoding="utf-8"))
        except UnicodeDecodeError as error:
            raise RuntimeError(f"HTML is not UTF-8: {document}") from error
        relative = document.relative_to(stage).as_posix()
        if not VERIFICATION_TOKENS.fullmatch(relative):
            if not parser.description:
                missing_description.append(relative)
            if not parser.has_heading:
                missing_heading.append(relative)
            if parser.has_meta_refresh:
                meta_refresh.append(relative)
        document_url = urljoin(base_url, document.relative_to(stage).as_posix())
        for reference in parser.references:
            if not reference or reference.startswith(("#", "mailto:", "tel:", "data:", "javascript:")):
                continue
            resolved = urlparse(urljoin(document_url, reference))
            if (resolved.scheme not in {"", "https"} or resolved.netloc != parsed_base.netloc):
                continue
            if not resolved.path.startswith(base_path):
                continue
            relative_path = unquote(resolved.path[len(base_path) :])
            candidate = stage / relative_path
            if relative_path.endswith("/") or candidate.is_dir():
                candidate = candidate / "index.html"
            if not candidate.is_file():
                broken.append(
                    f"{document.relative_to(stage)} -> {reference} ({candidate.relative_to(stage)})"
                )
    if broken:
        raise RuntimeError("Broken local links:\n  - " + "\n  - ".join(broken))

    defects = (
        ("Pages without a meta description", missing_description),
        ("Pages without an h1 heading", missing_heading),
        ("Pages using a meta refresh redirect", meta_refresh),
    )
    reported = [
        f"{label}:\n  - " + "\n  - ".join(pages) for label, pages in defects if pages
    ]
    if reported:
        raise RuntimeError("\n".join(reported))

    sitemap = ElementTree.parse(stage / "sitemap.xml")
    locations = [element.text for element in sitemap.iter() if element.tag.endswith("}loc")]
    if not locations or len(locations) != len(set(locations)):
        raise RuntimeError("sitemap.xml must contain unique canonical URLs")
    if any(not location or not location.startswith(base_url) for location in locations):
        raise RuntimeError("sitemap.xml contains a URL outside the canonical site base")


def main() -> int:
    args = parse_args()
    if not SEMVER.fullmatch(args.version):
        raise RuntimeError("--version must use the form X.Y.Z")
    if not args.base_url.startswith("https://") or not args.base_url.endswith("/"):
        raise RuntimeError("--base-url must be an HTTPS URL ending in a slash")

    repository = Path.cwd().resolve()
    website = args.website.resolve()
    javadoc_root = args.javadoc_root.resolve()
    output = args.output.resolve()
    indexnow_key_value = indexnow_key()
    if not (website / "index.html").is_file():
        raise RuntimeError(f"Authored website entry point is missing: {website / 'index.html'}")
    output.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix="pages-", dir=output.parent))
    try:
        shutil.copytree(website, stage, dirs_exist_ok=True)
        social_preview = repository / ".github" / "assets" / "social-preview.png"
        if not social_preview.is_file():
            raise RuntimeError(f"Social preview image is missing: {social_preview}")
        (stage / "assets").mkdir(parents=True, exist_ok=True)
        shutil.copy2(social_preview, stage / "assets" / "social-preview.png")
        copy_javadocs(javadoc_root, stage, args.version)
        package_summaries = rewrite_javadoc_redirects(stage, args.version, args.base_url)
        write_api_indexes(stage, args.version, args.base_url)
        write_discovery_files(
            stage, args.version, args.base_url, indexnow_key_value, package_summaries
        )
        validate_site(stage, args.version, args.base_url, indexnow_key_value)
        if output.exists():
            shutil.rmtree(output)
        stage.replace(output)
    finally:
        if stage.exists():
            shutil.rmtree(stage)

    html_count = sum(1 for _ in output.rglob("*.html"))
    print(f"Assembled {html_count} HTML pages at {output}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print(f"error: {error}", file=sys.stderr)
        sys.exit(1)
