-- V7：奖励中心（家长预设可兑换奖励，孩子积分够了就来换）
-- 兑换走 t_points_log（source=3），余额=SUM(delta) 不变；奖励定义可下架（软删）。

CREATE TABLE t_reward (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL COMMENT '创建者',
    title VARCHAR(20) NOT NULL COMMENT '奖励名，如「冰淇淋」',
    cost INT NOT NULL COMMENT '所需积分',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1上架 2下架',
    sort_no INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_student_status (student_id, status)
) COMMENT '奖励中心 · 可兑换奖励';
