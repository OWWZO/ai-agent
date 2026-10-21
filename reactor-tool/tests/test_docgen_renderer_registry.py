# -*- coding: utf-8 -*-
"""docgen renderer 注册表回归。

阶段 5 把两根逐字相同的四分支 if 链收敛成 ``get_renderer``，并把 markdown
从 html.py 里拆成独立模块。这里固化：
1. 每个格式都能取到 renderer，markdown 不再寄生在 html 模块；
2. md 是 markdown 的别名；
3. 未知格式显式报错（原 if 链会静默回退到 markdown）；
4. renderers 包的 __getattr__ 仍然可用。
"""
import unittest

from reactor_tool.docgen.model import DocumentSpec
from reactor_tool.docgen.renderers._common import looks_chinese
from reactor_tool.docgen.renderers.registry import (
    SUPPORTED_FORMATS,
    get_renderer,
)


class RendererRegistryTest(unittest.TestCase):
    def test_every_supported_format_resolves_to_a_renderer(self):
        for fmt in SUPPORTED_FORMATS:
            self.assertTrue(callable(get_renderer(fmt)), fmt)

    def test_format_name_is_case_insensitive(self):
        self.assertEqual(get_renderer("PDF"), get_renderer("pdf"))
        self.assertEqual(get_renderer(" Markdown "), get_renderer("markdown"))

    def test_md_is_an_alias_of_markdown(self):
        self.assertEqual(get_renderer("md"), get_renderer("markdown"))

    def test_unknown_format_raises_instead_of_silently_falling_back(self):
        """原 if 链以 render_markdown 兜底，写错格式名会静默产出 markdown。"""
        with self.assertRaises(ValueError) as ctx:
            get_renderer("epub")
        self.assertIn("epub", str(ctx.exception))

    def test_markdown_renderer_lives_in_its_own_module(self):
        module_name = get_renderer("markdown").__module__
        self.assertTrue(
            module_name.endswith("renderers.markdown"), module_name
        )
        self.assertNotIn("html", module_name)

    def test_renderers_package_still_exposes_all_five(self):
        from reactor_tool.docgen import renderers

        for name in renderers.__all__:
            self.assertTrue(callable(getattr(renderers, name)), name)

    def test_renderers_package_rejects_unknown_attribute(self):
        from reactor_tool.docgen import renderers

        with self.assertRaises(AttributeError):
            renderers.render_epub


class LooksChineseTest(unittest.TestCase):
    def test_detects_chinese_title(self):
        spec = DocumentSpec.model_validate({"title": "季度分析报告", "blocks": []})
        self.assertTrue(looks_chinese(spec))

    def test_detects_chinese_body_when_title_is_ascii(self):
        spec = DocumentSpec.model_validate(
            {"title": "Report", "blocks": [{"type": "paragraph", "text": "这是正文"}]}
        )
        self.assertTrue(looks_chinese(spec))

    def test_pure_ascii_is_not_chinese(self):
        spec = DocumentSpec.model_validate(
            {"title": "Report", "blocks": [{"type": "paragraph", "text": "hello world"}]}
        )
        self.assertFalse(looks_chinese(spec))

    def test_empty_document_is_not_chinese(self):
        spec = DocumentSpec.model_validate({"title": "", "blocks": []})
        self.assertFalse(looks_chinese(spec))


class MarkdownRenderRoundTripTest(unittest.TestCase):
    """真渲染一遍，确认拆模块没有破坏输出。"""

    def test_markdown_renderer_writes_document(self):
        import tempfile
        from pathlib import Path

        from reactor_tool.docgen.model import (
            DocumentSpec,
            HeadingBlock,
            ParagraphBlock,
        )

        spec = DocumentSpec.model_validate(
            {
                "title": "标题",
                "theme": "professional",
                "blocks": [
                    HeadingBlock(text="小节"),
                    ParagraphBlock(text="正文内容"),
                ],
            }
        )
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "doc.md"
            result = get_renderer("markdown")(spec, out)
            self.assertTrue(result["success"])
            self.assertEqual(result["format"], "markdown")
            text = out.read_text(encoding="utf-8")
            self.assertIn("# 标题", text)
            self.assertIn("## 小节", text)
            self.assertIn("正文内容", text)

    def test_markdown_checklist_renders_gfm_task_list(self):
        """``_checklist_markdown`` 是随 markdown 一起从 html.py 搬过来的，验证没搬坏。"""
        import tempfile
        from pathlib import Path

        from reactor_tool.docgen.model import (
            ChecklistBlock,
            ChecklistItem,
            DocumentSpec,
        )

        spec = DocumentSpec.model_validate(
            {
                "title": "清单",
                "theme": "professional",
                "blocks": [
                    ChecklistBlock(
                        title="发布清单",
                        items=[
                            ChecklistItem(text="代码评审", status="completed", priority="high"),
                            ChecklistItem(text="灰度上线", status="pending"),
                        ],
                    )
                ],
            }
        )
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "c.md"
            get_renderer("markdown")(spec, out)
            text = out.read_text(encoding="utf-8")
        self.assertIn("- [x]", text)
        self.assertIn("- [ ]", text)
        self.assertIn("[HIGH]", text)

    def test_pptx_renderer_accepts_deck_spec(self):
        """pptx 吃 DeckSpec 而非 DocumentSpec，签名形状一致但入参类型不同。"""
        import tempfile
        from pathlib import Path

        from reactor_tool.docgen.model import DeckSpec

        deck = DeckSpec.model_validate(
            {"title": "Q3", "slides": [{"title": "S1", "body": "要点"}]}
        )
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "deck.pptx"
            result = get_renderer("pptx")(deck, out)
            self.assertTrue(result["success"])
            self.assertEqual(result["slide_count"], 2)


if __name__ == "__main__":
    unittest.main()
