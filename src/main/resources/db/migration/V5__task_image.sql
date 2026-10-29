-- V5：任务本身可配一张「任务详情图」
-- 家长布置任务时内容常在图片上（作业页照片、手写任务卡），打卡人看图做任务。
-- 图片走 t_file 体系，bizType=6（任务图），归属=创建人；归属校验同打卡凭证。

ALTER TABLE t_task
    ADD COLUMN image_file_id BIGINT NULL COMMENT '任务详情图，t_file.id' AFTER points,
    ADD KEY idx_image_file (image_file_id);
