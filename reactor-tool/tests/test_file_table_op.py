# -*- coding: utf-8 -*-
import asyncio
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from reactor_tool.db.file_table_op import (
    FileDB,
    normalize_stored_file_name,
    normalize_stored_relative_path,
)
from reactor_tool.model.protocal import get_file_id


class FileNameSafetyTest(unittest.TestCase):
    def test_should_sanitize_long_windows_file_name_before_writing(self):
        raw_name = (
            "researchandcompare<>multi-agent:collaboration/architectures?"
            "decision_framework" * 12 + ".txt"
        )

        with tempfile.TemporaryDirectory(prefix="file-name-safety-") as temp_dir:
            with patch.object(FileDB, "_work_dir", temp_dir):
                saved_path = asyncio.run(FileDB.save(raw_name, "content", "session-1"))

            saved_name = Path(saved_path).name
            self.assertTrue(Path(saved_path).is_file())
            self.assertLessEqual(len(saved_name), 120)
            self.assertEqual(".txt", Path(saved_name).suffix)
            self.assertFalse(any(char in saved_name for char in '<>:"/\\|?*'))

    def test_should_reject_empty_file_name(self):
        with self.assertRaises(ValueError):
            normalize_stored_file_name("   ")

    def test_should_keep_relative_directories(self):
        self.assertEqual(
            "site/css/style.css",
            normalize_stored_relative_path("./site/css/style.css"),
        )
        self.assertNotEqual(
            get_file_id("session-1", normalize_stored_relative_path("a/style.css")),
            get_file_id("session-1", normalize_stored_relative_path("b/style.css")),
        )

    def test_should_reject_relative_path_escape(self):
        with self.assertRaises(ValueError):
            normalize_stored_relative_path("../secret.txt")


if __name__ == "__main__":
    unittest.main()
