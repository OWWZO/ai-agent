package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Session run keyset cursor.
 *
 * <p>The encoded value is opaque to callers, but always carries the session,
 * create time and primary key needed by the ordered run query.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunCursor {

    private static final int VERSION = 1;

    private String sessionId;

    private LocalDateTime createTime;

    private Long id;

    public static RunCursor from(DialogueRunView run) {
        if (run == null) {
            throw new IllegalArgumentException("run cursor source cannot be null");
        }
        return validated(run.getSessionId(), run.getCreateTime(), run.getId());
    }

    public static RunCursor validated(String sessionId, LocalDateTime createTime, Long id) {
        if (sessionId == null || sessionId.isBlank() || createTime == null || id == null || id <= 0) {
            throw new IllegalArgumentException("run cursor fields are invalid");
        }
        return new RunCursor(sessionId, createTime, id);
    }

    public String encode() {
        RunCursor cursor = validated(sessionId, createTime, id);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(VERSION);
                output.writeUTF(cursor.sessionId);
                output.writeUTF(cursor.createTime.toString());
                output.writeLong(cursor.id);
            }
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to encode run cursor", exception);
        }
    }

    public static RunCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()
                || !encoded.matches("[A-Za-z0-9_-]+")) {
            throw invalidCursor();
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded.getBytes(StandardCharsets.US_ASCII));
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (input.readInt() != VERSION) {
                    throw invalidCursor();
                }
                String sessionId = input.readUTF();
                LocalDateTime createTime = LocalDateTime.parse(input.readUTF());
                long id = input.readLong();
                if (input.available() != 0) {
                    throw invalidCursor();
                }
                return validated(sessionId, createTime, id);
            }
        } catch (IOException | RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("非法 cursor");
    }
}
