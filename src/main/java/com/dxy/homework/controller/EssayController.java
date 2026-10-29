package com.dxy.homework.controller;

import com.dxy.homework.common.AuthContext;
import com.dxy.homework.common.R;
import com.dxy.homework.dto.EssayGenerateRequest;
import com.dxy.homework.service.EssayService;
import com.dxy.homework.vo.EssayVo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作台 ·「每日一话」：按用户要求生成小美文。 */
@RestController
@RequestMapping("/api/v1/essay")
public class EssayController {

    private final EssayService essayService;

    public EssayController(EssayService essayService) {
        this.essayService = essayService;
    }

    /** 生成一篇小美文（考虑孩子年级）。同步返回，前端 loading 等待。 */
    @PostMapping("/generate")
    public R<EssayVo> generate(@RequestBody @Valid EssayGenerateRequest request) {
        return R.ok(essayService.generate(AuthContext.requireUserId(), request));
    }
}
