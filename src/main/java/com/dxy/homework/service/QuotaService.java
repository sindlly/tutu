package com.dxy.homework.service;

import com.dxy.homework.entity.UserUsage;
import com.dxy.homework.mapper.UserUsageMapper;
import com.dxy.homework.vo.QuotaVo;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

/**
 * 每日额度。
 *
 * <p>扣减放在数据库里用一条带条件的 UPDATE 完成（{@code used_count < limit}），
 * 而不是先查再改或走 Redis 计数 —— 并发下只有数据库行锁能保证不超发。
 */
@Service
public class QuotaService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final UserUsageMapper usageMapper;

    public QuotaService(UserUsageMapper usageMapper) {
        this.usageMapper = usageMapper;
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** @return true 表示成功占用一次额度；false 表示今日已用完 */
    public boolean tryAcquire(Long userId, int dailyLimit) {
        LocalDate today = today();
        usageMapper.ensureRow(userId, today);
        return usageMapper.tryAcquire(userId, today, dailyLimit) == 1;
    }

    /**
     * 返还额度。只在"确认没有产生 AI 计费"的场景调用：
     * 队列满、网络失败、模型没返回内容这些情况用户没拿到任何结果，不该扣他的次数。
     * 如果模型已经正常返回而我们自己后续处理失败，说明钱已经花了，不返还。
     */
    public void release(Long userId) {
        usageMapper.release(userId, today());
    }

    public QuotaVo today(Long userId, int dailyLimit) {
        UserUsage usage = usageMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<UserUsage>lambdaQuery()
                .eq(UserUsage::getUserId, userId)
                .eq(UserUsage::getUsageDate, today())
                .last("LIMIT 1"));
        int used = (usage == null || usage.getUsedCount() == null) ? 0 : usage.getUsedCount();
        return new QuotaVo(dailyLimit, used, Math.max(0, dailyLimit - used));
    }
}
