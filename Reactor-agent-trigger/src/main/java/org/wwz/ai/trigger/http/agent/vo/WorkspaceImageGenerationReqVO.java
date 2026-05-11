package org.wwz.ai.trigger.http.agent.vo;

import lombok.Data;

import java.util.List;

/**
 * 生图工作台请求 VO。
 */
@Data
public class WorkspaceImageGenerationReqVO {
    private String requestId;
    private String prompt;
    private String mode;
    private List<String> fileNames;
    private List<String> maskFileNames;
    private String fileName;
    private String fileDescription;
    private String model;
    private String size;
    private Integer n;
}
