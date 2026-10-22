package org.wwz.ai.domain.auth.exception;

/** Raised when account persistence detects a duplicate login name. */
public class LoginNameAlreadyExistsException extends RuntimeException {

    public LoginNameAlreadyExistsException() {
        super("Login name already exists");
    }
}
