package com.dxy.homework.controller;

import com.dxy.homework.common.AuthContext;
import com.dxy.homework.common.R;
import com.dxy.homework.service.QuoteService;
import com.dxy.homework.vo.DailyQuoteVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 首页每日美句。 */
@RestController
@RequestMapping("/api/v1/quote")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /** 今天的句子（按孩子年级）。当天同年级幂等，生成一次全天复用。 */
    @GetMapping("/today")
    public R<DailyQuoteVo> today(@RequestParam("studentId") Long studentId) {
        return R.ok(quoteService.todayOf(AuthContext.requireUserId(), studentId));
    }
}
