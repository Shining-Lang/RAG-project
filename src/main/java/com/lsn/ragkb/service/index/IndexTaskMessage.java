package com.lsn.ragkb.service.index;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexTaskMessage {

    private Long taskId;

    private Long docId;

    private String taskType;

    private Long userId;

    private String departmentId;

    private String role;
}
