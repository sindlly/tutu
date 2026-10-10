package com.dxy.homework.vo;

import com.dxy.homework.entity.AppFile;

/** 上传结果。客户端后续用 fileId 提交批改任务，不直接接触存储 key。 */
public record UploadedFileVo(
        Long fileId,
        Integer width,
        Integer height,
        Integer size,
        /** 是否命中了同内容去重（同一张图重复提交不会重复占用存储） */
        boolean reused
) {

    public static UploadedFileVo of(AppFile file, boolean reused) {
        return new UploadedFileVo(file.getId(), file.getWidth(), file.getHeight(), file.getSize(), reused);
    }
}
