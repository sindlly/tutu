package com.dxy.homework.service;

import com.dxy.homework.entity.Question;
import java.util.Locale;

/**
 * 模型返回的题型字符串 → 数据库枚举值。
 * 顺带兼容中文写法，因为提示词迭代过程中模型偶尔会输出中文题型。
 */
public final class QuestionTypeMapper {

    private QuestionTypeMapper() {
    }

    public static int toCode(String type) {
        if (type == null || type.isBlank()) {
            return Question.TYPE_OTHER;
        }
        return switch (type.trim().toLowerCase(Locale.ROOT).replace("-", "_")) {
            case "choice", "single_choice", "single", "选择", "单选" -> Question.TYPE_CHOICE;
            case "multi_choice", "multiple_choice", "多选" -> Question.TYPE_MULTI_CHOICE;
            case "judge", "true_false", "判断" -> Question.TYPE_JUDGE;
            case "fill", "blank", "填空" -> Question.TYPE_FILL;
            case "calc", "calculate", "calculation", "计算" -> Question.TYPE_CALC;
            case "solve", "word_problem", "application", "解答", "应用", "应用题" -> Question.TYPE_SOLVE;
            case "essay", "writing", "作文", "写作" -> Question.TYPE_ESSAY;
            default -> Question.TYPE_OTHER;
        };
    }
}
