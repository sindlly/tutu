-- 拍照批改作业：初始表结构
-- 所有表统一 utf8mb4 / utf8mb4_unicode_ci，避免联表时 collation 不一致
-- 时间统一用 DATETIME（不用 TIMESTAMP，避免 2038 与时区隐式转换问题）

-- ---------------------------------------------------------------- 1. 用户
CREATE TABLE t_user
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    open_id       VARCHAR(64)     NOT NULL COMMENT '微信 openid',
    union_id      VARCHAR(64)              DEFAULT NULL COMMENT '微信 unionid',
    nickname      VARCHAR(64)              DEFAULT NULL COMMENT '昵称',
    avatar_url    VARCHAR(512)             DEFAULT NULL COMMENT '头像',
    phone         VARCHAR(20)              DEFAULT NULL COMMENT '手机号',
    role          TINYINT         NOT NULL DEFAULT 1 COMMENT '1家长 2老师',
    quota_daily   INT             NOT NULL DEFAULT 10 COMMENT '每日免费批改次数',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    last_login_at DATETIME                 DEFAULT NULL COMMENT '最近登录时间',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_open_id (open_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户';

-- ---------------------------------------------------------------- 2. 学生（一个家长可有多个孩子）
CREATE TABLE t_student
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '所属家长',
    name       VARCHAR(32)     NOT NULL COMMENT '称呼，不强制真实姓名',
    grade      TINYINT         NOT NULL COMMENT '1-12 年级',
    school     VARCHAR(64)              DEFAULT NULL,
    sort_no    INT             NOT NULL DEFAULT 0 COMMENT '排序',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='学生';

-- ---------------------------------------------------------------- 3. 文件元数据（实际内容在 COS 私有桶）
CREATE TABLE t_file
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '归属用户，用于越权校验',
    biz_type   TINYINT         NOT NULL COMMENT '1作业原图 2错题裁剪图 3错题卡片 4头像',
    file_key   VARCHAR(255)    NOT NULL COMMENT 'COS 对象 key',
    mime       VARCHAR(64)              DEFAULT NULL,
    size       INT             NOT NULL DEFAULT 0 COMMENT '字节',
    width      INT                      DEFAULT NULL,
    height     INT                      DEFAULT NULL,
    sha256     CHAR(64)                 DEFAULT NULL COMMENT '内容指纹，用于去重',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1有效 0待清理',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_biz (user_id, biz_type, created_at),
    KEY idx_sha (sha256)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文件元数据';

-- ---------------------------------------------------------------- 4. 批改任务
CREATE TABLE t_homework_submit
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    biz_no            VARCHAR(40)     NOT NULL COMMENT '客户端幂等号',
    user_id           BIGINT UNSIGNED NOT NULL,
    student_id        BIGINT UNSIGNED NOT NULL,
    subject           VARCHAR(16)     NOT NULL COMMENT 'math/english/...',
    grade             TINYINT         NOT NULL,
    status            TINYINT         NOT NULL DEFAULT 0 COMMENT '0待处理 1审核中 2批改中 3成功 4失败 5已取消',
    image_count       TINYINT         NOT NULL DEFAULT 1,
    total_count       INT                      DEFAULT NULL,
    right_count       INT                      DEFAULT NULL,
    wrong_count       INT                      DEFAULT NULL,
    score             DECIMAL(5, 2)             DEFAULT NULL,
    duration_ms       INT                      DEFAULT NULL,
    model             VARCHAR(32)              DEFAULT NULL,
    prompt_tokens     INT                      DEFAULT NULL,
    completion_tokens INT                      DEFAULT NULL,
    cost              DECIMAL(10, 6)           DEFAULT NULL,
    error_code        VARCHAR(64)              DEFAULT NULL,
    error_msg         VARCHAR(512)             DEFAULT NULL,
    retry_count       TINYINT         NOT NULL DEFAULT 0,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at       DATETIME                 DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_no (biz_no),
    KEY idx_user_time (user_id, created_at),
    KEY idx_status (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='批改任务';

-- ---------------------------------------------------------------- 5. 任务-原图
CREATE TABLE t_submit_image
(
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    submit_id BIGINT UNSIGNED NOT NULL,
    seq       TINYINT         NOT NULL COMMENT '页序，从1开始',
    file_id   BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    KEY idx_submit (submit_id, seq)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='任务原图';

-- ---------------------------------------------------------------- 6. 题目
CREATE TABLE t_question
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    submit_id      BIGINT UNSIGNED NOT NULL,
    seq            SMALLINT        NOT NULL COMMENT '题序',
    question_no    VARCHAR(32)              DEFAULT NULL COMMENT '卷面题号，如 口算15',
    question_type  TINYINT         NOT NULL DEFAULT 8 COMMENT '1单选2多选3判断4填空5计算6解答7作文8其他',
    stem           TEXT                     DEFAULT NULL COMMENT '题干',
    student_answer VARCHAR(512)             DEFAULT NULL COMMENT '学生答案',
    correct_answer VARCHAR(512)             DEFAULT NULL COMMENT '正确答案',
    is_correct     TINYINT         NOT NULL COMMENT '1对 0错',
    confidence     DECIMAL(4, 3)             DEFAULT NULL COMMENT 'AI 置信度',
    wrong_reason   VARCHAR(512)             DEFAULT NULL,
    analysis       TEXT                     DEFAULT NULL,
    knowledge_points JSON                   DEFAULT NULL COMMENT '知识点数组',
    difficulty     TINYINT                  DEFAULT NULL COMMENT '1-5',
    page_seq       TINYINT         NOT NULL DEFAULT 1 COMMENT '所在页',
    bbox_x         DECIMAL(6, 5)             DEFAULT NULL COMMENT '归一化 0-1',
    bbox_y         DECIMAL(6, 5)             DEFAULT NULL,
    bbox_w         DECIMAL(6, 5)             DEFAULT NULL,
    bbox_h         DECIMAL(6, 5)             DEFAULT NULL,
    bbox_source    TINYINT                  DEFAULT NULL COMMENT '1AI 2OCR 3手动',
    crop_file_id   BIGINT UNSIGNED          DEFAULT NULL COMMENT '错题裁剪图',
    user_corrected TINYINT         NOT NULL DEFAULT 0 COMMENT '用户是否手动纠正过判定',
    needs_check    TINYINT         NOT NULL DEFAULT 0 COMMENT '是否需人工核对',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_submit (submit_id, seq),
    KEY idx_submit_wrong (submit_id, is_correct)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='题目';

-- ---------------------------------------------------------------- 7. 错题本
CREATE TABLE t_mistake
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    student_id      BIGINT UNSIGNED NOT NULL,
    question_id     BIGINT UNSIGNED NOT NULL,
    submit_id       BIGINT UNSIGNED NOT NULL,
    subject         VARCHAR(16)     NOT NULL,
    grade           TINYINT         NOT NULL,
    question_type   TINYINT         NOT NULL,
    stem_snapshot   TEXT                     DEFAULT NULL COMMENT '快照，避免题目被改后历史失真',
    answer_snapshot VARCHAR(512)             DEFAULT NULL,
    knowledge_key   VARCHAR(64)              DEFAULT NULL COMMENT '主知识点，用于分组',
    crop_file_id    BIGINT UNSIGNED          DEFAULT NULL COMMENT '原图裁剪',
    card_file_id    BIGINT UNSIGNED          DEFAULT NULL COMMENT '错题卡片',
    mastery_level   TINYINT         NOT NULL DEFAULT 0 COMMENT '0-5 掌握度',
    review_count    INT             NOT NULL DEFAULT 0,
    right_streak    TINYINT         NOT NULL DEFAULT 0 COMMENT '连续答对次数',
    last_review_at  DATETIME                 DEFAULT NULL,
    next_review_at  DATETIME                 DEFAULT NULL COMMENT '下次复习时间',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '0在册 1已掌握 2已移除',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_question (question_id),
    KEY idx_review (user_id, student_id, status, next_review_at),
    KEY idx_subject (user_id, subject, created_at),
    KEY idx_knowledge (user_id, knowledge_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='错题本';

-- ---------------------------------------------------------------- 8. 复习记录
CREATE TABLE t_mistake_review
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    mistake_id BIGINT UNSIGNED NOT NULL,
    user_id    BIGINT UNSIGNED NOT NULL,
    result     TINYINT         NOT NULL COMMENT '1答对 0答错',
    cost_ms    INT                      DEFAULT NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mistake (mistake_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='错题复习记录';

-- ---------------------------------------------------------------- 9. AI 调用日志
CREATE TABLE t_ai_call_log
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    submit_id         BIGINT UNSIGNED          DEFAULT NULL,
    scene             VARCHAR(32)     NOT NULL COMMENT '场景标识',
    model             VARCHAR(32)     NOT NULL,
    request_id        VARCHAR(64)              DEFAULT NULL COMMENT '服务端返回 id，用于对账',
    http_status       SMALLINT                 DEFAULT NULL,
    latency_ms        INT                      DEFAULT NULL,
    prompt_tokens     INT                      DEFAULT NULL,
    completion_tokens INT                      DEFAULT NULL,
    cache_hit_tokens  INT                      DEFAULT NULL,
    cost              DECIMAL(10, 6)           DEFAULT NULL,
    retry_no          TINYINT         NOT NULL DEFAULT 0,
    error_code        VARCHAR(64)              DEFAULT NULL,
    error_msg         VARCHAR(512)             DEFAULT NULL,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_submit (submit_id),
    KEY idx_time (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='AI 调用日志';

-- ---------------------------------------------------------------- 10. 每日额度用量
CREATE TABLE t_user_usage
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    usage_date DATE            NOT NULL,
    used_count INT             NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_date (user_id, usage_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='每日额度用量';
