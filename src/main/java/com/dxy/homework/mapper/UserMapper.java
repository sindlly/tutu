package com.dxy.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dxy.homework.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
