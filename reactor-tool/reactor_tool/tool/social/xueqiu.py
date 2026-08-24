"""Read-only Xueqiu client using a Cookie header from the environment."""

from __future__ import annotations

import html
import json
import re
from typing import Any

import httpx

from .common import (
    SocialPlatformError,
    bounded_limit,
    bounded_text,
    require_secret,
    scrub_text,
    success_result,
)


HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://xueqiu.com/",
    "Accept": "application/json, text/plain, */*",
}
SYMBOL_RE = re.compile(r"^[A-Za-z0-9._-]{1,32}$")


def _strip_html(value: Any) -> str:
    text = re.sub(r"<[^>]+>", " ", "" if value is None else str(value))
    return html.unescape(re.sub(r"\s+", " ", text)).strip()


class XueqiuClient:
    def __init__(self, cookie: str, timeout_seconds: float = 20.0):
        cookie_header = cookie.strip()
        if cookie_header.casefold().startswith("cookie:"):
            cookie_header = cookie_header.split(":", 1)[1].strip()
        self._client = httpx.Client(
            headers={**HEADERS, "Cookie": cookie_header},
            timeout=timeout_seconds,
            follow_redirects=True,
        )

    def close(self) -> None:
        self._client.close()

    def request_json(self, url: str, params: dict[str, Any]) -> dict[str, Any]:
        try:
            response = self._client.get(url, params=params)
        except httpx.RequestError as exc:
            raise SocialPlatformError("UPSTREAM_ERROR", "Xueqiu request failed") from exc
        if response.status_code in {401, 403}:
            raise SocialPlatformError("AUTH_REQUIRED", "Xueqiu session was rejected")
        if response.status_code >= 400:
            raise SocialPlatformError(
                "UPSTREAM_ERROR", f"Xueqiu returned HTTP {response.status_code}"
            )
        try:
            payload = response.json()
        except ValueError as exc:
            raise SocialPlatformError("INVALID_RESPONSE", "Xueqiu returned invalid JSON") from exc
        if not isinstance(payload, dict):
            raise SocialPlatformError("INVALID_RESPONSE", "Xueqiu returned an unexpected response")
        error_code = payload.get("error_code")
        if error_code not in (None, 0):
            message = payload.get("error_description") or "Xueqiu rejected the request"
            code = "AUTH_REQUIRED" if str(error_code) in {"400016", "401"} else "UPSTREAM_REJECTED"
            raise SocialPlatformError(
                code, scrub_text(message, (self._client.headers.get("Cookie", ""),))
            )
        return payload


def _symbol(value: Any) -> str:
    symbol = bounded_text(value, 32).strip()
    if not SYMBOL_RE.fullmatch(symbol):
        raise SocialPlatformError(
            "INVALID_INPUT", "symbol must contain only letters, numbers, dot, underscore, or hyphen"
        )
    return symbol


def _stock_type(value: Any) -> int:
    try:
        stock_type = int(value or 10)
    except (TypeError, ValueError) as exc:
        raise SocialPlatformError("INVALID_INPUT", "stock_type must be an integer") from exc
    if not 1 <= stock_type <= 100:
        raise SocialPlatformError("INVALID_INPUT", "stock_type must be between 1 and 100")
    return stock_type


def execute(params: dict[str, Any]) -> dict[str, Any]:
    operation = bounded_text(params.get("operation"), 40).casefold().replace("-", "_")
    client = XueqiuClient(require_secret("REACTOR_XUEQIU_COOKIE"))
    try:
        limit = bounded_limit(params.get("limit"), maximum=50)
        if operation == "quote":
            symbol = _symbol(params.get("symbol"))
            payload = client.request_json(
                "https://stock.xueqiu.com/v5/stock/quote.json",
                {"symbol": symbol, "extend": "detail"},
            )
            quote_data = (payload.get("data") or {}).get("quote") or {}
            return success_result(
                "xueqiu",
                operation,
                [
                    {
                        "symbol": quote_data.get("symbol", symbol),
                        "name": quote_data.get("name", ""),
                        "current": quote_data.get("current"),
                        "percent": quote_data.get("percent"),
                        "chg": quote_data.get("chg"),
                        "high": quote_data.get("high"),
                        "low": quote_data.get("low"),
                        "open": quote_data.get("open"),
                        "last_close": quote_data.get("last_close"),
                        "volume": quote_data.get("volume"),
                        "amount": quote_data.get("amount"),
                        "market_capital": quote_data.get("market_capital"),
                        "turnover_rate": quote_data.get("turnover_rate"),
                        "pe_ttm": quote_data.get("pe_ttm"),
                        "pe_forecast": quote_data.get("pe_forecast"),
                        "pb": quote_data.get("pb"),
                        "eps": quote_data.get("eps"),
                        "timestamp": quote_data.get("timestamp"),
                    }
                ],
                query=symbol,
            )

        if operation == "search":
            query = bounded_text(params.get("query"), 100)
            if not query:
                raise SocialPlatformError("INVALID_INPUT", "query is required for Xueqiu search")
            payload = client.request_json(
                "https://xueqiu.com/stock/search.json",
                {"code": query, "size": limit},
            )
            stocks = payload.get("stocks") or []
            return success_result(
                "xueqiu",
                operation,
                [
                    {
                        "symbol": item.get("code", ""),
                        "name": item.get("name", ""),
                        "exchange": item.get("exchange", ""),
                    }
                    for item in stocks[:limit]
                    if isinstance(item, dict)
                ],
                query=query,
            )

        if operation == "hot_posts":
            payload = client.request_json(
                "https://xueqiu.com/v4/statuses/public_timeline_by_category.json",
                {"since_id": -1, "max_id": -1, "count": limit, "category": -1},
            )
            results = []
            for item in (payload.get("list") or [])[:limit]:
                if not isinstance(item, dict):
                    continue
                raw_post = item.get("data")
                try:
                    post = json.loads(raw_post) if isinstance(raw_post, str) else {}
                except (TypeError, ValueError):
                    post = {}
                user = post.get("user") or {}
                target = post.get("target", "")
                results.append(
                    {
                        "id": post.get("id", 0),
                        "title": bounded_text(post.get("title"), 500),
                        "text": _strip_html(post.get("text") or post.get("description"))[:4_000],
                        "author": bounded_text(user.get("screen_name"), 200),
                        "likes": post.get("like_count", 0),
                        "url": f"https://xueqiu.com{target}"
                        if str(target).startswith("/")
                        else bounded_text(target, 2_000),
                    }
                )
            return success_result("xueqiu", operation, results)

        if operation == "hot_stocks":
            stock_type = _stock_type(params.get("stock_type"))
            payload = client.request_json(
                "https://stock.xueqiu.com/v5/stock/hot_stock/list.json",
                {"size": limit, "type": stock_type},
            )
            items = ((payload.get("data") or {}).get("items") or [])[:limit]
            return success_result(
                "xueqiu",
                operation,
                [
                    {
                        "symbol": item.get("code") or item.get("symbol", ""),
                        "name": item.get("name", ""),
                        "current": item.get("current"),
                        "percent": item.get("percent"),
                        "rank": index,
                    }
                    for index, item in enumerate(items, 1)
                    if isinstance(item, dict)
                ],
            )

        raise SocialPlatformError(
            "INVALID_INPUT",
            "unsupported Xueqiu operation; use quote, search, hot_posts, or hot_stocks",
        )
    finally:
        client.close()
