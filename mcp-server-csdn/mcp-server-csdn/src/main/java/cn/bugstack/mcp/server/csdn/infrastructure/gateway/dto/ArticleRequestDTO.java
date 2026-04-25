package cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 请求csdn后端的参数
 */
@Data
public class ArticleRequestDTO {

    private String title;
    private String markdowncontent;
    private String content;
    private String readType = "public";
    private String level = "0";
    private String tags;
    private Integer status = 0;
    private String categories = "后端";
    private String type = "original";
    private String original_link = "";
    private Boolean authorized_status = true;
    private String Description;
    private String resource_url = "";
    private String not_auto_saved = "0";
    private String source = "pc_mdeditor";
    private List<String> cover_images = Collections.emptyList();
    private Integer cover_type = 0;
    private Integer is_new = 1;
    private Integer vote_id = 0;
    private String resource_id = "";
    private String pubStatus = "draft";
    private Integer sync_git_code = 0;

}