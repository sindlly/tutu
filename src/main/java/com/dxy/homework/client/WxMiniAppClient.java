package com.dxy.homework.client;

import com.dxy.homework.common.BizException;
import com.dxy.homework.common.ErrorCode;
import com.dxy.homework.config.WxProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 微信小程序登录接口封装。
 *
 * <p>只做一件事：拿 code 换 openid。直接调 HTTP 而没引 SDK —— 这个接口就一个 GET，
 * 引一整个 SDK 反而增加依赖面和升级负担。
 */
@Component
public class WxMiniAppClient {

    private static final Logger log = LoggerFactory.getLogger(WxMiniAppClient.class);
    private static final String HOST = "api.weixin.qq.com";

    private final WxProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public WxMiniAppClient(WxProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** @param unionId 未绑定开放平台时可能为空 */
    public record WxSession(String openId, String unionId) {
    }

    public WxSession code2Session(String code) {
        if (!props.configured()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "微信小程序 AppID/Secret 未配置");
        }

        URI uri = UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(HOST)
                .path("/sns/jscode2session")
                .queryParam("appid", props.appid())
                .queryParam("secret", props.secret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .encode()
                .toUri();

        JsonNode root;
        try {
            byte[] body = restClient.get().uri(uri).retrieve().body(byte[].class);
            root = objectMapper.readTree(new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("调用微信 code2session 失败", ex);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "微信登录服务暂时不可用");
        }

        // 注意：微信失败时 HTTP 仍是 200，必须检查业务错误码
        int errcode = root.path("errcode").asInt(0);
        if (errcode != 0) {
            String errmsg = root.path("errmsg").asText("");
            log.warn("微信 code2session 返回错误 errcode={} errmsg={}", errcode, errmsg);
            if (errcode == 40029 || errcode == 40163) {
                throw BizException.badRequest("登录凭证已失效，请重新进入小程序");
            }
            throw new BizException(ErrorCode.INTERNAL_ERROR, "微信登录失败");
        }

        String openId = root.path("openid").asText(null);
        if (openId == null || openId.isBlank()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "微信未返回 openid");
        }
        return new WxSession(openId, root.path("unionid").asText(null));
    }
}
