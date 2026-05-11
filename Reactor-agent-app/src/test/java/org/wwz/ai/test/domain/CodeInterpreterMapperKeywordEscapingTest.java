package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * code_interpreter 输出表 SQL 关键字转义回归测试。
 */
public class CodeInterpreterMapperKeywordEscapingTest {

    private static final Path MAPPER_PATH = Path.of("src", "main", "resources", "mybatis", "mapper", "tool_output_code_interpreter_mapper.xml");
    private static final Path SCHEMA_PATH = Path.of("src", "main", "resources", "db", "schema.sql");

    @Test
    public void shouldQuoteExplainColumnInMapperSql() throws IOException {
        String mapperXml = Files.readString(MAPPER_PATH, StandardCharsets.UTF_8);

        // explain 是 MySQL 关键字，mapper 中的读写 SQL 必须使用反引号包裹。
        Assert.assertTrue(
                "tool_output_code_interpreter_mapper.xml 必须对 explain 列做反引号转义",
                Pattern.compile("code_output,\\s*content,\\s*code,\\s*`explain`,\\s*created_at,\\s*updated_at")
                        .matcher(mapperXml)
                        .find()
        );
    }

    @Test
    public void shouldQuoteExplainColumnInSchemaDefinition() throws IOException {
        String schemaSql = Files.readString(SCHEMA_PATH, StandardCharsets.UTF_8);

        // schema 也需要保持同样的转义，避免初始化数据库时再次踩到关键字冲突。
        Assert.assertTrue(
                "schema.sql 必须对 explain 列做反引号转义",
                Pattern.compile("`explain`\\s+MEDIUMTEXT").matcher(schemaSql).find()
        );
    }
}
