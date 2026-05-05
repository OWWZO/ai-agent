package org.wwz.ai.application.agent.rag;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG 应用服务接口。
 * 为管理入口提供知识库上传 seam，防止 controller 继续直连 domain/service 根接口。
 */
public interface IRagApplicationService {

    void storeRagFile(String name, String tag, List<MultipartFile> files);
}
