package org.wwz.ai.application.auth.command;

public record AuthChangePasswordCommand(String userId,
                                        String currentSessionId,
                                        String oldPassword,
                                        String newPassword) {
}
