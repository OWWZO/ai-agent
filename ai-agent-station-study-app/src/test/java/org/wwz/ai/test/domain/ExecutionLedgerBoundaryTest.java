package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.service.impl.AgentExecutionRecorderImpl;
import org.wwz.ai.domain.agent.reactor.service.impl.ExecutionLedgerQueryServiceImpl;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * 锁定 Phase 1 之后的执行账本领域服务边界。
 */
public class ExecutionLedgerBoundaryTest {

    @Test
    public void shouldKeepLedgerServicesFreeOfDaoFields() {
        assertNoLedgerDaoFields(AgentExecutionRecorderImpl.class);
        assertNoLedgerDaoFields(ExecutionLedgerQueryServiceImpl.class);
        assertHasField(AgentExecutionRecorderImpl.class, "IExecutionLedgerWriteRepository");
        assertHasField(ExecutionLedgerQueryServiceImpl.class, "IExecutionLedgerReadRepository");
    }

    private void assertNoLedgerDaoFields(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            String packageName = field.getType().getPackageName();
            Assert.assertFalse(
                    type.getSimpleName() + " 不应继续直接持有 DAO 字段: " + field.getName(),
                    packageName.contains(".domain.agent.reactor.mapper")
                            || field.getType().getSimpleName().endsWith("Dao")
            );
        }
    }

    private void assertHasField(Class<?> type, String fieldTypeSimpleName) {
        Assert.assertTrue(
                type.getSimpleName() + " 应通过仓储端口协作: " + fieldTypeSimpleName,
                Arrays.stream(type.getDeclaredFields())
                        .map(Field::getType)
                        .map(Class::getSimpleName)
                        .anyMatch(fieldTypeSimpleName::equals)
        );
    }
}
