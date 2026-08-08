package com.lsn.ragkb.service.index;

public interface IndexTaskDispatcher {

    void dispatchFromMinio(Long taskId, Long docId,
                           Long userId, String departmentId, String role);

    void dispatchWithText(Long taskId, Long docId, String textContent,
                          Long userId, String departmentId, String role);
}
