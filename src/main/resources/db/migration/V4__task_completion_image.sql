-- V4：任务打卡支持凭证图片
-- 打卡时可选上传一张图（走既有 t_file 体系，bizType=5，归属=打卡人）。
-- 查询侧（今日面板/历史记录）由服务端 presign 后随 VO 下发 URL，
-- 家人共享场景下校验的是「对该孩子有访问权」，而非文件 owner。

ALTER TABLE t_task_completion
    ADD COLUMN image_file_id BIGINT NULL COMMENT '打卡凭证图片，t_file.id' AFTER points_awarded,
    ADD KEY idx_image_file (image_file_id);
