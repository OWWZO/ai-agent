package org.wwz.ai.infrastructure.codemode;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;

/**
 * 创建最小权限的 GraalJS Context。
 * <p>
 * 权限从"全部关闭"开始：禁 Java 类查找、禁宿主类加载、禁建线程、禁建进程、
 * 禁 Native、禁 Polyglot 互通、禁环境变量访问；IO 用默认（未授予）。
 * <b>不使用 {@code allowAllAccess(true)} 或 {@code HostAccess.ALL}</b>。
 * </p>
 * <p>
 * Engine 全局共享（创建成本高），Context 每次执行独立（绑定与取消作用域）。
 * </p>
 */
public final class GraalJsContextFactory {

    private static volatile Engine sharedEngine;

    private GraalJsContextFactory() {
    }

    /** 懒加载共享 Engine；选项不可用时降级为默认 Engine。 */
    public static Engine sharedEngine() {
        if (sharedEngine == null) {
            synchronized (GraalJsContextFactory.class) {
                if (sharedEngine == null) {
                    sharedEngine = createEngine();
                }
            }
        }
        return sharedEngine;
    }

    private static Engine createEngine() {
        try {
            // 当前 JVM 未开 JVMCI，只能解释执行；这条警告每次启动都刷屏，关掉它。
            return Engine.newBuilder()
                    .option("engine.WarnInterpreterOnly", "false")
                    .build();
        } catch (RuntimeException ignore) {
            return Engine.newBuilder().build();
        }
    }

    public static Context newContext() {
        return Context.newBuilder("js")
                .engine(sharedEngine())
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(className -> false)
                .allowHostClassLoading(false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowAllAccess(false)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .build();
    }
}
