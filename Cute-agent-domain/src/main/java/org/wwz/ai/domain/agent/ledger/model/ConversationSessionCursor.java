package org.wwz.ai.domain.agent.ledger.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Keyset cursor for the recent-session ordering.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSessionCursor {

    private static final int VERSION = 1;
    private static final String INVALID_MESSAGE = "非法会话 cursor";

    private LocalDateTime lastActiveAt;

    private Long id;

    public static ConversationSessionCursor from(DialogueSessionView session) {
        if (session == null) {
            throw new IllegalArgumentException("session cursor source cannot be null");
        }
        return validated(session.getLastActiveAt(), session.getId());
    }

    public static ConversationSessionCursor validated(LocalDateTime lastActiveAt, Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("session cursor fields are invalid");
        }
        return new ConversationSessionCursor(lastActiveAt, id);
    }

    public String encode() {
        ConversationSessionCursor cursor = validated(lastActiveAt, id);
        return KeysetCursorCodec.encode(VERSION, output -> {
            output.writeBoolean(cursor.lastActiveAt != null);
            if (cursor.lastActiveAt != null) {
                output.writeUTF(cursor.lastActiveAt.toString());
            }
            output.writeLong(cursor.id);
        });
    }

    public static ConversationSessionCursor decode(String encoded) {
        return KeysetCursorCodec.decode(encoded, VERSION, INVALID_MESSAGE, input -> {
            LocalDateTime lastActiveAt = input.readBoolean()
                    ? LocalDateTime.parse(input.readUTF())
                    : null;
            long id = input.readLong();
            return validated(lastActiveAt, id);
        });
    }

}
