package com.dxy.homework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 验证 Demo 的运行参数，全部可通过命令行 --demo.xxx=yyy 覆盖。
 */
@ConfigurationProperties(prefix = "demo")
public record DemoProperties(
        /** 待批改图片路径，逗号分隔。 */
        @DefaultValue("") String images,
        /** 待批改图片目录。 */
        @DefaultValue("") String dir,
        @DefaultValue("math") String subject,
        @DefaultValue("3") int grade,
        /** grade = 完整批改；ocr = 只识别不判分。 */
        @DefaultValue("grade") String mode,
        /** 无密钥时用 mock 结果跑通链路。 */
        @DefaultValue("false") boolean mock,
        @DefaultValue("out") String outDir,
        /** 命令行模式下处理完即退出。 */
        @DefaultValue("true") boolean exitAfterRun,
        /** 人工标注 CSV 路径。非空即进入评测模式：每张图重复批改 evalRepeat 次并统计指标。 */
        @DefaultValue("") String evalLabels,
        /** 评测模式下每张图重复批改的次数。单次结果噪声很大，至少跑 3 次。 */
        @DefaultValue("3") int evalRepeat,
        /** 从指定批改结果 JSON 导出标注草稿；非空时只做导出，不跑批改。 */
        @DefaultValue("") String evalExport,
        /** 标注草稿的输出目录。 */
        @DefaultValue("labels") String evalLabelDir
) {
}
