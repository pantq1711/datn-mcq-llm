package com.datn.mcq.qc.checker;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Option;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.dto.QuestionQcInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QcFormatAndSolverCheckerTest {

    private final FormatChecker formatChecker = new FormatChecker();
    private final AnswerSolverChecker solverChecker = new AnswerSolverChecker(
            new LlmClient("", "gemini-3.5-flash-lite"),
            new PromptBuilder(),
            "gemini-3.5-flash-lite"
    );

    @Test
    void formatCheckerShouldPassValidMathQuestionAndRejectDuplicateOptionsOrUnwrappedLatex() {
        QuestionQcInput validInput = new QuestionQcInput(
                1,
                true,
                "Rút gọn $\\frac{4x^2y}{8xy^3}$",
                "$\\frac{x}{2y^2}$",
                "$\\frac{2x}{y^2}$",
                "$\\frac{x}{4y^2}$",
                "$\\frac{x}{2y}$",
                Option.A,
                "Chia tử và mẫu cho $4xy$.",
                true,
                false
        );
        FormatChecker.FormatCheckOutcome okOutcome = formatChecker.check(validInput);
        assertTrue(okOutcome.passed(), () -> "Kỳ vọng qua format check nhưng gặp: " + okOutcome.errors());

        QuestionQcInput unwrappedLatexInput = new QuestionQcInput(
                2,
                true,
                "Rút gọn biểu thức",
                "A = \\frac{1}{x}",
                "$1$",
                "$x$",
                "$-x$",
                Option.A,
                "Giải thích hợp lệ.",
                true,
                false
        );
        FormatChecker.FormatCheckOutcome badLatexOutcome = formatChecker.check(unwrappedLatexInput);
        assertFalse(badLatexOutcome.passed());
        assertTrue(badLatexOutcome.errors().stream().anyMatch(e -> e.contains("option_a")));

        QuestionQcInput duplicateOptionsInput = new QuestionQcInput(
                3,
                true,
                "Tính $2 + 3$",
                "$5$",
                "$5$",
                "$6$",
                "$7$",
                Option.A,
                "Ta có $2 + 3 = 5$.",
                true,
                false
        );
        FormatChecker.FormatCheckOutcome dupOutcome = formatChecker.check(duplicateOptionsInput);
        assertFalse(dupOutcome.passed());
        assertTrue(dupOutcome.errors().stream().anyMatch(e -> e.contains("trùng lặp")));
    }

    @Test
    void answerSolverCheckerShouldDetectWrongKeyAndNoneCorrectAndPassMatchingKey() {
        String solverWrongKeyJson = """
                {
                  "reasoning": "Ta có $2^5 + 9 - 16 = 32 + 9 - 16 = 25$, khớp với phương án B.",
                  "judge_answer": "B"
                }
                """;
        AnswerSolverChecker.SolverCheckOutcome mismatch =
                solverChecker.parseAndCompare(solverWrongKeyJson, "gemini-3.5-flash-lite", Option.A);
        assertFalse(mismatch.passed());
        assertEquals(AnswerChoice.B, mismatch.judgeAnswer());

        String solverNoneJson = """
                {
                  "reasoning": "Nghiệm của hệ là $(15/7; 5/7)$, không khớp phương án nào trong A, B, C, D.",
                  "judge_answer": "none"
                }
                """;
        AnswerSolverChecker.SolverCheckOutcome noneMatch =
                solverChecker.parseAndCompare(solverNoneJson, "gemini-3.5-flash-lite", Option.B);
        assertFalse(noneMatch.passed());
        assertEquals(AnswerChoice.NONE, noneMatch.judgeAnswer());

        AnswerSolverChecker.SolverCheckOutcome exactMatch =
                solverChecker.parseAndCompare(solverWrongKeyJson, "gemini-3.5-flash-lite", Option.B);
        assertTrue(exactMatch.passed());
        assertEquals(AnswerChoice.B, exactMatch.judgeAnswer());
    }
}

