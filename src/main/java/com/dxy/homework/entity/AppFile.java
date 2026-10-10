package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 文件元数据。文件内容存在 COS 私有桶，库里只留 key 与校验信息。 */
@Data
@TableName("t_file")
public class AppFile {

    public static final int BIZ_HOMEWORK_IMAGE = 1;
    public static final int BIZ_MISTAKE_CROP = 2;
    public static final int BIZ_MISTAKE_CARD = 3;
    public static final int BIZ_AVATAR = 4;

    /** 允许客户端主动上传的类型。错题裁剪图/卡片由服务端生成，不接受客户端指定。 */
    public static final java.util.Set<Integer> CLIENT_UPLOAD_BIZ_TYPES =
            java.util.Set.of(BIZ_HOMEWORK_IMAGE, BIZ_AVATAR);

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户，读取时必须校验，避免越权拿到别人的作业照片 */
    private Long userId;

    private Integer bizType;

    /** COS 对象 key */
    private String fileKey;

    private String mime;
    private Integer size;
    private Integer width;
    private Integer height;

    /** 内容指纹，用于同一张图重复提交时复用 */
    private String sha256;

    /** 1有效 0待清理 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
