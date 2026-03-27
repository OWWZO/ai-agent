package org.wwz.ai.domain.agent.reactor.data.provider;


import org.wwz.ai.domain.agent.reactor.data.QueryResult;


public interface DataProvider<T extends DataQueryRequest> {

    QueryResult queryData(T request) throws Exception;

    boolean queryForTest(T request);
}
