package com.dxy.homework.eval;

import java.util.List;

/**
 * 一次运行的评测结果。
 *
 * <p>判分层的四象限以「学生真实对错」为基准：
 * <pre>
 *            模型判对      模型判错
 * 真实 对     TN           FP  ← 误判：把做对的判成错，会让家长白改一遍
 * 真实 错     FN  ← 漏判    TP
 * </pre>
 * 漏判（FN）是最危险的错误：家长会以为孩子做对了，错题溜进不了错题本。
 */
public record EvalResult(
        String file,
        int runIndex,
        int totalQuestions,
        int unmatched,
        int unusedLabels,
        int answerMatched,
        int answerMismatched,
        int tp,
        int fp,
        int fn,
        int tn,
        int unknownVerdict,
        int needsCheck,
        List<Miss> answerMisses,
        List<Miss> falseNegatives,
        List<Miss> falsePositives,
        List<QuestionVerdict> verdicts
) {

    /** 一条差异记录。判分层用它同时呈现"真实答案 vs 模型读到的答案"，以便区分是识别问题还是判分问题。 */
    public record Miss(
            String no,
            String stem,
            String expectAnswer,
            String modelAnswer,
            String expectVerdict,
            String modelVerdict,
            String note
    ) {
    }

    /**
     * 单个匹配上的题在本次运行中的记录，用于跨次比对。
     * 同时带上 modelAnswer，才能把"读数翻转"和"判定翻转"分开统计。
     */
    public record QuestionVerdict(
            String key,
            String no,
            String stem,
            Boolean expectCorrect,
            Boolean modelCorrect,
            String modelAnswer
    ) {
    }

    public int judgedCount() {
        return tp + fp + fn + tn;
    }

    /** 识别层准确率：模型读到的学生答案与标注一致的比例。 */
    public double answerAccuracy() {
        int total = answerMatched + answerMismatched;
        return total == 0 ? Double.NaN : (double) answerMatched / total;
    }

    /** 模型报出的"错题"里，有多少是真的错题。低精度率意味着大量误判。 */
    public double precision() {
        int reported = tp + fp;
        return reported == 0 ? Double.NaN : (double) tp / reported;
    }

    /** 真实错题里，有多少被模型抓出来。 */
    public double recall() {
        int actualWrong = tp + fn;
        return actualWrong == 0 ? Double.NaN : (double) tp / actualWrong;
    }

    /** 漏判率 = 漏掉的真错题 / 全部真错题。这个指标比总体准确率更值得盯。 */
    public double missRate() {
        int actualWrong = tp + fn;
        return actualWrong == 0 ? Double.NaN : (double) fn / actualWrong;
    }

    /** 误判率 = 被判错的好题 / 全部真做对的题。 */
    public double falseAlarmRate() {
        int actualRight = tn + fp;
        return actualRight == 0 ? Double.NaN : (double) fp / actualRight;
    }

    public double f1() {
        double p = precision();
        double r = recall();
        if (Double.isNaN(p) || Double.isNaN(r) || p + r == 0) {
            return Double.NaN;
        }
        return 2 * p * r / (p + r);
    }
}
