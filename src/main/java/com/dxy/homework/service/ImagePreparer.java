package com.dxy.homework.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.dxy.homework.common.BizException;
import com.dxy.homework.config.GradingProperties;
import com.dxy.homework.model.PreparedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 图片预处理：类型识别 → EXIF 摆正 → 等比压缩 → 编码 → base64。
 *
 * <p>为什么必须做 EXIF 摆正：手机竖拍的作业照片，像素数据往往是横躺的，只靠 orientation
 * 标记描述方向。如果直接把原始字节送给模型，模型看到的是一张转了 90 度的作业，识别率会崩。
 */
@Component
public class ImagePreparer {

    private static final Logger log = LoggerFactory.getLogger(ImagePreparer.class);

    private final GradingProperties properties;

    public ImagePreparer(GradingProperties properties) {
        this.properties = properties;
    }

    public PreparedImage prepare(Path path) throws IOException {
        return prepare(Files.readAllBytes(path), path.getFileName().toString());
    }

    public PreparedImage prepare(byte[] source, String fileName) {
        String mime = detectMimeType(source);
        if (mime == null) {
            throw BizException.badRequest("无法识别的图片格式（支持 JPEG / PNG / GIF）：" + fileName);
        }

        BufferedImage image;
        try (ByteArrayInputStream in = new ByteArrayInputStream(source)) {
            image = ImageIO.read(in);
        } catch (IOException ex) {
            throw BizException.badRequest("图片读取失败：" + fileName);
        }
        if (image == null) {
            throw BizException.badRequest("图片解码失败，文件可能已损坏或为不支持的格式（如 WebP）：" + fileName);
        }

        int orientation = readExifOrientation(source);
        String originalSize = image.getWidth() + "x" + image.getHeight();

        image = applyExifOrientation(image, orientation);
        image = downscale(image, properties.maxLongSide());
        BufferedImage rgb = flattenToOpaque(image);
        byte[] encoded = encode(rgb, mime);

        log.debug("图片预处理完成 {}：原始 {} {}KB，EXIF orientation={} → 上传 {}x{} {}KB",
                fileName, originalSize, source.length / 1024, orientation,
                rgb.getWidth(), rgb.getHeight(), encoded.length / 1024);

        String outputMime = "image/png".equals(mime) ? "image/png" : "image/jpeg";
        return new PreparedImage(fileName, outputMime, rgb.getWidth(), rgb.getHeight(),
                source.length, encoded, Base64.getEncoder().encodeToString(encoded));
    }

    /** 按文件头魔数判断类型。官方文档明确说明按文件实际内容判定，不看扩展名和 MIME 声明。 */
    private String detectMimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
            return "image/gif";
        }
        // WebP 官方支持但 JDK 自带 ImageIO 无法解码，这里识别出来只是为了给出更准确的报错
        return null;
    }

    private int readExifOrientation(byte[] source) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(source)) {
            Metadata metadata = ImageMetadataReader.readMetadata(in);
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifDirectoryBase.TAG_ORIENTATION)) {
                return directory.getInt(ExifDirectoryBase.TAG_ORIENTATION);
            }
        } catch (Exception ex) {
            // 非 JPEG/TIFF 或没有 EXIF 段，属正常情况
            log.debug("读取 EXIF 方向失败，按原方向处理：{}", ex.getMessage());
        }
        return 1;
    }

    private BufferedImage applyExifOrientation(BufferedImage src, int orientation) {
        if (orientation <= 1) {
            return src;
        }
        int width = src.getWidth();
        int height = src.getHeight();
        boolean swapAxes = orientation >= 5;

        AffineTransform transform = new AffineTransform();
        switch (orientation) {
            case 2 -> transform.translate(width, 0);
            case 3 -> transform.translate(width, height);
            case 4 -> transform.translate(0, height);
            case 5 -> transform.rotate(Math.PI / 2);
            case 6 -> transform.translate(height, 0);
            case 7 -> transform.translate(height, 0);
            case 8 -> transform.translate(0, width);
            default -> {
                return src;
            }
        }
        switch (orientation) {
            case 2 -> transform.scale(-1, 1);
            case 3 -> transform.rotate(Math.PI);
            case 4 -> transform.scale(1, -1);
            case 5 -> transform.scale(1, -1);
            case 6 -> transform.rotate(Math.PI / 2);
            case 7 -> {
                transform.rotate(Math.PI / 2);
                transform.scale(-1, 1);
            }
            case 8 -> transform.rotate(3 * Math.PI / 2);
            default -> {
                // 已在上一个 switch 返回
            }
        }

        int targetWidth = swapAxes ? height : width;
        int targetHeight = swapAxes ? width : height;
        BufferedImage dst = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, targetWidth, targetHeight);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR), 0, 0);
        g.dispose();
        return dst;
    }

    /** 只缩小不放大，避免小图被放大后徒增 token 却没有任何信息增益。 */
    private BufferedImage downscale(BufferedImage src, int maxLongSide) {
        int width = src.getWidth();
        int height = src.getHeight();
        int longSide = Math.max(width, height);
        if (longSide <= maxLongSide) {
            return src;
        }
        double scale = (double) maxLongSide / longSide;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage dst = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return dst;
    }

    /** 统一铺白底，去掉 alpha 通道，避免转 JPEG 时透明区域变黑。 */
    private BufferedImage flattenToOpaque(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    /** PNG 保持无损（截图类文本更清晰），其余一律转 JPEG。 */
    private byte[] encode(BufferedImage image, String sourceMime) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if ("image/png".equals(sourceMime)) {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            }
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(properties.jpegQuality());
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BizException(com.dxy.homework.common.ErrorCode.INTERNAL_ERROR, "图片编码失败");
        }
    }
}
