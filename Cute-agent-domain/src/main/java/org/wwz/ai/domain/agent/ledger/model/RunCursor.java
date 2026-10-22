package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private static final String INVALID_MESSAGE = "非法 cursor";

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
        return KeysetCursorCodec.encode(VERSION, output -> {
            output.writeUTF(cursor.sessionId);
            output.writeUTF(cursor.createTime.toString());
            output.writeLong(cursor.id);
        });
    }

    public static RunCursor decode(String encoded) {
        return KeysetCursorCodec.decode(encoded, VERSION, INVALID_MESSAGE, input -> {
            String sessionId = input.readUTF();
            LocalDateTime createTime = LocalDateTime.parse(input.readUTF());
            long id = input.readLong();
            return validated(sessionId, createTime, id);
        });
    }
}
