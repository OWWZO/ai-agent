# -*- coding: utf-8 -*-
import os
import unittest
from unittest.mock import patch

from reactor_tool.tool.table_rag.es_client import ElasticsearchClient
from reactor_tool.tool.table_rag.retriever import Retriever


class ElasticsearchClientAuthTest(unittest.TestCase):

    def test_should_use_api_key_for_cloud_es(self):
        with patch("reactor_tool.tool.table_rag.es_client.Elasticsearch") as elasticsearch_cls:
            ElasticsearchClient(
                {
                    "host": "https://elastic.example.com:443",
                    "scheme": "https",
                    "api_key": "encoded-api-key",
                }
            )

        elasticsearch_cls.assert_called_once_with(
            "https://elastic.example.com:443",
            api_key="encoded-api-key",
        )

    def test_should_fallback_to_basic_auth(self):
        with patch("reactor_tool.tool.table_rag.es_client.Elasticsearch") as elasticsearch_cls:
            ElasticsearchClient(
                {
                    "host": "elastic.example.com:9200",
                    "scheme": "https",
                    "user": "elastic",
                    "password": "pwd",
                }
            )

        elasticsearch_cls.assert_called_once_with(
            "https://elastic.example.com:9200",
            http_auth=("elastic", "pwd"),
        )

    def test_retriever_should_read_data_agent_es_config_only(self):
        with patch.dict(
            os.environ,
            {
                "DATA_AGENT_ES_HOST": "https://elastic.example.com:443",
                "DATA_AGENT_ES_SCHEME": "https",
                "DATA_AGENT_ES_API_KEY": "encoded-api-key",
                "TR_ES_CONFIGS_INDEX": "reactor_model_column_value",
            },
            clear=True,
        ):
            with patch("reactor_tool.tool.table_rag.retriever.ElasticsearchClient") as elasticsearch_client_cls:
                retriever = Retriever("request-1")

        elasticsearch_client_cls.assert_called_once_with(
            {
                "host": "https://elastic.example.com:443",
                "port": None,
                "scheme": "https",
                "user": None,
                "password": None,
                "api_key": "encoded-api-key",
            }
        )
        self.assertEqual("reactor_model_column_value", retriever.es_index)

    def test_should_retry_without_analyzer_when_ik_plugin_is_missing(self):
        fake_client = unittest.mock.Mock()
        fake_client.search.side_effect = [
            RuntimeError("RequestError(400, 'search_phase_execution_exception', '[match] analyzer [ik_max_word] not found')"),
            {
                "hits": {
                    "hits": [
                        {
                            "_id": "doc-1",
                            "_source": {"value": "销售额"},
                        }
                    ]
                }
            },
        ]

        with patch("reactor_tool.tool.table_rag.es_client.Elasticsearch", return_value=fake_client):
            client = ElasticsearchClient(
                {
                    "host": "https://elastic.example.com:443",
                    "scheme": "https",
                }
            )
            result = client.search_body(
                "reactor_model_column_value",
                {
                    "query": "销售额",
                    "model_code_list": ["sales_model"],
                    "size": 5,
                },
            )

        self.assertEqual({"doc-1": {"value": "销售额"}}, result)
        self.assertEqual(2, fake_client.search.call_count)
        first_body = fake_client.search.call_args_list[0].kwargs["body"]
        second_body = fake_client.search.call_args_list[1].kwargs["body"]
        self.assertEqual("ik_max_word", first_body["query"]["bool"]["must"]["match"]["value"]["analyzer"])
        self.assertNotIn("analyzer", second_body["query"]["bool"]["must"]["match"]["value"])


if __name__ == "__main__":
    unittest.main()
