package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.domain.agent.reactor.data.QueryResult;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnEsRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnVectorRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.NL2SQLReq;
import org.wwz.ai.domain.agent.reactor.model.req.DataAgentChatReq;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.domain.agent.reactor.service.ChatModelInfoService;
import org.wwz.ai.domain.agent.reactor.service.DataAgentService;
import org.wwz.ai.domain.agent.reactor.service.IGptProcessService;
import org.wwz.ai.domain.agent.reactor.service.SchemaRecallService;
import org.wwz.ai.trigger.http.dataagent.DataAgentController;
import org.wwz.ai.trigger.http.reactor.ReactorController;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 锁定 legacy Reactor / DataAgent HTTP 路由和代表性委派行为。
 */
public class ReactorHttpControllerTest {

    @Test
    public void shouldExposeLegacyRouteSetFromTriggerControllers() {
        Assert.assertTrue(ReactorController.class.getPackageName().startsWith("org.wwz.ai.trigger.http"));
        Assert.assertTrue(DataAgentController.class.getPackageName().startsWith("org.wwz.ai.trigger.http"));

        Set<String> routes = new LinkedHashSet<>();
        routes.addAll(extractRoutes(ReactorController.class));
        routes.addAll(extractRoutes(DataAgentController.class));

        Assert.assertEquals(Set.of(
                "POST /1/AutoAgent",
                "REQUEST /1/web/health",
                "REQUEST /1/web/api/v1/gpt/queryAgentStreamIncr",
                "POST /data/queryModelInfo",
                "POST /data/vectorRecall",
                "POST /data/esRecall",
                "POST /data/chatQuery",
                "POST /data/apiChatQuery",
                "POST /data/testQuery",
                "POST /data/getNl2SqlReq",
                "GET /data/allModels",
                "GET /data/previewData"
        ), routes);
    }

    @Test
    public void shouldKeepRepresentativeDelegationAndResponseShapes() throws Exception {
        ReactorController reactorController = new ReactorController();
        IGptProcessService gptProcessService = Mockito.mock(IGptProcessService.class);
        ReflectionTestUtils.setField(reactorController, "gptProcessService", gptProcessService);

        GptQueryReq gptQueryReq = new GptQueryReq();
        SseEmitter expectedEmitter = new SseEmitter();
        Mockito.when(gptProcessService.queryMultiAgentIncrStream(gptQueryReq)).thenReturn(expectedEmitter);

        Assert.assertSame(expectedEmitter, reactorController.queryAgentStreamIncr(gptQueryReq));
        Assert.assertEquals("ok", reactorController.health().getBody());

        DataAgentController dataAgentController = new DataAgentController();
        DataAgentService dataAgentService = Mockito.mock(DataAgentService.class);
        SchemaRecallService schemaRecallService = Mockito.mock(SchemaRecallService.class);
        ChatModelInfoService chatModelInfoService = Mockito.mock(ChatModelInfoService.class);
        ReflectionTestUtils.setField(dataAgentController, "dataAgentService", dataAgentService);
        ReflectionTestUtils.setField(dataAgentController, "schemaRecallService", schemaRecallService);
        ReflectionTestUtils.setField(dataAgentController, "chatModelInfoService", chatModelInfoService);

        NL2SQLReq nl2SQLReq = new NL2SQLReq();
        Mockito.when(dataAgentService.queryAllSchemaNl2SqlReq()).thenReturn(nl2SQLReq);
        Assert.assertSame(nl2SQLReq, dataAgentController.vectorRecall(new JSONObject()));

        ColumnVectorRecallReq vectorRecallReq = new ColumnVectorRecallReq();
        List<Map<String, Object>> vectorResult = List.of(Map.of("column", "user_name"));
        Mockito.when(schemaRecallService.vectorRecall(vectorRecallReq)).thenReturn(vectorResult);
        Assert.assertSame(vectorResult, dataAgentController.vectorRecall(vectorRecallReq));

        ColumnEsRecallReq esRecallReq = new ColumnEsRecallReq();
        List<Map<String, Object>> esResult = List.of(Map.of("value", "杭州"));
        Mockito.when(schemaRecallService.esValueRecall(esRecallReq)).thenReturn(esResult);
        Assert.assertSame(esResult, dataAgentController.esRecall(esRecallReq));

        DataAgentChatReq chatReq = new DataAgentChatReq();
        chatReq.setContent("查询销量");
        SseEmitter chatEmitter = new SseEmitter();
        Mockito.when(dataAgentService.webChatQueryData(chatReq)).thenReturn(chatEmitter);
        Assert.assertSame(chatEmitter, dataAgentController.chatQuery(chatReq));

        Object queryData = List.of("row-1");
        Mockito.when(dataAgentService.apiChatQueryData(chatReq)).thenReturn((List) queryData);
        Assert.assertSame(queryData, dataAgentController.apiChatQuery(chatReq));

        Object testResult = Map.of("sql", "select 1");
        Mockito.when(dataAgentService.testQuery(chatReq)).thenReturn(testResult);
        Assert.assertSame(testResult, dataAgentController.testQuery(chatReq));

        Mockito.when(dataAgentService.getNl2SqlReq("查询销量")).thenReturn(nl2SQLReq);
        Assert.assertSame(nl2SQLReq, dataAgentController.getNl2SqlReq(chatReq));

        List<String> modelList = List.of("sales_model");
        Mockito.when(chatModelInfoService.queryAllModelsWithSchema()).thenReturn((List) modelList);
        Map<String, Object> allModels = dataAgentController.allModels();
        Assert.assertEquals(200, allModels.get("code"));
        Assert.assertSame(modelList, allModels.get("data"));

        QueryResult previewRows = new QueryResult();
        previewRows.setDataList(List.of(Map.of("gmv", 123)));
        Mockito.when(chatModelInfoService.previewData("sales_model")).thenReturn(previewRows);
        Map<String, Object> preview = dataAgentController.previewData("sales_model");
        Assert.assertEquals(200, preview.get("code"));
        Assert.assertSame(previewRows, preview.get("data"));
    }

    private Set<String> extractRoutes(Class<?> controllerClass) {
        String prefix = resolveFirstPath(controllerClass.getAnnotation(RequestMapping.class));
        Set<String> routes = new LinkedHashSet<>();
        for (Method method : controllerClass.getDeclaredMethods()) {
            PostMapping postMapping = method.getAnnotation(PostMapping.class);
            if (postMapping != null) {
                routes.add("POST " + normalizePath(prefix, resolveFirstPath(postMapping.value(), postMapping.path())));
                continue;
            }
            GetMapping getMapping = method.getAnnotation(GetMapping.class);
            if (getMapping != null) {
                routes.add("GET " + normalizePath(prefix, resolveFirstPath(getMapping.value(), getMapping.path())));
                continue;
            }
            RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
            if (requestMapping != null) {
                routes.add("REQUEST " + normalizePath(prefix, resolveFirstPath(requestMapping.value(), requestMapping.path())));
            }
        }
        return routes;
    }

    private String resolveFirstPath(RequestMapping requestMapping) {
        if (requestMapping == null) {
            return "";
        }
        return resolveFirstPath(requestMapping.value(), requestMapping.path());
    }

    private String resolveFirstPath(String[] value, String[] path) {
        if (value != null && value.length > 0) {
            return value[0];
        }
        if (path != null && path.length > 0) {
            return path[0];
        }
        return "";
    }

    private String normalizePath(String prefix, String path) {
        String normalizedPrefix = trimSlash(prefix == null ? "" : prefix.trim());
        String normalizedPath = trimSlash(path == null ? "" : path.trim());
        String merged;
        if (normalizedPrefix.isEmpty()) {
            merged = normalizedPath;
        } else if (normalizedPath.isEmpty()) {
            merged = normalizedPrefix;
        } else {
            merged = normalizedPrefix + "/" + normalizedPath;
        }
        return "/" + merged.replaceAll("/{2,}", "/");
    }

    private String trimSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
