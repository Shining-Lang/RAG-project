package com.lsn.ragkb.service.index;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "index.kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalIndexTaskDispatcher implements IndexTaskDispatcher {

    private final IndexTaskLauncher taskLauncher;

    @Override
    public void dispatchFromMinio(Long taskId, Long docId,
                                  Long userId, String departmentId, String role) {
        taskLauncher.launchFromMinio(taskId, docId, userId, departmentId, role);
    }

    @Override
    public void dispatchWithText(Long taskId, Long docId, String textContent,
                                 Long userId, String departmentId, String role) {
        taskLauncher.launchWithText(taskId, docId, textContent, userId, departmentId, role);
    }
}
