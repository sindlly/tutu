package com.dxy.homework.client;

import com.dxy.homework.model.OcrLine;
import com.dxy.homework.model.PreparedImage;
import java.util.List;

/**
 * OCR 能力抽象。
 *
 * <p>抽接口有两个实际原因：一是本地没配密钥时要有明确的行为（{@link #enabled()} 返回 false，
 * 调用方直接跳过，不打日志噪音）；二是后面真要换供应商，改实现类即可。
 */
public interface OcrClient {

    boolean enabled();

    String name();

    /**
     * @param pageSeq 页序号，从 1 开始，用于多页作业区分归属
     * @throws OcrException 任何失败都抛异常，由调用方决定是否降级
     */
    List<OcrLine> recognize(PreparedImage image, int pageSeq);
}
