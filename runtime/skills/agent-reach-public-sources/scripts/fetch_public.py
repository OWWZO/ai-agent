#!/usr/bin/env python3
"""Read public RSS, video, community, finance, GitHub, and LinkedIn sources.

The script deliberately uses argument lists instead of a shell. It emits one
bounded JSON document so an agent can consume the result without parsing human
or platform-specific command output.
"""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import html
import ipaddress
import json
from html.parser import HTMLParser
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urljoin, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


USER_AGENT = "agent-reach-public-sources/1.0"
MAX_RESPONSE_BYTES = 8 * 1024 * 1024
MAX_TEXT_CHARS = 120_000
MAX_JSON_OUTPUT_CHARS = 48_000
DEFAULT_LIMIT = 10
ALLOWED_HTTP_SCHEMES = {"http", "https"}
HOSTNAME_RE = re.compile(r"^[A-Za-z0-9.-]+$")
GITHUB_REPO_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
BVID_RE = re.compile(r"^[Bb][Vv][A-Za-z0-9]+$")
XUEQIU_SYMBOL_RE = re.compile(r"^[A-Za-z0-9._-]{1,32}$")
TIMESTAMP_RE = re.compile(r"^\d{1,2}:\d{2}:\d{2}(?:\.\d+)?\s+-->\s+")
HTML_TAG_RE = re.compile(r"<[^>]+>")
SECRET_RE = re.compile(r"(?i)(?:bearer\s+|(?:token|auth_token|ct0|cookie)=?\s*)[A-Za-z0-9._-]{12,}")


class PublicSourceError(RuntimeError):
    """An expected, user-visible source error."""


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def clean_text(value: Any, limit: int = 4_000) -> str:
    text = "" if value is None else str(value)
    text = html.unescape(HTML_TAG_RE.sub(" ", text))
    text = re.sub(r"\s+", " ", text).strip()
    return text[:limit]


def extract_page_content(body: str, limit: int = MAX_TEXT_CHARS) -> str:
    """Keep useful text from an HTML fallback without returning scripts/styles."""
    if "<html" not in body.casefold() and "<script" not in body.casefold():
        return body[:limit]
    structured: list[str] = []
    for pattern in (r'"headline"\s*:\s*"((?:\\.|[^"\\])*)"', r'"text"\s*:\s*"((?:\\.|[^"\\])*)"'):
        for match in re.findall(pattern, body):
            try:
                structured.append(json.loads('"' + match + '"'))
            except json.JSONDecodeError:
                structured.append(match)
    stripped = re.sub(r"(?is)<(script|style|noscript).*?</\1>", " ", body)
    title = re.search(r"(?is)<title[^>]*>(.*?)</title>", stripped)
    description = re.search(
        r'(?is)<meta[^>]+name=["\']description["\'][^>]+content=["\'](.*?)["\']',
        stripped,
    )
    if title:
        structured.insert(0, clean_text(title.group(1)))
    if description:
        structured.insert(1 if title else 0, clean_text(description.group(1)))
    visible = clean_text(HTML_TAG_RE.sub(" ", stripped), limit)
    return clean_text("\n".join([*structured, visible]), limit)


def scrub_error(value: Any) -> str:
    text = clean_text(value, 1_000)
    return SECRET_RE.sub("[redacted]", text)


def make_result(
    source: str,
    operation: str,
    query: str,
    items: Iterable[dict[str, Any]] = (),
    errors: Iterable[str] = (),
) -> dict[str, Any]:
    return {
        "source": source,
        "operation": operation,
        "query": query,
        "retrieved_at": utc_now(),
        "items": list(items),
        "errors": [scrub_error(error) for error in errors if str(error).strip()],
        "warnings": [],
    }


def emit(result: dict[str, Any]) -> None:
    payload = json.dumps(result, ensure_ascii=False, separators=(",", ":"))
    if len(payload) > MAX_JSON_OUTPUT_CHARS:
        result.setdefault("warnings", []).append(
            "result was shortened to stay within the output limit"
        )
        for item in result.get("items", []):
            if not isinstance(item, dict):
                continue
            for field in ("content", "transcript", "summary"):
                value = item.get(field)
                if isinstance(value, str) and len(value) > 3_000:
                    item[field] = value[:3_000] + "...[truncated]"
        while len(
            json.dumps(result, ensure_ascii=False, separators=(",", ":"))
        ) > MAX_JSON_OUTPUT_CHARS and result.get("items"):
            result["items"].pop()
        payload = json.dumps(result, ensure_ascii=False, separators=(",", ":"))
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    print(payload)


def require_limit(value: str) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError) as exc:
        raise argparse.ArgumentTypeError("limit must be an integer") from exc
    if not 1 <= parsed <= 100:
        raise argparse.ArgumentTypeError("limit must be between 1 and 100")
    return parsed


def _host_matches(host: str, allowed: set[str]) -> bool:
    normalized = host.casefold().rstrip(".")
    return any(normalized == item or normalized.endswith("." + item) for item in allowed)


def _reject_private_host(host: str) -> None:
    normalized = host.casefold().rstrip(".")
    if normalized in {
        "localhost",
        "localhost.localdomain",
        "metadata.google.internal",
    } or normalized.endswith((".local", ".internal", ".lan", ".home.arpa")):
        raise PublicSourceError("private or local host is not allowed")
    try:
        address = ipaddress.ip_address(normalized)
    except ValueError:
        return
    if address.is_private or address.is_loopback or address.is_link_local or address.is_reserved:
        raise PublicSourceError("private or reserved address is not allowed")


def validate_public_url(url: str, allowed_hosts: set[str] | None = None) -> str:
    value = str(url or "").strip()
    parsed = urlsplit(value)
    if parsed.scheme.casefold() not in ALLOWED_HTTP_SCHEMES:
        raise PublicSourceError("only http(s) URLs are allowed")
    if not parsed.hostname or not HOSTNAME_RE.fullmatch(parsed.hostname):
        raise PublicSourceError("URL host is invalid")
    if parsed.username is not None or parsed.password is not None:
        raise PublicSourceError("URLs with embedded credentials are not allowed")
    if parsed.port not in {None, 80, 443}:
        raise PublicSourceError("custom URL ports are not allowed")
    _reject_private_host(parsed.hostname)
    if allowed_hosts and not _host_matches(parsed.hostname, allowed_hosts):
        raise PublicSourceError("URL host is outside the requested public platform")
    return value


def _check_dns_public(host: str) -> None:
    """Reject obvious DNS-to-private targets before making a public request."""
    try:
        addresses = {item[4][0] for item in socket.getaddrinfo(host, None)}
    except OSError:
        return
    for address in addresses:
        _reject_private_host(address)


def fetch_bytes(
    url: str,
    *,
    headers: dict[str, str] | None = None,
    allowed_hosts: set[str] | None = None,
    timeout: int = 30,
) -> bytes:
    value = validate_public_url(url, allowed_hosts)
    _check_dns_public(urlsplit(value).hostname or "")
    request_headers = {"User-Agent": USER_AGENT, **(headers or {})}
    request = Request(value, headers=request_headers)
    try:
        opener = build_opener(_SafeRedirectHandler(allowed_hosts))
        with opener.open(request, timeout=timeout) as response:
            body = response.read(MAX_RESPONSE_BYTES + 1)
    except HTTPError as exc:
        try:
            error_body = exc.read(MAX_RESPONSE_BYTES)
            detail = clean_text(error_body.decode("utf-8", errors="replace"), 800)
        except (OSError, UnicodeError):
            detail = ""
        suffix = f": {detail}" if detail else ""
        raise PublicSourceError(f"HTTP {exc.code} from public source{suffix}") from exc
    except (URLError, TimeoutError, OSError) as exc:
        raise PublicSourceError(f"public source request failed: {scrub_error(exc)}") from exc
    if len(body) > MAX_RESPONSE_BYTES:
        raise PublicSourceError("public source response exceeds the 8 MiB safety limit")
    return body


class _SafeRedirectHandler(HTTPRedirectHandler):
    """Validate every redirect instead of trusting urllib's default handler."""

    def __init__(self, allowed_hosts: set[str] | None):
        super().__init__()
        self.allowed_hosts = allowed_hosts

    def redirect_request(self, request, file, code, message, headers, newurl):  # type: ignore[no-untyped-def]
        target = urljoin(request.full_url, newurl)
        validate_public_url(target, self.allowed_hosts)
        _check_dns_public(urlsplit(target).hostname or "")
        return super().redirect_request(request, file, code, message, headers, target)


def fetch_json(
    url: str,
    *,
    headers: dict[str, str] | None = None,
    allowed_hosts: set[str] | None = None,
) -> Any:
    body = fetch_bytes(
        url,
        headers={"Accept": "application/json", **(headers or {})},
        allowed_hosts=allowed_hosts,
    )
    try:
        return json.loads(body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise PublicSourceError("public source returned invalid JSON") from exc


def fetch_text(
    url: str,
    *,
    headers: dict[str, str] | None = None,
    allowed_hosts: set[str] | None = None,
) -> str:
    return fetch_bytes(url, headers=headers, allowed_hosts=allowed_hosts).decode(
        "utf-8", errors="replace"
    )


def fetch_jina(url: str, allowed_hosts: set[str]) -> str:
    target = validate_public_url(url, allowed_hosts)
    jina_url = "https://r.jina.ai/" + target
    return fetch_text(
        jina_url,
        headers={"Accept": "text/plain"},
        allowed_hosts={"r.jina.ai"},
    )


def command_output(command: list[str], timeout: int = 45) -> str:
    executable = shutil.which(command[0])
    if not executable:
        raise PublicSourceError(f"required command is not installed: {command[0]}")
    try:
        completed = subprocess.run(
            [executable, *command[1:]],
            stdin=subprocess.DEVNULL,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
            check=False,
            env=os.environ.copy(),
        )
    except subprocess.TimeoutExpired as exc:
        raise PublicSourceError(f"command timed out: {command[0]}") from exc
    if completed.returncode != 0:
        detail = scrub_error(completed.stderr or completed.stdout or "command failed")
        raise PublicSourceError(f"{command[0]} failed: {detail}")
    return completed.stdout.strip()


def first_value(mapping: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        value = mapping.get(key)
        if value not in (None, ""):
            return value
    return ""


def parse_json_lines(text: str) -> list[Any]:
    values: list[Any] = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            values.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    if values:
        return values
    try:
        return [json.loads(text)] if text else []
    except json.JSONDecodeError:
        return []


def normalize_video(item: dict[str, Any], source: str = "youtube") -> dict[str, Any]:
    video_id = clean_text(first_value(item, "id", "video_id", "bvid"), 200)
    url = first_value(item, "webpage_url", "webpageUrl", "url", "arcurl")
    if not url and video_id:
        url = (
            f"https://www.youtube.com/watch?v={video_id}"
            if source == "youtube"
            else f"https://www.bilibili.com/video/{video_id}"
        )
    owner = item.get("owner")
    author = first_value(item, "channel", "uploader", "author")
    if not author and isinstance(owner, dict):
        author = first_value(owner, "name", "mid")
    return {
        "title": clean_text(first_value(item, "title", "name")),
        "author": clean_text(author),
        "published_at": clean_text(first_value(item, "upload_date", "pubdate", "published_at")),
        "url": clean_text(url, 2_000),
        "summary": clean_text(first_value(item, "description", "desc", "summary")),
        "id": video_id,
        "duration": first_value(item, "duration", "length", "duration_string"),
    }


def rss_read(url: str, limit: int) -> dict[str, Any]:
    body = fetch_bytes(
        url,
        headers={"Accept": "application/rss+xml, application/atom+xml, application/xml, text/xml"},
    )
    try:
        root = ET.fromstring(body)
    except ET.ParseError as exc:
        raise PublicSourceError("RSS/Atom response is not valid XML") from exc

    entries = [element for element in root.iter() if _xml_name(element.tag) in {"item", "entry"}]
    items = []
    for entry in entries[:limit]:
        link = _xml_value(entry, "link")
        if not link:
            link_node = next((node for node in entry if _xml_name(node.tag) == "link"), None)
            link = link_node.attrib.get("href", "") if link_node is not None else ""
        identity = _xml_value(entry, "guid", "id") or link
        items.append(
            {
                "title": clean_text(_xml_value(entry, "title")),
                "author": clean_text(_xml_value(entry, "author", "creator")),
                "published_at": clean_text(
                    _xml_value(entry, "pubDate", "published", "updated", "date")
                ),
                "url": clean_text(link, 2_000),
                "summary": clean_text(
                    _xml_value(entry, "description", "summary", "content", "encoded")
                ),
                "id": clean_text(identity, 2_000),
            }
        )
    return make_result("rss", "read", url, items)


XUEQIU_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Referer": "https://xueqiu.com/",
}


def _xueqiu_json(path: str, params: dict[str, Any]) -> dict[str, Any]:
    """Call a public Xueqiu endpoint without cookies or browser state."""
    url = "https://" + path.lstrip("/")
    data = fetch_json(url + "?" + urlencode(params), headers=XUEQIU_HEADERS)
    if not isinstance(data, dict):
        raise PublicSourceError("Xueqiu public API returned an unexpected response")
    error_code = data.get("error_code")
    if error_code not in (None, 0):
        message = data.get("error_description") or data.get("error_uri") or "request rejected"
        raise PublicSourceError(f"Xueqiu public API rejected the request: {message}")
    return data


def _validate_xueqiu_symbol(symbol: str) -> str:
    normalized = str(symbol or "").strip()
    if not XUEQIU_SYMBOL_RE.fullmatch(normalized):
        raise PublicSourceError(
            "Xueqiu symbol must contain only letters, numbers, dot, underscore, or hyphen"
        )
    return normalized


def xueqiu_quote(symbol: str) -> dict[str, Any]:
    normalized = _validate_xueqiu_symbol(symbol)
    data = _xueqiu_json(
        "stock.xueqiu.com/v5/stock/quote.json",
        {"symbol": normalized, "extend": "detail"},
    )
    quote = (data.get("data") or {}).get("quote") or {}
    if not isinstance(quote, dict) or not quote:
        raise PublicSourceError("Xueqiu public quote returned no data")
    item = {
        "symbol": quote.get("symbol", normalized),
        "name": clean_text(quote.get("name", "")),
        "current": quote.get("current"),
        "percent": quote.get("percent"),
        "chg": quote.get("chg"),
        "high": quote.get("high"),
        "low": quote.get("low"),
        "open": quote.get("open"),
        "last_close": quote.get("last_close"),
        "volume": quote.get("volume"),
        "amount": quote.get("amount"),
        "market_capital": quote.get("market_capital"),
        "turnover_rate": quote.get("turnover_rate"),
        "pe_ttm": quote.get("pe_ttm"),
        "pe_forecast": quote.get("pe_forecast"),
        "pb": quote.get("pb"),
        "eps": quote.get("eps"),
        "timestamp": quote.get("timestamp"),
    }
    return make_result("xueqiu", "quote", normalized, [item])


def xueqiu_search(query: str, limit: int) -> dict[str, Any]:
    normalized = str(query or "").strip()
    if not normalized:
        raise PublicSourceError("Xueqiu search query is required")
    data = _xueqiu_json(
        "xueqiu.com/stock/search.json",
        {"code": normalized, "size": limit},
    )
    stocks = data.get("stocks") or []
    items = [
        {
            "symbol": stock.get("code", ""),
            "name": clean_text(stock.get("name", "")),
            "exchange": clean_text(stock.get("exchange", "")),
        }
        for stock in stocks[:limit]
        if isinstance(stock, dict)
    ]
    if not items:
        raise PublicSourceError("Xueqiu public search returned no stocks")
    return make_result("xueqiu", "search", normalized, items)


def xueqiu_hot_posts(limit: int) -> dict[str, Any]:
    bounded_limit = min(limit, 50)
    data = _xueqiu_json(
        "xueqiu.com/v4/statuses/public_timeline_by_category.json",
        {"since_id": -1, "max_id": -1, "count": bounded_limit, "category": -1},
    )
    results = []
    for item in (data.get("list") or [])[:bounded_limit]:
        if not isinstance(item, dict):
            continue
        raw_post = item.get("data")
        try:
            post = json.loads(raw_post) if isinstance(raw_post, str) else raw_post
        except json.JSONDecodeError:
            post = {}
        if not isinstance(post, dict):
            continue
        user = post.get("user") or {}
        target = clean_text(post.get("target", ""), 2_000)
        if target and not target.startswith("http"):
            target = "https://xueqiu.com" + (target if target.startswith("/") else "/" + target)
        results.append(
            {
                "id": post.get("id", 0),
                "title": clean_text(post.get("title", "")),
                "text": clean_text(post.get("text") or post.get("description") or "", 1_000),
                "author": clean_text(user.get("screen_name", "")) if isinstance(user, dict) else "",
                "likes": post.get("like_count", 0),
                "url": target,
            }
        )
    if not results:
        raise PublicSourceError("Xueqiu public timeline returned no posts")
    return make_result("xueqiu", "hot-posts", "hot", results)


def xueqiu_hot_stocks(limit: int, stock_type: int = 10) -> dict[str, Any]:
    if stock_type not in {10, 12}:
        raise PublicSourceError("Xueqiu hot stock type must be 10 or 12")
    bounded_limit = min(limit, 50)
    data = _xueqiu_json(
        "stock.xueqiu.com/v5/stock/hot_stock/list.json",
        {"size": bounded_limit, "type": stock_type},
    )
    raw_items = (data.get("data") or {}).get("items") or []
    items = [
        {
            "symbol": item.get("code") or item.get("symbol", ""),
            "name": clean_text(item.get("name", "")),
            "current": item.get("current"),
            "percent": item.get("percent"),
            "rank": index,
        }
        for index, item in enumerate(raw_items[:bounded_limit], 1)
        if isinstance(item, dict)
    ]
    if not items:
        raise PublicSourceError("Xueqiu public hot-stock API returned no stocks")
    return make_result("xueqiu", "hot-stocks", str(stock_type), items)


def _xml_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1].casefold()


def _xml_value(element: ET.Element, *names: str) -> str:
    wanted = {name.casefold() for name in names}
    for child in element:
        if _xml_name(child.tag) in wanted and child.text:
            return child.text
    return ""


def youtube_search(query: str, limit: int) -> dict[str, Any]:
    output = command_output(
        [
            "yt-dlp",
            "--flat-playlist",
            "--dump-json",
            "--no-warnings",
            "--ignore-no-formats",
            f"ytsearch{limit}:{query}",
        ]
    )
    values = parse_json_lines(output)
    items = [normalize_video(value) for value in values if isinstance(value, dict)]
    if not items:
        raise PublicSourceError("YouTube search returned no structured results")
    return make_result("youtube", "search", query, items[:limit])


def youtube_detail(url: str) -> dict[str, Any]:
    validate_public_url(url, {"youtube.com", "youtu.be"})
    output = command_output(
        [
            "yt-dlp",
            "--no-playlist",
            "--dump-single-json",
            "--skip-download",
            "--ignore-no-formats",
            url,
        ]
    )
    values = parse_json_lines(output)
    if not values or not isinstance(values[0], dict):
        raise PublicSourceError("YouTube detail returned no structured result")
    return make_result("youtube", "detail", url, [normalize_video(values[0])])


def youtube_transcript(url: str, languages: str) -> dict[str, Any]:
    validate_public_url(url, {"youtube.com", "youtu.be"})
    primary_error: PublicSourceError | None = None
    try:
        with tempfile.TemporaryDirectory(prefix="agent-reach-youtube-") as directory:
            output_template = str(Path(directory) / "%(id)s.%(ext)s")
            command_output(
                [
                    "yt-dlp",
                    "--no-playlist",
                    "--write-subs",
                    "--write-auto-subs",
                    "--sub-langs",
                    languages,
                    "--sub-format",
                    "vtt",
                    "--skip-download",
                    "--ignore-no-formats",
                    "-o",
                    output_template,
                    url,
                ],
                timeout=90,
            )
            transcripts = []
            for path in sorted(Path(directory).glob("*.vtt")):
                text = clean_vtt(path.read_text(encoding="utf-8", errors="replace"))
                if text:
                    transcripts.append(text)
            if not transcripts:
                raise PublicSourceError("YouTube returned no non-empty subtitle file")
        transcript = "\n".join(transcripts)[:MAX_TEXT_CHARS]
        return make_result(
            "youtube",
            "transcript",
            url,
            [{"url": url, "summary": transcript, "transcript": transcript}],
        )
    except PublicSourceError as exc:
        primary_error = exc

    if shutil.which("opencli"):
        try:
            output = command_output(
                ["opencli", "youtube", "transcript", url, "-f", "yaml"],
                timeout=90,
            )
            if output:
                return make_result(
                    "youtube",
                    "transcript",
                    url,
                    [{"url": url, "summary": clean_text(output, MAX_TEXT_CHARS)}],
                )
        except PublicSourceError as fallback_error:
            raise PublicSourceError(
                f"{primary_error}; OpenCLI fallback failed: {fallback_error}"
            ) from fallback_error
    raise primary_error or PublicSourceError("YouTube transcript failed")


def _normalize_youtube_comment(comment: dict[str, Any], url: str) -> dict[str, Any]:
    comment_id = clean_text(first_value(comment, "id", "comment_id"), 300)
    parent_id = clean_text(first_value(comment, "parent", "parent_id", "root_id"), 300)
    uploader_value = first_value(comment, "author_is_uploader", "is_uploader")
    if isinstance(uploader_value, str):
        is_uploader = uploader_value.casefold() in {"1", "true", "yes"}
    else:
        is_uploader = bool(uploader_value)
    return {
        "id": comment_id,
        "root_id": parent_id or comment_id,
        "author": clean_text(first_value(comment, "author", "author_name", "uploader")),
        "author_id": clean_text(first_value(comment, "author_id", "channel_id"), 300),
        "content": clean_text(first_value(comment, "text", "content"), 6_000),
        "like": first_value(comment, "like_count", "likes"),
        "published_at": clean_text(first_value(comment, "published_time", "timestamp")),
        "is_uploader": is_uploader,
        "url": f"{url}#comment-{comment_id}" if comment_id else url,
    }


def youtube_comments(url: str, limit: int) -> dict[str, Any]:
    """Read public YouTube comments without selecting or downloading a format."""
    validate_public_url(url, {"youtube.com", "youtu.be"})
    output = command_output(
        [
            "yt-dlp",
            "--no-playlist",
            "--skip-download",
            "--ignore-no-formats",
            "--dump-single-json",
            "--write-comments",
            "--extractor-args",
            f"youtube:comment_sort=top;max_comments={limit}",
            url,
        ],
        timeout=120,
    )
    values = parse_json_lines(output)
    if not values or not isinstance(values[0], dict):
        raise PublicSourceError("YouTube comments returned no structured metadata")
    payload = values[0]
    raw_comments = payload.get("comments") or []
    comments = [
        _normalize_youtube_comment(comment, url)
        for comment in raw_comments[:limit]
        if isinstance(comment, dict)
    ]
    if not comments:
        raise PublicSourceError(
            "YouTube returned no public comments; comments may be disabled or the request was blocked"
        )
    video_url = clean_text(first_value(payload, "webpage_url", "webpageUrl"), 2_000) or url
    return make_result(
        "youtube",
        "comments",
        url,
        [
            {
                "title": clean_text(first_value(payload, "title", "name")),
                "url": video_url,
                "id": clean_text(first_value(payload, "id", "video_id"), 300),
                "total": first_value(payload, "comment_count") or len(comments),
                "items": comments,
            }
        ],
    )


def clean_vtt(value: str) -> str:
    lines: list[str] = []
    previous = ""
    for raw_line in value.splitlines():
        line = raw_line.strip()
        if not line or line.casefold() == "webvtt" or line.startswith("NOTE"):
            continue
        if TIMESTAMP_RE.match(line) or re.fullmatch(r"\d+", line):
            continue
        line = clean_text(line, 2_000)
        if line and line != previous:
            lines.append(line)
            previous = line
    return "\n".join(lines)


def bilibili_search(query: str, limit: int) -> dict[str, Any]:
    try:
        output = command_output(["bili", "search", query, "--type", "video", "-n", str(limit)])
        values = parse_json_lines(output)
        if values:
            items = _bili_items(values)[:limit]
            if items:
                return make_result("bilibili", "search", query, items)
        if output:
            return make_result(
                "bilibili",
                "search",
                query,
                [{"title": "Bilibili search output", "summary": clean_text(output, 12_000)}],
            )
    except PublicSourceError:
        pass
    data = fetch_json(
        "https://api.bilibili.com/x/web-interface/search/all/v2?"
        + urlencode({"keyword": query, "page": 1})
    )
    items = _bili_api_search_items(data)[:limit]
    if not items:
        raise PublicSourceError("Bilibili search returned no results")
    return make_result("bilibili", "search", query, items)


def bilibili_detail(bvid: str) -> dict[str, Any]:
    normalized = bvid.strip()
    if not BVID_RE.fullmatch(normalized):
        raise PublicSourceError("Bilibili detail requires a valid BV id")
    try:
        output = command_output(["bili", "video", normalized])
        values = parse_json_lines(output)
        if values:
            items = _bili_items(values)
            if items:
                return make_result("bilibili", "detail", normalized, items[:1])
        if output:
            return make_result(
                "bilibili",
                "detail",
                normalized,
                [
                    {
                        "id": normalized,
                        "url": f"https://www.bilibili.com/video/{normalized}",
                        "summary": clean_text(output, 12_000),
                    }
                ],
            )
    except PublicSourceError:
        pass
    data = fetch_json(
        "https://api.bilibili.com/x/web-interface/view?" + urlencode({"bvid": normalized})
    )
    if data.get("code") not in (None, 0):
        raise PublicSourceError("Bilibili detail API rejected the BV id")
    payload = data.get("data") or {}
    if not isinstance(payload, dict):
        raise PublicSourceError("Bilibili detail returned no result")
    return make_result("bilibili", "detail", normalized, [normalize_video(payload, "bilibili")])


def _bilibili_api_headers(bvid: str = "") -> dict[str, str]:
    headers = {
        "User-Agent": "Mozilla/5.0",
        "Referer": f"https://www.bilibili.com/video/{bvid}"
        if bvid
        else "https://www.bilibili.com/",
    }
    return headers


def _bilibili_aid(bvid: str) -> tuple[int, dict[str, Any]]:
    normalized = bvid.strip()
    if not BVID_RE.fullmatch(normalized):
        raise PublicSourceError("Bilibili comments require a valid BV id")
    data = fetch_json(
        "https://api.bilibili.com/x/web-interface/view?" + urlencode({"bvid": normalized}),
        headers=_bilibili_api_headers(normalized),
    )
    if data.get("code") != 0 or not isinstance(data.get("data"), dict):
        raise PublicSourceError("Bilibili view API rejected the BV id")
    payload = data["data"]
    try:
        return int(payload["aid"]), payload
    except (KeyError, TypeError, ValueError) as exc:
        raise PublicSourceError("Bilibili detail did not return an aid") from exc


def _normalize_bilibili_reply(
    reply: dict[str, Any], bvid: str, root: int | None = None
) -> dict[str, Any]:
    member = reply.get("member") or {}
    content = reply.get("content") or {}
    return {
        "id": reply.get("rpid", ""),
        "root_id": root if root is not None else reply.get("rpid", ""),
        "author": clean_text(member.get("uname", "")),
        "mid": member.get("mid", ""),
        "content": clean_text(content.get("message", ""), 6_000),
        "like": reply.get("like", 0),
        "published_at": reply.get("ctime", ""),
        "url": f"https://www.bilibili.com/video/{bvid}/?comment={reply.get('rpid', '')}",
    }


def bilibili_comments(bvid: str, limit: int, next_cursor: int = 0) -> dict[str, Any]:
    aid, payload = _bilibili_aid(bvid)
    data = fetch_json(
        "https://api.bilibili.com/x/v2/reply/main?"
        + urlencode(
            {
                "next": next_cursor,
                "type": 1,
                "oid": aid,
                "mode": 3,
                "plat": 1,
            }
        ),
        headers=_bilibili_api_headers(bvid),
    )
    if data.get("code") != 0:
        raise PublicSourceError(
            f"Bilibili comments API rejected the request: {data.get('message', 'unknown error')}"
        )
    body = data.get("data") or {}
    replies = body.get("replies") or []
    items = [
        _normalize_bilibili_reply(reply, bvid)
        for reply in replies[:limit]
        if isinstance(reply, dict)
    ]
    cursor = body.get("cursor") or {}
    return make_result(
        "bilibili",
        "comments",
        bvid,
        [
            {
                "title": clean_text(payload.get("title", "")),
                "url": f"https://www.bilibili.com/video/{bvid}",
                "id": bvid,
                "aid": aid,
                "total": cursor.get("all_count", ""),
                "next": cursor.get("next", ""),
                "items": items,
            }
        ],
    )


def bilibili_replies(bvid: str, rpid: int, limit: int, page: int = 1) -> dict[str, Any]:
    aid, payload = _bilibili_aid(bvid)
    data = fetch_json(
        "https://api.bilibili.com/x/v2/reply/reply?"
        + urlencode(
            {
                "oid": aid,
                "type": 1,
                "root": rpid,
                "ps": limit,
                "pn": page,
            }
        ),
        headers=_bilibili_api_headers(bvid),
    )
    if data.get("code") != 0:
        raise PublicSourceError(
            f"Bilibili replies API rejected the request: {data.get('message', 'unknown error')}"
        )
    body = data.get("data") or {}
    replies = body.get("replies") or []
    items = [
        _normalize_bilibili_reply(reply, bvid, rpid) for reply in replies if isinstance(reply, dict)
    ]
    return make_result(
        "bilibili",
        "replies",
        f"{bvid}:{rpid}",
        [
            {
                "title": clean_text(payload.get("title", "")),
                "url": f"https://www.bilibili.com/video/{bvid}",
                "id": bvid,
                "aid": aid,
                "root_id": rpid,
                "page": page,
                "total": body.get("page", {}).get("count", "")
                if isinstance(body.get("page"), dict)
                else "",
                "items": items,
            }
        ],
    )


def bilibili_simple(operation: str, limit: int) -> dict[str, Any]:
    command = ["bili", operation, "-n", str(limit)]
    output = command_output(command)
    values = parse_json_lines(output)
    items = _bili_items(values)[:limit] if values else []
    if not items:
        items = [{"title": f"Bilibili {operation} output", "summary": clean_text(output, 12_000)}]
    return make_result("bilibili", operation, operation, items)


def bilibili_subtitle(bvid: str) -> dict[str, Any]:
    normalized = bvid.strip()
    if not BVID_RE.fullmatch(normalized):
        raise PublicSourceError("Bilibili subtitle requires a valid BV id")
    output = command_output(
        ["opencli", "bilibili", "subtitle", normalized, "-f", "yaml"], timeout=90
    )
    if not output:
        raise PublicSourceError("Bilibili subtitle output is empty")
    return make_result(
        "bilibili",
        "transcript",
        normalized,
        [
            {
                "url": f"https://www.bilibili.com/video/{normalized}",
                "summary": clean_text(output, MAX_TEXT_CHARS),
            }
        ],
    )


def _bili_items(values: list[Any]) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    for value in values:
        if isinstance(value, list):
            items.extend(_bili_items(value))
        elif isinstance(value, dict):
            nested = value.get("data")
            if isinstance(nested, list):
                items.extend(_bili_items(nested))
            else:
                items.append(normalize_video(value, "bilibili"))
    return items


def _bili_api_search_items(data: Any) -> list[dict[str, Any]]:
    result = ((data or {}).get("data") or {}).get("result") if isinstance(data, dict) else None
    if not isinstance(result, list):
        return []
    raw_items = []
    for group in result:
        if not isinstance(group, dict):
            continue
        group_items = group.get("data")
        if isinstance(group_items, list):
            raw_items.extend(group_items)
        else:
            raw_items.append(group)
    items = []
    for item in raw_items:
        if not isinstance(item, dict):
            continue
        normalized = normalize_video(
            {
                "bvid": item.get("bvid"),
                "title": item.get("title"),
                "author": item.get("author"),
                "description": item.get("description"),
                "arcurl": item.get("arcurl"),
                "duration": item.get("duration"),
                "pubdate": item.get("pubdate"),
            },
            "bilibili",
        )
        if normalized["title"] or normalized["id"]:
            items.append(normalized)
    return items


def v2ex_api(
    path: str, params: dict[str, Any], operation: str, query: str, limit: int | None = None
) -> dict[str, Any]:
    url = "https://www.v2ex.com" + path + "?" + urlencode(params)
    data = fetch_json(url, headers={"User-Agent": "agent-reach-public-sources/1.0"})
    values = data if isinstance(data, list) else [data]
    if limit is not None:
        values = values[:limit]
    return make_result(
        "v2ex",
        operation,
        query,
        [_normalize_v2ex(item) for item in values if isinstance(item, dict)],
    )


def v2ex_search(query: str, limit: int) -> dict[str, Any]:
    page_url = "https://www.v2ex.com/?" + urlencode({"q": query})
    body = fetch_jina(page_url, {"v2ex.com"})
    items = []
    for title, url in re.findall(r"\[([^\]]+)\]\((https?://(?:www\.)?v2ex\.com/t/\d+)\)", body):
        items.append({"title": clean_text(title), "url": url, "summary": ""})
        if len(items) >= limit:
            break
    if not items and body.strip():
        items.append(
            {"title": "V2EX search page", "url": page_url, "summary": clean_text(body, 12_000)}
        )
    if not items:
        raise PublicSourceError("V2EX search page returned no content")
    return make_result("v2ex", "search", query, items)


def _normalize_v2ex(item: dict[str, Any]) -> dict[str, Any]:
    node = item.get("node") or {}
    member = item.get("member") or {}
    return {
        "title": clean_text(first_value(item, "title", "username")),
        "author": clean_text(first_value(member, "username", "author"))
        if isinstance(member, dict)
        else "",
        "published_at": first_value(item, "created", "last_modified"),
        "url": clean_text(first_value(item, "url"), 2_000),
        "summary": clean_text(first_value(item, "content", "bio", "website")),
        "id": first_value(item, "id"),
        "node": clean_text(first_value(node, "name", "title")) if isinstance(node, dict) else "",
        "replies": first_value(item, "replies"),
    }


def _normalize_github_repo(item: dict[str, Any]) -> dict[str, Any]:
    owner = item.get("owner") or {}
    owner_name = owner.get("login") if isinstance(owner, dict) else owner
    full_name = first_value(item, "full_name", "nameWithOwner")
    if not full_name:
        name = first_value(item, "name")
        full_name = f"{owner_name}/{name}" if owner_name and name else name
    return {
        "title": clean_text(full_name or first_value(item, "name")),
        "author": clean_text(owner_name),
        "published_at": clean_text(
            first_value(item, "updated_at", "updatedAt", "pushed_at", "pushedAt")
        ),
        "url": clean_text(first_value(item, "html_url", "url"), 2_000),
        "summary": clean_text(first_value(item, "description")),
        "stars": first_value(item, "stargazers_count", "stargazersCount"),
        "language": clean_text(first_value(item, "language")),
    }


def github_repo(repo: str) -> dict[str, Any]:
    normalized = validate_repo(repo)
    try:
        data = github_json(f"/repos/{normalized}")
        return make_result("github", "detail", normalized, [_normalize_github_repo(data)])
    except PublicSourceError as api_error:
        return github_page_fallback(normalized, "detail", api_error)


def github_readme(repo: str) -> dict[str, Any]:
    normalized = validate_repo(repo)
    url = f"https://api.github.com/repos/{normalized}/readme"
    try:
        body = fetch_bytes(url, headers={"Accept": "application/vnd.github.raw+json"})
        text = body.decode("utf-8", errors="replace")
        if text.lstrip().startswith("{"):
            payload = json.loads(text)
            encoded = payload.get("content", "")
            text = base64.b64decode(encoded).decode("utf-8", errors="replace")
    except (PublicSourceError, ValueError, KeyError) as api_error:
        for branch in ("main", "master"):
            try:
                text = fetch_text(
                    f"https://raw.githubusercontent.com/{normalized}/{branch}/README.md",
                    allowed_hosts={"raw.githubusercontent.com"},
                )
                break
            except PublicSourceError:
                text = ""
        if not text:
            return github_page_fallback(normalized, "read", api_error)
    return make_result(
        "github",
        "read",
        normalized,
        [
            {
                "title": f"README: {normalized}",
                "url": f"https://github.com/{normalized}",
                "summary": text[:MAX_TEXT_CHARS],
                "content": text[:MAX_TEXT_CHARS],
            }
        ],
    )


def github_search_repos(query: str, limit: int) -> dict[str, Any]:
    try:
        data = github_json(
            "/search/repositories?"
            + urlencode(
                {
                    "q": query + " visibility:public",
                    "sort": "stars",
                    "order": "desc",
                    "per_page": limit,
                }
            )
        )
        items = [
            _normalize_github_repo(item)
            for item in (data.get("items") or [])
            if isinstance(item, dict)
        ]
        return make_result("github", "search", query, items[:limit])
    except PublicSourceError as api_error:
        return github_search_page_fallback(query, "repositories", limit, api_error)


def github_search_code(query: str, limit: int, language: str | None) -> dict[str, Any]:
    params = {"q": query, "per_page": limit}
    if language:
        params["q"] += f" language:{language}"
    try:
        data = github_json("/search/code?" + urlencode(params))
        items = []
        for item in (data.get("items") or [])[:limit]:
            if not isinstance(item, dict):
                continue
            repository = item.get("repository") or {}
            items.append(
                {
                    "title": clean_text(item.get("name")),
                    "author": clean_text(repository.get("full_name")),
                    "published_at": "",
                    "url": clean_text(item.get("html_url"), 2_000),
                    "summary": clean_text(item.get("path")),
                }
            )
        return make_result("github", "search", query, items)
    except PublicSourceError as api_error:
        return github_search_page_fallback(query, "code", limit, api_error)


def github_page_fallback(
    repo: str,
    operation: str,
    api_error: PublicSourceError,
) -> dict[str, Any]:
    url = f"https://github.com/{repo}"
    try:
        body = fetch_jina(url, {"github.com"})
    except PublicSourceError:
        body = fetch_text(url, headers={"Accept": "text/html"}, allowed_hosts={"github.com"})
    if not body.strip():
        raise PublicSourceError(f"GitHub API failed and public page was empty: {api_error}")
    result = make_result(
        "github",
        operation,
        repo,
        [
            {
                "title": repo,
                "url": url,
                "summary": extract_page_content(body),
                "content": extract_page_content(body),
            }
        ],
    )
    result["warnings"] = [f"GitHub API fallback used: {api_error}"]
    return result


def github_search_page_fallback(
    query: str,
    search_type: str,
    limit: int,
    api_error: PublicSourceError,
) -> dict[str, Any]:
    url = "https://github.com/search?" + urlencode({"q": query, "type": search_type})
    body = fetch_jina(url, {"github.com"})
    repository_paths = re.findall(r"https?://github\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)", body)
    items = []
    seen: set[str] = set()
    for path in repository_paths:
        if path in seen or path.split("/", 1)[1] in {"issues", "pulls", "actions"}:
            continue
        seen.add(path)
        items.append({"title": path, "url": f"https://github.com/{path}", "summary": ""})
        if len(items) >= limit:
            break
    if not items and body.strip():
        items.append(
            {"title": f"GitHub {search_type} search", "url": url, "summary": body[:12_000]}
        )
    if not items:
        raise PublicSourceError(f"GitHub API and public search page failed: {api_error}")
    result = make_result(
        "github",
        "search",
        query,
        items,
    )
    result["warnings"] = [f"GitHub API fallback used: {api_error}"]
    return result


def validate_repo(repo: str) -> str:
    normalized = str(repo or "").strip().strip("/")
    if not GITHUB_REPO_RE.fullmatch(normalized):
        raise PublicSourceError("GitHub repo must use the OWNER/REPO form")
    return normalized


def github_json(path: str) -> dict[str, Any]:
    url = "https://api.github.com" + path
    data = fetch_json(
        url, headers={"Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
    )
    if not isinstance(data, dict):
        raise PublicSourceError("GitHub API returned an unexpected response")
    if data.get("message") and data.get("documentation_url"):
        raise PublicSourceError(f"GitHub API: {data['message']}")
    return data


def linkedin_read(url: str) -> dict[str, Any]:
    target = validate_public_url(url, {"linkedin.com"})
    try:
        body = fetch_jina(target, {"linkedin.com"})
    except PublicSourceError:
        body = fetch_text(target, headers={"Accept": "text/html"}, allowed_hosts={"linkedin.com"})
    if not body.strip():
        raise PublicSourceError("LinkedIn public page returned empty content")
    title_match = re.search(r"(?m)^#\s+(.+)$", body)
    title = clean_text(title_match.group(1) if title_match else "LinkedIn public page")
    return make_result(
        "linkedin",
        "read",
        target,
        [
            {
                "title": title,
                "url": target,
                "summary": extract_page_content(body),
                "content": extract_page_content(body),
            }
        ],
    )


def _hn_item(item: dict[str, Any]) -> dict[str, Any]:
    item_id = item.get("id", "")
    return {
        "title": clean_text(item.get("title", "")), "author": clean_text(item.get("by", "")),
        "published_at": dt.datetime.fromtimestamp(item.get("time", 0), dt.timezone.utc).isoformat().replace("+00:00", "Z") if item.get("time") else "",
        "url": clean_text(item.get("url") or (f"https://news.ycombinator.com/item?id={item_id}" if item_id else ""), 2000),
        "summary": clean_text(item.get("text", "")), "id": item_id,
        "score": item.get("score"), "kids": item.get("kids", []), "type": item.get("type", ""),
        "dead": item.get("dead", False), "deleted": item.get("deleted", False),
    }


def hackernews(op: str, limit: int = 10, item_id: int | None = None, name: str = "") -> dict[str, Any]:
    base = "https://hacker-news.firebaseio.com/v0"
    if op == "item":
        data = fetch_json(f"{base}/item/{item_id}.json", allowed_hosts={"hacker-news.firebaseio.com"})
        return make_result("hackernews", op, str(item_id), [_hn_item(data)] if isinstance(data, dict) else [])
    if op == "user":
        data = fetch_json(f"{base}/user/{quote(name)}.json", allowed_hosts={"hacker-news.firebaseio.com"})
        if not isinstance(data, dict): raise PublicSourceError("Hacker News user not found")
        return make_result("hackernews", op, name, [{"id": name, "author": name, "summary": clean_text(data.get("about", "")), "created": data.get("created"), "karma": data.get("karma"), "submitted": data.get("submitted", [])[:limit]}])
    ids = fetch_json(f"{base}/{op}stories.json", allowed_hosts={"hacker-news.firebaseio.com"})
    if not isinstance(ids, list): raise PublicSourceError("Hacker News returned no story IDs")
    items = []
    for story_id in ids[:limit]:
        data = fetch_json(f"{base}/item/{story_id}.json", allowed_hosts={"hacker-news.firebaseio.com"})
        if isinstance(data, dict): items.append(_hn_item(data))
    return make_result("hackernews", op, op, items)


def _se_json(path: str, params: dict[str, Any]) -> dict[str, Any]:
    data = fetch_json("https://api.stackexchange.com/2.3/" + path + "?" + urlencode(params), allowed_hosts={"api.stackexchange.com"})
    if not isinstance(data, dict): raise PublicSourceError("Stack Exchange API returned an unexpected response")
    if data.get("error_message"): raise PublicSourceError(f"Stack Exchange API: {data['error_message']}")
    return data


def _se_item(item: dict[str, Any]) -> dict[str, Any]:
    return {"title": clean_text(item.get("title", "")), "author": clean_text((item.get("owner") or {}).get("display_name", "")), "published_at": dt.datetime.fromtimestamp(item["creation_date"], dt.timezone.utc).isoformat().replace("+00:00", "Z") if item.get("creation_date") else "", "url": clean_text(item.get("link", ""), 2000), "summary": clean_text(item.get("body")), "id": item.get("question_id", item.get("answer_id", item.get("user_id", ""))), "score": item.get("score"), "accepted": item.get("is_accepted"), "tags": item.get("tags", []), "link": item.get("link", "")}


def stackexchange(op: str, site: str, limit: int = 10, query: str = "", tag: str = "", item_id: int | None = None) -> dict[str, Any]:
    params = {"site": site, "pagesize": limit, "filter": "default"}
    if op == "questions":
        path = "search/advanced" if query or tag else "questions"
        if query: params["q"] = query
        if tag: params["tagged"] = tag
    elif op == "question": path = f"questions/{item_id}"
    elif op == "answers": path = f"questions/{item_id}/answers"
    else: path = f"users/{item_id}"
    data = _se_json(path, params)
    items = [_se_item(x) for x in data.get("items", [])[:limit] if isinstance(x, dict)]
    result = make_result("stackexchange", op, f"{site}:{item_id or query or tag}", items)
    if data.get("backoff") is not None: result["warnings"].append(f"Stack Exchange requested backoff: {data['backoff']} seconds")
    if "quota_remaining" in data: result["warnings"].append(f"Stack Exchange quota_remaining: {data['quota_remaining']}")
    return result


def _mastodon_instance(instance: str) -> str:
    value = str(instance or "").strip().lower().rstrip("/")
    validate_public_url("https://" + value)
    if not HOSTNAME_RE.fullmatch(value): raise PublicSourceError("Mastodon instance is invalid")
    return value


def _mastodon_item(item: dict[str, Any]) -> dict[str, Any]:
    account = item.get("account") or {}
    return {"title": "", "author": clean_text(account.get("acct", account.get("username", ""))), "account": account, "content": clean_text(item.get("content", ""), 6000), "published_at": item.get("created_at", ""), "url": clean_text(item.get("url", ""), 2000), "id": item.get("id", ""), "summary": clean_text(item.get("spoiler_text", "") or item.get("content", "")), "favourites": item.get("favourites_count", 0), "reblogs": item.get("reblogs_count", 0), "replies": item.get("replies_count", 0)}


def mastodon(op: str, instance: str, limit: int = 10, acct: str = "", tag: str = "", url: str = "") -> dict[str, Any]:
    host = _mastodon_instance(instance)
    if op == "status":
        target = validate_public_url(url)
        parsed = urlsplit(target)
        if parsed.hostname.casefold() != host: raise PublicSourceError("status URL host must match Mastodon instance")
        match = re.search(r"/(?:statuses|notice)/([0-9]+)(?:/)?$", parsed.path)
        if not match: raise PublicSourceError("Mastodon status URL has no numeric status id")
        path, query = f"statuses/{match.group(1)}", match.group(1)
    elif op == "account": path, query = "accounts/lookup", acct
    elif op == "tag": path, query = f"timelines/tag/{quote(tag)}", tag
    else: path, query = "timelines/public", "public"
    data = fetch_json(f"https://{host}/api/v1/{path}?" + urlencode({"acct": acct} if op == "account" else ({"limit": limit} if op != "status" else {})), allowed_hosts={host})
    raw = data if isinstance(data, list) else [data]
    items = [_mastodon_item(x) for x in raw[:limit] if isinstance(x, dict)]
    return make_result("mastodon", op, query, items)


class _TelegramPreviewParser(HTMLParser):
    """Extract visible metadata from Telegram's public channel preview HTML."""

    def __init__(self, channel: str, limit: int):
        super().__init__(convert_charrefs=True)
        self.channel = channel
        self.limit = limit
        self.title = ""
        self.channel_name = ""
        self._tag = ""
        self._classes: set[str] = set()
        self._attrs: dict[str, str] = {}
        self._buffer: list[str] = []
        self._post: dict[str, Any] | None = None
        self._text_depth = 0
        self._text_buffer: list[str] = []
        self.items: list[dict[str, Any]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        self._tag = tag
        self._attrs = {key: value or "" for key, value in attrs}
        self._classes = set(self._attrs.get("class", "").split())
        if "tgme_channel_info" in self._classes:
            self._post = None
        if "tgme_widget_message" in self._classes and len(self.items) < self.limit:
            if self._post is not None and self._post not in self.items:
                self.items.append(self._post)
            self._post = {"title": "", "author": self.channel, "content": "", "published_at": "", "url": "", "summary": "", "views": ""}
            data_post = self._attrs.get("data-post", "")
            if data_post:
                self._post["url"] = "https://t.me/" + data_post
        if self._post is not None and "tgme_widget_message_text" in self._classes:
            self._text_depth = 1
            self._text_buffer = []

    def handle_endtag(self, tag: str) -> None:
        if self._text_depth:
            self._text_depth -= 1
            if self._text_depth == 0 and self._post is not None:
                text = clean_text(" ".join(self._text_buffer), 6000)
                self._post["content"] = text
                self._post["summary"] = text
                self._text_buffer = []
        self._tag, self._classes, self._attrs = "", set(), {}

    def handle_data(self, data: str) -> None:
        text = data.strip()
        if not text:
            return
        if self._tag == "title" and not self.title:
            self.title = clean_text(text)
        if self._text_depth and self._post is not None:
            self._text_buffer.append(text)
        elif self._post is not None:
            if "tgme_widget_message_date" in self._classes:
                self._post["published_at"] = self._attrs.get("datetime", text)
            elif "tgme_widget_message_views" in self._classes:
                self._post["views"] = text
            elif "tgme_channel_info" in self._classes:
                self.channel_name = text

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        self.handle_starttag(tag, attrs)
        self.handle_endtag(tag)

    def close(self) -> None:
        super().close()
        if self._post is not None and self._post not in self.items:
            self.items.append(self._post)


def telegram_channel(channel: str, limit: int = 10) -> dict[str, Any]:
    value = str(channel or "").strip().lstrip("@").rstrip("/")
    if not re.fullmatch(r"[A-Za-z0-9_]{1,64}", value):
        raise PublicSourceError("Telegram channel username is invalid")
    target = f"https://t.me/s/{value}"
    body = fetch_text(target, headers={"Accept": "text/html"}, allowed_hosts={"t.me", "telegram.me"})
    parser = _TelegramPreviewParser(value, limit)
    parser.feed(body)
    parser.close()
    items = parser.items[:limit]
    # Telegram nests links/spans inside message text; use a bounded HTML fallback
    # so a small markup change does not silently discard visible post text.
    if items and not any(item.get("content") for item in items):
        blocks = re.findall(
            r'<div[^>]+class="[^"]*tgme_widget_message_text[^"]*"[^>]*>(.*?)</div>',
            body, flags=re.I | re.S,
        )
        for item, block in zip(items, blocks[:limit]):
            item["content"] = clean_text(re.sub(r"<br\\s*/?>", " ", block, flags=re.I), 6000)
            item["summary"] = item["content"]
    result = make_result("telegram", "channel", target, items)
    result["channel"] = parser.channel_name or value
    result["title"] = parser.title
    result["warnings"].append("Telegram data is from the public t.me/s preview; private or login-only content is not accessed")
    if not items:
        result["warnings"].append("no public posts were parsed; the channel may be unavailable, empty, or have changed HTML")
    return result


def _reddit_url(value: str) -> str:
    target = validate_public_url(value, {"reddit.com", "www.reddit.com", "old.reddit.com"})
    parsed = urlsplit(target)
    return target if parsed.path.endswith(".rss") else target.rstrip("/") + ".rss"


def reddit(op: str, name: str = "", query: str = "", limit: int = 10, url: str = "") -> dict[str, Any]:
    if op == "subreddit": target = f"https://www.reddit.com/r/{quote(name)}/.rss?" + urlencode({"limit": limit})
    elif op == "search": target = "https://www.reddit.com/search.rss?" + urlencode({"q": query, "limit": limit})
    else: target = _reddit_url(url)
    result = rss_read(target, limit)
    result["source"], result["operation"] = "reddit", op
    result["warnings"].append("Reddit data is from official RSS; fields are limited and no anonymous JSON/OAuth is used")
    return result


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Read public internet sources as bounded JSON")
    sources = parser.add_subparsers(dest="source", required=True)

    rss = sources.add_parser("rss")
    rss_ops = rss.add_subparsers(dest="operation", required=True)
    rss_read_parser = rss_ops.add_parser("read")
    rss_read_parser.add_argument("--url", required=True)
    rss_read_parser.add_argument("--limit", type=require_limit, default=DEFAULT_LIMIT)

    youtube = sources.add_parser("youtube")
    youtube_ops = youtube.add_subparsers(dest="operation", required=True)
    youtube_search_parser = youtube_ops.add_parser("search")
    youtube_search_parser.add_argument("--query", required=True)
    youtube_search_parser.add_argument("--limit", type=require_limit, default=5)
    youtube_detail_parser = youtube_ops.add_parser("detail")
    youtube_detail_parser.add_argument("--url", required=True)
    youtube_comments_parser = youtube_ops.add_parser("comments")
    youtube_comments_parser.add_argument("--url", required=True)
    youtube_comments_parser.add_argument("--limit", type=require_limit, default=20)
    youtube_transcript_parser = youtube_ops.add_parser("transcript")
    youtube_transcript_parser.add_argument("--url", required=True)
    youtube_transcript_parser.add_argument("--languages", default="zh-Hans,zh,en")

    bilibili = sources.add_parser("bilibili")
    bilibili_ops = bilibili.add_subparsers(dest="operation", required=True)
    bilibili_search_parser = bilibili_ops.add_parser("search")
    bilibili_search_parser.add_argument("--query", required=True)
    bilibili_search_parser.add_argument("--limit", type=require_limit, default=5)
    bilibili_detail_parser = bilibili_ops.add_parser("detail")
    bilibili_detail_parser.add_argument("--bvid", required=True)
    for operation in ("hot", "rank"):
        simple = bilibili_ops.add_parser(operation)
        simple.add_argument("--limit", type=require_limit, default=10)
    bilibili_comments_parser = bilibili_ops.add_parser("comments")
    bilibili_comments_parser.add_argument("--bvid", required=True)
    bilibili_comments_parser.add_argument("--limit", type=require_limit, default=20)
    bilibili_comments_parser.add_argument("--next", type=int, default=0)
    bilibili_replies_parser = bilibili_ops.add_parser("replies")
    bilibili_replies_parser.add_argument("--bvid", required=True)
    bilibili_replies_parser.add_argument("--rpid", required=True, type=int)
    bilibili_replies_parser.add_argument("--limit", type=require_limit, default=20)
    bilibili_replies_parser.add_argument("--page", type=int, default=1)
    bilibili_subtitle_parser = bilibili_ops.add_parser("subtitle")
    bilibili_subtitle_parser.add_argument("--bvid", required=True)

    xueqiu = sources.add_parser("xueqiu")
    xueqiu_ops = xueqiu.add_subparsers(dest="operation", required=True)
    xueqiu_quote_parser = xueqiu_ops.add_parser("quote")
    xueqiu_quote_parser.add_argument("--symbol", required=True)
    xueqiu_search_parser = xueqiu_ops.add_parser("search")
    xueqiu_search_parser.add_argument("--query", required=True)
    xueqiu_search_parser.add_argument("--limit", type=require_limit, default=10)
    xueqiu_hot_parser = xueqiu_ops.add_parser("hot-posts")
    xueqiu_hot_parser.add_argument("--limit", type=require_limit, default=20)
    xueqiu_hot_stocks_parser = xueqiu_ops.add_parser("hot-stocks")
    xueqiu_hot_stocks_parser.add_argument("--limit", type=require_limit, default=10)
    xueqiu_hot_stocks_parser.add_argument("--type", type=int, choices=(10, 12), default=10)

    v2ex = sources.add_parser("v2ex")
    v2ex_ops = v2ex.add_subparsers(dest="operation", required=True)
    hot = v2ex_ops.add_parser("hot")
    hot.add_argument("--limit", type=require_limit, default=10)
    node = v2ex_ops.add_parser("node")
    node.add_argument("--name", required=True)
    node.add_argument("--limit", type=require_limit, default=10)
    topic = v2ex_ops.add_parser("topic")
    topic.add_argument("--id", required=True, type=int)
    replies = v2ex_ops.add_parser("replies")
    replies.add_argument("--id", required=True, type=int)
    replies.add_argument("--page", type=int, default=1)
    user = v2ex_ops.add_parser("user")
    user.add_argument("--name", required=True)
    search = v2ex_ops.add_parser("search")
    search.add_argument("--query", required=True)
    search.add_argument("--limit", type=require_limit, default=10)

    github = sources.add_parser("github")
    github_ops = github.add_subparsers(dest="operation", required=True)
    repo = github_ops.add_parser("repo")
    repo.add_argument("--repo", required=True)
    readme = github_ops.add_parser("readme")
    readme.add_argument("--repo", required=True)
    repos = github_ops.add_parser("search-repos")
    repos.add_argument("--query", required=True)
    repos.add_argument("--limit", type=require_limit, default=10)
    code = github_ops.add_parser("search-code")
    code.add_argument("--query", required=True)
    code.add_argument("--language")
    code.add_argument("--limit", type=require_limit, default=10)

    linkedin = sources.add_parser("linkedin")
    linkedin_ops = linkedin.add_subparsers(dest="operation", required=True)
    linkedin_read_parser = linkedin_ops.add_parser("read")
    linkedin_read_parser.add_argument("--url", required=True)

    hn = sources.add_parser("hackernews")
    hn_ops = hn.add_subparsers(dest="operation", required=True)
    for op in ("top", "new", "best", "ask", "show", "jobs"):
        p = hn_ops.add_parser(op); p.add_argument("--limit", type=require_limit, default=10)
    p = hn_ops.add_parser("item"); p.add_argument("--id", type=int, required=True)
    p = hn_ops.add_parser("user"); p.add_argument("--name", required=True); p.add_argument("--limit", type=require_limit, default=10)

    se = sources.add_parser("stackexchange")
    se_ops = se.add_subparsers(dest="operation", required=True)
    p = se_ops.add_parser("questions"); p.add_argument("--site", required=True); p.add_argument("--query"); p.add_argument("--tag"); p.add_argument("--limit", type=require_limit, default=10)
    for op in ("question", "answers", "user"):
        p = se_ops.add_parser(op); p.add_argument("--site", required=True); p.add_argument("--id", type=int, required=True); p.add_argument("--limit", type=require_limit, default=10)

    mast = sources.add_parser("mastodon")
    mast_ops = mast.add_subparsers(dest="operation", required=True)
    p = mast_ops.add_parser("status"); p.add_argument("--url", required=True)
    p = mast_ops.add_parser("account"); p.add_argument("--instance", required=True); p.add_argument("--acct", required=True)
    for op in ("tag", "timeline"):
        p = mast_ops.add_parser(op); p.add_argument("--instance", required=True); p.add_argument("--limit", type=require_limit, default=10)
        if op == "tag": p.add_argument("--tag", required=True)

    telegram = sources.add_parser("telegram")
    telegram_ops = telegram.add_subparsers(dest="operation", required=True)
    p = telegram_ops.add_parser("channel")
    p.add_argument("--name", required=True, help="public channel username, with or without @")
    p.add_argument("--limit", type=require_limit, default=10)

    red = sources.add_parser("reddit")
    red_ops = red.add_subparsers(dest="operation", required=True)
    p = red_ops.add_parser("subreddit"); p.add_argument("--name", required=True); p.add_argument("--limit", type=require_limit, default=10)
    p = red_ops.add_parser("search"); p.add_argument("--query", required=True); p.add_argument("--limit", type=require_limit, default=10)
    p = red_ops.add_parser("post"); p.add_argument("--url", required=True); p.add_argument("--limit", type=require_limit, default=10)

    return parser


def dispatch(args: argparse.Namespace) -> dict[str, Any]:
    if args.source == "rss":
        return rss_read(args.url, args.limit)
    if args.source == "youtube":
        if args.operation == "search":
            return youtube_search(args.query, args.limit)
        if args.operation == "detail":
            return youtube_detail(args.url)
        if args.operation == "comments":
            return youtube_comments(args.url, args.limit)
        return youtube_transcript(args.url, args.languages)
    if args.source == "bilibili":
        if args.operation == "search":
            return bilibili_search(args.query, args.limit)
        if args.operation == "detail":
            return bilibili_detail(args.bvid)
        if args.operation in {"hot", "rank"}:
            return bilibili_simple(args.operation, args.limit)
        if args.operation == "comments":
            return bilibili_comments(args.bvid, args.limit, args.next)
        if args.operation == "replies":
            return bilibili_replies(args.bvid, args.rpid, args.limit, args.page)
        return bilibili_subtitle(args.bvid)
    if args.source == "xueqiu":
        if args.operation == "quote":
            return xueqiu_quote(args.symbol)
        if args.operation == "search":
            return xueqiu_search(args.query, args.limit)
        if args.operation == "hot-posts":
            return xueqiu_hot_posts(args.limit)
        return xueqiu_hot_stocks(args.limit, args.type)
    if args.source == "v2ex":
        if args.operation == "hot":
            return v2ex_api("/api/topics/hot.json", {}, "hot", "hot", args.limit)
        if args.operation == "node":
            return v2ex_api(
                "/api/topics/show.json",
                {"node_name": args.name, "page": 1},
                "node",
                args.name,
                args.limit,
            )
        if args.operation == "topic":
            return v2ex_api("/api/topics/show.json", {"id": args.id}, "detail", str(args.id))
        if args.operation == "replies":
            return v2ex_api(
                "/api/replies/show.json",
                {"topic_id": args.id, "page": args.page},
                "replies",
                str(args.id),
            )
        if args.operation == "user":
            return v2ex_api("/api/members/show.json", {"username": args.name}, "user", args.name)
        return v2ex_search(args.query, args.limit)
    if args.source == "hackernews":
        return hackernews(args.operation, getattr(args, "limit", 10), getattr(args, "id", None), getattr(args, "name", ""))
    if args.source == "stackexchange":
        return stackexchange(args.operation, args.site, getattr(args, "limit", 10), getattr(args, "query", "") or "", getattr(args, "tag", "") or "", getattr(args, "id", None))
    if args.source == "mastodon":
        return mastodon(args.operation, getattr(args, "instance", "") or urlsplit(args.url).hostname, getattr(args, "limit", 1), getattr(args, "acct", ""), getattr(args, "tag", ""), getattr(args, "url", ""))
    if args.source == "reddit":
        return reddit(args.operation, getattr(args, "name", ""), getattr(args, "query", ""), getattr(args, "limit", 10), getattr(args, "url", ""))
    if args.source == "telegram":
        return telegram_channel(args.name, args.limit)
    if args.source == "github":
        if args.operation == "repo":
            return github_repo(args.repo)
        if args.operation == "readme":
            return github_readme(args.repo)
        if args.operation == "search-repos":
            return github_search_repos(args.query, args.limit)
        return github_search_code(args.query, args.limit, args.language)
    return linkedin_read(args.url)


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        result = dispatch(args)
    except (PublicSourceError, ValueError, OSError) as exc:
        result = make_result(
            args.source,
            getattr(args, "operation", "unknown"),
            _query_for_error(args),
            errors=[str(exc)],
        )
        emit(result)
        return 1
    emit(result)
    return 0 if not result["errors"] else 1


def _query_for_error(args: argparse.Namespace) -> str:
    for name in ("url", "query", "repo", "bvid", "symbol", "name", "id"):
        value = getattr(args, name, None)
        if value is not None:
            return str(value)
    return str(getattr(args, "operation", ""))


if __name__ == "__main__":
    sys.exit(main())
