package org.wwz.ai.infrastructure.dataquery.elasticsearch;

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
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elasticsearch 技术访问工具。
 *
 * <p>该类只位于 Infrastructure，负责客户端构造、索引管理、bulk 写入和 ES 响应兼容
 * 处理；Domain 通过列值 Port 使用这些能力。</p>
 */
@Slf4j
public final class ESUtil {

    private static final Map<String, Boolean> INDEX_EXIST_MAP = new ConcurrentHashMap<>();

    private ESUtil() {
    }

    public static RestHighLevelClient buildRestClient(String esClusterHost, String esClusterUser,
                                                       String esClusterPassword, int timeout) {
        return buildRestClient(esClusterHost, esClusterUser, esClusterPassword, timeout, "http");
    }

    public static RestHighLevelClient buildRestClient(String esClusterHost, String esClusterUser,
                                                       String esClusterPassword, int timeout, String scheme) {
        return buildRestClient(esClusterHost, esClusterUser, esClusterPassword, null, timeout, scheme);
    }

    public static RestHighLevelClient buildRestClient(String esClusterHost, String esClusterUser,
                                                       String esClusterPassword, String esClusterApiKey,
                                                       int timeout, String scheme) {
        String normalizedHost = StringUtils.trimToNull(esClusterHost);
        String normalizedScheme = StringUtils.trimToNull(scheme);
        if (normalizedHost == null) {
            throw new IllegalArgumentException("esClusterHost is blank");
        }
        if (normalizedScheme == null) {
            throw new IllegalArgumentException("es scheme is blank");
        }
        String[] split = normalizedHost.split("[,;]");
        HttpHost[] httpHosts = new HttpHost[split.length];
        String pathPrefix = null;
        for (int i = 0; i < split.length; i++) {
            EsEndpoint endpoint = parseEndpoint(split[i], normalizedScheme);
            httpHosts[i] = endpoint.toHttpHost();
            if (StringUtils.isBlank(pathPrefix) && StringUtils.isNotBlank(endpoint.pathPrefix)) {
                pathPrefix = endpoint.pathPrefix;
            }
        }
        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(HTTP.TARGET_HOST, httpHosts[0].getHostName()));
        headers.add(new BasicHeader(HTTP.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString()));
        String authorization = resolveAuthorizationHeaderValue(esClusterUser, esClusterPassword, esClusterApiKey);
        if (StringUtils.isNotBlank(authorization)) {
            headers.add(new BasicHeader("Authorization", authorization));
        }
        RestClientBuilder builder = RestClient.builder(httpHosts)
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout(timeout)
                        .setSocketTimeout(timeout)
                        .setConnectionRequestTimeout(timeout))
                .setDefaultHeaders(headers.toArray(new Header[0]));
        if (StringUtils.isNotBlank(pathPrefix)) {
            builder.setPathPrefix(pathPrefix);
        }
        return new RestHighLevelClient(builder);
    }

    public static String resolveAuthorizationHeaderValue(String username, String password, String apiKey) {
        if (StringUtils.isNotBlank(apiKey)) {
            return "ApiKey " + apiKey.trim();
        }
        if (StringUtils.isNotBlank(username)) {
            return basicAuthHeaderValue(username, password);
        }
        return null;
    }

    private static String basicAuthHeaderValue(String username, String password) {
        String normalizedPassword = Optional.ofNullable(password).orElse("");
        CharBuffer chars = CharBuffer.allocate(username.length() + normalizedPassword.length() + 1);
        byte[] charBytes = null;
        try {
            chars.put(username).put(':').put(normalizedPassword.toCharArray());
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars.array()));
            charBytes = Arrays.copyOfRange(bytes.array(), bytes.position(), bytes.limit());
            return "Basic " + Base64.getEncoder().encodeToString(charBytes);
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
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("invalid es endpoint: " + rawEndpoint);
        }
        int port = uri.getPort() < 0 ? ("https".equalsIgnoreCase(scheme) ? 443 : 9200) : uri.getPort();
        return new EsEndpoint(uri.getHost(), port, scheme, normalizePathPrefix(uri.getPath()));
    }

    private static String normalizePathPrefix(String path) {
        if (StringUtils.isBlank(path) || "/".equals(path)) {
            return null;
        }
        return StringUtils.removeEnd(path.startsWith("/") ? path : "/" + path, "/");
    }

    public static boolean isExistsIndex(RestHighLevelClient client, String index) {
        if (Optional.ofNullable(INDEX_EXIST_MAP.get(index)).orElse(false)) {
            return true;
        }
        try {
            boolean exists = client.indices().exists(new GetIndexRequest(index), RequestOptions.DEFAULT);
            if (exists) {
                INDEX_EXIST_MAP.put(index, true);
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
                try {
                    return performIndexManagementRequest(client, "PUT", index,
                            fallbackToStandardAnalyzer(sanitizedBody));
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

    public static boolean createIndex(RestHighLevelClient client, String indexName, Map<String, String> columns,
                                      int numberOfShards, int numberOfReplicas, String aliasName) {
        try {
            Map<String, Object> properties = new HashMap<>();
            for (Map.Entry<String, String> entry : columns.entrySet()) {
                Map<String, Object> field = new HashMap<>();
                switch (entry.getValue().toUpperCase()) {
                    case "VARCHAR", "TEXT", "STRING" -> {
                        field.put("type", "text");
                        field.put("analyzer", "standard");
                        Map<String, Object> keyword = new HashMap<>();
                        keyword.put("type", "keyword");
                        keyword.put("ignore_above", 256);
                        field.put("fields", Map.of("keyword", keyword));
                    }
                    case "INT" -> field.put("type", "integer");
                    case "BIGINT", "LONG", "NUMBER" -> field.put("type", "long");
                    case "FLOAT" -> field.put("type", "float");
                    case "DOUBLE" -> field.put("type", "double");
                    case "DATE", "TIMESTAMP" -> {
                        field.put("type", "date");
                        field.put("format", "yyyy-MM-dd HH:mm:ss.SSS || yyyy-MM-dd HH:mm:ss || yyyy-MM-dd");
                    }
                    default -> {
                        field.put("type", "keyword");
                        field.put("ignore_above", 256);
                    }
                }
                properties.put(entry.getKey(), field);
            }
            Map<String, Object> mappings = new HashMap<>();
            mappings.put("properties", properties);
            Map<String, Object> settings = new HashMap<>();
            settings.put("number_of_shards", numberOfShards);
            settings.put("number_of_replicas", numberOfReplicas);
            Map<String, Object> body = new HashMap<>();
            body.put("settings", settings);
            body.put("mappings", mappings);
            if (StringUtils.isNotBlank(aliasName)) {
                body.put("aliases", Map.of(aliasName, Collections.emptyMap()));
            }
            return createIndex(client, indexName, JSON.toJSONString(body));
        } catch (Exception e) {
            log.error("创建es索引失败，index:{}", indexName, e);
            return false;
        }
    }

    public static boolean deleteIndex(RestHighLevelClient client, String indexName) {
        try {
            boolean deleted = performIndexManagementRequest(client, "DELETE", indexName, null);
            if (deleted) {
                INDEX_EXIST_MAP.remove(indexName);
            }
            return deleted;
        } catch (IOException e) {
            log.error("deleteIndex error-{}", indexName, e);
            return false;
        }
    }

    public static boolean addAlias(RestHighLevelClient client, String indexName, String aliasName) {
        try {
            IndicesAliasesRequest request = new IndicesAliasesRequest();
            request.addAliasAction(new IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD)
                    .index(indexName).alias(aliasName));
            AcknowledgedResponse response = client.indices().updateAliases(request, RequestOptions.DEFAULT);
            return response.isAcknowledged();
        } catch (Exception e) {
            log.error("添加es索引别名失败，index:{}, alias:{}", indexName, aliasName, e);
            return false;
        }
    }

    public static boolean bulkInsert(RestHighLevelClient client, String index, List<Map<String, Object>> dataList,
                                     String idKey) throws IOException {
        if (CollectionUtils.isEmpty(dataList)) {
            return false;
        }
        Request request = new Request("POST", "/_bulk");
        request.addParameter("timeout", "1m");
        request.setEntity(new StringEntity(buildBulkRequestBody(index, dataList, idKey),
                ContentType.create("application/x-ndjson", StandardCharsets.UTF_8)));
        Response response = client.getLowLevelClient().performRequest(request);
        String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        JSONObject responseJson = JSON.parseObject(responseBody);
        if (Boolean.TRUE.equals(responseJson.getBoolean("errors"))) {
            log.error("批量写入 ES 失败，index:{}, detail:{}", index, extractBulkFailureMessage(responseJson));
            return false;
        }
        return true;
    }

    private static boolean performIndexManagementRequest(RestHighLevelClient client, String method,
                                                         String indexName, String body) throws IOException {
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
        return StringUtils.isNotBlank(body) && body.contains("ik_max_word")
                && exception.getMessage() != null
                && exception.getMessage().contains("analyzer [ik_max_word] has not been configured");
    }

    private static String fallbackToStandardAnalyzer(String body) {
        return body.replace("\"ik_max_word\"", "\"standard\"");
    }

    private static String buildBulkRequestBody(String index, List<Map<String, Object>> dataList, String idKey) {
        StringBuilder builder = new StringBuilder(dataList.size() * 128);
        for (Map<String, Object> data : dataList) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("_index", index);
            Object idValue = data.get(idKey);
            if (idValue != null && StringUtils.isNotBlank(String.valueOf(idValue))) {
                metadata.put("_id", String.valueOf(idValue));
            }
            builder.append(JSON.toJSONString(Map.of("index", metadata))).append('\n');
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
            failures.add(String.format("id=%s,status=%s,reason=%s", indexResult.getString("_id"),
                    indexResult.getInteger("status"), StringUtils.defaultIfBlank(reason, "unknown")));
            if (failures.size() >= 5) {
                break;
            }
        }
        return CollectionUtils.isEmpty(failures) ? "bulk response contains errors" : String.join("; ", failures);
    }

    private record EsEndpoint(String host, int port, String scheme, String pathPrefix) {
        private HttpHost toHttpHost() {
            return new HttpHost(host, port, scheme);
        }
    }
}
