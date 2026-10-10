package com.dxy.homework.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 把错题重新排版成一张干净的卡片图。
 *
 * <p>为什么主力方案是卡片而不是裁剪原图：模型返回的坐标是估算值，裁歪是常态；
 * 而卡片用的是结构化结果，100% 准确、排版可控、能直接打印。原图裁剪只作为"原始书写痕迹"的补充。
 *
 * <p>排版分两趟：先按宽度把文字折行、算出总高度，再建图绘制，所以卡片高度随内容自适应。
 * 带底色的小节（我的答案 / 正确答案）按"块"画背景 —— 一块要覆盖标题和它折行后的全部内容，
 * 逐行画背景会把答案本身漏在色块外面。
 */
@Component
public class MistakeCardRenderer {

    private static final Logger log = LoggerFactory.getLogger(MistakeCardRenderer.class);

    private static final int WIDTH = 750;
    private static final int PADDING = 44;
    private static final int CARD_INSET = 24;
    private static final int CONTENT_X = PADDING + 12;
    private static final int CONTENT_WIDTH = WIDTH - PADDING * 2 - 24;
    private static final int CORNER_RADIUS = 12;
    private static final int BOX_PAD_V = 16;
    private static final int BOX_PAD_H = 20;
    /** 非底色小节里，标题行与内容行之间的紧凑间距 */
    private static final int LABEL_GAP = 4;
    private static final int TAG_GAP = 22;
    private static final int TAG_PAD_V = 12;
    private static final int FOOTER_GAP = 26;

    private static final Color BG = new Color(0xF5, 0xF6, 0xF8);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color TEXT_MAIN = new Color(0x1F, 0x23, 0x29);
    private static final Color TEXT_SUB = new Color(0x8C, 0x92, 0x9E);
    private static final Color WRONG_BG = new Color(0xFF, 0xF1, 0xF0);
    private static final Color WRONG_TEXT = new Color(0xCF, 0x13, 0x22);
    private static final Color RIGHT_BG = new Color(0xF6, 0xFF, 0xED);
    private static final Color RIGHT_TEXT = new Color(0x38, 0x9E, 0x0D);
    private static final Color ACCENT = new Color(0xE8, 0x4C, 0x4C);
    private static final Color TAG_BG = new Color(0xF0, 0xF2, 0xF5);
    private static final Color DIVIDER = new Color(0xEE, 0xF0, 0xF3);

    /** 优先使用这些字体，第一个能显示中文的胜出。生产环境（Linux）请确保装了其中任意一个。 */
    private static final String[] FONT_CANDIDATES = {
            "PingFang SC", "Hiragino Sans GB", "Heiti SC", "Microsoft YaHei",
            "Noto Sans CJK SC", "Source Han Sans SC", "WenQuanYi Micro Hei", "SansSerif"
    };

    public record Model(
            String subjectText,
            String gradeText,
            String questionNo,
            String stem,
            String studentAnswer,
            String correctAnswer,
            String wrongReason,
            String analysis,
            List<String> knowledgePoints,
            String footerText
    ) {
    }

    /** 一行文字。boxId 非 0 表示它属于某个带底色的块，同一块的背景会合并成一块画。 */
    private record Row(String text, Font font, Color color, int topGap, int boxId) {
    }

    public byte[] render(Model model) {
        Font base = resolveFont();
        Font titleFont = base.deriveFont(Font.BOLD, 36f);
        Font metaFont = base.deriveFont(Font.PLAIN, 24f);
        Font labelFont = base.deriveFont(Font.BOLD, 24f);
        Font stemFont = base.deriveFont(Font.BOLD, 30f);
        Font bodyFont = base.deriveFont(Font.PLAIN, 28f);
        Font tagFont = base.deriveFont(Font.PLAIN, 22f);
        Font footerFont = base.deriveFont(Font.PLAIN, 22f);

        // 量文字需要一个 Graphics2D，先借一张 1x1 的图拿到 FontMetrics
        Graphics2D measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        measure.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Map<Integer, Color> boxColors = new HashMap<>();
        List<Row> rows = buildRows(model, measure, labelFont, stemFont, bodyFont, boxColors);

        // 每个色块的总高度 = 块内文字行高之和 + 上下内边距
        Map<Integer, Integer> boxHeights = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.boxId() > 0) {
                boxHeights.merge(row.boxId(), lineHeight(measure, row.font()), Integer::sum);
            }
        }
        boxHeights.replaceAll((id, height) -> height + BOX_PAD_V * 2);

        int rowsHeight = rows.stream().mapToInt(row -> row.topGap() + lineHeight(measure, row.font())).sum();
        // 只补色块的内边距：块内文字行高已包含在 rowsHeight 里，这里再加一次会算重
        int boxExtra = boxHeights.size() * BOX_PAD_V * 2;

        int headerHeight = lineHeight(measure, titleFont) + 26;
        boolean hasTags = model.knowledgePoints() != null && !model.knowledgePoints().isEmpty();
        // 标签块与页脚块的高度必须和下面绘制时推进的量完全一致，否则卡片底部会留出多余空白
        int tagBlock = hasTags ? TAG_GAP + lineHeight(measure, tagFont) + TAG_PAD_V : 0;
        int footerBlock = FOOTER_GAP + lineHeight(measure, footerFont);

        int totalHeight = CARD_INSET * 2 + PADDING + headerHeight + rowsHeight + boxExtra
                + tagBlock + footerBlock;

        BufferedImage canvas = new BufferedImage(WIDTH, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setColor(BG);
        g.fillRect(0, 0, WIDTH, totalHeight);
        g.setColor(CARD_BG);
        g.fillRoundRect(CARD_INSET, CARD_INSET, WIDTH - CARD_INSET * 2, totalHeight - CARD_INSET * 2, 24, 24);
        // 左侧竖条：一眼区分这是错题
        g.setColor(ACCENT);
        g.fillRoundRect(CARD_INSET, CARD_INSET, 8, totalHeight - CARD_INSET * 2, 8, 8);

        int y = CARD_INSET + PADDING;

        // 标题栏
        g.setColor(TEXT_MAIN);
        g.setFont(titleFont);
        g.drawString("错题卡片", CONTENT_X, y + ascent(measure, titleFont));
        String meta = buildMeta(model);
        g.setColor(TEXT_SUB);
        g.setFont(metaFont);
        g.drawString(meta, WIDTH - PADDING - 12 - textWidth(measure, meta, metaFont),
                y + ascent(measure, titleFont));
        y += headerHeight;
        g.setColor(DIVIDER);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(CONTENT_X, y, WIDTH - PADDING - 12, y);
        g.setStroke(new BasicStroke(1f));

        // 正文：带底色的小节整块画背景，其余逐行画
        int index = 0;
        while (index < rows.size()) {
            Row row = rows.get(index);
            y += row.topGap();

            if (row.boxId() > 0) {
                int boxHeight = boxHeights.getOrDefault(row.boxId(), 0);
                g.setColor(boxColors.getOrDefault(row.boxId(), TAG_BG));
                g.fillRoundRect(CONTENT_X, y, CONTENT_WIDTH, boxHeight, CORNER_RADIUS, CORNER_RADIUS);
                y += BOX_PAD_V;
                while (index < rows.size() && rows.get(index).boxId() == row.boxId()) {
                    Row inner = rows.get(index);
                    g.setColor(inner.color());
                    g.setFont(inner.font());
                    g.drawString(inner.text(), CONTENT_X + BOX_PAD_H, y + ascent(measure, inner.font()));
                    y += lineHeight(measure, inner.font());
                    index++;
                }
                y += BOX_PAD_V;
            } else {
                g.setColor(row.color());
                g.setFont(row.font());
                g.drawString(row.text(), CONTENT_X, y + ascent(measure, row.font()));
                y += lineHeight(measure, row.font());
                index++;
            }
        }

        // 知识点标签
        if (hasTags) {
            y += TAG_GAP;
            int x = CONTENT_X;
            g.setFont(tagFont);
            for (String point : model.knowledgePoints()) {
                int tagWidth = textWidth(measure, point, tagFont) + 28;
                if (x + tagWidth > WIDTH - PADDING - 12) {
                    break;
                }
                g.setColor(TAG_BG);
                g.fillRoundRect(x, y, tagWidth, lineHeight(measure, tagFont) + TAG_PAD_V, 16, 16);
                g.setColor(TEXT_SUB);
                g.drawString(point, x + 14, y + TAG_PAD_V / 2 + ascent(measure, tagFont));
                x += tagWidth + 12;
            }
            y += lineHeight(measure, tagFont) + TAG_PAD_V;
        }

        // 页脚
        y += FOOTER_GAP;
        g.setColor(TEXT_SUB);
        g.setFont(footerFont);
        String footer = model.footerText() == null ? "" : model.footerText();
        g.drawString(footer, WIDTH - PADDING - 12 - textWidth(measure, footer, footerFont),
                y + ascent(measure, footerFont));

        g.dispose();
        measure.dispose();

        return toPng(canvas);
    }

    private String buildMeta(Model model) {
        StringBuilder sb = new StringBuilder();
        appendMeta(sb, model.subjectText());
        appendMeta(sb, model.gradeText());
        appendMeta(sb, model.questionNo());
        return sb.toString();
    }

    private void appendMeta(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(sb.isEmpty() ? "" : " · ").append(value);
        }
    }

    private List<Row> buildRows(Model model, Graphics2D measure,
                                Font labelFont, Font stemFont, Font bodyFont,
                                Map<Integer, Color> boxColors) {
        List<Row> rows = new ArrayList<>();
        int boxSeq = 0;

        // 题干不加底色，用醒目的粗体
        addSection(rows, "题干", model.stem(), labelFont, stemFont, measure,
                TEXT_MAIN, null, 6, boxColors, 0);

        // 我的答案 / 正确答案 用底色块强调对比
        boxSeq++;
        addSection(rows, "我的答案", blankToDash(model.studentAnswer()), labelFont, bodyFont,
                measure, WRONG_TEXT, WRONG_BG, 22, boxColors, boxSeq);
        boxSeq++;
        addSection(rows, "正确答案", blankToDash(model.correctAnswer()), labelFont, bodyFont,
                measure, RIGHT_TEXT, RIGHT_BG, 14, boxColors, boxSeq);

        if (hasText(model.wrongReason())) {
            addSection(rows, "错因", model.wrongReason(), labelFont, bodyFont, measure,
                    TEXT_MAIN, null, 22, boxColors, 0);
        }
        if (hasText(model.analysis())) {
            addSection(rows, "解析", model.analysis(), labelFont, bodyFont, measure,
                    TEXT_MAIN, null, 22, boxColors, 0);
        }
        return rows;
    }

    /**
     * 一个小节 = 一行标签 + 若干折行后的正文。
     *
     * @param boxColor 非空时该小节整块带底色，块内不留行间距（由块的上下内边距统一处理）
     */
    private void addSection(List<Row> rows, String label, String text,
                            Font labelFont, Font bodyFont, Graphics2D measure,
                            Color color, Color boxColor, int topGap,
                            Map<Integer, Color> boxColors, int boxId) {
        String labelText = label + "：";
        boolean boxed = boxColor != null;
        int used = textWidth(measure, labelText, labelFont) + (boxed ? BOX_PAD_H * 2 : 0);
        int bodyMaxWidth = Math.max(80, CONTENT_WIDTH - used);

        List<String> lines = wrap(text == null ? "" : text, measure, bodyFont, bodyMaxWidth);
        if (lines.isEmpty()) {
            lines = List.of("-");
        }
        if (boxed) {
            boxColors.put(boxId, boxColor);
        }

        rows.add(new Row(labelText, labelFont, boxed ? color : TEXT_SUB, topGap, boxId));
        rows.add(new Row(lines.get(0), bodyFont, color, boxed ? 0 : LABEL_GAP, boxId));
        for (int i = 1; i < lines.size(); i++) {
            rows.add(new Row(lines.get(i), bodyFont, color, 0, boxId));
        }
    }

    /** 折行。中文按字断行，英文尽量在空格处断，避免把单词劈开。 */
    private List<String> wrap(String text, Graphics2D measure, Font font, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        FontMetrics fm = measure.getFontMetrics(font);

        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            int width = 0;
            int lastSpace = -1;
            for (int i = 0; i < paragraph.length(); i++) {
                char c = paragraph.charAt(i);
                int charWidth = fm.charWidth(c);
                if (width + charWidth > maxWidth && line.length() > 0) {
                    if (lastSpace > 0 && isAsciiWord(paragraph, lastSpace)) {
                        lines.add(line.substring(0, lastSpace).stripTrailing());
                        String rest = line.substring(lastSpace + 1);
                        line = new StringBuilder(rest);
                        width = fm.stringWidth(rest);
                    } else {
                        lines.add(line.toString().stripTrailing());
                        line.setLength(0);
                        width = 0;
                    }
                    lastSpace = -1;
                }
                if (c == ' ') {
                    lastSpace = line.length();
                }
                line.append(c);
                width += charWidth;
            }
            if (line.length() > 0) {
                lines.add(line.toString().stripTrailing());
            }
        }
        return lines;
    }

    private boolean isAsciiWord(String text, int spaceIndex) {
        for (int i = spaceIndex + 1; i < Math.min(spaceIndex + 12, text.length()); i++) {
            if (text.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    private Font resolveFont() {
        for (String name : FONT_CANDIDATES) {
            Font font = new Font(name, Font.PLAIN, 28);
            // 必须真的能画中文，否则中文会变成方块
            if (font.canDisplay('测') && font.canDisplay('错')) {
                return font;
            }
        }
        log.warn("未找到可显示中文的字体，卡片上的中文可能显示为方块。"
                + "Linux 服务器请安装 fonts-noto-cjk 或类似字体包。");
        return new Font(Font.SANS_SERIF, Font.PLAIN, 28);
    }

    private int lineHeight(Graphics2D measure, Font font) {
        return measure.getFontMetrics(font).getHeight();
    }

    private int ascent(Graphics2D measure, Font font) {
        return measure.getFontMetrics(font).getAscent();
    }

    private int textWidth(Graphics2D measure, String text, Font font) {
        return measure.getFontMetrics(font).stringWidth(text);
    }

    private byte[] toPng(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException ex) {
            throw new IllegalStateException("卡片图编码失败", ex);
        }
        return out.toByteArray();
    }

    private String blankToDash(String value) {
        return hasText(value) ? value : "-";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
