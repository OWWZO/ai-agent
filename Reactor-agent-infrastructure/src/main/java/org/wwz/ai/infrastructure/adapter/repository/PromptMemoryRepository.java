package org.wwz.ai.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.domain.agent.memory.IPromptMemoryRepository;
import org.wwz.ai.domain.agent.memory.PromptMemoryLease;
import org.wwz.ai.domain.agent.memory.PromptMemoryMessage;
import org.wwz.ai.domain.agent.memory.PromptMemoryPublishCommand;
import org.wwz.ai.domain.agent.memory.PromptMemoryStreamKey;
import org.wwz.ai.domain.agent.memory.entity.PromptMemoryMessageRow;
import org.wwz.ai.domain.agent.memory.entity.PromptMemoryStream;
import org.wwz.ai.domain.agent.memory.entity.PromptMemoryTurn;
import org.wwz.ai.domain.agent.memory.model.PromptMemoryTurnStatus;
import org.wwz.ai.infrastructure.dao.reactor.IPromptMemoryMessageDao;
import org.wwz.ai.infrastructure.dao.reactor.IPromptMemoryStreamDao;
import org.wwz.ai.infrastructure.dao.reactor.IPromptMemoryTurnDao;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * 提示词记忆日志的 MySQL 持久化适配器。
 * LLM 执行期间只持有租约，真正写库仅发生在请求结束后的短事务内。
 */
@Repository
@RequiredArgsConstructor
public class PromptMemoryRepository implements IPromptMemoryRepository {

    private final IPromptMemoryStreamDao streamDao;
    private final IPromptMemoryTurnDao turnDao;
    private final IPromptMemoryMessageDao messageDao;

    @Override
    public List<PromptMemoryMessage> loadReadyMessages(PromptMemoryStreamKey key) {
        PromptMemoryStream stream = streamDao.queryByKey(key);
        if (stream == null) {
            return Collections.emptyList();
        }
        return messageDao.queryReadyByStreamId(stream.getId()).stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    public Optional<PromptMemoryLease> acquireLease(PromptMemoryStreamKey key, String requestId,
                                                     LocalDateTime now, Duration leaseDuration) {
        ensureStream(key);
        LocalDateTime leaseExpireAt = now.plus(leaseDuration);
        if (streamDao.acquireLease(key, requestId, now, leaseExpireAt) != 1) {
            return Optional.empty();
        }
        PromptMemoryStream stream = streamDao.queryByKey(key);
        if (stream == null || !requestId.equals(stream.getActiveRequestId())) {
            return Optional.empty();
        }
        return Optional.of(new PromptMemoryLease(key, stream.getId(), requestId,
                stream.getLatestTurnSeq(), leaseExpireAt));
    }

    @Override
    public void releaseLease(PromptMemoryLease lease) {
        if (lease != null) {
            streamDao.releaseLease(lease.streamId(), lease.requestId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(PromptMemoryPublishCommand command) {
        PromptMemoryLease lease = requireLease(command);
        PromptMemoryTurn completed = turnDao.queryByRequestId(lease.requestId());
        if (completed != null && PromptMemoryTurnStatus.READY == completed.getStatus()) {
            return;
        }

        PromptMemoryStream stream = streamDao.queryByKey(lease.key());
        if (stream == null || !lease.streamId().equals(stream.getId())
                || !lease.requestId().equals(stream.getActiveRequestId())
                || stream.getLeaseExpireAt() == null || !stream.getLeaseExpireAt().isAfter(LocalDateTime.now())
                || !lease.baselineTurnSeq().equals(stream.getLatestTurnSeq())) {
            throw new IllegalStateException("提示词记忆租约已失效");
        }

        List<PromptMemoryMessage> deltaMessages = command.getDeltaMessages() == null
                ? Collections.emptyList() : command.getDeltaMessages();
        int nextTurnSeq = lease.baselineTurnSeq() + 1;
        PromptMemoryTurn turn = PromptMemoryTurn.builder()
                .streamId(lease.streamId())
                .requestId(lease.requestId())
                .runId(command.getRunId())
                .turnSeq(nextTurnSeq)
                .baselineTurnSeq(lease.baselineTurnSeq())
                .status(PromptMemoryTurnStatus.BUILDING)
                .messageCount(deltaMessages.size())
                .startedAt(LocalDateTime.now())
                .build();
        if (turnDao.insertBuilding(turn) != 1) {
            PromptMemoryTurn existing = turnDao.queryByRequestId(lease.requestId());
            if (existing != null && PromptMemoryTurnStatus.READY == existing.getStatus()) {
                return;
            }
            throw new IllegalStateException("提示词记忆轮次正在构建");
        }

        if (!deltaMessages.isEmpty()) {
            messageDao.batchInsert(turn.getId(), toRows(turn.getId(), deltaMessages));
        }
        if (turnDao.markReady(turn.getId()) != 1
                || streamDao.advanceReadyTurn(lease.streamId(), lease.requestId(), lease.baselineTurnSeq(), nextTurnSeq) != 1) {
            throw new IllegalStateException("提示词记忆轮次发布失败");
        }
    }

    private void ensureStream(PromptMemoryStreamKey key) {
        if (streamDao.queryByKey(key) != null) {
            return;
        }
        streamDao.insertIgnore(PromptMemoryStream.builder()
                .sessionId(key.sessionId())
                .memoryScope(key.scope().name())
                .promptContractId(key.promptContractId())
                .toolContractId(key.toolContractId())
                .latestTurnSeq(0)
                .version(0)
                .build());
    }

    private PromptMemoryLease requireLease(PromptMemoryPublishCommand command) {
        if (command == null || command.getLease() == null) {
            throw new IllegalArgumentException("发布提示词记忆必须提供租约");
        }
        return command.getLease();
    }

    private List<PromptMemoryMessageRow> toRows(Long turnId, List<PromptMemoryMessage> messages) {
        return IntStream.range(0, messages.size())
                .mapToObj(index -> {
                    PromptMemoryMessage message = messages.get(index);
                    return PromptMemoryMessageRow.builder()
                            .turnId(turnId)
                            .seqNo(index + 1)
                            .role(message.getRole())
                            .content(message.getContent())
                            .base64Image(message.getBase64Image())
                            .toolCallId(message.getToolCallId())
                            .toolCallsJson(message.getToolCalls() == null ? null : JSON.toJSONString(message.getToolCalls()))
                            .build();
                })
                .toList();
    }

    private PromptMemoryMessage toMessage(PromptMemoryMessageRow row) {
        return PromptMemoryMessage.builder()
                .role(row.getRole())
                .content(row.getContent())
                .base64Image(row.getBase64Image())
                .toolCallId(row.getToolCallId())
                .toolCalls(row.getToolCallsJson() == null ? null
                        : JSON.parseObject(row.getToolCallsJson(), new TypeReference<>() {
                }))
                .build();
    }
}
