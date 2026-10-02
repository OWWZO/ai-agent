package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.infrastructure.dao.reactor.po.DesktopControlPO;

import java.util.List;

@Mapper
public interface IDesktopControlDao {

    int insert(DesktopControlPO row);

    DesktopControlPO selectByControlId(@Param("controlId") String controlId);

    DesktopControlPO selectByResumeRequestId(@Param("resumeRequestId") String resumeRequestId);

    List<DesktopControlPO> selectOpenBySessionId(@Param("sessionId") String sessionId);

    int countOpenBySessionId(@Param("sessionId") String sessionId);

    int casCompletePending(@Param("controlId") String controlId,
                           @Param("visitorId") String visitorId,
                           @Param("resumeRequestId") String resumeRequestId);

    int casClaimResume(@Param("resumeRequestId") String resumeRequestId,
                       @Param("visitorId") String visitorId);

    int markCompleted(@Param("controlId") String controlId);

    int markFailed(@Param("controlId") String controlId, @Param("status") String status);

    int casCancel(@Param("controlId") String controlId,
                  @Param("visitorId") String visitorId,
                  @Param("fromStatuses") List<String> fromStatuses);
}
