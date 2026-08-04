package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
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
        Map<String, Object> root = normalizeNode(castMap((Map<?, ?>) rootObj));
        envelope.put("root", root);
        // 深度和节点数是输入资源上限，防止异常树拖垮递归校验和前端渲染。
        int[] count = countNodesDepth(root, 1);
        if (count[1] > maxDepth) {
            throw new IllegalArgumentException("tree depth " + count[1] + " exceeds max " + maxDepth);
        }
        if (count[0] > maxNodes) {
            throw new IllegalArgumentException("tree node count " + count[0] + " exceeds max " + maxNodes);
        }
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
        if (tree.containsKey("tree") && tree.get("tree") instanceof Map<?, ?> nested
                && tree.keySet().stream().allMatch(k -> "tree".equals(k) || "canvas_id".equals(k))) {
            return normalizeEnvelope(castMap((Map<?, ?>) nested));
        }
        if (tree.containsKey("root") || tree.containsKey("schemaVersion")) {
            Object root = tree.get("root");
            if (!(root instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("envelope requires root object");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("schemaVersion", "1");
            out.put("root", castMap((Map<?, ?>) root));
            return out;
        }
        // 裸 root 只要能识别出 kind/type，就可以进入同一套节点规范化流程。
        if (tree.containsKey("kind") || tree.containsKey("type")) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("schemaVersion", "1");
            out.put("root", tree);
            return out;
        }
        throw new IllegalArgumentException("invalid tree envelope; expected {schemaVersion,root} or bare root");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeNode(Map<String, Object> node) {
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
        if (node.get("children") instanceof List<?> list) {
            for (Object child : list) {
                if (child instanceof Map<?, ?> childMap) {
                    children.add(normalizeNode(castMap(childMap)));
                } else if (child != null) {
                    throw new IllegalArgumentException("children must be node objects, not primitives");
                }
            }
        }
        out.put("children", children);
        return out;
    }

    private static void liftAlias(Map<String, Object> props, String from, String to) {
        if (!props.containsKey(to) && props.containsKey(from)) {
            props.put(to, props.get(from));
        }
    }

    @SuppressWarnings("unchecked")
    private static int[] countNodesDepth(Map<String, Object> node, int depth) {
        int total = 1;
        int maxD = depth;
        Object children = node.get("children");
        if (children instanceof List<?> list) {
            for (Object child : list) {
                if (child instanceof Map<?, ?> m) {
                    int[] sub = countNodesDepth((Map<String, Object>) m, depth + 1);
                    total += sub[0];
                    maxD = Math.max(maxD, sub[1]);
                }
            }
        }
        return new int[]{total, maxD};
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
