package com.dxy.homework.client;

import com.dxy.homework.config.OcrProperties;
import com.dxy.homework.model.OcrLine;
import com.dxy.homework.model.PreparedImage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 腾讯云 OCR 客户端（手写签名版，不引入官方 SDK）。
 *
 * <p>为什么不用官方 SDK：这个接口一年也调不了几次，但 SDK 会拖进来一整套云 API 依赖。
 * 这里按官方 TC3-HMAC-SHA256 规范自己签，代码量可控，也少一层依赖冲突风险。
 *
 * <p>返回的是**行级坐标**：腾讯云为检测到的每一行文字给出 {@code ItemPolygon}（X/Y/Width/Height），
 * 这正是定位错题区域所需要的粒度。模型自报坐标不可靠，行级坐标是实测能用的替代方案。
 */
@Component
public class TencentOcrClient implements OcrClient {

    private static final Logger log = LoggerFactory.getLogger(TencentOcrClient.class);
    private static final String SERVICE = "ocr";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";

    private final OcrProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TencentOcrClient(OcrProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
    }

    @Override
    public boolean enabled() {
        return properties.configured();
    }

    @Override
    public String name() {
        return "tencent-ocr:" + properties.action();
    }

    /**
     * 识别一页作业，按从上到下的顺序返回文字行。
     *
     * @throws OcrException 调用失败或返回业务错误，调用方应吞掉并降级
     */
    @Override
    public List<OcrLine> recognize(PreparedImage image, int pageSeq) {
        if (!properties.configured()) {
            throw new OcrException("CONFIG", "OCR 未配置：缺少密钥或已关闭");
        }
        if (image == null || image.width() <= 0 || image.height() <= 0) {
            throw new OcrException("PARAM", "图片尺寸异常，无法识别");
        }

        String payload = buildPayload(image);
        long timestamp = Instant.now().getEpochSecond();
        String authorization = sign(payload, timestamp);

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + properties.endpoint() + "/"))
                .timeout(properties.readTimeout())
                .header("Content-Type", CONTENT_TYPE)
                // Host 不能显式设置：JDK HttpClient 把 Host 列为受限首部，设置了会直接抛
                // IllegalArgumentException。它会自动按 URI 补上，和签名里用的值一致。
                .header("X-TC-Action", properties.action())
                .header("X-TC-Version", properties.version())
                .header("X-TC-Timestamp", String.valueOf(timestamp))
                .header("X-TC-Region", properties.region())
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        long startedAt = System.currentTimeMillis();
        String body;
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            body = response.body();
            if (response.statusCode() != 200) {
                throw new OcrException("HTTP_" + response.statusCode(),
                        "OCR 接口返回 " + response.statusCode() + "：" + abbreviate(body));
            }
        } catch (OcrException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OcrException("OCR 调用失败：" + ex.getMessage(), ex);
        }

        List<OcrLine> lines = parse(body, image, pageSeq);
        log.info("OCR 识别第 {} 页：{} 行，耗时 {}ms", pageSeq, lines.size(),
                System.currentTimeMillis() - startedAt);
        return lines;
    }

    private String buildPayload(PreparedImage image) {
        StringBuilder json = new StringBuilder(64 + (image.base64() == null ? 0 : image.base64().length()));
        json.append('{');
        // EnableDetectSplit 仅 GeneralAccurateOCR 支持，标准版/手写版传了会报 UnknownParameter
        if (properties.detectSplit() && "GeneralAccurateOCR".equals(properties.action())) {
            json.append("\"EnableDetectSplit\":true,");
        }
        json.append("\"ImageBase64\":\"").append(image.base64() == null ? "" : image.base64()).append("\"}");
        return json.toString();
    }

    /** TC3-HMAC-SHA256 签名。规范见腾讯云《签名方法 v3》。 */
    private String sign(String payload, long timestamp) {
        String date = Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC).toLocalDate().toString();
        String action = properties.action().toLowerCase();

        String canonicalHeaders = "content-type:" + CONTENT_TYPE + "\n"
                + "host:" + properties.endpoint() + "\n"
                + "x-tc-action:" + action + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n"
                + sha256Hex(payload);

        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = ALGORITHM + "\n" + timestamp + "\n" + credentialScope + "\n"
                + sha256Hex(canonicalRequest);

        byte[] secretDate = hmacSha256(("TC3" + properties.secretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = toHex(hmacSha256(secretSigning, stringToSign));

        return ALGORITHM
                + " Credential=" + properties.secretId() + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    private List<OcrLine> parse(String body, PreparedImage image, int pageSeq) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode response = root == null ? null : root.get("Response");
            if (response == null) {
                throw new OcrException("PARSE", "OCR 返回缺少 Response 节点");
            }
            JsonNode error = response.get("Error");
            if (error != null) {
                throw new OcrException(text(error.get("Code")), hint(text(error.get("Code")), text(error.get("Message"))));
            }
            JsonNode detections = response.get("TextDetections");
            if (detections == null || !detections.isArray()) {
                return List.of();
            }

            double width = image.width();
            double height = image.height();
            List<OcrLine> lines = new ArrayList<>(detections.size());
            for (JsonNode node : detections) {
                String text = text(node.get("DetectedText"));
                if (text == null || text.isBlank()) {
                    continue;
                }
                double[] box = lineBox(node, width, height);
                if (box == null) {
                    continue;
                }
                JsonNode confidence = node.get("Confidence");
                lines.add(new OcrLine(text, box[0], box[1], box[2], box[3],
                        confidence == null || confidence.isNull() ? null : confidence.asDouble(), pageSeq));
            }
            // OCR 返回顺序不保证是从上到下，定位算法强依赖顺序，这里自己排一次
            lines.sort((a, b) -> Double.compare(a.centerY(), b.centerY()));
            return lines;
        } catch (OcrException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OcrException("OCR 返回解析失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 取一行文字的矩形。优先用 {@code ItemPolygon}（已经是轴对齐矩形），
     * 没有时退回 {@code Polygon} 的四个顶点求外接矩形 —— 倾斜文本只能取外接框。
     */
    private double[] lineBox(JsonNode node, double width, double height) {
        JsonNode item = node.get("ItemPolygon");
        if (item != null && item.has("X") && item.has("Y")) {
            double x = item.get("X").asDouble();
            double y = item.get("Y").asDouble();
            double w = item.has("Width") ? item.get("Width").asDouble() : 0;
            double h = item.has("Height") ? item.get("Height").asDouble() : 0;
            if (w > 0 && h > 0) {
                return norm(x, y, w, h, width, height);
            }
        }

        JsonNode polygon = node.get("Polygon");
        if (polygon != null && polygon.isArray() && polygon.size() >= 2) {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (JsonNode point : polygon) {
                JsonNode px = point.get("X");
                JsonNode py = point.get("Y");
                if (px == null || py == null) {
                    continue;
                }
                minX = Math.min(minX, px.asDouble());
                maxX = Math.max(maxX, px.asDouble());
                minY = Math.min(minY, py.asDouble());
                maxY = Math.max(maxY, py.asDouble());
            }
            if (maxX > minX && maxY > minY) {
                return norm(minX, minY, maxX - minX, maxY - minY, width, height);
            }
        }
        return null;
    }

    private double[] norm(double x, double y, double w, double h, double width, double height) {
        return new double[]{
                clamp01(x / width),
                clamp01(y / height),
                clamp(w / width, 0.001, 1.0),
                clamp(h / height, 0.001, 1.0)
        };
    }

    private static double clamp01(double v) {
        return clamp(v, 0.0, 1.0);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    /**
     * 给最常见的两类失败补一句人话。签名/权限问题排查成本很高，
     * 报错里直接说清楚要做什么，能省掉一轮来回。
     */
    private static String hint(String code, String message) {
        String raw = message == null ? "" : message;
        if (code != null && code.startsWith("AuthFailure")) {
            return raw + " —— 子账号缺少 OCR 权限。请到 CAM 给该子账号附加 QcloudOCRFullAccess（或只读）策略，"
                    + "并确认已在控制台开通「文字识别」服务";
        }
        if ("FailedOperation.UnOpenError".equals(code)) {
            return raw + " —— 文字识别服务未开通，请先在腾讯云控制台开通";
        }
        if ("ResourceUnavailable.ResourcePackageRunOut".equals(code)) {
            return raw + " —— OCR 资源包已用完，请续费或改用按量计费";
        }
        return raw;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ");
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "...";
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new OcrException("签名计算失败：" + ex.getMessage(), ex);
        }
    }

    private static String sha256Hex(String data) {
        try {
            return toHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new OcrException("摘要计算失败：" + ex.getMessage(), ex);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
