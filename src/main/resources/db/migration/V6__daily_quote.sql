-- V6：每日美句（首页第一屏，每天一句、按年级生成）
-- 由 DeepSeek 生成，符合孩子当前年龄的理解力；uk(date, grade) 保证一天一年级只生成一次。
-- 生成失败/无 Key 时走服务端内置降级池，接口永远有产出。

CREATE TABLE t_daily_quote (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quote_date DATE NOT NULL COMMENT '哪天的句子',
    grade TINYINT NOT NULL COMMENT '年级（决定句子难度）',
    text VARCHAR(255) NOT NULL COMMENT '美句内容',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_grade (quote_date, grade)
) COMMENT '每日美句';
