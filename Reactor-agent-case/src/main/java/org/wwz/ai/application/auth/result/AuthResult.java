package org.wwz.ai.application.auth.result;

import org.wwz.ai.types.enums.ResponseCode;

public record AuthResult<T>(ResponseCode code, String info, T data) {

    public static <T> AuthResult<T> success(T data) {
        return new AuthResult<>(ResponseCode.SUCCESS, ResponseCode.SUCCESS.getInfo(), data);
    }

    public static <T> AuthResult<T> failure(ResponseCode code, String info) {
        String message = info == null || info.isBlank() ? code.getInfo() : info;
        return new AuthResult<>(code, message, null);
    }
}
