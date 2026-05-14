# -*- coding: utf-8 -*-
import tempfile
import unittest
from datetime import datetime

from sqlalchemy import create_engine

from reactor_tool.tool.mrag.storage.kb_doc_store_sqlite_impl import KBDocSQLite
from reactor_tool.tool.mrag.storage.models.kb_doc_model import (
    CANONICAL_FULL_TEXT_CHUNK_TYPE,
    KBDocModel,
)


class KBDocSQLiteTest(unittest.TestCase):

    def test_should_upsert_query_and_delete_canonical_full_text(self):
        with tempfile.TemporaryDirectory(prefix="mrag-kb-doc-store-") as temp_dir:
            db_path = tempfile.mktemp(suffix=".db", dir=temp_dir)
            engine = create_engine(f"sqlite:///{db_path}")
            store = KBDocSQLite(engine)
            now = datetime(2026, 5, 14, 10, 0, 0).isoformat()

            store.upsert_canonical_doc(
                KBDocModel(
                    kb_id="kb-1",
                    doc_id="file-1#canonical",
                    text="# 第一版正文",
                    chunk_type=CANONICAL_FULL_TEXT_CHUNK_TYPE,
                    file_id="file-1",
                    title="demo.pdf",
                    file_url="http://127.0.0.1:1601/download/req/demo.pdf",
                    parent_id="file-1",
                    deleted=0,
                    create_time=now,
                    modify_time=now,
                    creator=None,
                    modifier=None,
                )
            )
            store.upsert_canonical_doc(
                KBDocModel(
                    kb_id="kb-1",
                    doc_id="file-1#canonical",
                    text="# 第二版正文",
                    chunk_type=CANONICAL_FULL_TEXT_CHUNK_TYPE,
                    file_id="file-1",
                    title="demo.pdf",
                    file_url="http://127.0.0.1:1601/download/req/demo.pdf",
                    parent_id="file-1",
                    deleted=0,
                    create_time=now,
                    modify_time=now,
                    creator=None,
                    modifier=None,
                )
            )

            persisted_doc = store.get_canonical_doc("kb-1", "file-1")
            self.assertIsNotNone(persisted_doc)
            self.assertEqual("# 第二版正文", persisted_doc.text)

            deleted_count = store.delete_by_file_ids("kb-1", ["file-1"])
            self.assertEqual(1, deleted_count)
            self.assertIsNone(store.get_canonical_doc("kb-1", "file-1"))

            store.upsert_canonical_doc(
                KBDocModel(
                    kb_id="kb-2",
                    doc_id="file-2#canonical",
                    text="# 另一篇正文",
                    chunk_type=CANONICAL_FULL_TEXT_CHUNK_TYPE,
                    file_id="file-2",
                    title="demo-2.pdf",
                    file_url="http://127.0.0.1:1601/download/req/demo-2.pdf",
                    parent_id="file-2",
                    deleted=0,
                    create_time=now,
                    modify_time=now,
                    creator=None,
                    modifier=None,
                )
            )
            deleted_kb_count = store.delete_by_kb_id("kb-2")
            self.assertEqual(1, deleted_kb_count)
            self.assertIsNone(store.get_canonical_doc("kb-2", "file-2"))
            engine.dispose()


if __name__ == "__main__":
    unittest.main()
