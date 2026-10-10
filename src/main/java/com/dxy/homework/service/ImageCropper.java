package com.dxy.homework.service;

import com.dxy.homework.common.BizException;
import com.dxy.homework.config.GradingProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 按归一化坐标从原图裁出错题区域。
 *
 * <p>这是"错题卡片"的补充：卡片是重新排版的干净版本，裁剪图保留学生的原始书写痕迹，
 * 复习时能看到当时是怎么写错的。模型给的坐标只是估算，所以这里做了边界钳制、
 * 最小尺寸保护与外扩留白 —— 裁歪一点也不会出现半截字或全黑图。
 */
@Component
public class ImageCropper {

    private static final Logger log = LoggerFactory.getLogger(ImageCropper.class);

    /** 四周额外留白比例，避免把题干首字或单位裁掉 */
    private static final double PADDING_RATIO = 0.03;
    /** 裁剪区域最小边长（像素），太小说明坐标不可信，直接拒绝 */
    private static final int MIN_SIDE_PIXELS = 24;

    private final GradingProperties properties;

    public ImageCropper(GradingProperties properties) {
        this.properties = properties;
    }

    /**
     * @param x,y,w,h 归一化 0-1 坐标，左上角原点
     * @return 裁剪并编码后的图片字节；坐标明显不可用时返回 null，由调用方决定是否降级
     */
    public byte[] crop(byte[] source, BigDecimal x, BigDecimal y, BigDecimal w, BigDecimal h) {
        if (source == null || x == null || y == null || w == null || h == null) {
            return null;
        }

        BufferedImage image;
        try (ByteArrayInputStream in = new ByteArrayInputStream(source)) {
            image = ImageIO.read(in);
        } catch (IOException ex) {
            log.warn("裁剪失败：原图无法解码：{}", ex.getMessage());
            return null;
        }
        if (image == null) {
            return null;
        }

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        // 先把归一化坐标换算成像素，并外扩留白
        double nx = x.doubleValue() - PADDING_RATIO;
        double ny = y.doubleValue() - PADDING_RATIO;
        double nw = w.doubleValue() + PADDING_RATIO * 2;
        double nh = h.doubleValue() + PADDING_RATIO * 2;

        int px = (int) Math.floor(nx * imageWidth);
        int py = (int) Math.floor(ny * imageHeight);
        int pw = (int) Math.ceil(nw * imageWidth);
        int ph = (int) Math.ceil(nh * imageHeight);

        // 钳制到图片边界内
        px = Math.max(0, Math.min(px, imageWidth - 1));
        py = Math.max(0, Math.min(py, imageHeight - 1));
        pw = Math.min(pw, imageWidth - px);
        ph = Math.min(ph, imageHeight - py);

        if (pw < MIN_SIDE_PIXELS || ph < MIN_SIDE_PIXELS) {
            log.warn("裁剪区域过小已放弃：{}x{} @({},{})，归一化坐标 ({},{},{},{})",
                    pw, ph, px, py, x, y, w, h);
            return null;
        }

        // getSubimage 返回的是共享数据缓冲的视图，必须先拷贝再编码
        BufferedImage sub = image.getSubimage(px, py, pw, ph);
        BufferedImage copy = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, pw, ph);
        g.drawImage(sub, 0, 0, null);
        g.dispose();

        return encodeJpeg(copy);
    }

    private byte[] encodeJpeg(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(properties.jpegQuality());
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException ex) {
            throw new BizException(com.dxy.homework.common.ErrorCode.INTERNAL_ERROR, "裁剪图编码失败");
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
