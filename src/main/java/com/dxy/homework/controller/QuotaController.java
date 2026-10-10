package com.dxy.homework.controller;

import com.dxy.homework.common.AuthContext;
import com.dxy.homework.common.BizException;
import com.dxy.homework.common.R;
import com.dxy.homework.entity.User;
import com.dxy.homework.mapper.UserMapper;
import com.dxy.homework.service.QuotaService;
import com.dxy.homework.vo.QuotaVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quota")
public class QuotaController {

    private final QuotaService quotaService;
    private final UserMapper userMapper;

    public QuotaController(QuotaService quotaService, UserMapper userMapper) {
        this.quotaService = quotaService;
        this.userMapper = userMapper;
    }

    @GetMapping("/today")
    public R<QuotaVo> today() {
        Long userId = AuthContext.requireUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.unauthorized();
        }
        return R.ok(quotaService.today(userId, user.getQuotaDaily() == null ? 0 : user.getQuotaDaily()));
    }
}
