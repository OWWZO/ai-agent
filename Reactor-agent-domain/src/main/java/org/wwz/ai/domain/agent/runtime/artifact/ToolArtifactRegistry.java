package org.wwz.ai.domain.agent.runtime.artifact;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.dto.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 当前请求运行期内的工具产物登记簿。
 * registry 是生成文件来源的唯一事实来源，productFiles/taskProductFiles 仅作为兼容视图维护。
 */
public class ToolArtifactRegistry {

    // 核心存储：所有工具-文件绑定关系（唯一可信数据源）
    private final List<ToolArtifactBinding> bindings = new ArrayList<>();

    /**
     * 登记一个生成的文件
     * @param source           文件来源（哪个工具调用产生的）
     * @param file             要登记的文件
     * @param productFiles     兼容视图：所有产物文件（外部传入的列表，会被修改）
     * @param taskProductFiles 兼容视图：非内部产物文件（外部传入的列表，会被修改）
     * @return 创建或已存在的绑定对象
     */
    public synchronized ToolArtifactBinding registerGeneratedFile(ToolArtifactSource source,
                                                                  File file,
                                                                  List<File> productFiles,
                                                                  List<File> taskProductFiles) {
        // 参数校验
        Objects.requireNonNull(source, "toolArtifactSource must not be null");
        Objects.requireNonNull(file, "file must not be null");

        // 构建绑定对象
        ToolArtifactBinding binding = ToolArtifactBinding.builder()
            .source(source)
            .file(file)
            .build();

        // 核心存储去重：相同绑定不重复添加
        if (!containsBinding(binding)) {
            bindings.add(binding);
        }

        // 同步更新兼容视图
        addFileIfAbsent(productFiles, file);           // 所有文件进 productFiles
        if (!Boolean.TRUE.equals(file.getIsInternalFile())) {
            addFileIfAbsent(taskProductFiles, file);   // 非内部文件进 taskProductFiles
        }
        return binding;
    }

    /** 获取所有绑定（返回副本，防外部修改） */
    public synchronized List<ToolArtifactBinding> listBindings() {
        return new ArrayList<>(bindings);
    }

    /** 按工具调用ID查找绑定 */
    public synchronized List<ToolArtifactBinding> findBindingsByToolCallId(String toolCallId) {
        if (StringUtils.isBlank(toolCallId)) {
            return List.of();  // 空ID直接返回空列表
        }
        return bindings.stream()
            .filter(binding -> binding.getSource() != null)           // 过滤无来源的
            .filter(binding -> toolCallId.equals(binding.getSource().getToolCallId()))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    /** 获取所有非内部文件绑定（对外可见的产物） */
    public synchronized List<ToolArtifactBinding> listVisibleBindings() {
        return bindings.stream()
            .filter(binding -> !binding.isInternalFile())
            .collect(Collectors.toCollection(ArrayList::new));
    }

    // ========== 私有工具方法 ==========

    /** 判断是否已存在相同绑定 */
    private boolean containsBinding(ToolArtifactBinding candidate) {
        for (ToolArtifactBinding existing : bindings) {
            if (sameBinding(existing, candidate)) {
                return true;
            }
        }
        return false;
    }

    /** 绑定是否相同：比较 工具调用ID、工具名、文件名、文件URL、是否内部文件 */
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

    /** 文件加入列表（去重） */
    private void addFileIfAbsent(List<File> targetFiles, File candidate) {
        for (File existing : targetFiles) {
            if (sameFile(existing, candidate)) {
                return;  // 已存在，跳过
            }
        }
        targetFiles.add(candidate);
    }

    /** 文件是否相同：比较 文件名、URL、是否内部文件 */
    private boolean sameFile(File left, File right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getFileName(), right.getFileName())
            && Objects.equals(resolveFileUrl(left), resolveFileUrl(right))
            && Objects.equals(Boolean.TRUE.equals(left.getIsInternalFile()), Boolean.TRUE.equals(right.getIsInternalFile()));
    }

    // ========== 安全取值工具方法 ==========

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
