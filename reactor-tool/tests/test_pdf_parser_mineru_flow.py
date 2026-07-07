# -*- coding: utf-8 -*-
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from reactor_tool.tool.mrag.document.parser import PdfParser


class PdfParserMinerUFlowTest(unittest.TestCase):

    def test_should_use_mineru_managed_upload_for_small_pdf(self):
        with tempfile.TemporaryDirectory(prefix="pdf-parser-") as temp_dir:
            pdf_path = Path(temp_dir) / "sample.pdf"
            pdf_path.write_bytes(b"%PDF-1.4")

            client = MagicMock()
            client.prepare_file_upload.return_value.batch_id = "batch-1"
            client.prepare_file_upload.return_value.upload_url = "https://upload.example.com/file-1"
            client.prepare_file_upload.return_value.data_id = "data-1"
            client.wait_batch_result.return_value = "https://download.example.com/result.zip"

            with patch.dict(
                    os.environ,
                    {
                        "MINERU_UPLOAD_MODE": "mineru_managed",
                        "MINERU_API_KEY": "token",
                        "MINERU_API_BASE_URL": "https://mineru.net/api/v4",
                        "SMALL_PDF_PAGE_THRESHOLD": "10",
                    },
                    clear=False,
            ):
                with patch("reactor_tool.tool.mrag.document.parser.MinerUClient", return_value=client):
                    parser = PdfParser(temp_dir, str(pdf_path))

                with patch.object(parser, "_get_pdf_page_count", return_value=1):
                    with patch("reactor_tool.tool.mrag.document.parser.oss_utils.upload_oss") as mock_upload_oss:
                        with patch("reactor_tool.tool.mrag.document.parser.download_utils.download_file"):
                            with patch("reactor_tool.tool.mrag.document.parser.zipfile.ZipFile"):
                                with patch("reactor_tool.tool.mrag.document.parser.os.listdir", return_value=["sample.md"]):
                                    with patch("reactor_tool.tool.mrag.document.parser.shutil.move"):
                                        parser.mineru_parse()

        client.prepare_file_upload.assert_called_once()
        client.upload_file.assert_called_once()
        client.wait_batch_result.assert_called_once()
        mock_upload_oss.assert_not_called()

    def test_should_keep_external_url_flow_compatible(self):
        with tempfile.TemporaryDirectory(prefix="pdf-parser-") as temp_dir:
            pdf_path = Path(temp_dir) / "sample.pdf"
            pdf_path.write_bytes(b"%PDF-1.4")

            with patch.dict(
                    os.environ,
                    {
                        "MINERU_UPLOAD_MODE": "external_url",
                        "MINERU_API_KEY": "token",
                        "MINERU_API_BASE_URL": "https://mineru.net/api/v4",
                    },
                    clear=False,
            ):
                parser = PdfParser(temp_dir, str(pdf_path))

                with patch("reactor_tool.tool.mrag.document.parser.oss_utils.upload_oss", return_value=(True, "permanent", "https://files.example.com/sample.pdf")) as mock_upload_oss:
                    with patch.object(parser, "_call_mineru_api", return_value="task-1") as mock_call_api:
                        with patch.object(parser, "_wait_for_mineru_result", return_value="https://download.example.com/result.zip") as mock_wait_result:
                            result = parser._submit_pdf_to_mineru(str(pdf_path), request_key="chunk-0")

        self.assertEqual("https://download.example.com/result.zip", result)
        mock_upload_oss.assert_called_once()
        mock_call_api.assert_called_once_with("https://files.example.com/sample.pdf")
        mock_wait_result.assert_called_once_with("task-1")

    def test_should_pass_poppler_path_to_pdf2image_when_configured(self):
        with tempfile.TemporaryDirectory(prefix="pdf-parser-") as temp_dir:
            pdf_path = Path(temp_dir) / "sample.pdf"
            pdf_path.write_bytes(b"%PDF-1.4")
            configured_poppler_dir = str(Path(temp_dir) / "poppler-bin")
            Path(configured_poppler_dir).mkdir(parents=True, exist_ok=True)
            fake_image = MagicMock()

            with patch.dict(
                    os.environ,
                    {
                        "MINERU_API_KEY": "token",
                        "POPPLER_PATH": configured_poppler_dir,
                    },
                    clear=False,
            ):
                parser = PdfParser(temp_dir, str(pdf_path))
                with patch.object(parser, "mineru_parse"):
                    with patch("pdf2image.convert_from_path", return_value=[fake_image]) as mock_convert:
                        parser.parse()

        mock_convert.assert_called_once_with(str(pdf_path), poppler_path=configured_poppler_dir)
        fake_image.save.assert_called_once()


if __name__ == "__main__":
    unittest.main()
