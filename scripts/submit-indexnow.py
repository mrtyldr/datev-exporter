#!/usr/bin/env python3
"""Submit the deployed sitemap URLs to the public IndexNow endpoint."""

from __future__ import annotations

import json
from pathlib import Path
import re
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen
from xml.etree import ElementTree


BASE_URL = "https://mrtyldr.github.io/datev-exporter/"
ENDPOINT = "https://api.indexnow.org/indexnow"
KEY_PATTERN = re.compile(r"[A-Za-z0-9-]{8,128}\Z")


def fetch(url: str) -> bytes:
    with urlopen(url, timeout=20) as response:
        return response.read()


def main() -> int:
    key = Path(__file__).with_name("indexnow-key.txt").read_text(encoding="utf-8").strip()
    if not KEY_PATTERN.fullmatch(key):
        raise RuntimeError("scripts/indexnow-key.txt must contain 8-128 letters, digits or dashes")

    key_location = f"{BASE_URL}{key}.txt"
    for attempt in range(6):
        try:
            if fetch(key_location).decode("utf-8").strip() == key:
                break
        except (HTTPError, URLError, TimeoutError, UnicodeDecodeError):
            pass
        if attempt == 5:
            raise RuntimeError("the deployed IndexNow key file could not be verified")
        time.sleep(10)

    sitemap_url = f"{BASE_URL}sitemap.xml"
    sitemap = ElementTree.fromstring(fetch(sitemap_url))
    urls = [element.text for element in sitemap.iter() if element.tag.endswith("}loc")]
    if not urls or len(urls) > 10_000:
        raise RuntimeError("the deployed sitemap must contain between 1 and 10,000 URLs")
    site = urlparse(BASE_URL)
    if any(
        url is None
        or urlparse(url).scheme != "https"
        or urlparse(url).netloc != site.netloc
        or not urlparse(url).path.startswith(site.path)
        for url in urls
    ):
        raise RuntimeError("the deployed sitemap contains a URL outside the Pages site")

    payload = json.dumps(
        {
            "host": site.netloc,
            "key": key,
            "keyLocation": key_location,
            "urlList": urls,
        }
    ).encode("utf-8")
    request = Request(
        ENDPOINT,
        data=payload,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )
    try:
        with urlopen(request, timeout=30) as response:
            status = response.status
    except HTTPError as error:
        status = error.code
    if status not in {200, 202}:
        raise RuntimeError(f"IndexNow returned HTTP {status}")
    print(f"IndexNow accepted {len(urls)} canonical URLs (HTTP {status}).")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, HTTPError, URLError, TimeoutError) as error:
        print(f"error: {error}", file=sys.stderr)
        sys.exit(1)
