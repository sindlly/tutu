-- 多孩 + 多人绑定：数据主权从「家长」移到「孩子」。
--
-- 1. 每个孩子一个 6 位绑定编号：分享给家人（妈妈/爷爷/家教），对方在小程序里
--    输入编号即可绑定同一个孩子，看到同一份错题数据。
--    不用数据库自增 id 做绑定标识 —— 自增 id 可被连续枚举，绑定接口等于裸奔；
--    MD5(id + 盐) 截 6 位，无法从编号反推 id，也无法枚举邻居。
-- 2. t_user_student：用户-孩子关系表。孩子归属人（创建者）relation=1 之外，
--    其他人通过绑定进来 relation=2。查询「我能看哪些孩子」统一走这张表。

ALTER TABLE t_student
    ADD COLUMN bind_code CHAR(6) NULL COMMENT '孩子编号，用于家人绑定' AFTER school;

ALTER TABLE t_student
    ADD UNIQUE KEY uk_bind_code (bind_code);

-- 存量孩子补编号。MD5 十六进制只含 0-9a-f，没有 O/I/l 等易混淆字符
UPDATE t_student
SET bind_code = UPPER(SUBSTR(MD5(CONCAT(id, 'homework-grader-bind-2026')), 1, 6))
WHERE bind_code IS NULL;

CREATE TABLE t_user_student
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '用户',
    student_id BIGINT UNSIGNED NOT NULL COMMENT '孩子',
    relation   TINYINT         NOT NULL DEFAULT 2 COMMENT '1创建者 2家人绑定',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_student (user_id, student_id),
    KEY idx_student (student_id, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户-孩子关系';

-- 存量孩子的归属人补进关系表
INSERT INTO t_user_student (user_id, student_id, relation)
SELECT user_id, id, 1
FROM t_student
WHERE deleted = 0;
