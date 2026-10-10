package com.dxy.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dxy.homework.entity.Question;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
