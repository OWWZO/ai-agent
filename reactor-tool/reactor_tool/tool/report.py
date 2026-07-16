# -*- coding: utf-8 -*-
"""报告生成：按 file_type 分发 markdown / html / ppt 流式生成。

先下载引用文件 → 截断适配上下文 → 渲染 prompt → 流式调用 LLM。
"""
import os
from datetime import datetime
from typing import Optional, List, Literal, AsyncGenerator

from dotenv import load_dotenv
from jinja2 import Template
from loguru import logger

from reactor_tool.util.file_util import download_all_files, truncate_files, flatten_search_file
from reactor_tool.util.prompt_util import get_prompt
from reactor_tool.util.llm_util import ask_llm
from reactor_tool.util.log_util import timer
from reactor_tool.model.context import LLMModelInfoFactory

load_dotenv()


@timer(key="enter")
async def report(
        task: str,
        file_names: Optional[List[str]] = tuple(),
        model: str = "gpt-4.1",
        file_type: Literal["markdown", "html", "ppt"] = "markdown",
        template_type: str = "html",
) -> AsyncGenerator:
    """报告入口：按 file_type 分发到对应生成函数。"""
    report_factory = {
        "ppt": ppt_report,
        "markdown": markdown_report,
        "html": html_report,
    }
    model = os.getenv("REPORT_MODEL", "gpt-4.1")
    if file_type.lower() == "html":
        # html 支持 template_type（如不同版式）
        async for chunk in html_report(task, file_names, model, template_type=template_type):
            yield chunk
    else:
        async for chunk in report_factory[file_type](task, file_names, model):
            yield chunk


@timer(key="enter")
async def ppt_report(
        task: str,
        file_names: Optional[List[str]] = tuple(),
        model: str = "gpt-4.1",
        temperature: float = None,
        top_p: float = 0.6,
) -> AsyncGenerator:
    """生成 PPT 风格 HTML 报告（流式）。"""
    files = await download_all_files(file_names)
    flat_files = []

    # 优先用 md/html 素材；没有则回退全部文件
    filtered_files = [f for f in files if f["file_name"].split(".")[-1] in ["md", "html"]
                      and not f["file_name"].endswith("_搜索结果.md")] or files
    for f in filtered_files:
        # 搜索结果文件需展开为多条文档
        if f["file_name"].endswith("_search_result.txt"):
            flat_files.extend(flatten_search_file(f))
        else:
            flat_files.append(f)

    truncate_flat_files = truncate_files(flat_files, max_tokens=int(LLMModelInfoFactory.get_context_length(model) * 0.8))
    prompt = Template(get_prompt("report")["ppt_prompt"]) \
        .render(task=task, files=truncate_flat_files, date=datetime.now().strftime("%Y-%m-%d"))

    async for chunk in ask_llm(messages=prompt, model=model, stream=True,
                               temperature=temperature, top_p=top_p, only_content=True):
        yield chunk


@timer(key="enter")
async def markdown_report(
        task,
        file_names: Optional[List[str]] = tuple(),
        model: str = "gpt-4.1",
        temperature: float = 0,
        top_p: float = 0.9,
) -> AsyncGenerator:
    """生成 Markdown 报告（流式）。"""
    files = await download_all_files(file_names)
    flat_files = []
    for f in files:
        # 对于搜索文件有结构，需要重新解析
        if f["file_name"].endswith("_search_result.txt"):
            flat_files.extend(flatten_search_file(f))
        else:
            flat_files.append(f)

    truncate_flat_files = truncate_files(flat_files, max_tokens=int(LLMModelInfoFactory.get_context_length(model) * 0.8))
    prompt = Template(get_prompt("report")["markdown_prompt"]) \
        .render(task=task, files=truncate_flat_files, current_time=datetime.now().strftime("%Y-%m-%d %H:%M:%S"))

    async for chunk in ask_llm(messages=prompt, model=model, stream=True,
                               temperature=temperature, top_p=top_p, only_content=True):
        yield chunk


@timer(key="enter")
async def html_report(
        task,
        file_names: Optional[List[str]] = tuple(),
        model: str = "gpt-4.1",
        temperature: float = 0,
        top_p: float = 0.9,
        template_type: str = "html",
) -> AsyncGenerator:
    """生成 HTML 报告；template_type=fix 时用 fix_html_prompt 系统提示。"""
    files = await download_all_files(file_names)
    key_files = []  # 关键产物（如代码输出）优先占上下文
    flat_files = []
    # 搜索结果文件需展开；CI 输出单独归入 key_files
    for f in files:
        fpath = f["file_name"]
        fname = os.path.basename(fpath)
        if fname.split(".")[-1] in ["md", "txt", "csv"]:
            # CI 输出结果
            if "代码输出" in fname:
                key_files.append({"content": f["content"], "description": fname, "type": "txt", "link": fpath})
            # 搜索文件
            elif fname.endswith("_search_result.txt"):
                try:
                    flat_files.extend([{
                            "content": tf["content"],
                            "description": tf.get("title") or tf["content"][:20],
                            "type": "txt",
                            "link": tf.get("link"),
                        } for tf in flatten_search_file(f)
                    ])
                except Exception as e:
                    logger.warning(f"html_report parser file [{fpath}] error: {e}")
            # 其他文件
            else:
                flat_files.append({
                    "content": f["content"],
                    "description": fname,
                    "type": "txt",
                    "link": fpath
                })
    discount = int(LLMModelInfoFactory.get_context_length(model) * 0.8)
    key_files = truncate_files(key_files, max_tokens=discount)
    flat_files = truncate_files(flat_files, max_tokens=discount - sum([len(f["content"]) for f in key_files]))

    report_prompts = get_prompt("report")
    prompt = Template(report_prompts["html_task"]) \
        .render(task=task, key_files=key_files, files=flat_files, date=datetime.now().strftime('%Y年%m月%d日'))

    if template_type == "fix":
        async for chunk in ask_llm(
                messages=[{"role": "system", "content": report_prompts["fix_html_prompt"]},
                          {"role": "user", "content": prompt}],
                model=model, stream=True, temperature=temperature, top_p=top_p, only_content=True):
            yield chunk
    else:
        async for chunk in ask_llm(
                messages=[{"role": "system", "content": report_prompts["html_prompt"]},
                          {"role": "user", "content": prompt}],
                model=model, stream=True, temperature=temperature, top_p=top_p, only_content=True):
            yield chunk


if __name__ == "__main__":
    pass
