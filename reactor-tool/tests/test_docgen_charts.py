import unittest

from reactor_tool.docgen.charts import render_chart_png
from reactor_tool.docgen.model import ChartBlock, ChartSeries
from reactor_tool.docgen.themes import get_theme


class DocumentChartRenderTest(unittest.TestCase):

    def test_should_render_chinese_pie_chart_without_leagent_dependency(self):
        chart = ChartBlock(
            chart_type="pie",
            title="产品结构占比",
            categories=["A产品", "B产品", "C产品", "D产品"],
            series=[ChartSeries(name="产品占比", values=[35, 28, 22, 15])],
        )

        png = render_chart_png(chart, get_theme("professional"))

        self.assertIsNotNone(png)
        self.assertTrue(png.startswith(b"\x89PNG\r\n\x1a\n"))


if __name__ == "__main__":
    unittest.main()
