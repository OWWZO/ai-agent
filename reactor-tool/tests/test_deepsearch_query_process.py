import os
import sys
from types import ModuleType


llm_util_stub = ModuleType("reactor_tool.util.llm_util")
setattr(llm_util_stub, "ask_llm", None)


def resolve_openai_compat_env(prefix):
    normalized = prefix.upper()
    deepsearch_base = os.getenv(f"{normalized}_BASE_URL", "").strip()
    deepsearch_key = os.getenv(f"{normalized}_API_KEY", "").strip()
    return {
        "api_base": deepsearch_base or os.getenv("OPENAI_BASE_URL", "").strip(),
        "api_key": deepsearch_key or os.getenv("OPENAI_API_KEY", "").strip(),
    }


setattr(llm_util_stub, "resolve_openai_compat_env", resolve_openai_compat_env)
sys.modules["reactor_tool.util.llm_util"] = llm_util_stub

from reactor_tool.tool.search_component.query_process import (
    parse_report_structure,
    parse_search_plan,
)


def test_parse_report_structure_returns_title_and_content():
    chapters = parse_report_structure(
        '[{"title":"技术路线","content":"分析电池和电控"}]'
    )

    assert chapters == [{"title": "技术路线", "content": "分析电池和电控"}]


def test_parse_report_structure_supports_legacy_query_list():
    chapters = parse_report_structure("- 技术路线\n- 市场格局")

    assert chapters == [
        {"title": "技术路线", "content": "技术路线"},
        {"title": "市场格局", "content": "市场格局"},
    ]


def test_parse_search_plan_deduplicates_queries():
    plan = parse_search_plan(
        '{"search_queries":["关键词 A","关键词 A","关键词 B"],"reasoning":"覆盖章节"}'
    )

    assert plan["search_queries"] == ["关键词 A", "关键词 B"]


def test_parse_search_plan_falls_back_to_chapter_title():
    assert parse_search_plan("invalid", "章节标题") == {
        "search_queries": ["章节标题"],
        "reasoning": "",
    }
