package org.wwz.ai.domain.agent.runtime.desktopcontrol;

import java.util.List;
import java.util.Optional;

public interface IDesktopControlRepository {

    void insert(DesktopControlRecord record);

    Optional<DesktopControlRecord> findByControlId(String controlId);

    Optional<DesktopControlRecord> findByResumeRequestId(String resumeRequestId);

    List<DesktopControlRecord> listOpenBySessionId(String sessionId);

    boolean hasOpenBySessionId(String sessionId);

    boolean casCompletePending(String controlId, String visitorId, String resumeRequestId);

    boolean casClaimResume(String resumeRequestId, String visitorId);

    boolean markCompleted(String controlId);

    boolean markStatus(String controlId, String status);

    boolean casCancel(String controlId, String visitorId);
}
