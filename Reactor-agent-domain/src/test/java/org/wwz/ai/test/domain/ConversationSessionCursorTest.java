package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ConversationSessionCursor;

import java.time.LocalDateTime;

public class ConversationSessionCursorTest {

    @Test
    public void shouldRoundTripCursorWithActivityTimestamp() {
        ConversationSessionCursor cursor = ConversationSessionCursor.validated(
                LocalDateTime.of(2026, 10, 7, 12, 30, 0),
                42L
        );

        String encoded = cursor.encode();

        Assert.assertFalse(encoded.contains("+"));
        Assert.assertFalse(encoded.contains("/"));
        Assert.assertEquals(cursor, ConversationSessionCursor.decode(encoded));
    }

    @Test
    public void shouldRoundTripCursorWithNullActivityTimestamp() {
        ConversationSessionCursor cursor = ConversationSessionCursor.validated(null, 42L);

        Assert.assertEquals(cursor, ConversationSessionCursor.decode(cursor.encode()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectMalformedCursor() {
        ConversationSessionCursor.decode("invalid-cursor");
    }
}
