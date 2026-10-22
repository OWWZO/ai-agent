package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.RunCursor;

import java.time.LocalDateTime;

/**
 * Keyset cursor encoding contract tests.
 */
public class RunCursorTest {

    @Test
    public void shouldRoundTripUrlSafeCursorFields() {
        RunCursor cursor = RunCursor.validated(
                "session-cursor-001",
                LocalDateTime.of(2026, 9, 26, 2, 30, 0),
                42L
        );

        String encoded = cursor.encode();
        RunCursor decoded = RunCursor.decode(encoded);

        Assert.assertFalse(encoded.contains("+"));
        Assert.assertFalse(encoded.contains("/"));
        Assert.assertEquals(cursor, decoded);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectMalformedCursor() {
        RunCursor.decode("not-a-valid-cursor");
    }
}
