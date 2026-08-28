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
)


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
