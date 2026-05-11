package org.wwz.ai.domain.agent.reactor.config.data;

public class DataAgentConstants {

    //nl2sql服务地址
    public static final String NL2SQL_SERVER_PATH = "";
    ///模型召回服务地址
    public static final String TABLE_RAG_SERVER_PATH = "";

    //qdrant存储schema的collection名称
    public static final String SCHEMA_COLLECTION_NAME = "reactor_model_schema";
    //es存储列值索引名称
    public static final String COLUMN_VALUE_ES_INDEX = "reactor_model_column_value";
    //es 列值索引mapping
    public static final String COLUMN_VALUE_ES_MAPPING = """
            {
              "aliases": {
                "reactor_model_column_value_alias": {
                }
              },
              "mappings": {
                "properties": {
                  "modelCode": {
                    "type": "keyword"
                  },
                  "columnId": {
                    "type": "keyword"
                  },
                  "value": {
                    "type": "text",
                    "analyzer": "ik_max_word",
                    "search_analyzer": "ik_max_word"
                  },
                  "valueId": {
                    "type": "keyword"
                  },
                  "createTime": {
                    "type": "date",
                    "format": "yyyy-MM-dd HH:mm:ss"
                  },
                  "columnName": {
                    "type": "keyword"
                  },
                  "columnComment": {
                    "type": "keyword"
                  },
                  "dataType": {
                    "type": "keyword"
                  },
                  "synonyms": {
                    "type": "keyword"
                  }
                }
              },
              "settings": {
                "index": {
                  "number_of_shards": "10",
                  "number_of_replicas": "2"
                }
              }
            }
            """;
}
