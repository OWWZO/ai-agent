package org.wwz.ai.test.domain.cursor;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.ledger.model.ConversationSessionCursor;
import org.wwz.ai.domain.agent.ledger.model.RunCursor;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 两个 keyset cursor 共用的编码框架约束。
 * <p>
 * RunCursor 与 ConversationSessionCursor 的二进制框架（Base64url + 版本头 +
 * 尾部必须读尽 + 异常一律归一为"非法 cursor"）已收敛，各自只负责字段读写。
 * 这组测试锁住框架本身的安全约束：字符集、版本、尾随数据、异常归一——
 * 这些约束 duplicated 一次就等于可以被单独绕过一次，因此必须单独看守。
 */
public class KeysetCursorCodecTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 10, 22, 9, 0, 0);

    // ---------- 字符集 ----------

    @Test
    public void encodedValueIsUrlSafeBase64() {
        Assert.assertFalse(RunCursor.validated("s", WHEN, 1L).encode().contains("+"));
        Assert.assertFalse(RunCursor.validated("s", WHEN, 1L).encode().contains("/"));
        Assert.assertFalse(ConversationSessionCursor.validated(WHEN, 1L).encode().contains("+"));
        Assert.assertFalse(ConversationSessionCursor.validated(WHEN, 1L).encode().contains("/"));
    }

    @Test
    public void rejectsNonBase64UrlAlphabet() {
        assertRejected(() -> RunCursor.decode("has+plus"));
        assertRejected(() -> RunCursor.decode("has/slash"));
        assertRejected(() -> RunCursor.decode("has space"));
        assertRejected(() -> ConversationSessionCursor.decode("has+plus"));
        assertRejected(() -> ConversationSessionCursor.decode("has space"));
    }

    @Test
    public void rejectsNullOrBlankEncodedValue() {
        assertRejected(() -> RunCursor.decode(null));
        assertRejected(() -> RunCursor.decode(""));
        assertRejected(() -> RunCursor.decode("   "));
        assertRejected(() -> ConversationSessionCursor.decode(null));
        assertRejected(() -> ConversationSessionCursor.decode("  "));
    }

    // ---------- 版本 ----------

    @Test
    public void rejectsFutureVersion() {
        byte[] payload = payload(output -> {
            output.writeInt(2);
            output.writeUTF("session-1");
            output.writeUTF(WHEN.toString());
            output.writeLong(7L);
        });
        assertRejected(() -> RunCursor.decode(encode(payload)));
    }

    // ---------- 尾随数据 ----------

    @Test
    public void rejectsTrailingGarbageForRunCursor() {
        byte[] payload = payload(output -> {
            output.writeInt(1);
            output.writeUTF("session-1");
            output.writeUTF(WHEN.toString());
            output.writeLong(7L);
        });
        byte[] withTail = new byte[payload.length + 3];
        System.arraycopy(payload, 0, withTail, 0, payload.length);
        withTail[payload.length] = 'x';

        assertRejected(() -> RunCursor.decode(encode(withTail)));
    }

    @Test
    public void rejectsTrailingGarbageForSessionCursor() {
        byte[] payload = payload(output -> {
            output.writeInt(1);
            output.writeBoolean(true);
            output.writeUTF(WHEN.toString());
            output.writeLong(7L);
        });
        byte[] withTail = new byte[payload.length + 1];
        System.arraycopy(payload, 0, withTail, 0, payload.length);

        assertRejected(() -> ConversationSessionCursor.decode(encode(withTail)));
    }

    // ---------- 截断 ----------

    @Test
    public void rejectsTruncatedPayload() {
        byte[] payload = payload(output -> {
            output.writeInt(1);
            output.writeUTF("session-1");
        });
        assertRejected(() -> RunCursor.decode(encode(payload)));

        byte[] sessionPayload = payload(output -> {
            output.writeInt(1);
            output.writeBoolean(true);
        });
        assertRejected(() -> ConversationSessionCursor.decode(encode(sessionPayload)));
    }

    // ---------- 字段校验 ----------

    @Test
    public void runCursorRejectsInvalidFields() {
        assertRejected(() -> RunCursor.validated(null, WHEN, 1L));
        assertRejected(() -> RunCursor.validated("  ", WHEN, 1L));
        assertRejected(() -> RunCursor.validated("s", null, 1L));
        assertRejected(() -> RunCursor.validated("s", WHEN, null));
        assertRejected(() -> RunCursor.validated("s", WHEN, 0L));
        assertRejected(() -> RunCursor.validated("s", WHEN, -1L));
    }

    @Test
    public void sessionCursorRejectsNonPositiveId() {
        assertRejected(() -> ConversationSessionCursor.validated(WHEN, null));
        assertRejected(() -> ConversationSessionCursor.validated(WHEN, 0L));
        assertRejected(() -> ConversationSessionCursor.validated(WHEN, -3L));
    }

    // ---------- 往返 ----------

    @Test
    public void runCursorRoundTripsThroughCodec() {
        RunCursor cursor = RunCursor.validated("session-rt", WHEN, 99L);
        Assert.assertEquals(cursor, RunCursor.decode(cursor.encode()));
    }

    @Test
    public void sessionCursorRoundTripsBothTimestampShapes() {
        Assert.assertEquals(ConversationSessionCursor.validated(WHEN, 5L),
                ConversationSessionCursor.decode(ConversationSessionCursor.validated(WHEN, 5L).encode()));
        Assert.assertEquals(ConversationSessionCursor.validated(null, 5L),
                ConversationSessionCursor.decode(ConversationSessionCursor.validated(null, 5L).encode()));
    }

    // ---------- fixtures ----------

    private static String encode(byte[] payload) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    }

    private static byte[] payload(PayloadWriter writer) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            writer.write(output);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private static void assertRejected(Runnable decode) {
        try {
            decode.run();
            Assert.fail("畸形 cursor 必须抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 期望：所有失败路径都归一为同一种异常，调用方不必分辨原因
        }
    }

    private interface PayloadWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
