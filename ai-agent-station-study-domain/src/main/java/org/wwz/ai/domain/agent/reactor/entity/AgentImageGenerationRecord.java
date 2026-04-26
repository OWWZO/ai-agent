package org.wwz.ai.domain.agent.reactor.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 生图工作台结果明细表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentImageGenerationRecord {

    private Long id;

    /** 单次生成请求ID */
    private String requestId;

    /** 同批次结果图序号(0-based) */
    private Integer resultIndex;

    /** 匿名设备标识 */
    private String deviceId;

    /** 认证用户ID(预留) */
    private Long userId;

    /** 生成提示词 */
    private String prompt;

    /** 生成模式 images/edits */
    private String mode;

    /** 输出尺寸 */
    private String size;

    /** 本批次生成图片总数 */
    private Integer batchCount;

    /** 参考图数量 */
    private Integer sourceImageCount;

    /** 蒙版图数量 */
    private Integer maskImageCount;

    /** 是否走兼容降级接口 */
    private Integer usedFallback;

    /** 结果图片文件名 */
    private String fileName;

    /** 文件下载地址或对象存储地址 */
    private String ossUrl;

    /** 文件预览地址 */
    private String domainUrl;

    /** 稳定下载地址 */
    private String downloadUrl;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 结果图片MIME类型 */
    private String mimeType;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 软删除 */
    private Integer deleted;
}
