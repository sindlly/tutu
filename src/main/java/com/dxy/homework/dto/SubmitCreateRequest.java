package com.dxy.homework.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 创建批改任务。
 *
 * @param bizNo     客户端生成的幂等号。同一张作业重复提交必须复用同一个值，
 *                  服务端靠它返回同一任务而不是重复批改、重复扣额度
 * @param studentId 必须属于当前登录用户
 * @param fileIds   已上传的作业原图，按页序排列
 */
public record SubmitCreateRequest(
        @NotBlank(message = "不能为空") @Size(max = 40, message = "最长 40 字符") String bizNo,
        @NotNull(message = "不能为空") Long studentId,
        @NotBlank(message = "不能为空") @Size(max = 16, message = "最长 16 字符") String subject,
        @Min(value = 1, message = "年级范围 1-12") @Max(value = 12, message = "年级范围 1-12") Integer grade,
        @NotEmpty(message = "至少要有一张作业照片")
        @Size(max = 4, message = "单次最多 4 页") List<Long> fileIds
) {
}
