package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.reactor.entity.AgentImageGenerationRecord;
import org.wwz.ai.domain.agent.reactor.gateway.IReactorImageGenerationGateway;
import org.wwz.ai.domain.agent.reactor.mapper.IAgentImageGenerationRecordDao;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayFile;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayRequest;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.ImageGenerationGatewayResponse;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationCommand;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationHistoryPage;
import org.wwz.ai.domain.agent.reactor.model.imagegeneration.WorkspaceImageGenerationResult;
import org.wwz.ai.domain.agent.reactor.service.impl.WorkspaceImageGenerationServiceImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class WorkspaceImageGenerationServiceTest {

    @Test
    public void test_generatePersistsEachReturnedImage() {
        WorkspaceImageGenerationServiceImpl service = new WorkspaceImageGenerationServiceImpl();
        InMemoryImageGenerationRecordDao recordDao = new InMemoryImageGenerationRecordDao();
        AtomicReference<ImageGenerationGatewayRequest> capturedRequest = new AtomicReference<>();

        IReactorImageGenerationGateway gateway = Mockito.mock(IReactorImageGenerationGateway.class);
        Mockito.when(gateway.generate(Mockito.any(ImageGenerationGatewayRequest.class)))
                .thenAnswer(invocation -> {
                    ImageGenerationGatewayRequest request = invocation.getArgument(0);
                    capturedRequest.set(request);
                    return ImageGenerationGatewayResponse.builder()
                            .data("生成完成")
                            .requestId(request.getRequestId())
                            .mode(request.getMode())
                            .usedFallback(true)
                            .rawResponse(Map.of("requestId", request.getRequestId()))
                            .fileInfo(List.of(
                                    ImageGenerationGatewayFile.builder()
                                            .fileName("result-1.png")
                                            .domainUrl("https://file.example.com/result-1.png")
                                            .fileSize(128L)
                                            .mimeType("image/png")
                                            .build(),
                                    ImageGenerationGatewayFile.builder()
                                            .fileName("result-2.png")
                                            .downloadUrl("https://file.example.com/result-2.png")
                                            .fileSize(256L)
                                            .mimeType("image/png")
                                            .build()
                            ))
                            .build();
                });

        ReflectionTestUtils.setField(service, "imageGenerationGateway", gateway);
        ReflectionTestUtils.setField(service, "imageGenerationRecordDao", recordDao);

        WorkspaceImageGenerationResult result = service.generate(
                "device-100",
                WorkspaceImageGenerationCommand.builder()
                        .requestId("req-100")
                        .prompt(" 生成一张海报 ")
                        .mode("edits")
                        .fileNames(List.of("source-1", "source-2"))
                        .maskFileNames(List.of("mask-1", ""))
                        .fileName(" poster ")
                        .size("")
                        .n(3)
                        .build()
        );

        Assert.assertNotNull(capturedRequest.get());
        Assert.assertEquals("req-100", capturedRequest.get().getRequestId());
        Assert.assertEquals("edits", capturedRequest.get().getMode());
        Assert.assertEquals("生成一张海报", capturedRequest.get().getPrompt());
        Assert.assertEquals("poster", capturedRequest.get().getFileName());
        Assert.assertEquals("1024x1024", capturedRequest.get().getSize());
        Assert.assertEquals(Integer.valueOf(3), capturedRequest.get().getN());
        Assert.assertEquals(Integer.valueOf(300), capturedRequest.get().getTimeoutSeconds());

        Assert.assertEquals("req-100", result.getRequestId());
        Assert.assertEquals(2, result.getFileInfo().size());
        Assert.assertTrue(result.getUsedFallback());
        Assert.assertEquals("https://file.example.com/result-1.png", result.getFileInfo().get(0).getPreviewUrl());
        Assert.assertEquals("https://file.example.com/result-2.png", result.getFileInfo().get(1).getDownloadUrl());

        Assert.assertEquals(2, recordDao.snapshot().size());
        AgentImageGenerationRecord firstRecord = recordDao.snapshot().get(0);
        AgentImageGenerationRecord secondRecord = recordDao.snapshot().get(1);
        Assert.assertEquals("req-100", firstRecord.getRequestId());
        Assert.assertEquals("device-100", firstRecord.getDeviceId());
        Assert.assertEquals(Integer.valueOf(0), firstRecord.getResultIndex());
        Assert.assertEquals(Integer.valueOf(2), firstRecord.getBatchCount());
        Assert.assertEquals(Integer.valueOf(2), firstRecord.getSourceImageCount());
        Assert.assertEquals(Integer.valueOf(1), firstRecord.getMaskImageCount());
        Assert.assertEquals(Integer.valueOf(1), firstRecord.getUsedFallback());
        Assert.assertEquals(Integer.valueOf(1), secondRecord.getResultIndex());
    }

    @Test
    public void test_generateWithoutFilesDoesNotPersistHistory() {
        WorkspaceImageGenerationServiceImpl service = new WorkspaceImageGenerationServiceImpl();
        InMemoryImageGenerationRecordDao recordDao = new InMemoryImageGenerationRecordDao();

        IReactorImageGenerationGateway gateway = Mockito.mock(IReactorImageGenerationGateway.class);
        Mockito.when(gateway.generate(Mockito.any(ImageGenerationGatewayRequest.class)))
                .thenReturn(ImageGenerationGatewayResponse.builder()
                        .data("空结果")
                        .requestId("req-empty")
                        .mode("images")
                        .fileInfo(List.of())
                        .build());

        ReflectionTestUtils.setField(service, "imageGenerationGateway", gateway);
        ReflectionTestUtils.setField(service, "imageGenerationRecordDao", recordDao);

        try {
            service.generate(
                    "device-empty",
                    WorkspaceImageGenerationCommand.builder()
                            .requestId("req-empty")
                            .prompt("生成空图")
                            .mode("images")
                            .n(1)
                            .build()
            );
            Assert.fail("预期应抛出无图片结果异常");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("上游未返回可识别的图片结果", expected.getMessage());
        }

        Assert.assertTrue(recordDao.snapshot().isEmpty());
    }

    @Test
    public void test_queryHistoryPaginatesByRequestBatchAndIsolatesDevice() {
        WorkspaceImageGenerationServiceImpl service = new WorkspaceImageGenerationServiceImpl();
        InMemoryImageGenerationRecordDao recordDao = new InMemoryImageGenerationRecordDao();
        recordDao.reset(List.of(
                buildRecord("req-new", 0, "device-a", "最新批次", "images", LocalDateTime.of(2026, 4, 26, 10, 0, 0)),
                buildRecord("req-new", 1, "device-a", "最新批次", "images", LocalDateTime.of(2026, 4, 26, 10, 0, 0)),
                buildRecord("req-old", 0, "device-a", "旧批次", "edits", LocalDateTime.of(2026, 4, 25, 9, 0, 0)),
                buildRecord("req-other-device", 0, "device-b", "其他设备", "images", LocalDateTime.of(2026, 4, 26, 11, 0, 0))
        ));
        ReflectionTestUtils.setField(service, "imageGenerationRecordDao", recordDao);

        WorkspaceImageGenerationHistoryPage firstPage = service.queryHistory("device-a", 1, 1);
        Assert.assertEquals(2, firstPage.getTotal());
        Assert.assertEquals(1, firstPage.getList().size());
        Assert.assertEquals("req-new", firstPage.getList().get(0).getRequestId());
        Assert.assertEquals(2, firstPage.getList().get(0).getImages().size());
        Assert.assertEquals("最新批次", firstPage.getList().get(0).getPrompt());

        WorkspaceImageGenerationHistoryPage secondPage = service.queryHistory("device-a", 2, 1);
        Assert.assertEquals(2, secondPage.getTotal());
        Assert.assertEquals(1, secondPage.getList().size());
        Assert.assertEquals("req-old", secondPage.getList().get(0).getRequestId());
        Assert.assertEquals(1, secondPage.getList().get(0).getImages().size());
    }

    private AgentImageGenerationRecord buildRecord(String requestId,
                                                   int resultIndex,
                                                   String deviceId,
                                                   String prompt,
                                                   String mode,
                                                   LocalDateTime createTime) {
        return AgentImageGenerationRecord.builder()
                .requestId(requestId)
                .resultIndex(resultIndex)
                .deviceId(deviceId)
                .prompt(prompt)
                .mode(mode)
                .size("1024x1024")
                .batchCount("req-new".equals(requestId) ? 2 : 1)
                .sourceImageCount("edits".equals(mode) ? 1 : 0)
                .maskImageCount("edits".equals(mode) ? 1 : 0)
                .usedFallback(0)
                .fileName(requestId + "-" + resultIndex + ".png")
                .domainUrl("https://file.example.com/" + requestId + "/" + resultIndex + ".png")
                .downloadUrl("https://file.example.com/download/" + requestId + "/" + resultIndex + ".png")
                .fileSize(256L + resultIndex)
                .mimeType("image/png")
                .createTime(createTime)
                .deleted(0)
                .build();
    }

    private static class InMemoryImageGenerationRecordDao implements IAgentImageGenerationRecordDao {

        private final List<AgentImageGenerationRecord> records = new ArrayList<>();

        @Override
        public int insert(AgentImageGenerationRecord record) {
            records.add(record);
            return 1;
        }

        @Override
        public int countDistinctRequestIdByDeviceId(String deviceId) {
            return (int) records.stream()
                    .filter(record -> deviceMatches(record, deviceId))
                    .map(AgentImageGenerationRecord::getRequestId)
                    .distinct()
                    .count();
        }

        @Override
        public List<String> queryRequestIdsByDeviceId(String deviceId, int offset, int limit) {
            List<String> orderedRequestIds = records.stream()
                    .filter(record -> deviceMatches(record, deviceId))
                    .sorted(Comparator.comparing(AgentImageGenerationRecord::getCreateTime,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(AgentImageGenerationRecord::getRequestId, Comparator.reverseOrder()))
                    .map(AgentImageGenerationRecord::getRequestId)
                    .collect(Collectors.toList());

            List<String> distinctRequestIds = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String requestId : orderedRequestIds) {
                if (seen.add(requestId)) {
                    distinctRequestIds.add(requestId);
                }
            }

            int safeOffset = Math.min(offset, distinctRequestIds.size());
            int safeEnd = Math.min(safeOffset + limit, distinctRequestIds.size());
            return distinctRequestIds.subList(safeOffset, safeEnd);
        }

        @Override
        public List<AgentImageGenerationRecord> queryByRequestIds(String deviceId, List<String> requestIds) {
            Map<String, Integer> requestOrderMap = new java.util.LinkedHashMap<>();
            for (int index = 0; index < requestIds.size(); index++) {
                requestOrderMap.put(requestIds.get(index), index);
            }
            return records.stream()
                    .filter(record -> deviceMatches(record, deviceId))
                    .filter(record -> requestOrderMap.containsKey(record.getRequestId()))
                    .sorted(Comparator
                            .comparing((AgentImageGenerationRecord record) -> requestOrderMap.get(record.getRequestId()))
                            .thenComparing(AgentImageGenerationRecord::getResultIndex))
                    .collect(Collectors.toList());
        }

        private boolean deviceMatches(AgentImageGenerationRecord record, String deviceId) {
            return deviceId.equals(record.getDeviceId()) && !Integer.valueOf(1).equals(record.getDeleted());
        }

        private List<AgentImageGenerationRecord> snapshot() {
            return new ArrayList<>(records);
        }

        private void reset(List<AgentImageGenerationRecord> sourceRecords) {
            records.clear();
            records.addAll(sourceRecords);
        }
    }
}
