package org.wwz.ai.infrastructure.dataquery.elasticsearch;

public final class ElasticsearchDataQueryDefaults {
    public static final String COLUMN_VALUE_INDEX_NAME = "reactor_model_column_value";
    public static final String COLUMN_VALUE_INDEX_MAPPING = """
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

    private ElasticsearchDataQueryDefaults() {
    }
}
