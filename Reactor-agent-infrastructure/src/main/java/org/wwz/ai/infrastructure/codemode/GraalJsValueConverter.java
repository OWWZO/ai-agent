package org.wwz.ai.infrastructure.codemode;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JS 值 ⇄ Java 值的双向转换。
 * <p>
 * 出方向（JS → Java）只产出 {@code null / String / Boolean / Number / List / Map}，
 * 拒绝函数、Java 宿主对象、原生指针和超深的嵌套结构，避免引擎对象泄漏到工具结果里。
 * 入方向（Java → JS）把 Map / List 递归包成 {@link ProxyObject} / {@link ProxyArray}，
 * 因为 Context 用 {@code HostAccess.NONE}，普通 Java 对象在 JS 里不可访问。
 * </p>
 */
public final class GraalJsValueConverter {

    private static final int MAX_DEPTH = 32;
    private static final int MAX_ITEMS = 20_000;
    private static final int MAX_STRING_CHARS = 1_048_576;

    private GraalJsValueConverter() {
    }

    /** JS → Java（JSON-safe）。 */
    public static Object toJavaObject(Value value) {
        return toJavaObject(value, 0);
    }

    private static Object toJavaObject(Value value, int depth) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_OUTPUT_TOO_LARGE,
                    "返回值嵌套层级超过 " + MAX_DEPTH);
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            String text = value.asString();
            return text == null || text.length() <= MAX_STRING_CHARS
                    ? text
                    : text.substring(0, MAX_STRING_CHARS);
        }
        if (value.isNumber()) {
            if (value.fitsInLong()) {
                return value.asLong();
            }
            double number = value.asDouble();
            // JS 数字都是 double；整数值回传成整数，避免 2 变成 2.0 污染 JSON 与 CSV
            if (!Double.isInfinite(number) && !Double.isNaN(number)
                    && number == Math.rint(number) && Math.abs(number) <= 9_007_199_254_740_992d) {
                return (long) number;
            }
            return number;
        }
        if (value.isHostObject() || value.isNativePointer()) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_OUTPUT_TOO_LARGE,
                    "返回值包含宿主对象或原生指针，不允许回传");
        }
        if (value.canExecute()) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_OUTPUT_TOO_LARGE,
                    "返回值包含函数，不允许回传");
        }
        if (value.hasArrayElements()) {
            long size = value.getArraySize();
            if (size > MAX_ITEMS) {
                throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_OUTPUT_TOO_LARGE,
                        "返回数组元素数超过 " + MAX_ITEMS);
            }
            List<Object> list = new ArrayList<>((int) size);
            for (long i = 0; i < size; i++) {
                list.add(toJavaObject(value.getArrayElement(i), depth + 1));
            }
            return list;
        }
        if (value.hasMembers()) {
            List<String> keys = new ArrayList<>(value.getMemberKeys());
            if (keys.size() > MAX_ITEMS) {
                throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_OUTPUT_TOO_LARGE,
                        "返回对象成员数超过 " + MAX_ITEMS);
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : keys) {
                map.put(key, toJavaObject(value.getMember(key), depth + 1));
            }
            return map;
        }
        if (value.isException()) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_RUNTIME_ERROR,
                    value.toString());
        }
        return value.toString();
    }

    /** Java → JS（递归包成代理）。 */
    public static Object toJsObject(Object value) {
        return toJsObject(value, 0);
    }

    private static Object toJsObject(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (depth > MAX_DEPTH) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_OUTPUT_TOO_LARGE,
                    "入参嵌套层级超过 " + MAX_DEPTH);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), toJsObject(entry.getValue(), depth + 1));
            }
            return ProxyObject.fromMap(converted);
        }
        if (value instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            for (Object item : list) {
                converted.add(toJsObject(item, depth + 1));
            }
            return ProxyArray.fromList(converted);
        }
        if (value instanceof Object[] array) {
            List<Object> converted = new ArrayList<>(array.length);
            for (Object item : array) {
                converted.add(toJsObject(item, depth + 1));
            }
            return ProxyArray.fromList(converted);
        }
        if (value instanceof Boolean || value instanceof String
                || value instanceof Integer || value instanceof Long
                || value instanceof Double || value instanceof Float
                || value instanceof Short || value instanceof Byte) {
            return value;
        }
        // 其余类型一律转成字符串，避免把宿主对象带进 JS。
        return String.valueOf(value);
    }

    /**
     * JS 传入的 capability 参数 → Java Map。非对象参数（字符串等）按 {@code {value: ...}} 归一。
     */
    public static Map<String, Object> toJavaMap(Value value) {
        Object converted = toJavaObject(value);
        if (converted instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", converted);
        return result;
    }
}
