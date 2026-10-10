package com.dxy.homework.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 调用成本估算，便于在验证阶段直观看到"改一次作业要花多少钱"。
 *
 * <p>价格依据官方定价页（元 / 百万 tokens）。法定节假日未做判断，估算时按工作日规则处理，
 * 实际以账单为准。
 */
@Component
public class CostCalculator {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final double MILLION = 1_000_000.0;

    /** @param inputMiss 输入-未命中缓存；inputHit 输入-命中缓存；output 输出 */
    private record Price(double inputMiss, double inputHit, double output) {
    }

    private static final Price FLASH_IDLE = new Price(1.0, 0.02, 4.0);
    private static final Price FLASH_PEAK = new Price(2.0, 0.04, 8.0);
    private static final Price PRO_IDLE = new Price(4.5, 0.15, 13.5);
    private static final Price PRO_PEAK = new Price(9.0, 0.30, 27.0);

    /** 高峰时段：周一至周五 9:00-12:00、14:00-18:00，其余为空闲时段（价格减半）。 */
    public boolean isPeakPricing() {
        return isPeak(LocalDateTime.now(ZONE));
    }

    boolean isPeak(LocalDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        int hour = now.getHour();
        return (hour >= 9 && hour < 12) || (hour >= 14 && hour < 18);
    }

    public double estimate(String model, int promptTokens, int cacheHitTokens, int completionTokens, boolean peak) {
        Price price = priceOf(model, peak);
        int hit = Math.max(0, Math.min(cacheHitTokens, promptTokens));
        int miss = Math.max(0, promptTokens - hit);
        return miss / MILLION * price.inputMiss()
                + hit / MILLION * price.inputHit()
                + completionTokens / MILLION * price.output();
    }

    private Price priceOf(String model, boolean peak) {
        boolean pro = model != null && model.contains("pro");
        if (pro) {
            return peak ? PRO_PEAK : PRO_IDLE;
        }
        return peak ? FLASH_PEAK : FLASH_IDLE;
    }
}
