package com.dxy.homework.vo;

import com.dxy.homework.entity.PointsLog;
import java.time.LocalDateTime;

/** 积分流水条目。 */
public record PointsLogVo(
        Long id,
        int delta,
        int source,
        String sourceLabel,
        String reason,
        LocalDateTime createdAt
) {

    public static PointsLogVo from(PointsLog log) {
        return new PointsLogVo(log.getId(), log.getDelta() == null ? 0 : log.getDelta(),
                log.getSource() == null ? 0 : log.getSource(),
                log.getSource() != null && log.getSource() == PointsLog.SOURCE_TASK ? "任务" : "家长",
                log.getReason(), log.getCreatedAt());
    }
}
