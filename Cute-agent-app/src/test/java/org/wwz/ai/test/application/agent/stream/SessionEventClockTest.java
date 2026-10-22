package org.wwz.ai.test.application.agent.stream;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.application.agent.stream.SessionEventClock;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerReadRepository;
import org.wwz.ai.domain.agent.ledger.IExecutionLedgerWriteRepository;
import org.wwz.ai.domain.agent.ledger.entity.DialogueSession;

public class SessionEventClockTest {

    @Test
    public void nextContinuesFromPersistedWatermark() {
        IExecutionLedgerReadRepository read = Mockito.mock(IExecutionLedgerReadRepository.class);
        IExecutionLedgerWriteRepository write = Mockito.mock(IExecutionLedgerWriteRepository.class);
        Mockito.when(read.querySessionEntity("sess-1"))
                .thenReturn(DialogueSession.builder().sessionId("sess-1").eventSeq(40L).build());
        SessionEventClock clock = new SessionEventClock(read, write);

        Assert.assertEquals(41L, clock.next("sess-1"));
        Assert.assertEquals(42L, clock.next("sess-1"));
        clock.persist("sess-1", 42L);
        Mockito.verify(write).bumpSessionEventSeq("sess-1", 42L);
    }

    @Test
    public void missingSessionStartsAtOne() {
        IExecutionLedgerReadRepository read = Mockito.mock(IExecutionLedgerReadRepository.class);
        IExecutionLedgerWriteRepository write = Mockito.mock(IExecutionLedgerWriteRepository.class);
        SessionEventClock clock = new SessionEventClock(read, write);

        Assert.assertEquals(1L, clock.next("sess-new"));
    }
}
