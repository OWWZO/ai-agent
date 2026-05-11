package org.wwz.ai.domain.agent.reactor.util;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
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
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ESUtil {
    private static final Map<String, Boolean> indexExistMap = new ConcurrentHashMap<>();
    private static final int DEFAULT_BATCH_SIZE = 1000;

    public static RestHighLevelClient buildRestClient(String esClusterHost, String esClusterUser, String esClusterPassword, int timeout) {
        return buildRestClient(esClusterHost, esClusterUser, esClusterPassword, timeout, "http");
    }

    public static RestHighLevelClient buildRestClient(String esClusterHost, String esClusterUser, String esClusterPassword, int timeout, String scheme) {
        return buildRestClient(esClusterHost, esClusterUser, esClusterPassword, null, timeout, scheme);
    }

    public static RestHighLevelClient buildRestClient(String esClusterHost, String esClusterUser, String esClusterPassword, String esClusterApiKey, int timeout, String scheme) {
        String normalizedHost = StringUtils.trimToNull(esClusterHost);
        String normalizedScheme = StringUtils.trimToNull(scheme);
        if (normalizedHost == null) {
            throw new IllegalArgumentException("esClusterHost is blank");
        }
        if (normalizedScheme == null) {
            throw new IllegalArgumentException("es scheme is blank");
        }
        String[] split = normalizedHost.split("[,;]");
        HttpHost[] httpHostArray = new HttpHost[split.length];
        String pathPrefix = null;
        for (int i = 0; i < split.length; i++) {
            EsEndpoint endpoint = parseEndpoint(split[i], normalizedScheme);
            httpHostArray[i] = endpoint.toHttpHost();
            if (StringUtils.isBlank(pathPrefix) && StringUtils.isNotBlank(endpoint.getPathPrefix())) {
                pathPrefix = endpoint.getPathPrefix();
            }
        }
        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(HTTP.TARGET_HOST, httpHostArray[0].getHostName()));
        headers.add(new BasicHeader(HTTP.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString()));
        String authorizationHeaderValue = resolveAuthorizationHeaderValue(esClusterUser, esClusterPassword, esClusterApiKey);
        if (StringUtils.isNotBlank(authorizationHeaderValue)) {
            headers.add(new BasicHeader("Authorization", authorizationHeaderValue));
        }
        RestClientBuilder restClientBuilder = RestClient.builder(httpHostArray).setRequestConfigCallback(
                builder -> builder
                        .setConnectTimeout(timeout)
                        .setSocketTimeout(timeout)
                        .setConnectionRequestTimeout(timeout)
        ).setDefaultHeaders(headers.toArray(new Header[0]));
        if (StringUtils.isNotBlank(pathPrefix)) {
            restClientBuilder.setPathPrefix(pathPrefix);
        }
        return new RestHighLevelClient(restClientBuilder);
    }

    public static String resolveAuthorizationHeaderValue(String username, String passwd, String apiKey) {
        if (StringUtils.isNotBlank(apiKey)) {
            return "ApiKey " + apiKey.trim();
        }
        if (StringUtils.isNotBlank(username)) {
            return basicAuthHeaderValue(username, passwd);
        }
        return null;
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

    private static EsEndpoint parseEndpoint(String rawEndpoint, String defaultScheme) {
        String candidate = StringUtils.trimToEmpty(rawEndpoint);
        if (StringUtils.isBlank(candidate)) {
            throw new IllegalArgumentException("esClusterHost contains blank endpoint");
        }
        URI uri = URI.create(candidate.contains("://") ? candidate : defaultScheme + "://" + candidate);
        String scheme = StringUtils.defaultIfBlank(uri.getScheme(), defaultScheme);
        String host = uri.getHost();
        int port = uri.getPort();
        if (host == null) {
            throw new IllegalArgumentException("invalid es endpoint: " + rawEndpoint);
        }
        if (port < 0) {
            port = "https".equalsIgnoreCase(scheme) ? 443 : 9200;
        }
        return new EsEndpoint(host, port, scheme, normalizePathPrefix(uri.getPath()));
    }

    private static String normalizePathPrefix(String path) {
        if (StringUtils.isBlank(path) || "/".equals(path)) {
            return null;
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        return StringUtils.removeEnd(normalized, "/");
    }

    private static class EsEndpoint {
        private final String host;
        private final int port;
        private final String scheme;
        private final String pathPrefix;

        private EsEndpoint(String host, int port, String scheme, String pathPrefix) {
            this.host = host;
            this.port = port;
            this.scheme = scheme;
            this.pathPrefix = pathPrefix;
        }

        public HttpHost toHttpHost() {
            return new HttpHost(host, port, scheme);
        }

        public String getPathPrefix() {
            return pathPrefix;
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
        String sanitizedBody = sanitizeIndexDefinition(body);
        try {
            return performIndexManagementRequest(client, "PUT", index, sanitizedBody);
        } catch (ResponseException e) {
            if (shouldFallbackToStandardAnalyzer(e, sanitizedBody)) {
                String fallbackBody = fallbackToStandardAnalyzer(sanitizedBody);
                log.warn("ES 索引 {} 不支持 ik_max_word，回退为 standard analyzer", index);
                try {
                    return performIndexManagementRequest(client, "PUT", index, fallbackBody);
                } catch (Exception retryException) {
                    log.error("createIndex-{} fallback failed", index, retryException);
                    return false;
                }
            }
            log.error("createIndex-{}", index, e);
            return false;
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

            Map<String, Object> body = new HashMap<>();
            body.put("settings", settings);
            body.put("mappings", mappings);
            if (aliasName != null && !aliasName.isEmpty()) {
                Map<String, Object> aliases = new HashMap<>();
                aliases.put(aliasName, Collections.emptyMap());
                body.put("aliases", aliases);
            }
            return createIndex(client, indexName, JSON.toJSONString(body));
        } catch (Exception e) {
            log.error("创建es索引失败，index:{}", indexName, e);
            return false;
        }
    }

    public static boolean deleteIndex(RestHighLevelClient client, String indexName) {
        try {
            return performIndexManagementRequest(client, "DELETE", indexName, null);
        } catch (IOException e) {
            log.error("deleteIndex error-{}", indexName, e);
            return false;
        }
    }

    private static boolean performIndexManagementRequest(RestHighLevelClient client, String method, String indexName, String body) throws IOException {
        Request request = new Request(method, "/" + indexName);
        if (StringUtils.isNotBlank(body)) {
            request.setJsonEntity(body);
        }
        Response response = client.getLowLevelClient().performRequest(request);
        int statusCode = response.getStatusLine().getStatusCode();
        return statusCode >= 200 && statusCode < 300;
    }

    private static String sanitizeIndexDefinition(String body) {
        if (StringUtils.isBlank(body)) {
            return body;
        }
        JSONObject definition = JSON.parseObject(body);
        JSONObject settings = definition.getJSONObject("settings");
        if (settings == null) {
            return definition.toJSONString();
        }
        JSONObject indexSettings = settings.getJSONObject("index");
        if (indexSettings != null) {
            indexSettings.remove("number_of_shards");
            indexSettings.remove("number_of_replicas");
            if (indexSettings.isEmpty()) {
                settings.remove("index");
            }
        }
        settings.remove("number_of_shards");
        settings.remove("number_of_replicas");
        if (settings.isEmpty()) {
            definition.remove("settings");
        }
        return definition.toJSONString();
    }

    private static boolean shouldFallbackToStandardAnalyzer(ResponseException exception, String body) {
        if (StringUtils.isBlank(body) || !body.contains("ik_max_word")) {
            return false;
        }
        return exception.getMessage() != null && exception.getMessage().contains("analyzer [ik_max_word] has not been configured");
    }

    private static String fallbackToStandardAnalyzer(String body) {
        return body.replace("\"ik_max_word\"", "\"standard\"");
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
        Request request = new Request("POST", "/_bulk");
        request.addParameter("timeout", "1m");
        request.setEntity(new StringEntity(buildBulkRequestBody(index, dataList, idKey), ContentType.create("application/x-ndjson", StandardCharsets.UTF_8)));

        // Elastic Cloud/serverless 返回的 bulk 响应与旧版 HighLevelClient 存在兼容问题，
        // 这里统一走低层 REST 接口并自行解析结果，避免“写成功但响应解析失败”的误报。
        Response response = client.getLowLevelClient().performRequest(request);
        String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        JSONObject responseJson = JSON.parseObject(responseBody);
        if (Boolean.TRUE.equals(responseJson.getBoolean("errors"))) {
            log.error("批量写入 ES 失败，index:{}, detail:{}", index, extractBulkFailureMessage(responseJson));
            return false;
        }
        log.info("成功写入 ES {} {}条数据", index, dataList.size());
        return true;
    }

    private static String buildBulkRequestBody(String index, List<Map<String, Object>> dataList, String idKey) {
        StringBuilder builder = new StringBuilder(dataList.size() * 128);
        for (Map<String, Object> data : dataList) {
            Map<String, Object> action = new LinkedHashMap<>();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("_index", index);
            Object idValue = data.get(idKey);
            if (idValue != null && StringUtils.isNotBlank(String.valueOf(idValue))) {
                metadata.put("_id", String.valueOf(idValue));
            }
            action.put("index", metadata);
            builder.append(JSON.toJSONString(action)).append('\n');
            builder.append(JSON.toJSONString(data)).append('\n');
        }
        return builder.toString();
    }

    private static String extractBulkFailureMessage(JSONObject responseJson) {
        List<String> failures = new ArrayList<>();
        List<Object> items = responseJson.getJSONArray("items");
        if (items == null) {
            return "bulk response missing items";
        }
        for (Object item : items) {
            if (!(item instanceof JSONObject itemJson)) {
                continue;
            }
            JSONObject indexResult = itemJson.getJSONObject("index");
            if (indexResult == null || !indexResult.containsKey("error")) {
                continue;
            }
            JSONObject error = indexResult.getJSONObject("error");
            String reason = error == null ? indexResult.getString("error") : error.getString("reason");
            failures.add(String.format("id=%s,status=%s,reason=%s",
                    indexResult.getString("_id"),
                    indexResult.getInteger("status"),
                    StringUtils.defaultIfBlank(reason, "unknown")));
            if (failures.size() >= 5) {
                break;
            }
        }
        return CollectionUtils.isEmpty(failures) ? "bulk response contains errors" : String.join("; ", failures);
    }
}

