package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.memory.ltm.LtmManager;
import org.wwz.ai.domain.agent.memory.ltm.MemoryProvider;

public class LtmManagerProviderGuardTest {

    @Test
    public void rejectsSecondExternalProvider() {
        LtmManager manager = new LtmManager(100);
        Assert.assertTrue(manager.addProvider(new StubProvider("builtin", false)));
        Assert.assertTrue(manager.addProvider(new StubProvider("holographic", true)));
        Assert.assertFalse(manager.addProvider(new StubProvider("openviking", true)));
        Assert.assertEquals(2, manager.getProviders().size());
        manager.shutdownAll();
    }

    @Test
    public void allowsMultipleNonExternal() {
        LtmManager manager = new LtmManager(100);
        Assert.assertTrue(manager.addProvider(new StubProvider("builtin", false)));
        Assert.assertTrue(manager.addProvider(new StubProvider("other-local", false)));
        Assert.assertEquals(2, manager.getProviders().size());
        manager.shutdownAll();
    }

    private static final class StubProvider implements MemoryProvider {
        private final String name;
        private final boolean external;

        private StubProvider(String name, boolean external) {
            this.name = name;
            this.external = external;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isExternal() {
            return external;
        }
    }
}
