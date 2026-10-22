package org.wwz.ai.test.domain.util;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.util.ThreadUtil;

import java.lang.reflect.Field;
import java.util.concurrent.ThreadPoolExecutor;

public class ThreadUtilTest {

    @Test
    public void usesBoundedPoolAndQueue() throws Exception {
        ThreadUtil.initPool(2);
        Field field = ThreadUtil.class.getDeclaredField("executor");
        field.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(null);

        Assert.assertEquals(2, executor.getMaximumPoolSize());
        Assert.assertEquals(1000, executor.getQueue().remainingCapacity());
        Assert.assertTrue(executor.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy);
    }
}
