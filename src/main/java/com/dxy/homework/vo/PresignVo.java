package com.dxy.homework.vo;

/** 私有读地址。带时效，客户端不要长期缓存。 */
public record PresignVo(String url, long expiresInSeconds) {
}
