package org.wwz.ai.domain.agent.rag.model.config;

/** Fixed business guidance for the built-in analytical models. */
public final class DataQueryModelPrompts {

    private static final String SALES_MODEL_ID = "t_qtpbgamccmrctthlurauclckq";
    private static final String PURCHASE_MODEL_ID = "t_uegarulwipfivhutcvyawaoex";

    private DataQueryModelPrompts() {
    }

    public static String businessPromptFor(String modelId) {
        if (SALES_MODEL_ID.equals(modelId)) {
            return "order_date为日维度数据，如果统计月份要使用DATE_FORMAT(`order_date`, '%Y-%m')";
        }
        if (PURCHASE_MODEL_ID.equals(modelId)) {
            return "采购日期为日维度数据，如果统计月份要使用DATE_FORMAT(`采购日期`, '%Y-%m')";
        }
        return null;
    }
}
