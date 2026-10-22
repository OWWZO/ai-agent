package org.wwz.ai.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * API 统一响应包装。
 *
 * <p>code 表示协议级成功/失败，info 承载面向调用方的说明，data 承载业务结果；
 * 业务层不要把异常堆栈直接塞入 data，具体错误映射由入口层统一处理。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> implements Serializable {

    private static final long serialVersionUID = 7000723935764546321L;

    /** 协议级结果码。 */
    private String code;

    /** 面向调用方的结果说明或错误摘要。 */
    private String info;

    /** 成功时的业务载荷，类型由具体服务契约声明。 */
    private T data;

}
