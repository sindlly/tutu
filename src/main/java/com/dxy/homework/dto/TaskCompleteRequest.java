package com.dxy.homework.dto;

/** 打卡请求。图片可选：传了会作为/更新为该次打卡的凭证。 */
public record TaskCompleteRequest(Long imageFileId) {
}
