package org.wwz.ai.domain.agent.runtime.dto;


import lombok.Data;
import org.wwz.ai.domain.agent.runtime.enums.RoleType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * 记忆类 - 管理代理的消息历史
 */
@Data
public class Memory {
    private List<Message> messages = new ArrayList<>();

    /**
     * 添加消息
     */
    public void addMessage(Message message) {
        messages.add(message);
    }

    /**
     * 添加多条消息
     */
    public void addMessages(List<Message> newMessages) {
        messages.addAll(newMessages);
    }

    /**
     * 获取最后一条消息
     */
    public Message getLastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    /**
     * 清空记忆
     */
    public void clear() {
        messages.clear();
    }

    /**
     * 清空工具执行历史
     */
    public void clearToolContext() {
        Iterator<Message> iterator = messages.iterator();
        while (iterator.hasNext()) {
            Message message = iterator.next();
            if (message.getRole() == RoleType.TOOL) {
                iterator.remove();
            }
            if (message.getRole() == RoleType.ASSISTANT && Objects.nonNull(message.getToolCalls()) && !message.getToolCalls().isEmpty()) {
                iterator.remove();
            }
            if (Objects.nonNull(message.getContent()) && message.getContent().startsWith("根据当前状态和可用工具，确定下一步行动")) {
                iterator.remove();
            }
        }
    }

    /**
     * 格式化Message
     */
    public String getFormatMessage() {
        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            sb.append(String.format("role:%s content:%s\n", message.getRole(), message.getContent()));
        }
        return sb.toString();
    }


    /**
     * 获取消息数量
     */
    public int size() {
        return messages.size();
    }

    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }


    public Message get(int index) {
        return messages.get(index);
    }
}