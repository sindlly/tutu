package com.dxy.homework.vo;

import com.dxy.homework.entity.HomeworkSubmit;

import java.math.BigDecimal;

/** 批改任务状态。客户端靠 status 决定是继续轮询还是展示结果。 */
public record SubmitVo(
        Long submitId,
        Integer status,
        String statusText,
        Integer imageCount,
        Integer totalCount,
        Integer rightCount,
        Integer wrongCount,
        BigDecimal score,
        String errorMsg,
        boolean finished
) {

    public static SubmitVo from(HomeworkSubmit submit) {
        return new SubmitVo(
                submit.getId(),
                submit.getStatus(),
                statusText(submit.getStatus()),
                submit.getImageCount(),
                submit.getTotalCount(),
                submit.getRightCount(),
                submit.getWrongCount(),
                submit.getScore(),
                // 只在失败/取消时对外给出原因，避免历史失败信息在成功后继续展示
                (submit.getStatus() != null
                        && (submit.getStatus() == HomeworkSubmit.STATUS_FAILED
                        || submit.getStatus() == HomeworkSubmit.STATUS_CANCELED))
                        ? submit.getErrorMsg() : null,
                submit.getStatus() != null
                        && (submit.getStatus() == HomeworkSubmit.STATUS_SUCCESS
                        || submit.getStatus() == HomeworkSubmit.STATUS_FAILED
                        || submit.getStatus() == HomeworkSubmit.STATUS_CANCELED));
    }

    public static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case HomeworkSubmit.STATUS_PENDING -> "排队中";
            case HomeworkSubmit.STATUS_REVIEWING -> "内容审核中";
            case HomeworkSubmit.STATUS_GRADING -> "批改中";
            case HomeworkSubmit.STATUS_SUCCESS -> "批改完成";
            case HomeworkSubmit.STATUS_FAILED -> "批改失败";
            case HomeworkSubmit.STATUS_CANCELED -> "已取消";
            default -> "未知";
        };
    }
}
