package org.wwz.ai.application.agent.rag;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.wwz.ai.domain.agent.rag.IRagService;

import javax.annotation.Resource;
import java.util.List;

/**
 * RAG 应用服务。
 * 负责把 trigger 上传请求编排到领域知识库服务，不在入口层暴露领域根接口。
 */
@Service
public class RagApplicationService implements IRagApplicationService {

    @Resource
    private IRagService ragService;

    @Override
    public void storeRagFile(String name, String tag, List<MultipartFile> files) {
        ragService.storeRagFile(name, tag, files);
    }
}
