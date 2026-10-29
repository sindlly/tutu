package com.dxy.homework.vo;

/** 奖励中心条目。 */
public record RewardVo(
        Long id,
        String title,
        String icon,
        Integer cost,
        /** 1 上架 2 下架 */
        Integer status
) {
}
