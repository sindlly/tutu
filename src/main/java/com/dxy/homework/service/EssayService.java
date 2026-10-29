package com.dxy.homework.service;

import com.dxy.homework.client.ChatCallResult;
import com.dxy.homework.client.DeepSeekClient;
import com.dxy.homework.common.BizException;
import com.dxy.homework.common.ErrorCode;
import com.dxy.homework.dto.EssayGenerateRequest;
import com.dxy.homework.entity.Student;
import com.dxy.homework.mapper.StudentMapper;
import com.dxy.homework.vo.EssayVo;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 工作台 ·「每日一话」：按用户要求 + 孩子年级生成小美文。
 * 每次调用独立计费（不落库、不缓存——用户要求千变万化）。
 */
@Service
public class EssayService {

    private static final Logger log = LoggerFactory.getLogger(EssayService.class);

    private static final String SYSTEM_PROMPT = """
            你是写给中小学生的美文作者。按用户给出的要求写一篇小美文：
            1. 只输出一个 JSON 对象，形如 {"essay":"..."}，不要任何其他文字。
            2. 长度 120~220 个汉字，可分 2~3 个自然段，用 \\n 分隔。
            3. 语言干净、有画面感，适合朗读给孩子听；主题积极温和。
            4. 用词难度贴合孩子的年级；不堆砌成语，不喊口号，不说教。
            5. 严格贴合用户给出的主题与要求；要求与孩子年龄明显不符时，按孩子年级适度调整表达。""";

    private final StudentMapper studentMapper;
    private final StudentAccessService accessService;
    private final DeepSeekClient deepSeek;

    public EssayService(StudentMapper studentMapper,
                        StudentAccessService accessService,
                        DeepSeekClient deepSeek) {
        this.studentMapper = studentMapper;
        this.accessService = accessService;
        this.deepSeek = deepSeek;
    }

    public EssayVo generate(Long userId, EssayGenerateRequest request) {
        accessService.requireAccess(userId, request.studentId());
        Student student = studentMapper.selectById(request.studentId());
        if (student == null) {
            throw BizException.notFound("孩子不存在");
        }
        int grade = student.getGrade() == null ? 3 : Math.min(9, Math.max(1, student.getGrade()));

        String user = "孩子年级：%d 年级（约 %d 岁）。要求：%s"
                .formatted(grade, grade + 5, request.requirements().trim());

        try {
            ChatCallResult result = deepSeek.chatWithImages(SYSTEM_PROMPT, user, List.of());
            String essay = parseEssay(result.content());
            if (essay == null) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "AI 没能生成文章，请换个说法再试一次");
            }
            return new EssayVo(essay);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("每日一话生成失败：{}", ex.getMessage());
            throw new BizException(ErrorCode.INTERNAL_ERROR, "AI 服务忙，请稍后再试");
        }
    }

    /** 从模型输出抠出正文：JSON 形态 {"essay":"..."} 或裸文本。 */
    private static String parseEssay(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String s = content.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String body = s.substring(start + 1, end);
            int key = body.indexOf("\"essay\"");
            if (key >= 0) {
                int q1 = body.indexOf('"', key + 7);
                if (q1 >= 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = q1 + 1; i < body.length(); i++) {
                        char c = body.charAt(i);
                        if (c == '"' && body.charAt(i - 1) != '\\') {
                            break;
                        }
                        if (c == '\\' && i + 1 < body.length()) {
                            char n = body.charAt(++i);
                            sb.append(switch (n) {
                                case 'n' -> '\n';
                                case 't' -> ' ';
                                case '"' -> '"';
                                case '\\' -> '\\';
                                default -> n;
                            });
                        } else {
                            sb.append(c);
                        }
                    }
                    s = sb.toString().trim();
                }
            }
        }
        s = s.replace("\\n", "\n").trim();
        if (s.length() < 30 || s.length() > 1000) {
            return null;
        }
        return s;
    }
}
