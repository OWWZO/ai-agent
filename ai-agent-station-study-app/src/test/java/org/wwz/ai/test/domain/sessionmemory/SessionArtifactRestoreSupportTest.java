package org.wwz.ai.test.domain.sessionmemory;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.reactor.service.support.SessionArtifactRestoreSupport;

import java.util.List;
import java.util.Map;

public class SessionArtifactRestoreSupportTest {

    private final SessionArtifactRestoreSupport support = new SessionArtifactRestoreSupport();

    @Test
    public void test_parseFiles_supportsCanonicalConversationFileShape() {
        String filesJson = """
                [
                  {
                    "fileName":"需求说明书.pdf",
                    "fileDesc":"补充规格说明",
                    "domainUrl":"https://file.example.com/preview/spec.pdf",
                    "ossUrl":"https://file.example.com/download/spec.pdf",
                    "fileType":"pdf",
                    "fileSize":2048,
                    "resourceKey":"resource-spec-pdf",
                    "mimeType":"application/pdf",
                    "originFileName":"需求说明书.pdf"
                  }
                ]
                """;

        List<FileInformation> files = support.parseFiles(filesJson);

        Assert.assertEquals(1, files.size());
        FileInformation file = files.get(0);
        Assert.assertEquals("需求说明书.pdf", file.getFileName());
        Assert.assertEquals("pdf", file.getFileType());
        Assert.assertEquals(Integer.valueOf(2048), file.getFileSize());
        Assert.assertEquals("https://file.example.com/download/spec.pdf", file.getOssUrl());
        Assert.assertEquals("https://file.example.com/preview/spec.pdf", file.getDomainUrl());
        Assert.assertEquals("resource-spec-pdf", file.getResourceKey());
        Assert.assertEquals("application/pdf", file.getMimeType());
        Assert.assertEquals("需求说明书.pdf", file.getOriginFileName());
        Assert.assertEquals("补充规格说明", file.getFileDesc());
    }

    @Test
    public void test_mergeFiles_prefersResourceKeyForDeduplication() {
        List<FileInformation> mergedFiles = support.mergeFiles(
                List.of(
                        FileInformation.builder()
                                .fileName("方案A.pdf")
                                .resourceKey("resource-a")
                                .ossUrl("https://file.example.com/download/a-v1.pdf")
                                .domainUrl("https://file.example.com/preview/a-v1.pdf")
                                .build()),
                List.of(
                        FileInformation.builder()
                                .fileName("方案A(重复).pdf")
                                .resourceKey("resource-a")
                                .ossUrl("https://file.example.com/download/a-v2.pdf")
                                .domainUrl("https://file.example.com/preview/a-v2.pdf")
                                .build(),
                        FileInformation.builder()
                                .fileName("方案B.xlsx")
                                .resourceKey("resource-b")
                                .ossUrl("https://file.example.com/download/b.xlsx")
                                .domainUrl("https://file.example.com/preview/b.xlsx")
                                .build()));

        Assert.assertEquals(2, mergedFiles.size());
        Assert.assertEquals("方案A.pdf", mergedFiles.get(0).getFileName());
        Assert.assertEquals("方案B.xlsx", mergedFiles.get(1).getFileName());
    }

    @Test
    public void test_collectArtifactRefs_includesGeneratedFilesJson() {
        AgentMessage message = AgentMessage.builder()
                .id(1L)
                .filesJson("""
                        [
                          {
                            "fileName":"上传资料.pdf",
                            "domainUrl":"https://file.example.com/upload/preview.pdf",
                            "ossUrl":"https://file.example.com/upload/download.pdf",
                            "fileType":"pdf",
                            "resourceKey":"upload-pdf"
                          }
                        ]
                        """)
                .generatedFilesJson("""
                        [
                          {
                            "fileName":"结论报告.md",
                            "domainUrl":"https://file.example.com/generated/report.md",
                            "ossUrl":"https://file.example.com/generated/report-download.md",
                            "fileType":"markdown",
                            "resourceKey":"generated-report"
                          }
                        ]
                        """)
                .build();

        List<com.alibaba.fastjson.JSONObject> refs = support.collectArtifactRefs(List.of(message), Map.of());

        Assert.assertEquals(2, refs.size());
        Assert.assertEquals("upload-pdf", refs.get(0).getString("resourceKey"));
        Assert.assertEquals("generated-report", refs.get(1).getString("resourceKey"));
    }
}
