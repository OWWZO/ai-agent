"""Read-only Reddit client using an explicit session cookie from the environment."""

from __future__ import annotations

import os
import re
import time
from typing import Any
from urllib.parse import urlparse

import httpx

from .common import (
    SocialPlatformError,
    bounded_limit,
    bounded_text,
    require_secret,
    success_result,
)


BASE_URL = "https://www.reddit.com"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "en-US,en;q=0.9",
}
SUBREDDIT_RE = re.compile(r"^[A-Za-z0-9_]{1,50}$")
POST_ID_RE = re.compile(r"^[A-Za-z0-9]+$")


def _cookie_value(raw: str) -> str:
    value = raw.strip()
    if "=" not in value:
        return value
    for part in value.split(";"):
        name, separator, item = part.strip().partition("=")
        if separator and name.strip() == "reddit_session":
            return item.strip()
    return value


def _normalize_subreddit(value: Any) -> str:
    subreddit = bounded_text(value, 50).strip().lstrip("/")
    if subreddit.casefold().startswith("r/"):
        subreddit = subreddit[2:]
    if not SUBREDDIT_RE.fullmatch(subreddit):
        raise SocialPlatformError("INVALID_INPUT", "subreddit must be a valid Reddit name")
    return subreddit


def _normalize_post_id(value: Any) -> str:
    raw = bounded_text(value, 2_000).strip()
    if raw.startswith("t3_"):
        raw = raw[3:]
    if raw.startswith("http://") or raw.startswith("https://"):
        match = re.search(r"/comments/([A-Za-z0-9]+)", urlparse(raw).path)
        raw = match.group(1) if match else ""
    if not POST_ID_RE.fullmatch(raw):
        raise SocialPlatformError("INVALID_INPUT", "post_id must be a Reddit post id or post URL")
    return raw


def _children(listing: Any) -> list[dict[str, Any]]:
    if not isinstance(listing, dict):
        return []
    data = listing.get("data") or {}
    children = data.get("children") if isinstance(data, dict) else []
    if not isinstance(children, list):
        return []
    return [child.get("data", child) for child in children if isinstance(child, dict)]


def _post(item: dict[str, Any]) -> dict[str, Any]:
    permalink = bounded_text(item.get("permalink"), 2_000)
    url = item.get("url") or (BASE_URL + permalink if permalink.startswith("/") else permalink)
    return {
        "id": bounded_text(item.get("id"), 100),
        "title": bounded_text(item.get("title"), 1_000),
        "subreddit": bounded_text(item.get("subreddit"), 100),
        "author": bounded_text(item.get("author"), 200),
        "score": item.get("score", 0),
        "num_comments": item.get("num_comments", 0),
        "created_utc": item.get("created_utc"),
        "selftext": bounded_text(item.get("selftext"), 8_000),
        "url": bounded_text(url, 2_000),
        "permalink": permalink,
        "domain": bounded_text(item.get("domain"), 300),
        "is_self": bool(item.get("is_self", False)),
    }


def _comments(listing: Any, depth: int = 0) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for item in _children(listing):
        if item.get("body") is None:
            continue
        replies = item.get("replies")
        result.append(
            {
                "id": bounded_text(item.get("id"), 100),
                "author": bounded_text(item.get("author"), 200),
                "body": bounded_text(item.get("body"), 4_000),
                "score": item.get("score", 0),
                "created_utc": item.get("created_utc"),
                "depth": depth,
                "replies": _comments(replies, depth + 1) if isinstance(replies, dict) else [],
            }
        )
    return result


class RedditClient:
    def __init__(self, session_cookie: str, timeout_seconds: float = 30.0):
        self._session_cookie = _cookie_value(session_cookie)
        client_kwargs: dict[str, Any] = {
            "base_url": BASE_URL,
            "headers": HEADERS,
            "cookies": {"reddit_session": self._session_cookie},
            "timeout": timeout_seconds,
            "follow_redirects": True,
        }
        proxy = os.getenv("REACTOR_REDDIT_PROXY", "").strip()
        if proxy:
            client_kwargs["proxy"] = proxy
        self._client = httpx.Client(**client_kwargs)

    def close(self) -> None:
        self._client.close()

    def request_json(self, path: str, params: dict[str, Any]) -> Any:
        for attempt in range(3):
            try:
                response = self._client.get(path, params=params)
            except httpx.RequestError as exc:
                raise SocialPlatformError("UPSTREAM_ERROR", "Reddit request failed") from exc
            if response.status_code in {429, 500, 502, 503, 504} and attempt < 2:
                time.sleep(0.5 * (attempt + 1))
                continue
            if response.status_code in {401, 403}:
                raise SocialPlatformError("AUTH_REQUIRED", "Reddit session was rejected")
            if response.status_code == 429:
                raise SocialPlatformError("RATE_LIMITED", "Reddit rate limit reached")
            if response.status_code >= 400:
                raise SocialPlatformError(
                    "UPSTREAM_ERROR", f"Reddit returned HTTP {response.status_code}"
                )
            try:
                return response.json()
            except ValueError as exc:
                raise SocialPlatformError(
                    "INVALID_RESPONSE", "Reddit returned invalid JSON"
                ) from exc
        raise SocialPlatformError("UPSTREAM_ERROR", "Reddit request failed after retries")


def execute(params: dict[str, Any]) -> dict[str, Any]:
    operation = bounded_text(params.get("operation"), 40).casefold()
    session = require_secret("REACTOR_REDDIT_SESSION")
    client = RedditClient(session)
    try:
        limit = bounded_limit(params.get("limit"), maximum=100)
        sort = bounded_text(params.get("sort") or "relevance", 30)
        time_filter = bounded_text(params.get("time_filter") or "all", 30)

        if operation == "search":
            query = bounded_text(params.get("query"), 500)
            if not query:
                raise SocialPlatformError("INVALID_INPUT", "query is required for Reddit search")
            if sort not in {"relevance", "hot", "top", "new", "comments"}:
                sort = "relevance"
            subreddit = params.get("subreddit")
            path = (
                f"/r/{_normalize_subreddit(subreddit)}/search.json" if subreddit else "/search.json"
            )
            payload = client.request_json(
                path,
                {
                    "q": query,
                    "sort": sort,
                    "t": time_filter,
                    "limit": limit,
                    "restrict_sr": "on" if subreddit else "off",
                    "raw_json": 1,
                },
            )
            items = [_post(item) for item in _children(payload)]
            return success_result("reddit", operation, items, query=query)

        if operation == "subreddit":
            subreddit = _normalize_subreddit(params.get("subreddit"))
            if sort not in {"hot", "new", "top", "rising", "controversial", "best"}:
                sort = "hot"
            path = f"/r/{subreddit}.json" if sort == "hot" else f"/r/{subreddit}/{sort}.json"
            listing_params: dict[str, Any] = {"limit": limit, "raw_json": 1}
            if sort in {"top", "controversial"}:
                listing_params["t"] = time_filter
            payload = client.request_json(path, listing_params)
            return success_result(
                "reddit", operation, [_post(item) for item in _children(payload)], query=subreddit
            )

        if operation == "popular":
            payload = client.request_json("/r/popular.json", {"limit": limit, "raw_json": 1})
            return success_result("reddit", operation, [_post(item) for item in _children(payload)])

        if operation == "user_posts":
            username = bounded_text(params.get("username"), 100).strip()
            if username.casefold().startswith("u/"):
                username = username[2:]
            if not username or not re.fullmatch(r"[A-Za-z0-9_-]{1,100}", username):
                raise SocialPlatformError("INVALID_INPUT", "username is required for user_posts")
            payload = client.request_json(
                f"/user/{username}/submitted.json", {"limit": limit, "raw_json": 1}
            )
            return success_result(
                "reddit", operation, [_post(item) for item in _children(payload)], query=username
            )

        if operation == "read":
            post_id = _normalize_post_id(params.get("post_id"))
            if sort not in {"best", "new", "top", "controversial", "old", "qa"}:
                sort = "best"
            payload = client.request_json(
                f"/comments/{post_id}.json",
                {
                    "sort": sort or "best",
                    "limit": limit,
                    "raw_json": 1,
                },
            )
            listings = payload if isinstance(payload, list) else []
            post_items = _children(listings[0]) if listings else []
            post_item = _post(post_items[0]) if post_items else {}
            comment_items = _comments(listings[1]) if len(listings) > 1 else []
            return success_result(
                "reddit",
                operation,
                [
                    {
                        "post": post_item,
                        "comments": comment_items,
                        "comment_count": len(comment_items),
                    }
                ],
                query=post_id,
            )

        raise SocialPlatformError(
            "INVALID_INPUT",
            "unsupported Reddit operation; use search, subreddit, popular, read, or user_posts",
        )
    finally:
        client.close()
