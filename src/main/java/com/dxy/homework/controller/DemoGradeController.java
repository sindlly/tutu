package com.dxy.homework.controller;

import com.dxy.homework.common.R;
import com.dxy.homework.client.OcrException;
import com.dxy.homework.config.DemoProperties;
import com.dxy.homework.model.GradeOutcome;
import com.dxy.homework.model.OcrLine;
import com.dxy.homework.model.PreparedImage;
import com.dxy.homework.service.GradingService;
import com.dxy.homework.service.ImagePreparer;
import com.dxy.homework.service.OcrLayoutService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 单图快速验证接口，方便用 curl / Postman 反复试同一张照片。
 *
 * <pre>
 * curl -X POST http://localhost:8080/api/demo/grade \
 *   -F "file=@samples/hw1.jpg" -F "subject=math" -F "grade=3"
 * </pre>
 */
@RestController
@RequestMapping("/api/demo")
public class DemoGradeController {

    /** 单张上行大小上限，防止把内存打爆。 */
    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif");

    private final GradingService gradingService;
    private final DemoProperties demoProperties;
    private final ImagePreparer imagePreparer;
    private final OcrLayoutService ocrLayoutService;

    public DemoGradeController(GradingService gradingService,
                               DemoProperties demoProperties,
                               ImagePreparer imagePreparer,
                               OcrLayoutService ocrLayoutService) {
        this.gradingService = gradingService;
        this.demoProperties = demoProperties;
        this.imagePreparer = imagePreparer;
        this.ocrLayoutService = ocrLayoutService;
    }

    @PostMapping(value = "/grade", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<GradeOutcome> grade(@RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) String subject,
                                 @RequestParam(required = false) Integer grade,
                                 @RequestParam(required = false) String mode) throws IOException {
        if (file == null || file.isEmpty()) {
            return R.fail(400, "请上传作业图片");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            return R.fail(400, "图片超过 20MB，请压缩后重试");
        }

        String suffix = safeSuffix(file.getOriginalFilename());
        Path temp = Files.createTempFile("homework-", suffix);
        try {
            file.transferTo(temp);
            return R.ok(gradingService.grade(
                    temp,
                    subject == null ? demoProperties.subject() : subject,
                    grade == null ? demoProperties.grade() : grade,
                    mode == null ? demoProperties.mode() : mode));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * OCR 识别验证接口：上传一张作业，直接看腾讯云 OCR 认出了哪些行、坐标是多少。
     *
     * <pre>
     * curl -X POST http://localhost:8080/api/demo/ocr -F "file=@samples/xxx.jpg"
     * </pre>
     *
     * <p>调这个接口不经过批改模型，只花 OCR 的钱，用来确认"行坐标拿得准不准"最快。
     */
    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<OcrProbeVo> ocr(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return R.fail(400, "请上传作业图片");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            return R.fail(400, "图片超过 20MB，请压缩后重试");
        }

        PreparedImage prepared = imagePreparer.prepare(file.getBytes(),
                file.getOriginalFilename() == null ? "probe.jpg" : file.getOriginalFilename());
        List<OcrLine> lines;
        try {
            lines = ocrLayoutService.recognize(List.of(prepared));
        } catch (OcrException ex) {
            // 直接把原因返回：这个接口就是拿来排查 OCR 配置的，吞成"服务异常"没有意义
            return R.fail(502, ex.getMessage());
        }
        return R.ok(new OcrProbeVo(ocrLayoutService.name(), prepared.width(), prepared.height(),
                lines.stream()
                        .map(l -> new OcrLineVo(l.text(), round(l.x()), round(l.y()), round(l.w()), round(l.h()),
                                l.confidence()))
                        .toList()));
    }

    public record OcrProbeVo(String provider, int width, int height, List<OcrLineVo> lines) {
    }

    public record OcrLineVo(String text, double x, double y, double w, double h, Double confidence) {
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    /** 只取白名单内的扩展名，避免原始文件名污染临时文件路径。 */
    private String safeSuffix(String originalFilename) {
        if (originalFilename == null) {
            return ".jpg";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return ".jpg";
        }
        String ext = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.contains(ext) ? "." + ext : ".jpg";
    }
}
