package org.wwz.ai.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * API 层使用的稳定结果码集合。
 *
 * <p>code 是机器可判断的协议值，info 是默认人类可读说明；具体接口可以在不改变枚举含义的前提下补充上下文。</p>
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ResponseCode {

    SUCCESS("0000", "成功"),
    UN_ERROR("0001", "未知失败"),
    ILLEGAL_PARAMETER("0002", "非法参数"),
    LOGIN_FAILED("0003", "登录失败"),
    ;

    /** 对外传输的结果码。 */
    private String code;

    /** 该结果码的默认说明文本。 */
    private String info;

}
