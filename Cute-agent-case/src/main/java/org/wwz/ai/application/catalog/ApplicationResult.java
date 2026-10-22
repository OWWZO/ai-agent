package org.wwz.ai.application.catalog;

import org.wwz.ai.types.enums.ResponseCode;

/**
 * 应用服务结果。HTTP Response 的包装留在 Trigger，Case 只返回稳定的应用结果。
 */
public record ApplicationResult<T>(String code, String info, T data) {

    public static <T> ApplicationResult<T> success(T data) {
        return new ApplicationResult<>(ResponseCode.SUCCESS.getCode(), ResponseCode.SUCCESS.getInfo(), data);
    }

    public static <T> ApplicationResult<T> success(T data, String info) {
        return new ApplicationResult<>(ResponseCode.SUCCESS.getCode(),
                info == null || info.isBlank() ? ResponseCode.SUCCESS.getInfo() : info, data);
    }

    public static <T> ApplicationResult<T> failure(ResponseCode responseCode, String info, T data) {
        String message = info == null || info.isBlank() ? responseCode.getInfo() : info;
        return new ApplicationResult<>(responseCode.getCode(), message, data);
    }
}
