package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.util.FileUtil;

import java.util.List;

public class FileUtilAvailableFilesBlockTest {

    @Test
    public void formatAvailableFilesUserBlock_includesWorkspaceHint() {
        String block = FileUtil.formatAvailableFilesUserBlock(List.of(
                File.builder()
                        .fileName("notes.py")
                        .description("workspace:notes.py")
                        .ossUrl("https://example.com/notes.py")
                        .isInternalFile(false)
                        .build()
        ));

        Assert.assertTrue(block.contains("<user_uploaded_files>"));
        Assert.assertTrue(block.contains("fileName:notes.py"));
        Assert.assertTrue(block.contains("workspace:notes.py"));
        Assert.assertTrue(block.contains("workspace_list"));
    }

    @Test
    public void formatAvailableFilesUserBlock_skipsInternalAndEmpty() {
        Assert.assertEquals("", FileUtil.formatAvailableFilesUserBlock(null));
        Assert.assertEquals("", FileUtil.formatAvailableFilesUserBlock(List.of()));
        Assert.assertEquals("", FileUtil.formatAvailableFilesUserBlock(List.of(
                File.builder()
                        .fileName("secret.md")
                        .ossUrl("https://example.com/secret.md")
                        .isInternalFile(true)
                        .build()
        )));
    }
}
