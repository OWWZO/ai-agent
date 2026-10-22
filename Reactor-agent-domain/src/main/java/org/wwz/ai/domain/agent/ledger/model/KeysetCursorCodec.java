package org.wwz.ai.domain.agent.ledger.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * keyset cursor 的二进制编解码框架。
 * <p>
 * 各 cursor（session / run）只按自己的字段顺序读写 DataStream，框架统一负责
 * Base64url 包装、版本头、读尽校验与异常归一。这些安全约束 duplicated 一次
 * 就等于可以被单独绕过一次，因此集中在这里：
 * 字符集白名单挡住非 base64url 输入、版本头拒绝未来格式、
 * {@code available() != 0} 拒绝尾随数据、所有失败一律归一为 IllegalArgumentException。
 */
public final class KeysetCursorCodec {

    private static final String URL_SAFE_ALPHABET = "[A-Za-z0-9_-]+";

    private KeysetCursorCodec() {
    }

    /** 按字段写入顺序编码。 */
    public static String encode(int version, PayloadWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(version);
                writer.write(output);
            }
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to encode cursor", exception);
        }
    }

    /**
     * 校验字符集与版本头后按字段读取顺序解码；任何一步失败都归一为同一种异常。
     *
     * @param invalidMessage 异常文案，沿用各 cursor 原有的措辞（调用方可能据此定位）
     */
    public static <T> T decode(String encoded, int version, String invalidMessage, PayloadReader<T> reader) {
        if (encoded == null || encoded.isBlank() || !encoded.matches(URL_SAFE_ALPHABET)) {
            throw invalidCursor(invalidMessage);
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded.getBytes(StandardCharsets.US_ASCII));
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (input.readInt() != version) {
                    throw invalidCursor(invalidMessage);
                }
                T decoded = reader.read(input);
                if (input.available() != 0) {
                    throw invalidCursor(invalidMessage);
                }
                return decoded;
            }
        } catch (IOException | RuntimeException exception) {
            throw invalidCursor(invalidMessage);
        }
    }

    private static IllegalArgumentException invalidCursor(String message) {
        return new IllegalArgumentException(message);
    }

    public interface PayloadWriter {
        void write(DataOutputStream output) throws IOException;
    }

    public interface PayloadReader<T> {
        T read(DataInputStream input) throws IOException;
    }
}
