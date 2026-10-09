package org.wwz.ai.application.auth.command;

public record AuthRegisterCommand(String loginName, String password, String nickname) {
}
