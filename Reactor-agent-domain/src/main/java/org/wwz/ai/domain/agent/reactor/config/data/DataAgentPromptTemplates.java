package org.wwz.ai.domain.agent.reactor.config.data;

/** 问数模型的固定业务提示。 */
public final class DataAgentPromptTemplates {

    public static final String SALES_DATA_MODEL_ID = "t_qtpbgamccmrctthlurauclckq";
    public static final String PURCHASE_DATA_MODEL_ID = "t_uegarulwipfivhutcvyawaoex";

    private DataAgentPromptTemplates() {
    }

    public static String businessPromptFor(String modelId) {
        if (SALES_DATA_MODEL_ID.equals(modelId)) {
            return "order_date为日维度数据，如果统计月份要使用DATE_FORMAT(`order_date`, '%Y-%m')";
        }
        if (PURCHASE_DATA_MODEL_ID.equals(modelId)) {
            return "采购日期为日维度数据，如果统计月份要使用DATE_FORMAT(`采购日期`, '%Y-%m')";
        }
        return null;
    }
}
