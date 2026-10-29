package com.dxy.homework.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dxy.homework.client.ChatCallResult;
import com.dxy.homework.client.DeepSeekClient;
import com.dxy.homework.common.BizException;
import com.dxy.homework.entity.DailyQuote;
import com.dxy.homework.entity.Student;
import com.dxy.homework.mapper.DailyQuoteMapper;
import com.dxy.homework.mapper.StudentMapper;
import com.dxy.homework.vo.DailyQuoteVo;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 每日美句：首页第一屏每天一句，由 DeepSeek 生成，符合孩子当前年级的理解力。
 *
 * 幂等：uk(quote_date, grade) —— 同一天同年级只生成一次，全家/全天复用。
 * 降级：无 Key / 生成失败时走内置句子池（按日期散列选取），接口永远有产出，
 * 且失败不落库（下次有机会再试生成）。
 */
@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    /** 降级池：温暖、有画面感、低年级也读得懂。 */
    private static final List<String> FALLBACK = List.of(
            "今天的阳光落在课本上，也落在认真读书的你身上。",
            "写下的每一个字，都是你慢慢长大的脚印。",
            "错一题没关系，弄懂它的你已经比昨天更厉害了。",
            "铅笔短了一截，本事长了一截。",
            "书页翻动的声音，是安静又了不起的声音。",
            "你今天多想明白的一小步，是明天的一大步。",
            "慢慢来，把每个字写端正，时间会奖励认真的人。",
            "今天的风很轻，适合做一页口算，读一个故事。",
            "不会的题先别怕，你已经会了那么多别的。",
            "每天进步一点点，像春天的竹子，看不见的时候也在长。");

    private static final String SYSTEM_PROMPT = """
            你是写给小学生的「每日一句话」作者。要求：
            1. 只输出一个 JSON 对象，形如 {"quote":"..."}，不要任何其他文字。
            2. 一句话，15~28 个汉字之间，结尾用句号。
            3. 画面感强、温暖、安静，鼓励认真与坚持，像睡前故事的开头。
            4. 用词必须让目标年级的孩子读得懂：不用成语堆砌、不喊口号、不说教、不出现「加油」。
            5. 不引用名人名言，不用比喻词「像/仿佛」开头。""";

    private final DailyQuoteMapper quoteMapper;
    private final StudentMapper studentMapper;
    private final StudentAccessService accessService;
    private final DeepSeekClient deepSeek;

    public QuoteService(DailyQuoteMapper quoteMapper,
                        StudentMapper studentMapper,
                        StudentAccessService accessService,
                        DeepSeekClient deepSeek) {
        this.quoteMapper = quoteMapper;
        this.studentMapper = studentMapper;
        this.accessService = accessService;
        this.deepSeek = deepSeek;
    }

    /** 取某孩子今天的句子（按其年级）。 */
    public DailyQuoteVo todayOf(Long userId, Long studentId) {
        accessService.requireAccess(userId, studentId);
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw BizException.notFound("孩子不存在");
        }
        int grade = student.getGrade() == null ? 3 : Math.min(9, Math.max(1, student.getGrade()));
        LocalDate today = LocalDate.now();

        DailyQuote existing = quoteMapper.selectOne(Wrappers.<DailyQuote>lambdaQuery()
                .eq(DailyQuote::getQuoteDate, today)
                .eq(DailyQuote::getGrade, grade)
                .last("LIMIT 1"));
        if (existing != null) {
            return new DailyQuoteVo(today.toString(), grade, existing.getText());
        }

        String text = generate(grade, today);
        if (text != null) {
            DailyQuote row = new DailyQuote();
            row.setQuoteDate(today);
            row.setGrade(grade);
            row.setText(text);
            try {
                quoteMapper.insert(row);
            } catch (DuplicateKeyException ex) {
                // 并发下别人已生成，直接复用
            }
            return new DailyQuoteVo(today.toString(), grade, text);
        }

        // 降级：不落库，明天还能再试真生成
        return new DailyQuoteVo(today.toString(), grade, fallbackOf(today, grade));
    }

    /** 调 DeepSeek 生成；失败返回 null（走降级池）。 */
    private String generate(int grade, LocalDate date) {
        try {
            int age = grade + 5; // 一年级约 6 岁
            String user = "今天是 %s。请为小学 %d 年级（约 %d 岁）的孩子写今天的一句话。"
                    .formatted(date, grade, age);
            ChatCallResult result = deepSeek.chatWithImages(SYSTEM_PROMPT, user, List.of());
            return parseQuote(result.content());
        } catch (Exception ex) {
            log.warn("每日美句生成失败，走降级池：{}", ex.getMessage());
            return null;
        }
    }

    /** 从模型输出中抠出句子；接受裸文本或 JSON 两种形态。 */
    private static String parseQuote(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String s = content.trim();
        // JSON 形态 {"quote":"..."}
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String body = s.substring(start + 1, end);
            int q1 = body.indexOf('"');
            int q2 = body.indexOf('"', q1 + 1);
            int q3 = body.indexOf('"', q2 + 1);
            int q4 = body.indexOf('"', q3 + 1);
            if (q1 >= 0 && q4 > q3 && q3 > q2) {
                s = body.substring(q3 + 1, q4)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .trim();
            }
        }
        s = s.replace("\"", "").trim();
        // 顺带清掉模型偶尔输出的引号/多余前后缀
        if (s.length() < 8 || s.length() > 60) {
            return null;
        }
        if (!s.endsWith("。") && !s.endsWith("！") && !s.endsWith("？")) {
            s = s + "。";
        }
        return s;
    }

    private static String fallbackOf(LocalDate date, int grade) {
        int index = Math.floorMod(date.toEpochDay() * 31 + grade, FALLBACK.size());
        return FALLBACK.get(index);
    }
}
