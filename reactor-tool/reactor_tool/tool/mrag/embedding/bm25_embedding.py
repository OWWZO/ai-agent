from __future__ import annotations

import os
from pathlib import Path
from typing import Optional

from fastembed import SparseTextEmbedding

from ..utils.logger_utils import logger


BM25_MODEL_NAME = "Qdrant/bm25"
DEFAULT_BM25_MODEL_PATH = Path("/opt/reactor/reactor-tool/model_cache/bm25")


def _resolve_local_bm25_model_path() -> Optional[str]:
    configured_path = os.getenv("MRAG_BM25_MODEL_PATH", "").strip()
    candidate_path = Path(configured_path) if configured_path else DEFAULT_BM25_MODEL_PATH

    if not candidate_path.exists():
        if configured_path:
            raise FileNotFoundError(f"Configured BM25 model path does not exist: {candidate_path}")
        return None

    if not candidate_path.is_dir():
        raise NotADirectoryError(f"Configured BM25 model path is not a directory: {candidate_path}")

    required_files = ("config.json", "english.txt")
    missing_files = [filename for filename in required_files if not (candidate_path / filename).is_file()]
    if missing_files:
        raise FileNotFoundError(
            f"BM25 model directory is incomplete: {candidate_path}, missing files: {', '.join(missing_files)}"
        )

    return str(candidate_path)


class BM25Embedding:
    def __init__(self):
        model_kwargs = {}

        cache_dir = os.getenv("FASTEMBED_CACHE_PATH", "").strip()
        if cache_dir:
            model_kwargs["cache_dir"] = cache_dir

        local_model_path = _resolve_local_bm25_model_path()
        if local_model_path:
            logger.info(f"Loading BM25 model from local path: {local_model_path}")
            model_kwargs["specific_model_path"] = local_model_path
        else:
            logger.info("Loading BM25 model from fastembed sources")

        self._model = SparseTextEmbedding(BM25_MODEL_NAME, **model_kwargs)

    def encode_text_batch(self, texts: list[str]) -> list[dict]:
        embeddings = self._model.embed(texts)
        return [embedding.as_object() for embedding in embeddings]


_bm25_embedding_instance: Optional[BM25Embedding] = None


def get_bm25_embedding_model() -> BM25Embedding:
    global _bm25_embedding_instance
    if _bm25_embedding_instance is not None:
        return _bm25_embedding_instance

    _bm25_embedding_instance = BM25Embedding()
    return _bm25_embedding_instance
