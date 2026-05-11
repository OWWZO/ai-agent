package org.wwz.ai.test.domain;

import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.application.agent.dataquery.DataAgentApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.wwz.ai.application.agent.dataquery.IDataAgentApplicationService;
import org.wwz.ai.application.agent.query.GptQueryApplicationService;
import org.wwz.ai.application.agent.query.IGptQueryApplicationService;
import org.wwz.ai.application.agent.visitor.ConversationSessionOwnershipApplicationService;
import org.wwz.ai.domain.agent.reactor.data.QueryResult;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnEsRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.ColumnVectorRecallReq;
import org.wwz.ai.domain.agent.reactor.data.dto.NL2SQLReq;
import org.wwz.ai.domain.agent.reactor.model.req.DataAgentChatReq;
import org.wwz.ai.domain.agent.reactor.model.req.GptQueryReq;
import org.wwz.ai.trigger.http.AiAgentController;
import org.wwz.ai.trigger.http.admin.AiClientRagOrderAdminController;
import org.wwz.ai.trigger.http.agent.AgentRoleLibraryController;
import org.wwz.ai.trigger.http.dataagent.DataAgentController;
import org.wwz.ai.trigger.http.reactor.ReactorController;
import org.wwz.ai.types.agent.config.AgentExecutorProperties;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

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
    public void shouldInjectCaseOwnedServicesIntoAgentEntryControllers() {
        List<String> aiAgentFieldTypes = Arrays.stream(AiAgentController.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.toList());
        List<String> reactorFieldTypes = Arrays.stream(ReactorController.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.toList());
        List<String> dataAgentFieldTypes = Arrays.stream(DataAgentController.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.toList());
        List<String> gptQueryApplicationFieldTypes = Arrays.stream(GptQueryApplicationService.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.toList());
        List<String> dataAgentApplicationFieldTypes = Arrays.stream(DataAgentApplicationService.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.toList());
        List<String> roleLibraryFieldTypes = Arrays.stream(AgentRoleLibraryController.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.toList());
        List<String> ragAdminFieldTypes = Arrays.stream(AiClientRagOrderAdminController.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .collect(Collectors.toList());

        Assert.assertTrue(aiAgentFieldTypes.contains("org.wwz.ai.application.agent.dispatch.IAgentDispatchService"));
        Assert.assertTrue(aiAgentFieldTypes.contains("org.wwz.ai.application.agent.armory.IArmoryService"));
        Assert.assertTrue(aiAgentFieldTypes.contains("org.wwz.ai.application.agent.query.IGptQueryApplicationService"));
        Assert.assertFalse(aiAgentFieldTypes.contains("org.wwz.ai.domain.agent.service.IAgentDispatchService"));
        Assert.assertFalse(aiAgentFieldTypes.contains("org.wwz.ai.domain.agent.service.IArmoryService"));
        Assert.assertFalse(aiAgentFieldTypes.contains("org.wwz.ai.domain.agent.reactor.service.IGptProcessService"));

        Assert.assertTrue(reactorFieldTypes.contains("org.wwz.ai.application.agent.dispatch.IAgentDispatchService"));
        Assert.assertTrue(reactorFieldTypes.contains("org.wwz.ai.application.agent.query.IGptQueryApplicationService"));
        Assert.assertFalse(reactorFieldTypes.contains("org.wwz.ai.domain.agent.service.IAgentDispatchService"));
        Assert.assertFalse(reactorFieldTypes.contains("org.wwz.ai.domain.agent.reactor.service.IGptProcessService"));

        Assert.assertTrue(dataAgentFieldTypes.contains("org.wwz.ai.application.agent.dataquery.IDataAgentApplicationService"));
        Assert.assertFalse(dataAgentFieldTypes.contains("org.wwz.ai.domain.agent.reactor.service.DataAgentService"));
        Assert.assertFalse(dataAgentFieldTypes.contains("org.wwz.ai.domain.agent.rag.SchemaRecallService"));
        Assert.assertFalse(dataAgentFieldTypes.contains("org.wwz.ai.domain.agent.reactor.service.ChatModelInfoService"));

        Assert.assertTrue(gptQueryApplicationFieldTypes.contains("org.wwz.ai.domain.agent.runtime.AgentQueryService"));
        Assert.assertFalse(gptQueryApplicationFieldTypes.contains("org.wwz.ai.domain.agent.reactor.service.IGptProcessService"));
        Assert.assertFalse(gptQueryApplicationFieldTypes.contains("org.wwz.ai.domain.agent.reactor.service.IMultiAgentService"));

        Assert.assertTrue(dataAgentApplicationFieldTypes.contains("org.wwz.ai.domain.agent.rag.DataAgentQueryService"));
        Assert.assertFalse(dataAgentApplicationFieldTypes.contains("org.wwz.ai.domain.agent.reactor.service.DataAgentService"));
        Assert.assertFalse(dataAgentApplicationFieldTypes.contains("org.wwz.ai.domain.agent.reactor.service.Nl2SqlService"));
        Assert.assertFalse(dataAgentApplicationFieldTypes.contains("org.wwz.ai.domain.agent.rag.SchemaRecallService"));
        Assert.assertFalse(dataAgentApplicationFieldTypes.contains("org.wwz.ai.domain.agent.reactor.service.ChatModelInfoService"));

        Assert.assertTrue(roleLibraryFieldTypes.contains("org.wwz.ai.application.agent.role.IFixRoleQueryService"));
        Assert.assertFalse(roleLibraryFieldTypes.contains("org.wwz.ai.domain.agent.role.IFixRoleService"));

        Assert.assertFalse(ragAdminFieldTypes.contains("org.wwz.ai.application.agent.rag.IRagApplicationService"));
        Assert.assertFalse(ragAdminFieldTypes.contains("org.wwz.ai.domain.agent.rag.IRagService"));
    }

    @Test
    public void shouldKeepRepresentativeDelegationAndResponseShapes() throws Exception {
        ReactorController reactorController = new ReactorController();
        IGptQueryApplicationService gptQueryApplicationService = Mockito.mock(IGptQueryApplicationService.class);
        ReflectionTestUtils.setField(reactorController, "gptQueryApplicationService", gptQueryApplicationService);
        ReflectionTestUtils.setField(reactorController, "conversationSessionOwnershipApplicationService",
                Mockito.mock(ConversationSessionOwnershipApplicationService.class));
        ReflectionTestUtils.setField(reactorController, "agentExecutorProperties", new AgentExecutorProperties());
        ReflectionTestUtils.setField(reactorController, "dispatchExecutor", (Executor) Runnable::run);
        ReflectionTestUtils.setField(reactorController, "heartbeatScheduler", new ConcurrentTaskScheduler());

        GptQueryReq gptQueryReq = new GptQueryReq();
        Mockito.doNothing().when(gptQueryApplicationService).queryAgentStreamIncr(Mockito.eq(gptQueryReq), Mockito.any());

        Assert.assertNotNull(reactorController.queryAgentStreamIncr(gptQueryReq));
        Mockito.verify(gptQueryApplicationService).queryAgentStreamIncr(
                Mockito.eq(gptQueryReq),
                Mockito.argThat(stream -> stream != null
                        && stream.getClass().getName().equals(
                        "org.wwz.ai.trigger.http.reactor.support.SseEmitterAgentSessionStream"))
        );
        Assert.assertEquals("ok", reactorController.health().getBody());

        DataAgentController dataAgentController = new DataAgentController();
        IDataAgentApplicationService dataAgentApplicationService = Mockito.mock(IDataAgentApplicationService.class);
        ReflectionTestUtils.setField(dataAgentController, "dataAgentApplicationService", dataAgentApplicationService);

        NL2SQLReq nl2SQLReq = new NL2SQLReq();
        Mockito.when(dataAgentApplicationService.queryAllSchemaNl2SqlReq()).thenReturn(nl2SQLReq);
        Assert.assertSame(nl2SQLReq, dataAgentController.vectorRecall(new JSONObject()));

        ColumnVectorRecallReq vectorRecallReq = new ColumnVectorRecallReq();
        List<Map<String, Object>> vectorResult = List.of(Map.of("column", "user_name"));
        Mockito.when(dataAgentApplicationService.vectorRecall(vectorRecallReq)).thenReturn(vectorResult);
        Assert.assertSame(vectorResult, dataAgentController.vectorRecall(vectorRecallReq));

        ColumnEsRecallReq esRecallReq = new ColumnEsRecallReq();
        List<Map<String, Object>> esResult = List.of(Map.of("value", "杭州"));
        Mockito.when(dataAgentApplicationService.esRecall(esRecallReq)).thenReturn(esResult);
        Assert.assertSame(esResult, dataAgentController.esRecall(esRecallReq));

        DataAgentChatReq chatReq = new DataAgentChatReq();
        chatReq.setContent("查询销量");
        Mockito.doNothing().when(dataAgentApplicationService).chatQuery(Mockito.eq(chatReq), Mockito.any());
        Assert.assertNotNull(dataAgentController.chatQuery(chatReq));
        Mockito.verify(dataAgentApplicationService).chatQuery(
                Mockito.eq(chatReq),
                Mockito.argThat(stream -> stream != null
                        && stream.getClass().getName().equals(
                        "org.wwz.ai.trigger.http.reactor.support.SseEmitterAgentSessionStream"))
        );

        Object queryData = List.of("row-1");
        Mockito.when(dataAgentApplicationService.apiChatQuery(chatReq)).thenReturn((List) queryData);
        Assert.assertSame(queryData, dataAgentController.apiChatQuery(chatReq));

        Object testResult = Map.of("sql", "select 1");
        Mockito.when(dataAgentApplicationService.testQuery(chatReq)).thenReturn(testResult);
        Assert.assertSame(testResult, dataAgentController.testQuery(chatReq));

        Mockito.when(dataAgentApplicationService.getNl2SqlReq("查询销量")).thenReturn(nl2SQLReq);
        Assert.assertSame(nl2SQLReq, dataAgentController.getNl2SqlReq(chatReq));

        List<String> modelList = List.of("sales_model");
        Mockito.when(dataAgentApplicationService.queryAllModelsWithSchema()).thenReturn((List) modelList);
        Map<String, Object> allModels = dataAgentController.allModels();
        Assert.assertEquals(200, allModels.get("code"));
        Assert.assertSame(modelList, allModels.get("data"));

        QueryResult previewRows = new QueryResult();
        previewRows.setDataList(List.of(Map.of("gmv", 123)));
        Mockito.when(dataAgentApplicationService.previewData("sales_model")).thenReturn(previewRows);
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
