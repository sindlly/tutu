-- V8：奖励可配一张预制卡通图标（本地静态资源，不走上传）
-- icon 为白名单 key（前端 assets/rewards/{key}.png），空则前端用默认礼物图。

ALTER TABLE t_reward
    ADD COLUMN icon VARCHAR(32) NULL COMMENT '预制卡通图标 key' AFTER title;
