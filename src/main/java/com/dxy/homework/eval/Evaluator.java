package com.dxy.homework.eval;

import com.dxy.homework.eval.EvalResult.Miss;
import com.dxy.homework.eval.EvalResult.QuestionVerdict;
import com.dxy.homework.model.GradeOutcome;
import com.dxy.homework.model.GradedQuestion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 把一次批改结果与人工标注比对，算识别层与判分层的指标。
 */
@Component
public class Evaluator {

    public EvalResult evaluate(GradeOutcome outcome, LabelSet labels, int runIndex) {
        int unmatched = 0;
        int answerMatched = 0;
        int answerMismatched = 0;
        int tp = 0;
        int fp = 0;
        int fn = 0;
        int tn = 0;
        int unknown = 0;
        int needsCheck = 0;

        List<Miss> answerMisses = new ArrayList<>();
        List<Miss> falseNegatives = new ArrayList<>();
        List<Miss> falsePositives = new ArrayList<>();
        List<QuestionVerdict> verdicts = new ArrayList<>();
        Set<LabelEntry> used = new HashSet<>();

        for (GradedQuestion question : outcome.questions()) {
            LabelEntry label = labels.match(question.stem());
            if (label == null) {
                unmatched++;
                continue;
            }
            used.add(label);

            if (Boolean.TRUE.equals(question.needsHumanCheck())) {
                needsCheck++;
            }

            String no = question.no();
            String stem = question.stem();
            String modelAnswer = question.studentAnswer();

            // 识别层：标注里给了真实笔迹才考核
            if (label.expectAnswer() != null) {
                if (AnswerNormalizer.equal(label.expectAnswer(), modelAnswer)) {
                    answerMatched++;
                } else {
                    answerMismatched++;
                    answerMisses.add(new Miss(no, stem, label.expectAnswer(), modelAnswer,
                            "读数一致", "读数不一致", label.note()));
                }
            }

            // 每个匹配上的题都记一条，供跨次不稳定性分析使用
            verdicts.add(new QuestionVerdict(label.stemKey(), no, stem,
                    label.expectCorrect(), question.isCorrect(), modelAnswer));

            // 判分层：标注里给了真实对错才考核
            if (label.expectCorrect() != null) {
                Boolean modelCorrect = question.isCorrect();
                if (modelCorrect == null) {
                    unknown++;
                } else if (Boolean.TRUE.equals(label.expectCorrect())) {
                    if (modelCorrect) {
                        tn++;
                    } else {
                        fp++;
                        falsePositives.add(new Miss(no, stem, label.expectAnswer(), modelAnswer,
                                "做对", "判成错", label.note()));
                    }
                } else {
                    if (modelCorrect) {
                        fn++;
                        falseNegatives.add(new Miss(no, stem, label.expectAnswer(), modelAnswer,
                                "做错", "判成对", label.note()));
                    } else {
                        tp++;
                    }
                }
            }
        }

        int unusedLabels = (int) labels.entries().stream().filter(e -> !used.contains(e)).count();

        return new EvalResult(outcome.file(), runIndex, outcome.questions().size(),
                unmatched, unusedLabels, answerMatched, answerMismatched,
                tp, fp, fn, tn, unknown, needsCheck,
                List.copyOf(answerMisses), List.copyOf(falseNegatives), List.copyOf(falsePositives),
                List.copyOf(verdicts));
    }
}
