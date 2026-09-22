package org.wwz.ai.test.domain;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

/**
 * 工作记忆 Mapper XML 解析回归测试，不连接数据库。
 */
public class WorkingMemoryMapperBindingTest {

    @Test
    public void shouldParseWorkingMemoryMessageMapper() throws Exception {
        ClassPathResource resource = new ClassPathResource("mybatis/mapper/working_memory_message_mapper.xml");
        Assert.assertTrue("missing working memory mapper resource", resource.exists());

        Configuration configuration = new Configuration();
        try (InputStream inputStream = resource.getInputStream()) {
            new XMLMapperBuilder(inputStream, configuration, resource.getPath(),
                    configuration.getSqlFragments()).parse();
        }

        String namespace = "org.wwz.ai.infrastructure.dao.reactor.IWorkingMemoryMessageDao";
        Assert.assertTrue(configuration.hasStatement(namespace + ".selectScrollBefore"));
        Assert.assertTrue(configuration.hasStatement(namespace + ".selectScrollAfter"));
    }
}
