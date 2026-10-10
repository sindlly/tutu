package com.dxy.homework.controller;

import com.dxy.homework.common.AuthInterceptor;
import com.dxy.homework.common.R;
import com.dxy.homework.dto.WxLoginRequest;
import com.dxy.homework.service.AuthService;
import com.dxy.homework.service.TokenService;
import com.dxy.homework.vo.LoginVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    /** 微信小程序登录。首次登录会自动注册并创建一个默认孩子档案。 */
    @PostMapping("/wx-login")
    public R<LoginVo> wxLogin(@Valid @RequestBody WxLoginRequest request) {
        return R.ok(authService.login(request));
    }

    /** 退出登录：直接吊销 token（这是用不透明 token 而非 JWT 的好处）。 */
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        tokenService.revoke(AuthInterceptor.extractToken(request));
        return R.ok(null);
    }
}
