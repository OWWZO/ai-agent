package org.wwz.ai.trigger.http.admin;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.multipart.MultipartFile;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPackageParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPackageService;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class SkillAdminControllerMultipartTest {

    @Test
    public void shouldRejectInvalidParsePackageFilesBeforeReadingBytes() {
        RecordingService service = new RecordingService();
        SkillAdminController controller = new SkillAdminController(service);

        Assert.assertNotNull(controller.parsePackage(null).getInfo());

        TestMultipartFile empty = new TestMultipartFile(0, true);
        Assert.assertNotNull(controller.parsePackage(empty).getInfo());
        Assert.assertFalse(empty.bytesRead);

        TestMultipartFile oversized = new TestMultipartFile(SkillPackageParser.MAX_PACKAGE_BYTES + 1, false);
        Assert.assertNotNull(controller.parsePackage(oversized).getInfo());
        Assert.assertFalse(oversized.bytesRead);
        Assert.assertEquals(0, service.previewCalls);
    }

    @Test
    public void shouldRejectInvalidUploadFilesBeforeReadingBytes() {
        RecordingService service = new RecordingService();
        SkillAdminController controller = new SkillAdminController(service);

        Assert.assertNotNull(controller.upload(null, false).getInfo());

        TestMultipartFile empty = new TestMultipartFile(0, false);
        Assert.assertNotNull(controller.upload(empty, false).getInfo());
        Assert.assertFalse(empty.bytesRead);

        TestMultipartFile oversized = new TestMultipartFile(SkillPackageParser.MAX_PACKAGE_BYTES + 1, false);
        Assert.assertNotNull(controller.upload(oversized, false).getInfo());
        Assert.assertFalse(oversized.bytesRead);
        Assert.assertEquals(0, service.installCalls);
    }

    @Test
    public void shouldAllowPackageAtTheLimitForBothEndpoints() {
        RecordingService service = new RecordingService();
        SkillAdminController controller = new SkillAdminController(service);
        TestMultipartFile parseFile = new TestMultipartFile(SkillPackageParser.MAX_PACKAGE_BYTES, false);
        TestMultipartFile uploadFile = new TestMultipartFile(SkillPackageParser.MAX_PACKAGE_BYTES, false);

        Response<Map<String, Object>> parseResponse = controller.parsePackage(parseFile);
        Response<Map<String, Object>> uploadResponse = controller.upload(uploadFile, false);

        Assert.assertNotNull(parseResponse.getData());
        Assert.assertNotNull(uploadResponse.getData());
        Assert.assertTrue(parseFile.bytesRead);
        Assert.assertTrue(uploadFile.bytesRead);
        Assert.assertEquals(1, service.previewCalls);
        Assert.assertEquals(1, service.installCalls);
    }

    private static final class RecordingService extends SkillPackageService {
        private int previewCalls;
        private int installCalls;

        private RecordingService() {
            super(null, null);
        }

        @Override
        public Map<String, Object> previewZipAsMap(byte[] zipBytes) {
            previewCalls++;
            return Map.of();
        }

        @Override
        public Map<String, Object> installZip(byte[] zipBytes, String originalFilename, boolean replace) {
            installCalls++;
            return Map.of();
        }
    }

    private static final class TestMultipartFile implements MultipartFile {
        private final long size;
        private final boolean empty;
        private boolean bytesRead;

        private TestMultipartFile(long size, boolean empty) {
            this.size = size;
            this.empty = empty;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return "skill.zip";
        }

        @Override
        public String getContentType() {
            return "application/zip";
        }

        @Override
        public boolean isEmpty() {
            return empty;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public byte[] getBytes() {
            bytesRead = true;
            return new byte[]{'P', 'K'};
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public void transferTo(File dest) {
        }

        @Override
        public void transferTo(Path dest) throws java.io.IOException {
            Files.write(dest, getBytes());
        }
    }
}
