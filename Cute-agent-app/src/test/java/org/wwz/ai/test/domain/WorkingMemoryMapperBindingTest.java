package org.wwz.ai.test.domain;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作记忆 Mapper XML 解析回归测试，不连接数据库。
 */
public class WorkingMemoryMapperBindingTest {

    private static final String NAMESPACE = "org.wwz.ai.infrastructure.dao.cute.IWorkingMemoryMessageDao";

    @Test
    public void shouldParseWorkingMemoryMessageMapper() throws Exception {
        Configuration configuration = parse("mybatis/mapper/working_memory_message_mapper.xml");

        Assert.assertTrue(configuration.hasStatement(NAMESPACE + ".selectScrollBefore"));
        Assert.assertTrue(configuration.hasStatement(NAMESPACE + ".selectScrollAfter"));
    }

    /**
     * 固化隔离不变量：用户级记忆检索（跨会话按 userId 搜）必须同时满足
     * <ul>
     *   <li>{@code m.memory_scope = 'main'} —— 子代理写 {@code sub:{agentId}} 作用域，天然不在命中集；</li>
     *   <li>{@code s.session_kind = 'conversation'} —— 纵深兜底，子智能体隐藏会话即便残留 main 行也不外泄。</li>
     * </ul>
     * 二者任一被移除，子会话内容就可能被用户级检索搜出（见计划书 §5.5）。
     */
    @Test
    public void userScopedMemorySearchMustExcludeSubAgentSessions() throws Exception {
        Configuration configuration = parse("mybatis/mapper/working_memory_message_mapper.xml");

        Map<String, Object> params = new HashMap<>();
        params.put("userId", "user-1");
        params.put("query", "billing");
        params.put("limit", 10);
        params.put("roles", List.of("USER", "ASSISTANT"));

        for (String statement : List.of("searchFullTextByUserId", "scanByUserId")) {
            String sql = normalize(configuration.getMappedStatement(NAMESPACE + "." + statement)
                    .getBoundSql(params)
                    .getSql());
            Assert.assertTrue(statement + " 必须以 memory_scope='main' 隔离子代理作用域",
                    sql.contains("m.memory_scope = 'main'"));
            Assert.assertTrue(statement + " 必须以 session_kind='conversation' 排除子会话",
                    sql.contains("s.session_kind = 'conversation'"));
        }
    }

    private static Configuration parse(String classpathResource) throws Exception {
        ClassPathResource resource = new ClassPathResource(classpathResource);
        Assert.assertTrue("missing mapper resource: " + classpathResource, resource.exists());

        Configuration configuration = new Configuration();
        try (InputStream inputStream = resource.getInputStream()) {
            new XMLMapperBuilder(inputStream, configuration, resource.getPath(),
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String normalize(String sql) {
        return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
    }
}
