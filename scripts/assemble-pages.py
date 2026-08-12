#!/usr/bin/env python3
"""Assemble and validate the static GitHub Pages artifact.

The authored website is copied verbatim. Generated, versioned Javadocs and small discovery files
are then added around it so the deployable directory never has to be committed.
"""

from __future__ import annotations

import argparse
import html
from html.parser import HTMLParser
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

SEMVER = re.compile(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\Z")
INDEXNOW_KEY_PATTERN = re.compile(r"[A-Za-z0-9-]{8,128}\Z")


class ResourceParser(HTMLParser):
    """Collect resource and navigation links without requiring a third-party parser."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.references: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag in {"a", "link"} and attributes.get("href"):
            self.references.append(attributes["href"])
        if tag in {"img", "script", "source"} and attributes.get("src"):
            self.references.append(attributes["src"])


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


def page(title: str, body: str, canonical: str, base_path: str) -> str:
    escaped_title = html.escape(title)
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="index,follow">
  <title>{escaped_title}</title>
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
        ),
        encoding="utf-8",
    )

    api_body = f"""<p><a href="../">DATEV exporter documentation</a></p>
    <h1>Java API documentation</h1>
    <p><a href="{html.escape(version)}/">Version {html.escape(version)}</a> is the current published API.</p>"""
    (api_root / "index.html").write_text(
        page("DATEV exporter Java API", api_body, urljoin(base_url, "api/"), "../"),
        encoding="utf-8",
    )

    latest = api_root / "latest"
    latest.mkdir()
    target = f"../{version}/"
    canonical = urljoin(base_url, f"api/{version}/")
    (latest / "index.html").write_text(
        f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="robots" content="noindex,follow">
  <meta http-equiv="refresh" content="0; url={html.escape(target, quote=True)}">
  <link rel="canonical" href="{html.escape(canonical, quote=True)}">
  <title>Latest DATEV exporter API</title>
</head>
<body><p>Continue to the <a href="{html.escape(target, quote=True)}">current API documentation</a>.</p></body>
</html>
""",
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


def canonical_html_urls(stage: Path, version: str, base_url: str) -> list[str]:
    urls: list[str] = []
    for document in sorted(stage.rglob("*.html")):
        relative = document.relative_to(stage).as_posix()
        # Generated Javadoc module indexes canonically redirect to their package summaries. Keep
        # those redirect pages and thousands of implementation pages out of the sitemap; the API
        # and version indexes provide stable discovery entry points for crawlers and people.
        if relative.startswith("api/") and relative not in {
            "api/index.html",
            f"api/{version}/index.html",
            "api/latest/index.html",
        }:
            continue
        if relative == "api/latest/index.html":
            continue
        if relative.endswith("/index.html"):
            relative = relative[: -len("index.html")]
        elif relative == "index.html":
            relative = ""
        encoded = "/".join(quote(segment) for segment in relative.split("/"))
        urls.append(urljoin(base_url, encoded))
    return urls


def write_discovery_files(stage: Path, version: str, base_url: str, indexnow_key: str) -> None:
    urls = canonical_html_urls(stage, version, base_url)
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
- Java API {version}: {urljoin(base_url, f'api/{version}/')}
- Source: https://github.com/mrtyldr/datev-exporter
- Maven Central: https://central.sonatype.com/artifact/io.github.mrtyldr/datev-exporter

The library implements file-format serialization and deterministic technical validation. It is not
an accounting engine, DATEV API client, SKR03/SKR04 mapper or import certification service.
""",
        encoding="utf-8",
    )
    (stage / ".nojekyll").touch()
    (stage / f"{indexnow_key}.txt").write_text(f"{indexnow_key}\n", encoding="utf-8")


def validate_site(stage: Path, base_url: str, indexnow_key: str) -> None:
    required = (
        "index.html",
        "api/index.html",
        "api/latest/index.html",
        "sitemap.xml",
        "llms.txt",
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
    for document in sorted(stage.rglob("*.html")):
        parser = ResourceParser()
        try:
            parser.feed(document.read_text(encoding="utf-8"))
        except UnicodeDecodeError as error:
            raise RuntimeError(f"HTML is not UTF-8: {document}") from error
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
        write_api_indexes(stage, args.version, args.base_url)
        write_discovery_files(stage, args.version, args.base_url, indexnow_key_value)
        validate_site(stage, args.base_url, indexnow_key_value)
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
