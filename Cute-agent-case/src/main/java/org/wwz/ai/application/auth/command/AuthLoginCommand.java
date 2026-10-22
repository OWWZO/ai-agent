package org.wwz.ai.application.auth.command;

public record AuthLoginCommand(String loginName, String account, String password) {
}
