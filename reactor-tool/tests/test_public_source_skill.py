"""Contract tests for the public-source runtime skill."""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path

import pytest


SCRIPT = (
    Path(__file__).resolve().parents[2]
    / "runtime"
    / "skills"
    / "agent-reach-public-sources"
    / "scripts"
    / "fetch_public.py"
)
SPEC = importlib.util.spec_from_file_location("public_source_skill", SCRIPT)
assert SPEC and SPEC.loader
public_source_skill = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(public_source_skill)


def test_rss_parser_returns_stable_identity(monkeypatch: pytest.MonkeyPatch) -> None:
    payload = b"""
    <rss><channel>
      <item>
        <title>First item</title>
        <guid>entry-1</guid>
        <link>https://example.com/one</link>
        <description><![CDATA[<p>Summary</p>]]></description>
      </item>
    </channel></rss>
    """
    monkeypatch.setattr(public_source_skill, "fetch_bytes", lambda *args, **kwargs: payload)

    result = public_source_skill.rss_read("https://example.com/feed.xml", 5)

    assert result["errors"] == []
    assert result["items"] == [
        {
            "title": "First item",
            "author": "",
            "published_at": "",
            "url": "https://example.com/one",
            "summary": "Summary",
            "id": "entry-1",
        }
    ]


def test_bilibili_api_parser_handles_result_groups() -> None:
    payload = {
        "data": {
            "result": [
                {
                    "result_type": "video",
                    "data": [
                        {
                            "bvid": "BV1abc",
                            "title": "A video",
                            "author": "creator",
                            "arcurl": "https://www.bilibili.com/video/BV1abc",
                        }
                    ],
                }
            ]
        }
    }

    items = public_source_skill._bili_api_search_items(payload)

    assert len(items) == 1
    assert items[0]["id"] == "BV1abc"
    assert items[0]["author"] == "creator"


def test_bilibili_comments_normalizes_cursor_and_replies(monkeypatch: pytest.MonkeyPatch) -> None:
    def fake_fetch_json(url: str, **kwargs):
        if "web-interface/view" in url:
            return {"code": 0, "data": {"aid": 99, "title": "Video title"}}
        if "reply/main" in url:
            return {
                "code": 0,
                "data": {
                    "cursor": {"all_count": 2, "next": 1},
                    "replies": [
                        {
                            "rpid": 123,
                            "member": {"uname": "alice", "mid": "7"},
                            "content": {"message": "hello"},
                            "like": 4,
                            "ctime": 10,
                        }
                    ],
                },
            }
        if "reply/reply" in url:
            return {
                "code": 0,
                "data": {
                    "page": {"count": 1},
                    "replies": [
                        {
                            "rpid": 456,
                            "member": {"uname": "bob", "mid": "8"},
                            "content": {"message": "reply"},
                        }
                    ],
                },
            }
        raise AssertionError(url)

    monkeypatch.setattr(public_source_skill, "fetch_json", fake_fetch_json)

    comments = public_source_skill.bilibili_comments("BV1abc", 1, 0)
    replies = public_source_skill.bilibili_replies("BV1abc", 123, 1, 1)

    assert comments["items"][0]["aid"] == 99
    assert comments["items"][0]["next"] == 1
    assert comments["items"][0]["items"][0]["id"] == 123
    assert comments["items"][0]["items"][0]["author"] == "alice"
    assert replies["items"][0]["items"][0]["root_id"] == 123
    assert replies["items"][0]["items"][0]["content"] == "reply"


def test_youtube_comments_uses_metadata_only_mode(monkeypatch: pytest.MonkeyPatch) -> None:
    command = {}
    payload = {
        "id": "video-1",
        "title": "Video title",
        "webpage_url": "https://www.youtube.com/watch?v=video-1",
        "comments": [
            {
                "id": "comment-1",
                "parent": "root-1",
                "author": "alice",
                "author_id": "channel-1",
                "text": "hello",
                "like_count": 3,
                "timestamp": 123,
                "author_is_uploader": True,
            }
        ],
    }

    def fake_command_output(args: list[str], timeout: int = 45) -> str:
        command["args"] = args
        command["timeout"] = timeout
        return json.dumps(payload)

    monkeypatch.setattr(public_source_skill, "command_output", fake_command_output)

    result = public_source_skill.youtube_comments("https://www.youtube.com/watch?v=video-1", 20)

    assert result["errors"] == []
    assert result["operation"] == "comments"
    assert result["items"][0]["items"][0]["content"] == "hello"
    assert result["items"][0]["items"][0]["root_id"] == "root-1"
    assert "--skip-download" in command["args"]
    assert "--ignore-no-formats" in command["args"]
    assert "--write-comments" in command["args"]
    assert "-f" not in command["args"]
    assert "--format" not in command["args"]


def test_xueqiu_public_routes_normalize_without_login_state(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def fake_xueqiu_json(path: str, params: dict[str, object]):
        if path == "stock.xueqiu.com/v5/stock/quote.json":
            return {
                "data": {
                    "quote": {
                        "symbol": "SH600519",
                        "name": "贵州茅台",
                        "current": 1_234.5,
                        "percent": 1.2,
                    }
                }
            }
        if path == "xueqiu.com/stock/search.json":
            return {"stocks": [{"code": "SH600519", "name": "贵州茅台", "exchange": "SH"}]}
        if path == "xueqiu.com/v4/statuses/public_timeline_by_category.json":
            return {
                "list": [
                    {
                        "data": json.dumps(
                            {
                                "id": 9,
                                "title": "Market note",
                                "text": "<p>Public post</p>",
                                "user": {"screen_name": "alice"},
                                "like_count": 2,
                                "target": "/S/9",
                            }
                        )
                    }
                ]
            }
        if path == "stock.xueqiu.com/v5/stock/hot_stock/list.json":
            return {
                "data": {
                    "items": [
                        {"code": "SH600519", "name": "贵州茅台", "current": 1_234.5, "percent": 1.2}
                    ]
                }
            }
        raise AssertionError((path, params))

    monkeypatch.setattr(public_source_skill, "_xueqiu_json", fake_xueqiu_json)

    quote = public_source_skill.xueqiu_quote("SH600519")
    search = public_source_skill.xueqiu_search("贵州茅台", 10)
    posts = public_source_skill.xueqiu_hot_posts(20)
    stocks = public_source_skill.xueqiu_hot_stocks(10)

    assert quote["source"] == "xueqiu"
    assert quote["items"][0]["current"] == 1_234.5
    assert search["items"][0]["symbol"] == "SH600519"
    assert posts["items"][0]["text"] == "Public post"
    assert posts["items"][0]["url"] == "https://xueqiu.com/S/9"
    assert stocks["items"][0]["rank"] == 1


def test_xueqiu_symbol_guard_rejects_injection() -> None:
    with pytest.raises(public_source_skill.PublicSourceError):
        public_source_skill.xueqiu_quote("SH600519&cookie=secret")


@pytest.mark.parametrize(
    "url",
    [
        "file:///tmp/feed.xml",
        "http://127.0.0.1/feed.xml",
        "https://user:password@example.com/feed.xml",
        "https://example.com:8080/feed.xml",
    ],
)
def test_public_url_guard_rejects_non_public_targets(url: str) -> None:
    with pytest.raises(public_source_skill.PublicSourceError):
        public_source_skill.validate_public_url(url)


def test_invalid_request_emits_json_error(capsys: pytest.CaptureFixture[str]) -> None:
    exit_code = public_source_skill.main(["github", "repo", "--repo", "invalid repo"])

    assert exit_code == 1
    output = json.loads(capsys.readouterr().out)
    assert output["source"] == "github"
    assert output["errors"]
