package org.wwz.ai.domain.agent.rag.model.query;

import lombok.Data;
import org.wwz.ai.domain.agent.rag.model.schema.DataQueryModelDescriptor;

import java.util.List;

/** Business query context supplied to NL2SQL generation. */
@Data
public class Nl2SqlQuery {
    private String requestId;
    private String query;
    private List<String> modelCodeList;
    private List<DataQueryModelDescriptor> schemaInfo;
    private String currentDateInfo = "当前时间信息：%s,%s";
    private String traceId;
    private String recallType = "only_recall";
    private Boolean stream = true;
    private String userInfo = "";
    private String dbType;
    private boolean useVector;
    private boolean useElastic;
}
