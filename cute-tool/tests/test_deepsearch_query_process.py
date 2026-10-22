import os
from unittest.mock import patch

from cute_tool.util.llm_util import resolve_openai_compat_env
from cute_tool.tool.search_component.query_process import (
    parse_report_structure,
)


def test_resolve_openai_compat_env_prefers_prefixed_over_global():
    with patch.dict(
        os.environ,
        {
            "DEEPSEARCH_BASE_URL": "https://deepsearch.example/v1",
            "DEEPSEARCH_API_KEY": "deepsearch-key",
            "OPENAI_BASE_URL": "https://global.example/v1",
            "OPENAI_API_KEY": "global-key",
        },
        clear=False,
    ):
        config = resolve_openai_compat_env("DEEPSEARCH")

    assert config == {
        "api_base": "https://deepsearch.example/v1",
        "api_key": "deepsearch-key",
    }


def test_parse_report_structure_returns_chapter_and_search_queries():
    chapters = parse_report_structure(
        '[{"title":"技术路线","content":"分析电池和电控",'
        '"search_queries":["电池技术","电控技术","电池技术"]}]'
    )

    assert chapters == [
        {
            "title": "技术路线",
            "content": "分析电池和电控",
            "search_queries": ["电池技术", "电控技术"],
        }
    ]


def test_parse_report_structure_supports_legacy_query_list():
    chapters = parse_report_structure("- 技术路线\n- 市场格局")

    assert chapters == [
        {
            "title": "技术路线",
            "content": "技术路线",
            "search_queries": ["技术路线"],
        },
        {
            "title": "市场格局",
            "content": "市场格局",
            "search_queries": ["市场格局"],
        },
    ]
