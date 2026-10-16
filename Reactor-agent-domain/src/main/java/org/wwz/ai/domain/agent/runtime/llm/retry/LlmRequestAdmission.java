package org.wwz.ai.domain.agent.runtime.llm.retry;

/**
 * 请求准入端口：约束「provider 真正看到的在飞请求数」。
 *
 * <p>移植自 ZCode 的 {@code request-admission.ts}。语义要点：
 * <ul>
 *   <li>每次尝试<b>发出前</b>取票，拿到票才发请求；尝试结束（成功/失败/放弃）即还票</li>
 *   <li>退避 sleep 期间不持票 —— 否则排队时间会被算成在飞请求，把并发上限变成摆设</li>
 *   <li>没配准入端口时返回空实现，调用点不必分叉，行为与旧实现逐字一致</li>
 * </ul>
 */
public interface LlmRequestAdmission {

    /** 快路径：拿不到返回 null（调用方据此决定是否发「排队中」事件）。 */
    Ticket tryAcquire();

    /** 排队等待，直到拿到票据；线程被中断时抛 {@link InterruptedException}。 */
    Ticket acquire() throws InterruptedException;

    interface Ticket {
        /** 归还槽位；必须幂等。 */
        void release();
    }

    static LlmRequestAdmission none() {
        return NoAdmission.INSTANCE;
    }

    /** 空实现：所有请求立即放行。 */
    final class NoAdmission implements LlmRequestAdmission {

        private static final NoAdmission INSTANCE = new NoAdmission();
        private static final Ticket NO_TICKET = () -> {
        };

        private NoAdmission() {
        }

        @Override
        public Ticket tryAcquire() {
            return NO_TICKET;
        }

        @Override
        public Ticket acquire() {
            return NO_TICKET;
        }
    }

    /**
     * 一次尝试的准入：把「取票 → 还票」包成幂等对象，配合 try-with-resources 或 finally 使用。
     */
    final class Attempt implements AutoCloseable {

        private final Ticket ticket;
        private final boolean queued;
        private boolean released;

        private Attempt(Ticket ticket, boolean queued) {
            this.ticket = ticket;
            this.queued = queued;
        }

        /** 无准入端口时的空票据。 */
        public static Attempt none() {
            return new Attempt(null, false);
        }

        /** 取一次准入票据（快路径优先，未命中则排队）。 */
        public static Attempt acquire(LlmRequestAdmission admission) throws InterruptedException {
            if (admission == null) {
                return none();
            }
            Ticket fast = admission.tryAcquire();
            if (fast != null) {
                return new Attempt(fast, false);
            }
            return new Attempt(admission.acquire(), true);
        }

        /** 准入票据；无准入端口时为 null。 */
        public Ticket ticket() {
            return ticket;
        }

        /** 是否走了排队路径（调用方据此发「排队中」事件）。 */
        public boolean queued() {
            return queued;
        }

        /** 幂等归还：finally 与「退避 sleep 之前」两处都会调。 */
        public void release() {
            if (released || ticket == null) {
                return;
            }
            released = true;
            ticket.release();
        }

        @Override
        public void close() {
            release();
        }
    }
}
