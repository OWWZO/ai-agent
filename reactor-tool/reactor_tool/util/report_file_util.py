def sanitize_report_html_content(content: str) -> str:
    """清洗报告模型输出的 HTML 包装，确保最终落盘为可直接预览的纯 HTML。"""
    normalized = (content or "").strip()
    if normalized.lower().startswith("html:"):
        normalized = normalized[5:].lstrip()
    if normalized.startswith("```html"):
        normalized = normalized[len("```html"):].lstrip()
    elif normalized.startswith("```"):
        normalized = normalized[len("```"):].lstrip()
    if normalized.endswith("```"):
        normalized = normalized[:-3].rstrip()
    return normalized
