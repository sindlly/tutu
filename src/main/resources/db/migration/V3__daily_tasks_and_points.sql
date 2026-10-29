-- ---------------------------------------------------------------- 10. 每日任务（家长动态添加，挂孩子名下）
CREATE TABLE t_task
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_id BIGINT UNSIGNED NOT NULL COMMENT '孩子。任务数据主权与错题一致',
    user_id    BIGINT UNSIGNED          DEFAULT NULL COMMENT '创建人（家长）。系统种子任务为 NULL',
    title      VARCHAR(64)     NOT NULL COMMENT '任务名，如「口算练习一页」',
    points     INT             NOT NULL COMMENT '完成奖励积分，1-999',
    sort_no    INT             NOT NULL DEFAULT 0,
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 2归档（归档不物理删，历史流水还要引用）',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_student (student_id, status, sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='每日任务定义';

-- ---------------------------------------------------------------- 11. 任务打卡（每天每任务最多一条，unique 兜住并发重复打卡）
CREATE TABLE t_task_completion
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id         BIGINT UNSIGNED NOT NULL,
    student_id      BIGINT UNSIGNED NOT NULL COMMENT '冗余存孩子，统计免 join',
    complete_date   DATE            NOT NULL COMMENT '打卡日，跨天自动「重置」靠它',
    points_awarded  INT             NOT NULL COMMENT '奖励积分快照：任务后来改分，历史打卡不变',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '打卡操作人（家长）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_date (task_id, complete_date),
    KEY idx_student_date (student_id, complete_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='任务打卡记录';

-- ---------------------------------------------------------------- 12. 积分流水（余额=SUM(delta)，唯一事实来源，不做冗余余额字段防不一致）
CREATE TABLE t_points_log
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_id  BIGINT UNSIGNED NOT NULL COMMENT '孩子。积分跟人（孩子）走',
    delta       INT             NOT NULL COMMENT '变动值，正=获得 负=消耗',
    source      TINYINT         NOT NULL COMMENT '1任务打卡 2家长调整/兑换',
    ref_id      BIGINT UNSIGNED          DEFAULT NULL COMMENT '来源单据 id：任务打卡 id / NULL',
    reason      VARCHAR(128)             DEFAULT NULL COMMENT '家长调整时的备注（兑换了什么）',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '操作人',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_student (student_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='积分流水';
