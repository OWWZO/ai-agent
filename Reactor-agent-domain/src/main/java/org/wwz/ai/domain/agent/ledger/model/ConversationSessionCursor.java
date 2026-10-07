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
 * Keyset cursor for the recent-session ordering.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSessionCursor {

    private static final int VERSION = 1;

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
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(VERSION);
                output.writeBoolean(cursor.lastActiveAt != null);
                if (cursor.lastActiveAt != null) {
                    output.writeUTF(cursor.lastActiveAt.toString());
                }
                output.writeLong(cursor.id);
            }
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to encode session cursor", exception);
        }
    }

    public static ConversationSessionCursor decode(String encoded) {
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
                LocalDateTime lastActiveAt = input.readBoolean()
                        ? LocalDateTime.parse(input.readUTF())
                        : null;
                long id = input.readLong();
                if (input.available() != 0) {
                    throw invalidCursor();
                }
                return validated(lastActiveAt, id);
            }
        } catch (IOException | RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("非法会话 cursor");
    }
}
