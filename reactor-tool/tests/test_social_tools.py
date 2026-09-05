"""Tests for Reactor's standalone authenticated platform adapters."""

from __future__ import annotations

import json
import sys

from fastapi import FastAPI
from fastapi.testclient import TestClient

from reactor_tool.api.social import router
from reactor_tool.tool.social import reddit, twitter, xueqiu
from reactor_tool.tool.social.common import SocialPlatformError
from reactor_tool.tool.social.service import execute_social
from reactor_tool.tool.social import twitter_cli_runner
from reactor_tool.tool.social.twitter_cli_runner import _extract_search_query_id


def test_missing_credentials_return_structured_auth_error(monkeypatch):
    for name in (
        "REACTOR_TWITTER_AUTH_TOKEN",
        "REACTOR_TWITTER_CT0",
        "REACTOR_REDDIT_SESSION",
        "REACTOR_XUEQIU_COOKIE",
    ):
        monkeypatch.delenv(name, raising=False)

    result = execute_social("reddit", {"operation": "popular"})

    assert result["ok"] is False
    assert result["error"]["code"] == "AUTH_REQUIRED"
    assert "REACTOR_REDDIT_SESSION" in result["error"]["message"]


def test_twitter_passes_credentials_only_to_child_environment(monkeypatch):
    monkeypatch.setenv("REACTOR_TWITTER_AUTH_TOKEN", "auth-secret")
    monkeypatch.setenv("REACTOR_TWITTER_CT0", "ct0-secret")
    monkeypatch.setenv("REACTOR_REDDIT_SESSION", "reddit-secret")
    monkeypatch.setenv("REACTOR_XUEQIU_COOKIE", "xueqiu-secret")
    captured = {}

    def fake_command_output(command, *, env, timeout_seconds=90, secrets=()):
        captured["command"] = command
        captured["env"] = env
        captured["timeout"] = timeout_seconds
        captured["secrets"] = secrets
        return json.dumps(
            {
                "ok": True,
                "schema_version": "1",
                "data": {
                    "tweets": [
                        {
                            "id": "123",
                            "text": "hello",
                            "author": {"name": "Alice", "screenName": "alice"},
                            "metrics": {
                                "likes": 3,
                                "retweets": 2,
                                "replies": 1,
                                "views": 9,
                            },
                        }
                    ]
                },
            }
        )

    monkeypatch.setattr(twitter, "command_output", fake_command_output)

    result = twitter.execute({"operation": "search", "query": "agent", "limit": 2})

    assert result["ok"] is True
    assert result["items"][0]["id"] == "123"
    assert result["items"][0]["username"] == "alice"
    assert result["items"][0]["likes"] == 3
    assert captured["command"][:4] == [
        sys.executable,
        "-m",
        "reactor_tool.tool.social.twitter_cli_runner",
        "search",
    ]
    assert "auth-secret" not in captured["command"]
    assert "ct0-secret" not in captured["command"]
    assert captured["env"]["TWITTER_AUTH_TOKEN"] == "auth-secret"
    assert captured["env"]["TWITTER_CT0"] == "ct0-secret"
    assert captured["secrets"] == ("auth-secret", "ct0-secret")
    assert "REACTOR_REDDIT_SESSION" not in captured["env"]
    assert "REACTOR_XUEQIU_COOKIE" not in captured["env"]


def test_twitter_error_scrubs_cookie_shaped_values(monkeypatch):
    monkeypatch.setenv("REACTOR_TWITTER_AUTH_TOKEN", "auth-secret")
    monkeypatch.setenv("REACTOR_TWITTER_CT0", "ct0-secret")

    def fake_command_output(*args, **kwargs):
        raise SocialPlatformError("UPSTREAM_ERROR", "Cookie=auth-secret")

    monkeypatch.setattr(twitter, "command_output", fake_command_output)

    result = execute_social("twitter", {"operation": "feed"})

    assert result["ok"] is False
    assert "auth-secret" not in json.dumps(result)
    assert "[redacted]" in result["error"]["message"]


def test_twitter_proxy_is_forwarded_to_cli(monkeypatch):
    monkeypatch.setenv("REACTOR_TWITTER_PROXY", "socks5://127.0.0.1:7890")

    env = twitter._command_env("auth-secret", "ct0-secret")

    assert env["TWITTER_PROXY"] == "socks5://127.0.0.1:7890"


def test_twitter_runner_extracts_search_query_id_from_bundle():
    bundle = 'queryId:"hyPfJYJ_XAtDYoslQc-Rgg",operationName:"SearchTimeline"'

    assert _extract_search_query_id(bundle) == "hyPfJYJ_XAtDYoslQc-Rgg"
    assert _extract_search_query_id('operationName:"SearchTimeline"') is None


def test_twitter_runner_retries_query_id_fetch(monkeypatch):
    attempts = {"count": 0}

    def flaky_fetch(url):
        attempts["count"] += 1
        if attempts["count"] < 2:
            raise TimeoutError("temporary timeout")
        return "ok"

    monkeypatch.setattr(twitter_cli_runner, "_authenticated_url_fetch", flaky_fetch)
    monkeypatch.setattr(twitter_cli_runner.time, "sleep", lambda _: None)

    assert twitter_cli_runner._fetch_with_retry("https://x.com/home") == "ok"
    assert attempts["count"] == 2


def test_reddit_proxy_is_forwarded_to_http_client(monkeypatch):
    monkeypatch.setenv("REACTOR_REDDIT_PROXY", "http://127.0.0.1:7890")
    captured = {}

    class FakeHttpClient:
        def __init__(self, **kwargs):
            captured.update(kwargs)

        def close(self):
            pass

    monkeypatch.setattr(reddit.httpx, "Client", FakeHttpClient)

    client = reddit.RedditClient("reddit-session-secret")
    client.close()

    assert captured["proxy"] == "http://127.0.0.1:7890"


def test_reddit_read_normalizes_post_and_nested_comments(monkeypatch):
    monkeypatch.setenv("REACTOR_REDDIT_SESSION", "reddit-session-secret")
    observed = {}

    class FakeRedditClient:
        def __init__(self, session_cookie):
            observed["cookie"] = session_cookie

        def request_json(self, path, params):
            observed["path"] = path
            observed["params"] = params
            return [
                {
                    "data": {
                        "children": [
                            {
                                "data": {
                                    "id": "abc",
                                    "title": "A post",
                                    "subreddit": "python",
                                    "author": "alice",
                                    "permalink": "/r/python/comments/abc/a_post/",
                                }
                            }
                        ]
                    }
                },
                {
                    "data": {
                        "children": [
                            {
                                "data": {
                                    "id": "comment-1",
                                    "author": "bob",
                                    "body": "first",
                                    "replies": {
                                        "data": {
                                            "children": [
                                                {
                                                    "data": {
                                                        "id": "reply-1",
                                                        "author": "carol",
                                                        "body": "reply",
                                                    }
                                                }
                                            ]
                                        }
                                    },
                                }
                            }
                        ]
                    }
                },
            ]

        def close(self):
            observed["closed"] = True

    monkeypatch.setattr(reddit, "RedditClient", FakeRedditClient)

    result = reddit.execute({"operation": "read", "post_id": "abc", "limit": 10})

    assert result["ok"] is True
    assert observed["cookie"] == "reddit-session-secret"
    assert observed["path"] == "/comments/abc.json"
    assert result["items"][0]["post"]["title"] == "A post"
    assert result["items"][0]["comments"][0]["replies"][0]["body"] == "reply"
    assert observed["closed"] is True


def test_xueqiu_quote_normalizes_response(monkeypatch):
    monkeypatch.setenv("REACTOR_XUEQIU_COOKIE", "xq_a_token=secret")
    observed = {}

    class FakeXueqiuClient:
        def __init__(self, cookie):
            observed["cookie"] = cookie

        def request_json(self, url, params):
            observed["url"] = url
            observed["params"] = params
            return {
                "data": {
                    "quote": {
                        "symbol": "SH600519",
                        "name": "贵州茅台",
                        "current": 1234.5,
                        "percent": 1.2,
                    }
                }
            }

        def close(self):
            observed["closed"] = True

    monkeypatch.setattr(xueqiu, "XueqiuClient", FakeXueqiuClient)

    result = xueqiu.execute({"operation": "quote", "symbol": "SH600519"})

    assert result["ok"] is True
    assert observed["cookie"] == "xq_a_token=secret"
    assert observed["params"]["symbol"] == "SH600519"
    assert result["items"][0]["current"] == 1234.5
    assert observed["closed"] is True


def test_social_endpoints_return_request_id_and_data(monkeypatch):
    app = FastAPI()
    app.include_router(router, prefix="/v1/tool")
    client = TestClient(app)

    monkeypatch.setattr(
        "reactor_tool.tool.social.execute_social",
        lambda platform, params: {
            "ok": True,
            "platform": platform,
            "operation": params["operation"],
            "items": [],
            "warnings": [],
            "retrieved_at": "2026-01-01T00:00:00Z",
        },
    )

    response = client.post(
        "/v1/tool/reddit",
        json={"requestId": "req-social-1", "operation": "popular"},
    )

    assert response.status_code == 200
    assert response.json()["requestId"] == "req-social-1"
    assert response.json()["data"]["platform"] == "reddit"
