package com.datn.mcq.review.service;

import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.experiment.service.ExperimentService;
import com.datn.mcq.generation.dto.ParsedMcqDto;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.parser.McqJsonParser;
import com.datn.mcq.generation.service.DedupScopeResolver;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.llm.dto.LlmCallResult;
import com.datn.mcq.qc.dto.QcPipelineReport;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import com.datn.mcq.review.dto.ReviewQuestionListItemDto;
import com.datn.mcq.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Khởi tạo 5 câu hỏi Toán Đại số 8 mẫu (kèm question_template, experiment_run) để demo hiển thị công thức KaTeX.
 * Hàm seedMathDemoQuestions() có tính idempotent (gọi nhiều lần không tạo bản ghi trùng).
 *
 * <p>Quyết định 7 (2026-10-21): seed đi qua pipeline QC thật ở bước format và dedup, KHÔNG chạy answer_check
 * (không gọi Gemini), không ghi kết quả QC dựng sẵn và không ép QC_PASSED. Vì vậy câu seed giữ trạng thái
 * do pipeline quyết định (thường là generated) và không dùng làm bằng chứng QC hay để duyệt vào ngân hàng.
 */
@Service
public class MathDemoSeedService {

    private record DemoMathItem(
            String topic,
            String learningOutcome,
            String sourceQuestion,
            Difficulty difficulty,
            String rawJson
    ) {}

    private static final List<DemoMathItem> DEMO_ITEMS = List.of(
            new DemoMathItem(
                    "Rút gọn phân thức đại số",
                    "Thực hiện được việc rút gọn phân thức đại số bằng cách phân tích tử và mẫu thành nhân tử.",
                    "Rút gọn phân thức $\\frac{4x^2y}{8xy^3}$ với $x \\neq 0, y \\neq 0$.",
                    Difficulty.EASY,
                    """
                    {
                      "question_text": "Rút gọn phân thức $\\\\frac{4x^2y}{8xy^3}$ với $x \\\\neq 0$ và $y \\\\neq 0$, ta được kết quả là:",
                      "option_a": "$\\\\frac{x}{2y}$",
                      "option_b": "$\\\\frac{x}{2y^2}$",
                      "option_c": "$\\\\frac{2x}{y^2}$",
                      "option_d": "$\\\\frac{x^2}{2y}$",
                      "correct_option": "B",
                      "explanation": "Ta có $\\\\frac{4x^2y}{8xy^3} = \\\\frac{4xy \\\\cdot x}{4xy \\\\cdot 2y^2} = \\\\frac{x}{2y^2}$ khi chia cả tử và mẫu cho nhân tử chung $4xy$."
                    }
                    """
            ),
            new DemoMathItem(
                    "Căn bậc hai của số chính phương",
                    "Áp dụng được hằng đẳng thức $\\sqrt{A^2} = |A|$ để tính giá trị biểu thức số.",
                    "Tính $\\sqrt{(-5)^2}$.",
                    Difficulty.EASY,
                    """
                    {
                      "question_text": "Kết quả của phép tính $\\\\sqrt{(-5)^2}$ là gì?",
                      "option_a": "$-5$",
                      "option_b": "$5$",
                      "option_c": "$-25$",
                      "option_d": "$25$",
                      "correct_option": "B",
                      "explanation": "Áp dụng hằng đẳng thức $\\\\sqrt{A^2} = |A|$ với mọi số thực $A$, ta được $\\\\sqrt{(-5)^2} = |-5| = 5$."
                    }
                    """
            ),
            new DemoMathItem(
                    "Bất phương trình bậc nhất một ẩn",
                    "Nhận biết được bất phương trình bậc nhất một ẩn dạng $ax + b > 0$ ($a \\neq 0$).",
                    "Chỉ ra bất phương trình bậc nhất một ẩn trong các bất phương trình cho trước.",
                    Difficulty.EASY,
                    """
                    {
                      "question_text": "Bất phương trình nào sau đây là bất phương trình bậc nhất một ẩn?",
                      "option_a": "$2x - 3 > 0$",
                      "option_b": "$x^2 + 1 \\\\ge 0$",
                      "option_c": "$0x + 5 < 0$",
                      "option_d": "$\\\\frac{1}{x} - 2 \\\\le 0$",
                      "correct_option": "A",
                      "explanation": "Bất phương trình bậc nhất một ẩn có dạng $ax + b > 0$ (hoặc $<, \\\\ge, \\\\le$) với $a \\\\neq 0$. Phương án A có $a = 2 \\\\neq 0$ nên thỏa mãn."
                    }
                    """
            ),
            new DemoMathItem(
                    "Hệ hai phương trình bậc nhất hai ẩn",
                    "Giải được hệ hai phương trình bậc nhất hai ẩn bằng phương pháp cộng đại số hoặc thế.",
                    "Tìm nghiệm của hệ phương trình $\\left\\{\\begin{matrix} 2x + y = 5 \\\\ x - y = 1 \\end{matrix}\\right.$.",
                    Difficulty.MEDIUM,
                    """
                    {
                      "question_text": "Cặp số nào dưới đây là nghiệm của hệ phương trình $\\\\left\\\\{\\\\begin{matrix} 2x + y = 5 \\\\\\\\ x - y = 1 \\\\end{matrix}\\\\right.$?",
                      "option_a": "$(1; 2)$",
                      "option_b": "$(2; 1)$",
                      "option_c": "$(3; 2)$",
                      "option_d": "$(2; 3)$",
                      "correct_option": "B",
                      "explanation": "Cộng từng vế hai phương trình: $(2x + y) + (x - y) = 5 + 1 \\\\Leftrightarrow 3x = 6 \\\\Leftrightarrow x = 2$. Thế $x = 2$ vào $x - y = 1$ được $y = 1$. Vậy nghiệm là $(2; 1)$."
                    }
                    """
            ),
            new DemoMathItem(
                    "Tính giá trị biểu thức chứa lũy thừa",
                    "Vận dụng quy tắc nhân hai lũy thừa cùng cơ số và lũy thừa của số âm.",
                    "Tính giá trị biểu thức $A = 2^3 \\cdot 2^2 + 3^2 - (-2)^4$.",
                    Difficulty.MEDIUM,
                    """
                    {
                      "question_text": "Tính giá trị của biểu thức $A = 2^3 \\\\cdot 2^2 + 3^2 - (-2)^4$.",
                      "option_a": "$17$",
                      "option_b": "$25$",
                      "option_c": "$33$",
                      "option_d": "$9$",
                      "correct_option": "A",
                      "explanation": "Ta có $2^3 \\\\cdot 2^2 = 2^5 = 32$, $3^2 = 9$, và $(-2)^4 = 16$. Thay vào biểu thức: $A = 32 + 9 - 16 = 25$."
                    }
                    """
            )
    );

    private final UserService userService;
    private final CatalogService catalogService;
    private final ExperimentService experimentService;
    private final GenerationService generationService;
    private final QcOrchestrator qcOrchestrator;
    private final DedupScopeResolver dedupScopeResolver;
    private final TeacherReviewService teacherReviewService;

    public MathDemoSeedService(
            UserService userService,
            CatalogService catalogService,
            ExperimentService experimentService,
            GenerationService generationService,
            QcOrchestrator qcOrchestrator,
            DedupScopeResolver dedupScopeResolver,
            TeacherReviewService teacherReviewService) {
        this.userService = userService;
        this.catalogService = catalogService;
        this.experimentService = experimentService;
        this.generationService = generationService;
        this.qcOrchestrator = qcOrchestrator;
        this.dedupScopeResolver = dedupScopeResolver;
        this.teacherReviewService = teacherReviewService;
    }

    @Transactional
    public List<ReviewQuestionListItemDto> seedMathDemoQuestions() {
        userService.getOrCreateDefaultTeacher();
        Subject mathSubject = catalogService.getOrCreateSubject("Toán Đại số 8");
        ExperimentRun demoRun = experimentService.getOrCreateDemoRun(
                mathSubject.getId(),
                "Seed 5 câu hỏi Toán Đại số 8 phục vụ demo hiển thị công thức KaTeX và kiểm duyệt"
        );

        List<GeneratedQuestion> existingInRun = generationService.listQuestionsByRunId(demoRun.getId());
        if (existingInRun.isEmpty()) {
            for (DemoMathItem item : DEMO_ITEMS) {
                QuestionTemplate tpl = catalogService.getOrCreateTemplate(
                        mathSubject.getId(),
                        8,
                        item.topic(),
                        item.learningOutcome(),
                        item.sourceQuestion()
                );
                ParsedMcqDto parsed = McqJsonParser.parse(item.rawJson());
                LlmCallResult llmResult = new LlmCallResult(
                        item.rawJson().trim(),
                        180,
                        850.0,
                        0.0,
                        "gemini-3.5-flash-lite"
                );
                GeneratedQuestion saved = generationService.saveParsedQuestion(
                        tpl.getId(),
                        demoRun.getId(),
                        item.difficulty(),
                        llmResult,
                        parsed
                );

                // Pipeline QC thật: format -> dedup (phạm vi theo purpose của run seed).
                // runAnswerCheck = false: không gọi Gemini, nên câu không thể đạt QC_PASSED (Q-2).
                QcPipelineReport report = qcOrchestrator.evaluateAndSave(new QuestionQcInput(
                        saved.getId(),
                        parsed.parseSuccess(),
                        parsed.questionText(),
                        parsed.optionA(),
                        parsed.optionB(),
                        parsed.optionC(),
                        parsed.optionD(),
                        parsed.correctOption(),
                        parsed.explanation(),
                        true,
                        false,
                        parsed.parseError()
                ), dedupScopeResolver.resolve(demoRun.getId()));
                generationService.updateStatusAndDifficulty(saved.getId(), report.resultingStatus(), null);
            }
        }
        return teacherReviewService.listQuestionsForReview();
    }
}

