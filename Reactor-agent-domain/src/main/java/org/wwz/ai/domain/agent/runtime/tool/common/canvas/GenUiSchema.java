package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GenUI 树和增量补丁的轻量级规范化器。
 *
 * <p>模型输出的字段形态可能是完整 envelope、裸 root 或带有历史别名的节点，
 * 因此这里先收敛成前端稳定消费的结构，再执行节点种类、层级和数量校验。
 * 这个类只负责输入边界，不负责保存 canvas 状态或应用 JSON Patch。</p>
 */
public final class GenUiSchema {

    public static final int DEFAULT_MAX_DEPTH = 24;
    public static final int DEFAULT_MAX_NODES = 200;

    private static final Set<String> NODE_KEYS = Set.of("nodeId", "kind", "props", "children", "type");
    private static final Set<String> PATCH_OPS = Set.of("add", "replace", "remove");

    private GenUiSchema() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> validateUiTree(Object raw) {
        return validateUiTree(raw, DEFAULT_MAX_DEPTH, DEFAULT_MAX_NODES);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> validateUiTree(Object raw, int maxDepth, int maxNodes) {
        if (!(raw instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("tree must be an object");
        }
        // 先把非字符串 key 转成字符串，避免模型输出的 Map 实现把边界校验绕开。
        Map<String, Object> tree = castMap((Map<?, ?>) raw);
        Map<String, Object> envelope = normalizeEnvelope(tree);
        Object rootObj = envelope.get("root");
        if (!(rootObj instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("root must be an object");
        }
        Map<String, Object> root = normalizeNode((Map<?, ?>) rootObj, maxDepth, maxNodes);
        envelope.put("root", root);
        return envelope;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> validateUiPatch(Object raw) {
        if (!(raw instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("patch payload must be an object");
        }
        Map<String, Object> payload = castMap((Map<?, ?>) raw);
        Object patchesObj = payload.get("patches");
        if (!(patchesObj instanceof List<?> patches) || patches.isEmpty()) {
            throw new IllegalArgumentException("patches must be a non-empty array");
        }
        if (patches.size() > 200) {
            throw new IllegalArgumentException("patches exceeds max 200");
        }
        // 只保留 RFC 6901 所需字段，避免把模型附带的未知字段继续传播到前端。
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : patches) {
            if (!(item instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("each patch must be an object");
            }
            Map<String, Object> patch = castMap((Map<?, ?>) item);
            String op = stringVal(patch.get("op"));
            String path = stringVal(patch.get("path"));
            if (!PATCH_OPS.contains(op)) {
                throw new IllegalArgumentException("patch.op must be add|replace|remove");
            }
            if (StringUtils.isBlank(path) || !path.startsWith("/")) {
                throw new IllegalArgumentException("patch.path must be an RFC6901 pointer starting with /");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("op", op);
            out.put("path", path);
            if (!"remove".equals(op)) {
                if (!patch.containsKey("value")) {
                    throw new IllegalArgumentException("patch.value required for op=" + op);
                }
                out.put("value", patch.get("value"));
            }
            normalized.add(out);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patches", normalized);
        if (payload.get("canvas_id") != null) {
            result.put("canvas_id", String.valueOf(payload.get("canvas_id")));
        }
        if (payload.get("seq") != null) {
            result.put("seq", payload.get("seq"));
        }
        return result;
    }

    private static Map<String, Object> normalizeEnvelope(Map<String, Object> tree) {
        // 兼容 {schemaVersion, root}、{root}、{tree:{...}} 和裸 root，统一输出单一 envelope。
        Map<String, Object> current = tree;
        Set<Map<?, ?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current.containsKey("tree") && current.get("tree") instanceof Map<?, ?> nested
                && current.keySet().stream().allMatch(k -> "tree".equals(k) || "canvas_id".equals(k))) {
            if (!seen.add(nested)) {
                throw new IllegalArgumentException("tree contains cyclic envelope reference");
            }
            current = castMap((Map<?, ?>) nested);
        }
        if (current.containsKey("root") || current.containsKey("schemaVersion")) {
            Object root = current.get("root");
            if (!(root instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("envelope requires root object");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("schemaVersion", "1");
            out.put("root", castMap((Map<?, ?>) root));
            return out;
        }
        // 裸 root 只要能识别出 kind/type，就可以进入同一套节点规范化流程。
        if (current.containsKey("kind") || current.containsKey("type")) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("schemaVersion", "1");
            out.put("root", current);
            return out;
        }
        throw new IllegalArgumentException("invalid tree envelope; expected {schemaVersion,root} or bare root");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeNode(Map<?, ?> root, int maxDepth, int maxNodes) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("tree depth 1 exceeds max " + maxDepth);
        }
        if (maxNodes < 1) {
            throw new IllegalArgumentException("tree node count 1 exceeds max " + maxNodes);
        }

        Map<String, Object> normalizedRoot = null;
        int nodeCount = 0;
        List<NodeFrame> stack = new ArrayList<>();
        Set<Map<?, ?>> activeNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        stack.add(new NodeFrame(root, 1, null));
        while (!stack.isEmpty()) {
            NodeFrame frame = stack.get(stack.size() - 1);
            if (frame.output == null) {
                if (frame.depth > maxDepth) {
                    throw new IllegalArgumentException("tree depth " + frame.depth + " exceeds max " + maxDepth);
                }
                if (++nodeCount > maxNodes) {
                    throw new IllegalArgumentException("tree node count " + nodeCount + " exceeds max " + maxNodes);
                }
                if (!activeNodes.add(frame.input)) {
                    throw new IllegalArgumentException("tree contains cyclic node reference");
                }
                frame.output = normalizeNodeFields(castMap(frame.input));
                if (frame.parentChildren == null) {
                    normalizedRoot = frame.output;
                } else {
                    frame.parentChildren.add(frame.output);
                }
                frame.children = childrenOf(frame.input);
            }
            if (frame.nextChild < frame.children.size()) {
                Object child = frame.children.get(frame.nextChild++);
                if (child == null) {
                    continue;
                }
                if (!(child instanceof Map<?, ?> childMap)) {
                    throw new IllegalArgumentException("children must be node objects, not primitives");
                }
                stack.add(new NodeFrame(childMap, frame.depth + 1,
                        (List<Map<String, Object>>) frame.output.get("children")));
            } else {
                activeNodes.remove(frame.input);
                stack.remove(stack.size() - 1);
            }
        }
        return normalizedRoot;
    }

    private static Map<String, Object> normalizeNodeFields(Map<String, Object> node) {
        Map<String, Object> out = new LinkedHashMap<>();
        String kind = stringVal(node.get("kind"));
        if (StringUtils.isBlank(kind)) {
            kind = stringVal(node.get("type"));
        }
        if (StringUtils.isBlank(kind)) {
            throw new IllegalArgumentException("node.kind is required");
        }
        if (!GenUiCatalog.isAllowedKind(kind)) {
            throw new IllegalArgumentException("unsupported kind: " + kind + "; call list_ui_components");
        }
        String nodeId = stringVal(node.get("nodeId"));
        if (StringUtils.isBlank(nodeId)) {
            nodeId = "n_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        out.put("nodeId", nodeId);
        out.put("kind", kind);

        Map<String, Object> props = new LinkedHashMap<>();
        if (node.get("props") instanceof Map<?, ?> p) {
            props.putAll(castMap(p));
        }
        // 模型经常把 props 里的字段误放到节点顶层；提升它们可以兼容输出偏差，保留保留字不被覆盖。
        for (Map.Entry<String, Object> e : node.entrySet()) {
            String key = e.getKey();
            if (NODE_KEYS.contains(key)) {
                continue;
            }
            props.putIfAbsent(key, e.getValue());
        }
        // 将常见自然语言别名收敛为渲染器约定的属性名。
        liftAlias(props, "text", "value");
        liftAlias(props, "title", "value");
        liftAlias(props, "content", "value");
        liftAlias(props, "label", "value");
        liftAlias(props, "url", "src");
        liftAlias(props, "imageUrl", "src");
        liftAlias(props, "href", "url");
        out.put("props", props);

        List<Map<String, Object>> children = new ArrayList<>();
        out.put("children", children);
        return out;
    }

    private static List<?> childrenOf(Map<?, ?> node) {
        Object children = node.get("children");
        return children instanceof List<?> list ? list : List.of();
    }

    private static final class NodeFrame {
        private final Map<?, ?> input;
        private final int depth;
        private final List<Map<String, Object>> parentChildren;
        private Map<String, Object> output;
        private List<?> children;
        private int nextChild;

        private NodeFrame(Map<?, ?> input, int depth, List<Map<String, Object>> parentChildren) {
            this.input = input;
            this.depth = depth;
            this.parentChildren = parentChildren;
        }
    }

    private static void liftAlias(Map<String, Object> props, String from, String to) {
        if (!props.containsKey(to) && props.containsKey(from)) {
            props.put(to, props.get(from));
        }
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private static String stringVal(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
