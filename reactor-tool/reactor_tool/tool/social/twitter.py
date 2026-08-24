"""Read-only Twitter/X adapter backed by the explicit twitter-cli command."""

from __future__ import annotations

import os
from typing import Any

from .common import (
    SocialPlatformError,
    bounded_limit,
    bounded_text,
    command_output,
    first_value,
    parse_structured_output,
    require_secret,
    success_result,
    unwrap_cli_payload,
)


def _command_env(auth_token: str, ct0: str) -> dict[str, str]:
    env = os.environ.copy()
    for name in (
        "REACTOR_TWITTER_AUTH_TOKEN",
        "REACTOR_TWITTER_CT0",
        "REACTOR_REDDIT_SESSION",
        "REACTOR_XUEQIU_COOKIE",
        "TWITTER_AUTH_TOKEN",
        "TWITTER_CT0",
    ):
        env.pop(name, None)
    env["TWITTER_AUTH_TOKEN"] = auth_token
    env["TWITTER_CT0"] = ct0
    proxy = os.getenv("REACTOR_TWITTER_PROXY", "").strip()
    if proxy:
        env["TWITTER_PROXY"] = proxy
    return env


def _tweet_items(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, dict):
        for key in ("tweets", "items", "results", "data"):
            value = payload.get(key)
            if isinstance(value, list):
                return [item for item in value if isinstance(item, dict)]
            if isinstance(value, dict):
                return _tweet_items(value)
        tweet = payload.get("tweet") or payload.get("post")
        if isinstance(tweet, dict):
            if isinstance(payload.get("replies"), list) and "replies" not in tweet:
                tweet = {**tweet, "replies": payload["replies"]}
            return [tweet]
        return [payload]
    return [item for item in payload if isinstance(item, dict)] if isinstance(payload, list) else []


def _normalize_tweet(item: dict[str, Any]) -> dict[str, Any]:
    nested = item.get("tweet") or item.get("post")
    source = nested if isinstance(nested, dict) else item
    author = source.get("author") or source.get("user") or {}
    if not isinstance(author, dict):
        author = {"name": author}
    metrics = source.get("metrics") or {}
    if not isinstance(metrics, dict):
        metrics = {}
    tweet_id = str(first_value(source, "id", "tweet_id", "rest_id"))
    username = bounded_text(
        first_value(author, "username", "screen_name", "screenName", "handle"), 200
    )
    url = first_value(source, "url", "permalink", "tweet_url", "web_url")
    if not url and tweet_id and username:
        url = f"https://x.com/{username}/status/{tweet_id}"
    result: dict[str, Any] = {
        "id": tweet_id,
        "text": bounded_text(
            first_value(source, "text", "full_text", "note_text", "articleText"), 8_000
        ),
        "article_title": bounded_text(first_value(source, "articleTitle"), 500),
        "author": bounded_text(
            first_value(author, "name", "display_name", "username", "screenName"), 200
        ),
        "username": username,
        "created_at": bounded_text(
            first_value(source, "created_at", "createdAt", "createdAtISO", "createdAtLocal"), 100
        ),
        "url": bounded_text(url, 2_000),
        "likes": first_value(source, "likes", "like_count", "favorite_count")
        or first_value(metrics, "likes"),
        "reposts": first_value(source, "reposts", "retweets", "retweet_count")
        or first_value(metrics, "retweets"),
        "replies": first_value(source, "replies", "reply_count") or first_value(metrics, "replies"),
        "views": first_value(source, "views", "view_count") or first_value(metrics, "views"),
    }
    nested_replies = source.get("replies")
    if isinstance(nested_replies, list):
        result["reply_items"] = [
            _normalize_tweet(reply) for reply in nested_replies if isinstance(reply, dict)
        ]
    return result


def _run_cli(operation: str, params: dict[str, Any]) -> Any:
    auth_token = require_secret("REACTOR_TWITTER_AUTH_TOKEN")
    ct0 = require_secret("REACTOR_TWITTER_CT0")
    limit = bounded_limit(params.get("limit"), maximum=50)
    query = bounded_text(params.get("query"), 500)
    target = bounded_text(params.get("tweet_id") or params.get("target"), 2_000)
    username = bounded_text(params.get("username"), 200).lstrip("@")

    if operation == "search":
        if not query:
            raise SocialPlatformError("INVALID_INPUT", "query is required for Twitter search")
        args = ["search", query, "--json", "--max", str(limit)]
    elif operation in {"tweet", "thread"}:
        if not target:
            raise SocialPlatformError("INVALID_INPUT", "tweet_id is required")
        args = ["tweet", target, "--json"]
    elif operation == "article":
        if not target:
            raise SocialPlatformError("INVALID_INPUT", "tweet_id is required for article")
        args = ["article", target, "--json"]
    elif operation == "feed":
        args = ["feed", "--json", "--max", str(limit)]
    elif operation in {"timeline", "user_posts"}:
        if not username:
            raise SocialPlatformError("INVALID_INPUT", "username is required for timeline")
        args = ["user-posts", username, "--json", "--max", str(limit)]
    else:
        raise SocialPlatformError(
            "INVALID_INPUT",
            "unsupported Twitter operation; use search, tweet, thread, article, feed, timeline, or user_posts",
        )

    command = [os.getenv("REACTOR_TWITTER_COMMAND", "twitter"), *args]
    output = command_output(
        command,
        env=_command_env(auth_token, ct0),
        secrets=(auth_token, ct0),
    )
    return unwrap_cli_payload(parse_structured_output(output))


def execute(params: dict[str, Any]) -> dict[str, Any]:
    operation = bounded_text(params.get("operation"), 40).casefold()
    payload = _run_cli(operation, params)
    items = [_normalize_tweet(item) for item in _tweet_items(payload)]
    return success_result(
        "twitter",
        operation,
        items,
        query=bounded_text(
            params.get("query") or params.get("tweet_id") or params.get("username"), 500
        ),
    )
