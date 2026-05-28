# -*- coding: utf-8 -*-
"""单网页抓取与正文提取能力。"""

import os
import re
from dataclasses import dataclass
from typing import Any
from urllib.parse import unquote, urlparse

import aiohttp
import trafilatura
from bs4 import BeautifulSoup

from reactor_tool.model.protocal import WebFetchRequest


DEFAULT_TIMEOUT_SECONDS = 30
DEFAULT_INLINE_CONTENT_CHARS = 12000
DEFAULT_USER_AGENT = "ReactorToolWebFetch/1.0"
TRUNCATED_SUFFIX = "\n\n[内容已截断，完整正文请查看附件文件。]"


@dataclass
class DownloadedPage:
    """下载后的网页原始信息。"""

    final_url: str
    raw_content: str
    content_type: str


@dataclass
class ExtractedContent:
    """正文提取结果。"""

    title: str
    content: str
    content_format: str
    content_source: str
    metadata: dict[str, Any]


@dataclass
class WebFetchResult:
    """web_fetch 返回给 API 层的标准结果。"""

    title: str
    final_url: str
    full_content: str
    inline_content: str
    content_format: str
    word_count: int
    truncated: bool
    content_source: str
    metadata: dict[str, Any]
    file_name: str

    def to_response_data(self) -> dict[str, Any]:
        """组装统一 data 结构，避免 API 层重复拼装字段。"""
        return {
            "title": self.title,
            "finalUrl": self.final_url,
            "content": self.inline_content,
            "contentFormat": self.content_format,
            "wordCount": self.word_count,
            "truncated": self.truncated,
            "contentSource": self.content_source,
            "metadata": self.metadata,
        }


class WebFetcher:
    """抓取单个网页并提取正文。"""

    def __init__(self, inline_content_limit: int | None = None):
        configured_limit = os.getenv("WEB_FETCH_INLINE_CHAR_LIMIT", str(DEFAULT_INLINE_CONTENT_CHARS))
        self.inline_content_limit = inline_content_limit or self._parse_positive_int(
            configured_limit,
            DEFAULT_INLINE_CONTENT_CHARS,
        )

    async def fetch(self, request: WebFetchRequest) -> WebFetchResult:
        """抓取网页、提取正文，并生成统一返回结果。"""
        page = await self._download_page(request.url, request.timeout_seconds)
        extracted = self._extract_content(page)
        if not extracted.content:
            raise ValueError("网页正文提取失败")

        normalized_title = extracted.title or self._build_title_from_url(page.final_url)
        file_name = self._build_file_name(normalized_title, page.final_url)
        inline_content, truncated = self._truncate_content(extracted.content)
        return WebFetchResult(
            title=normalized_title,
            final_url=page.final_url,
            full_content=extracted.content,
            inline_content=inline_content,
            content_format=extracted.content_format,
            word_count=self._count_words(extracted.content),
            truncated=truncated,
            content_source=extracted.content_source,
            metadata=extracted.metadata,
            file_name=file_name,
        )

    async def _download_page(self, url: str, timeout_seconds: int) -> DownloadedPage:
        """下载网页或文本内容，供后续按内容类型分流处理。"""
        client_timeout = aiohttp.ClientTimeout(total=timeout_seconds or DEFAULT_TIMEOUT_SECONDS)
        headers = {"User-Agent": DEFAULT_USER_AGENT}
        async with aiohttp.ClientSession(timeout=client_timeout, headers=headers) as session:
            async with session.get(url, allow_redirects=True) as response:
                response.raise_for_status()
                content_type = (response.headers.get("Content-Type") or "").lower()
                if not self._is_supported_content_type(content_type):
                    raise ValueError("web_fetch 仅支持 HTML、Markdown 或纯文本内容")
                raw_content = await response.text(errors="ignore")
                if not raw_content.strip():
                    raise ValueError("网页内容为空")
                return DownloadedPage(
                    final_url=str(response.url),
                    raw_content=raw_content,
                    content_type=content_type,
                )

    def _extract_content(self, page: DownloadedPage) -> ExtractedContent:
        """按响应类型选择提取策略，HTML 走正文提取，文本直接复用原文。"""
        if self._is_html_content_type(page.content_type):
            return self._extract_html_content(page.raw_content, page.final_url)
        return self._extract_text_content(page.raw_content, page.final_url, page.content_type)

    def _extract_html_content(self, html: str, final_url: str) -> ExtractedContent:
        """优先使用 trafilatura 提取 HTML 正文，失败时回退到 BeautifulSoup。"""
        soup = BeautifulSoup(html, "html.parser")
        title = self._extract_title(soup)
        metadata = self._extract_metadata(soup)

        content = trafilatura.extract(
            html,
            url=final_url,
            output_format="markdown",
            include_comments=False,
            include_tables=True,
            include_links=True,
        )
        normalized_content = self._normalize_content(content)
        if normalized_content:
            return ExtractedContent(
                title=title,
                content=normalized_content,
                content_format="markdown",
                content_source="trafilatura",
                metadata=metadata,
            )

        fallback_content = self._extract_with_beautifulsoup(soup)
        return ExtractedContent(
            title=title,
            content=fallback_content,
            content_format="markdown",
            content_source="beautifulsoup",
            metadata=metadata,
        )

    def _extract_text_content(self, raw_content: str, final_url: str, content_type: str) -> ExtractedContent:
        """处理 Markdown 与纯文本响应，避免 raw 文件和 Reader 文本链路被误判为异常。"""
        normalized_content = self._normalize_content(raw_content)
        title = self._build_title_from_url(final_url)
        return ExtractedContent(
            title=title,
            content=normalized_content,
            content_format="markdown" if "markdown" in content_type else "text",
            content_source="plain_text",
            metadata={
                "description": "",
                "siteName": urlparse(final_url).netloc,
            },
        )

    def _extract_with_beautifulsoup(self, soup: BeautifulSoup) -> str:
        """在主提取器失败时，尽量保留可读文本作为兜底。"""
        for tag in soup(["script", "style", "noscript", "svg"]):
            tag.decompose()
        text = soup.get_text("\n")
        return self._normalize_content(text)

    def _extract_title(self, soup: BeautifulSoup) -> str:
        """优先取 OpenGraph 标题，其次取 HTML title。"""
        og_title = soup.find("meta", attrs={"property": "og:title"})
        if og_title and og_title.get("content"):
            return og_title["content"].strip()
        if soup.title and soup.title.string:
            return soup.title.string.strip()
        h1 = soup.find("h1")
        return h1.get_text(strip=True) if h1 else ""

    def _extract_metadata(self, soup: BeautifulSoup) -> dict[str, Any]:
        """提取最小 metadata，供 Java 侧调试和展示。"""
        return {
            "description": self._extract_meta_content(soup, "name", "description"),
            "siteName": self._extract_meta_content(soup, "property", "og:site_name"),
        }

    def _extract_meta_content(self, soup: BeautifulSoup, attr_name: str, attr_value: str) -> str:
        tag = soup.find("meta", attrs={attr_name: attr_value})
        content = tag.get("content") if tag else ""
        return content.strip() if content else ""

    def _normalize_content(self, content: str | None) -> str:
        """统一清洗换行和空白，保证正文能稳定写入 Markdown 文件。"""
        if not content:
            return ""
        normalized = content.replace("\r\n", "\n").replace("\r", "\n")
        normalized = re.sub(r"\n{3,}", "\n\n", normalized)
        normalized = re.sub(r"[ \t]+\n", "\n", normalized)
        return normalized.strip()

    def _is_supported_content_type(self, content_type: str) -> bool:
        """仅允许 HTML、Markdown 和纯文本，避免误抓二进制。"""
        if not content_type:
            return True
        return self._is_html_content_type(content_type) or any(
            marker in content_type for marker in ("text/plain", "text/markdown", "text/x-markdown")
        )

    def _is_html_content_type(self, content_type: str) -> bool:
        """识别可走正文提取器的 HTML 响应。"""
        return "html" in content_type or "xhtml" in content_type

    def _truncate_content(self, content: str) -> tuple[str, bool]:
        """控制内联返回体积，完整内容始终通过文件产物保留。"""
        if len(content) <= self.inline_content_limit:
            return content, False
        truncated = content[: self.inline_content_limit].rstrip()
        return truncated + TRUNCATED_SUFFIX, True

    def _count_words(self, content: str) -> int:
        """近似统计正文词数，兼容中英文混排。"""
        tokens = re.findall(r"[A-Za-z0-9_]+|[\u4e00-\u9fff]", content or "")
        return len(tokens)

    def _build_title_from_url(self, url: str) -> str:
        """标题缺失时回退到 URL slug，避免文件名不可读。"""
        parsed = urlparse(url)
        path_segments = [segment for segment in parsed.path.split("/") if segment]
        if path_segments:
            candidate = unquote(path_segments[-1]).rsplit(".", 1)[0]
            sanitized = self._sanitize_file_stem(candidate)
            if sanitized:
                return sanitized
        sanitized_host = self._sanitize_file_stem(parsed.netloc.replace(":", "_"))
        return sanitized_host or "web_fetch_result"

    def _build_file_name(self, title: str, url: str) -> str:
        """按标题优先、URL slug 兜底生成稳定文件名。"""
        file_stem = self._sanitize_file_stem(title) or self._build_title_from_url(url)
        return f"{file_stem}.md"

    def _sanitize_file_stem(self, value: str) -> str:
        """清洗文件名非法字符，兼容 Windows 工作区。"""
        cleaned = re.sub(r"[<>:\"/\\\\|?*\x00-\x1f]", " ", value or "")
        cleaned = re.sub(r"\s+", "_", cleaned).strip("._ ")
        return cleaned[:80]

    def _parse_positive_int(self, raw_value: str, default_value: int) -> int:
        try:
            parsed = int(str(raw_value).strip())
            return parsed if parsed > 0 else default_value
        except (TypeError, ValueError):
            return default_value
