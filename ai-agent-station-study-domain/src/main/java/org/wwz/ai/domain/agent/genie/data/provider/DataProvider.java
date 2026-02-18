package org.wwz.ai.domain.agent.genie.data.provider;


import org.wwz.ai.domain.agent.genie.data.QueryResult;


public interface DataProvider<T extends DataQueryRequest> {

    QueryResult queryData(T request) throws Exception;

    boolean queryForTest(T request);
}
