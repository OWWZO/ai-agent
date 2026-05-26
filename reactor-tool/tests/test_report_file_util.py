import unittest

from reactor_tool.util.report_file_util import sanitize_report_html_content


class ReportFileUtilTest(unittest.TestCase):
    def test_should_strip_html_wrapper(self):
        wrapped = "Html:\n```html\n<html><body><h1>demo</h1></body></html>\n```"

        self.assertEqual(
            "<html><body><h1>demo</h1></body></html>",
            sanitize_report_html_content(wrapped),
        )

    def test_should_keep_plain_html_untouched(self):
        plain = "<html><body><h1>demo</h1></body></html>"

        self.assertEqual(plain, sanitize_report_html_content(plain))


if __name__ == "__main__":
    unittest.main()
