package org.wwz.ai.domain.agent.reactor.agent.artifact;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.reactor.agent.dto.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 当前请求运行期内的工具产物登记簿。
 * registry 是生成文件来源的唯一事实来源，productFiles/taskProductFiles 仅作为兼容视图维护。
 */
public class ToolArtifactRegistry {

    private final List<ToolArtifactBinding> bindings = new ArrayList<>();

    public synchronized ToolArtifactBinding registerGeneratedFile(ToolArtifactSource source,
                                                                  File file,
                                                                  List<File> productFiles,
                                                                  List<File> taskProductFiles) {
        Objects.requireNonNull(source, "toolArtifactSource must not be null");
        Objects.requireNonNull(file, "file must not be null");

        ToolArtifactBinding binding = ToolArtifactBinding.builder()
                .source(source)
                .file(file)
                .build();
        if (!containsBinding(binding)) {
            bindings.add(binding);
        }

        addFileIfAbsent(productFiles, file);
        if (!Boolean.TRUE.equals(file.getIsInternalFile())) {
            addFileIfAbsent(taskProductFiles, file);
        }
        return binding;
    }

    public synchronized List<ToolArtifactBinding> listBindings() {
        return new ArrayList<>(bindings);
    }

    public synchronized List<ToolArtifactBinding> findBindingsByToolCallId(String toolCallId) {
        if (StringUtils.isBlank(toolCallId)) {
            return List.of();
        }
        return bindings.stream()
                .filter(binding -> binding.getSource() != null)
                .filter(binding -> toolCallId.equals(binding.getSource().getToolCallId()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public synchronized List<ToolArtifactBinding> listVisibleBindings() {
        return bindings.stream()
                .filter(binding -> !binding.isInternalFile())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean containsBinding(ToolArtifactBinding candidate) {
        for (ToolArtifactBinding existing : bindings) {
            if (sameBinding(existing, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameBinding(ToolArtifactBinding left, ToolArtifactBinding right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(readToolCallId(left), readToolCallId(right))
                && Objects.equals(readToolName(left), readToolName(right))
                && Objects.equals(readFileName(left), readFileName(right))
                && Objects.equals(readFileUrl(left), readFileUrl(right))
                && Objects.equals(readInternalFlag(left), readInternalFlag(right));
    }

    private void addFileIfAbsent(List<File> targetFiles, File candidate) {
        for (File existing : targetFiles) {
            if (sameFile(existing, candidate)) {
                return;
            }
        }
        targetFiles.add(candidate);
    }

    private boolean sameFile(File left, File right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getFileName(), right.getFileName())
                && Objects.equals(resolveFileUrl(left), resolveFileUrl(right))
                && Objects.equals(Boolean.TRUE.equals(left.getIsInternalFile()), Boolean.TRUE.equals(right.getIsInternalFile()));
    }

    private String readToolCallId(ToolArtifactBinding binding) {
        return binding.getSource() == null ? null : binding.getSource().getToolCallId();
    }

    private String readToolName(ToolArtifactBinding binding) {
        return binding.getSource() == null ? null : binding.getSource().getToolName();
    }

    private String readFileName(ToolArtifactBinding binding) {
        return binding.getFile() == null ? null : binding.getFile().getFileName();
    }

    private String readFileUrl(ToolArtifactBinding binding) {
        return binding.getFile() == null ? null : resolveFileUrl(binding.getFile());
    }

    private Boolean readInternalFlag(ToolArtifactBinding binding) {
        return binding.getFile() != null && Boolean.TRUE.equals(binding.getFile().getIsInternalFile());
    }

    private String resolveFileUrl(File file) {
        return ToolArtifactFormatter.resolveFileUrl(file);
    }
}
