package com.dxy.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dxy.homework.entity.UserUsage;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserUsageMapper extends BaseMapper<UserUsage> {

    /** 确保当天有一行记录，已存在则不动。 */
    @Insert("INSERT INTO t_user_usage (user_id, usage_date, used_count) VALUES (#{userId}, #{usageDate}, 0) "
            + "ON DUPLICATE KEY UPDATE user_id = user_id")
    int ensureRow(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);

    /**
     * 原子占用一次额度：只有当已用量小于上限时才会自增，返回 1 表示占用成功。
     * 用数据库的行锁保证并发下不会超发，不依赖 Redis 计数。
     */
    @Update("UPDATE t_user_usage SET used_count = used_count + 1 "
            + "WHERE user_id = #{userId} AND usage_date = #{usageDate} AND used_count < #{limit}")
    int tryAcquire(@Param("userId") Long userId,
                   @Param("usageDate") LocalDate usageDate,
                   @Param("limit") int limit);

    /** AI 调用失败且确认未计费时返还额度。 */
    @Update("UPDATE t_user_usage SET used_count = used_count - 1 "
            + "WHERE user_id = #{userId} AND usage_date = #{usageDate} AND used_count > 0")
    int release(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);
}
