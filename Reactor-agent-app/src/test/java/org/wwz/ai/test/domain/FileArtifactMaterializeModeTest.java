package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileRequest;
import org.wwz.ai.domain.agent.runtime.dto.FileResponse;
import org.wwz.ai.infrastructure.adapter.port.ReactorToolFileArtifactAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件产物物化模式：共享卷走 register，非共享部署走 multipart upload。
 * 这里只锁定模式分派和失败边界，真实 multipart 传输由部署联调覆盖。
 */
public class FileArtifactMaterializeModeTest {

    @Test
    public void shouldUseRegisterInSharedWorkspaceMode() throws Exception {
        RemoteHttpPort remoteHttpPort = Mockito.mock(RemoteHttpPort.class);
        Mockito.when(remoteHttpPort.execute(Mockito.any(RemoteHttpRequest.class)))
                .thenReturn("{\"ossUrl\":\"http://fs/download/a.png\","
                        + "\"domainUrl\":\"http://fs/preview/a.png\",\"fileSize\":12}");
        ReactorToolFileArtifactAdapter adapter = new ReactorToolFileArtifactAdapter(remoteHttpPort);
        adapter.setLocalFileMode("register");

        FileResponse response = adapter.materializeLocalFile("http://127.0.0.1:1601",
                FileRequest.builder()
                        .requestId("session-1")
                        .fileName("a.png")
                        .localPath("/tmp/a.png")
                        .build());

        Assert.assertEquals("http://fs/download/a.png", response.getOssUrl());
        Mockito.verify(remoteHttpPort).execute(Mockito.argThat(
                request -> request.getUrl().endsWith("/v1/file_tool/register_file")));
    }

    @Test
    public void shouldFailFastWhenUploadModeCannotFindLocalFile() throws Exception {
        ReactorToolFileArtifactAdapter adapter = new ReactorToolFileArtifactAdapter(
                Mockito.mock(RemoteHttpPort.class));
        adapter.setLocalFileMode("upload");
        Path missing = Files.createTempDirectory("upload-mode").resolve("missing.png");
        try {
            adapter.materializeLocalFile("http://127.0.0.1:1601", FileRequest.builder()
                    .requestId("session-1")
                    .fileName("missing.png")
                    .localPath(missing.toString())
                    .build());
            Assert.fail("upload 模式必须校验本地文件存在");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage().contains("local file not found"));
        }
    }

    @Test
    public void shouldRejectLocalFileLargerThanUploadLimit() throws Exception {
        ReactorToolFileArtifactAdapter adapter = new ReactorToolFileArtifactAdapter(
                Mockito.mock(RemoteHttpPort.class));
        adapter.setLocalFileMode("upload");
        Path oversized = Files.createTempFile("upload-mode-big", ".bin");
        try {
            // 只创建稀疏文件元数据，不真正写满 200MB
            try (var channel = Files.newByteChannel(oversized,
                    java.nio.file.StandardOpenOption.WRITE)) {
                channel.position(201L * 1024 * 1024);
                channel.write(java.nio.ByteBuffer.wrap("x".getBytes(StandardCharsets.UTF_8)));
            }
            adapter.materializeLocalFile("http://127.0.0.1:1601", FileRequest.builder()
                    .requestId("session-1")
                    .fileName("big.bin")
                    .localPath(oversized.toString())
                    .build());
            Assert.fail("超过上限的文件必须被拒绝");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage().contains("exceeds upload limit"));
        } finally {
            Files.deleteIfExists(oversized);
        }
    }

    @Test
    public void shouldDefaultToRegisterMode() throws Exception {
        RemoteHttpPort remoteHttpPort = Mockito.mock(RemoteHttpPort.class);
        Mockito.when(remoteHttpPort.execute(Mockito.any(RemoteHttpRequest.class)))
                .thenReturn("{\"downloadUrl\":\"http://fs/download/b.png\",\"domainUrl\":\"http://fs/preview/b.png\"}");
        ReactorToolFileArtifactAdapter adapter = new ReactorToolFileArtifactAdapter(remoteHttpPort);
        adapter.setLocalFileMode("unexpected-value");

        FileResponse response = adapter.materializeLocalFile("http://127.0.0.1:1601", FileRequest.builder()
                .requestId("session-1")
                .fileName("b.png")
                .localPath("/tmp/b.png")
                .build());

        // register 协议返回 downloadUrl 时也要收敛成 ossUrl
        Assert.assertEquals("http://fs/download/b.png", response.getOssUrl());
    }
}
