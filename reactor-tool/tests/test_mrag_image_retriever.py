import unittest
from unittest.mock import patch

from PIL import Image

from reactor_tool.tool.mrag.embedding.image_embedding import QwenVLEmbedding
from reactor_tool.tool.mrag.retrieval.image_retriever import ImageRetriever
from reactor_tool.tool.mrag.retrieval.retriever import BaseRetriever


class QwenVLEmbeddingConfigTest(unittest.TestCase):

    def test_should_normalize_dashscope_multimodal_embedding_base_url(self):
        with patch.dict(
            "os.environ",
            {
                "DASHSCOPE_MULTIMODAL_EMBEDDING_BASE_URL": "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "DASHSCOPE_MULTIMODAL_EMBEDDING_MODEL_NAME": "qwen3-vl-embedding",
            },
            clear=False,
        ):
            embedding = QwenVLEmbedding()

        self.assertEqual(
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding",
            embedding.dashscope_base_url,
        )

    def test_should_send_configured_dimension_when_encoding_text(self):
        response = unittest.mock.Mock()
        response.status_code = 200
        response.json.return_value = {
            "output": {"embeddings": [{"embedding": [0.1, 0.2]}]}
        }
        with patch.dict(
            "os.environ",
            {
                "DASHSCOPE_MULTIMODAL_EMBEDDING_MODEL_NAME": "qwen3-vl-embedding",
                "IMAGE_EMBEDDING_DIMENSION": "1024",
            },
            clear=False,
        ):
            embedding = QwenVLEmbedding()
            with patch(
                "reactor_tool.tool.mrag.embedding.image_embedding.requests.post",
                return_value=response,
            ) as mocked_post:
                embedding._encode_text("OpenSpec 是什么")

        request_body = mocked_post.call_args.kwargs["json"]
        self.assertEqual(1024, request_body["parameters"]["dimension"])

    def test_should_send_configured_dimension_when_encoding_image(self):
        response = unittest.mock.Mock()
        response.status_code = 200
        response.json.return_value = {
            "output": {"embeddings": [{"embedding": [0.1, 0.2]}]}
        }
        with patch.dict(
            "os.environ",
            {
                "DASHSCOPE_MULTIMODAL_EMBEDDING_MODEL_NAME": "qwen3-vl-embedding",
                "IMAGE_EMBEDDING_DIMENSION": "1024",
            },
            clear=False,
        ):
            embedding = QwenVLEmbedding()
            with patch(
                "reactor_tool.tool.mrag.embedding.image_embedding.requests.post",
                return_value=response,
            ) as mocked_post:
                embedding._encode_image(Image.new("RGB", (1, 1)))

        request_body = mocked_post.call_args.kwargs["json"]
        self.assertEqual(1024, request_body["parameters"]["dimension"])


class ImageRetrieverGuardTest(unittest.TestCase):

    def test_should_return_empty_results_when_text_embedding_is_empty(self):
        retriever = ImageRetriever()
        retriever.embedding_model = unittest.mock.Mock()
        retriever.vector_store = unittest.mock.Mock()
        retriever.embedding_model.encode_text_batch.return_value = [[], []]

        result = retriever.text2image_search("kb-test", ["问题一", "问题二"])

        self.assertEqual([[], []], result)
        retriever.vector_store.search_image_vector.assert_not_called()

    def test_should_return_empty_page_results_when_text_embedding_is_empty(self):
        retriever = ImageRetriever()
        retriever.embedding_model = unittest.mock.Mock()
        retriever.vector_store = unittest.mock.Mock()
        retriever.embedding_model.encode_text_batch.return_value = [[]]

        result = retriever.text2page_search("kb-test", ["问题一"])

        self.assertEqual([[]], result)
        retriever.vector_store.search_page_vector.assert_not_called()

    def test_should_fallback_to_empty_results_when_image_vector_dimension_mismatch(self):
        retriever = ImageRetriever()
        retriever.embedding_model = unittest.mock.Mock()
        retriever.vector_store = unittest.mock.Mock()
        retriever.embedding_model.encode_text_batch.return_value = [[0.1, 0.2], [0.3, 0.4]]
        retriever.vector_store.search_image_vector.side_effect = ValueError(
            "Query vector size 2560 does not match embedding size 1024 for collection t_reactor_mrag_image_vectors"
        )

        result = retriever.text2image_search("kb-test", ["问题一", "问题二"])

        self.assertEqual([[], []], result)

    def test_should_fallback_to_empty_page_results_when_vector_dimension_mismatch(self):
        retriever = ImageRetriever()
        retriever.embedding_model = unittest.mock.Mock()
        retriever.vector_store = unittest.mock.Mock()
        retriever.embedding_model.encode_text_batch.return_value = [[0.1, 0.2]]
        retriever.vector_store.search_page_vector.side_effect = ValueError(
            "Query vector size 2560 does not match embedding size 1024 for collection t_reactor_mrag_page_vectors"
        )

        result = retriever.text2page_search("kb-test", ["问题一"])

        self.assertEqual([[]], result)

    def test_should_skip_image_vector_retrieval_when_mode_is_text_proxy(self):
        with patch.dict(
            "os.environ",
            {"MRAG_IMAGE_INDEX_MODE": "text_proxy"},
            clear=False,
        ):
            retriever = ImageRetriever()

        retriever.embedding_model = unittest.mock.Mock()
        retriever.vector_store = unittest.mock.Mock()

        image_result = retriever.text2image_search("kb-test", ["问题一"])
        page_result = retriever.text2page_search("kb-test", ["问题一"])

        self.assertEqual([[]], image_result)
        self.assertEqual([[]], page_result)
        retriever.embedding_model.encode_text_batch.assert_not_called()
        retriever.vector_store.search_image_vector.assert_not_called()
        retriever.vector_store.search_page_vector.assert_not_called()


class BaseRetrieverModeTest(unittest.TestCase):

    def test_should_not_submit_image_retrieval_tasks_when_mode_is_text_proxy(self):
        with patch.dict(
            "os.environ",
            {"MRAG_IMAGE_INDEX_MODE": "text_proxy"},
            clear=False,
        ):
            retriever = BaseRetriever()

        retriever._text_retriever = unittest.mock.Mock()
        retriever._image_retriever = unittest.mock.Mock()
        retriever._text_retriever.vector_search.return_value = [[{"id": "dense"}]]
        retriever._text_retriever.sparse_search.return_value = [[{"id": "sparse"}]]

        result = retriever.retrieval_by_texts("kb-test", ["问题一"])

        self.assertEqual(1, len(result))
        self.assertCountEqual(["dense", "sparse"], [item["id"] for item in result[0]])
        retriever._image_retriever.text2image_search.assert_not_called()
        retriever._image_retriever.text2page_search.assert_not_called()


if __name__ == "__main__":
    unittest.main()
