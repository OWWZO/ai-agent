package org.wwz.ai.domain.agent.genie.util;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.*;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ESUtil {
    private static final Map<String, Boolean> indexExistMap = new ConcurrentHashMap<>();
    private static final int DEFAULT_BATCH_SIZE = 1000;

    public static RestHighLevelClient buildRestClient(String esClusterHost, String esClusterUser, String esClusterPassword, int timeout) {
        String pathPrefix = "/";
        // 解析hostList配置信息
        String[] split = esClusterHost.split("[,;]");
        // 创建HttpHost数组，其中存放es主机和端口的配置信息
        HttpHost[] httpHostArray = new HttpHost[split.length];
        for (int i = 0; i < split.length; i++) {
            String item = split[i];
            String hostTemp = item.split(":")[0];
            String[] temps = hostTemp.split("/");
            String host = temps[0];
            if (temps.length > 1) {
                pathPrefix = temps[1];
            }
            httpHostArray[i] = new HttpHost(host, Integer.parseInt(item.split(":")[1]), "http");
        }
        Header[] headers = {
                new BasicHeader(HTTP.TARGET_HOST, httpHostArray[0].getHostName()),
                new BasicHeader(HTTP.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString()),
                new BasicHeader("Authorization", basicAuthHeaderValue(esClusterUser, esClusterPassword))
        };
        RestClientBuilder restClientBuilder = RestClient.builder(httpHostArray).setRequestConfigCallback(
                builder -> builder
                        .setConnectTimeout(timeout)
                        .setSocketTimeout(timeout)
                        .setConnectionRequestTimeout(timeout)
        ).setDefaultHeaders(headers).setPathPrefix(pathPrefix);
        return new RestHighLevelClient(restClientBuilder);
    }

    private static String basicAuthHeaderValue(String username, String passwd) {
        passwd = Optional.ofNullable(passwd).orElse("");
        CharBuffer chars = CharBuffer.allocate(username.length() + passwd.length() + 1);
        byte[] charBytes = null;
        try {
            chars.put(username).put(':').put(passwd.toCharArray());
            CharBuffer charBuffer = CharBuffer.wrap(chars.array());
            ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
            charBytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
            String basicToken = Base64.getEncoder().encodeToString(charBytes);
            return "Basic " + basicToken;
        } finally {
            Arrays.fill(chars.array(), (char) 0);
            if (charBytes != null) {
                Arrays.fill(charBytes, (byte) 0);
            }
        }
    }

    public static boolean isExistsIndex(RestHighLevelClient client, String index) {
        if (Optional.ofNullable(indexExistMap.get(index)).orElse(false)) {
            return true;
        }
        GetIndexRequest indexRequest = new GetIndexRequest(index);
        try {
            boolean exists = client.indices().exists(indexRequest, RequestOptions.DEFAULT);
            if (exists) {
                indexExistMap.put(index, true);
            }
            return exists;
        } catch (Exception e) {
            log.error("isExistsIndexError-{}", index, e);
            return false;
        }
    }

    public static boolean createIndex(RestHighLevelClient client, String index, String body) {
        try {
            CreateIndexRequest createIndexRequest = new CreateIndexRequest(index);
            JSONObject jsonObject = JSON.parseObject(body);
            JSONObject mappings = jsonObject.getJSONObject("mappings");
            JSONObject aliases = jsonObject.getJSONObject("aliases");
            JSONObject settings = jsonObject.getJSONObject("settings");
            createIndexRequest.mapping(mappings.toJSONString(), XContentType.JSON);
            createIndexRequest.settings(settings.toJSONString(), XContentType.JSON);
            for (Map.Entry<String, Object> entry : aliases.entrySet()) {
                String alias = entry.getKey();
                createIndexRequest.alias(new Alias(alias));
            }
            IndicesClient indices = client.indices();
            CreateIndexResponse createIndexResponse = indices.create(createIndexRequest, RequestOptions.DEFAULT);
            return createIndexResponse.isAcknowledged();
        } catch (Exception e) {
            log.error("createIndex-{}", index, e);
            return false;
        }
    }

    public static boolean createIndex(RestHighLevelClient client, String indexName, Map<String, String> columns, int numberOfShards, int numberOfReplicas, String aliasName) {
        try {
            Map<String, Object> properties = new HashMap<>();
            for (Map.Entry<String, String> entry : columns.entrySet()) {
                String columnName = entry.getKey();
                String columnType = entry.getValue();

                Map<String, Object> fieldProperties = new HashMap<>();
                switch (columnType.toUpperCase()) {
                    case "VARCHAR":
                    case "TEXT":
                    case "STRING":
                        fieldProperties.put("type", "text");
                        fieldProperties.put("analyzer", "standard");
                        // 添加 keyword 子字段
                        Map<String, Object> keywordField = new HashMap<>();
                        keywordField.put("type", "keyword");
                        keywordField.put("ignore_above", 256);

                        Map<String, Object> fields = new HashMap<>();
                        fields.put("keyword", keywordField);
                        fieldProperties.put("fields", fields);
                        break;
                    case "INT":
                        fieldProperties.put("type", "integer");
                        break;
                    case "BIGINT":
                    case "LONG":
                    case "NUMBER":
                        fieldProperties.put("type", "long");
                        break;
                    case "FLOAT":
                        fieldProperties.put("type", "float");
                        break;
                    case "DOUBLE":
                        fieldProperties.put("type", "double");
                        break;
                    case "DATE":
                    case "TIMESTAMP":
                        fieldProperties.put("type", "date");
                        fieldProperties.put("format", "yyyy-MM-dd HH:mm:ss.SSS || yyyy-MM-dd HH:mm:ss || yyyy-MM-dd");
                        break;
                    default:
                        fieldProperties.put("type", "keyword");
                        fieldProperties.put("ignore_above", 256);
                        break;
                }
                properties.put(columnName, fieldProperties);
            }

            Map<String, Object> mappings = new HashMap<>();
            mappings.put("properties", properties);

            Map<String, Object> settings = new HashMap<>();
            settings.put("number_of_shards", numberOfShards);
            settings.put("number_of_replicas", numberOfReplicas);

            CreateIndexRequest request = new CreateIndexRequest(indexName);
            request.settings(settings);
            request.mapping(mappings);

            // 添加别名
            if (aliasName != null && !aliasName.isEmpty()) {
                request.alias(new Alias(aliasName));
            }

            CreateIndexResponse createIndexResponse = client.indices().create(request, RequestOptions.DEFAULT);
            return createIndexResponse.isAcknowledged();
        } catch (Exception e) {
            log.error("创建es索引失败，index:{}", indexName, e);
            return false;
        }
    }

    public static boolean deleteIndex(RestHighLevelClient client, String indexName) {
        try {
            DeleteIndexRequest request = new DeleteIndexRequest(indexName);
            // 执行删除索引请求
            AcknowledgedResponse delete = client.indices().delete(request, RequestOptions.DEFAULT);
            // 检查删除操作是否成功
            return delete.isAcknowledged();
        } catch (IOException e) {
            log.error("deleteIndex error-{}", indexName, e);
            return false;
        }
    }

    public static boolean addAlias(RestHighLevelClient client, String indexName, String aliasName) {
        try {
            IndicesAliasesRequest request = new IndicesAliasesRequest();
            IndicesAliasesRequest.AliasActions aliasAction = new IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD)
                    .index(indexName)
                    .alias(aliasName);
            request.addAliasAction(aliasAction);
            AcknowledgedResponse indicesAliasesResponse = client.indices().updateAliases(request, RequestOptions.DEFAULT);
            return indicesAliasesResponse.isAcknowledged();
        } catch (Exception e) {
            log.error("添加es索引别名失败，index:{}, alias:{}", indexName, aliasName);
            return false;
        }
    }

    public static boolean bulkInsert(RestHighLevelClient client, String index, List<Map<String, Object>> dataList, String idKey) throws IOException {
        if (CollectionUtils.isEmpty(dataList)) {
            return false;
        }
        BulkRequest request = new BulkRequest();

        // 构建批量请求（ES7.x+版本无需指定type）
        for (Map<String, Object> data : dataList) {
            Object idValue = data.get(idKey);
            if (idValue != null && StringUtils.isNotBlank(String.valueOf(idValue))) {
                request.add(new IndexRequest(index).id(String.valueOf(idValue)).source(data));
            } else {
                request.add(new IndexRequest(index)
                        .source(data));
            }
        }

        // 执行批量操作
        BulkResponse response = client.bulk(request, RequestOptions.DEFAULT);

        // 处理响应
        if (response.hasFailures()) {
            log.error(" 批量写入失败：{}", response.buildFailureMessage());
            return false;
        } else {
            log.info(" 成功写入 ES {}  {}条数据", index, response.getItems().length);
            return true;
        }
    }
}

