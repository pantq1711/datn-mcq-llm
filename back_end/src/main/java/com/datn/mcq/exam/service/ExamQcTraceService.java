package com.datn.mcq.exam.service;

import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.common.util.JsonStringArray;
import com.datn.mcq.enums.JobStatus;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.ActionDto;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.AnswerCheckDto;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.AnswerDto;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.ComparedToDto;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.DedupDto;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.FormatDto;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.JobDto;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.JudgeLabelDto;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.LabelDto;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.LineageDto;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.QcDto;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.QcTraceDto;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.UserRef;
import com.datn.mcq.exam.dto.ExamQcTraceDtos.VersionDto;
import com.datn.mcq.exam.entity.ExamSlot;
import com.datn.mcq.exam.entity.ExamSlotAction;
import com.datn.mcq.exam.repository.ExamSlotActionRepository;
import com.datn.mcq.exam.repository.ExamSlotRepository;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.parser.LlmOutputRepair;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.matrix.entity.ExamMatrixCell;
import com.datn.mcq.matrix.service.MatrixService;
import com.datn.mcq.qc.dto.TfJudgement;
import com.datn.mcq.qc.service.QcSummaryService;
import com.datn.mcq.qc.service.QcSummaryService.DedupStep;
import com.datn.mcq.qc.service.QcSummaryService.FormatStep;
import com.datn.mcq.qc.service.QcSummaryService.QcDetail;
import com.datn.mcq.qc.service.QcSummaryService.SolverStep;
import com.datn.mcq.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Vết QC theo ô đề (W1-3): chuỗi lần sinh và phiên bản của câu trong một chỗ, từng bước QC của mỗi phiên bản, lý do cờ
 * "cần xem kỹ" và lịch sử thao tác của giáo viên. CHỈ ĐỌC: không đối soát job (khác xem đề và tiến độ), nên xem vết
 * không làm đổi trạng thái chỗ. Kiểm chủ sở hữu trước (404 như id không tồn tại).
 */
@Service
public class ExamQcTraceService {

    private static final String AUTO_CLOSE_SUFFIX = ": đã tự đóng $";

    private final ExamBuildService buildService;
    private final ExamSlotRepository slotRepository;
    private final ExamSlotActionRepository actionRepository;
    private final MatrixService matrixService;
    private final GenerationService generationService;
    private final QcSummaryService qcSummaryService;
    private final BankService bankService;
    private final UserService userService;

    public ExamQcTraceService(ExamBuildService buildService, ExamSlotRepository slotRepository,
                              ExamSlotActionRepository actionRepository, MatrixService matrixService,
                              GenerationService generationService, QcSummaryService qcSummaryService,
                              BankService bankService, UserService userService) {
        this.buildService = buildService;
        this.slotRepository = slotRepository;
        this.actionRepository = actionRepository;
        this.matrixService = matrixService;
        this.generationService = generationService;
        this.qcSummaryService = qcSummaryService;
        this.bankService = bankService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public QcTraceDto trace(Integer examId, Integer slotId, Integer actorId) {
        buildService.getOwnedExam(examId, actorId);
        ExamSlot slot = slotRepository.findById(slotId)
                .filter(s -> s.getExamId().equals(examId))
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu id=" + slotId + " trong đề"));
        ExamMatrixCell cell = matrixService.getCell(slot.getCellId());
        List<ExamSlotAction> actions = actionRepository.findBySlotIdOrderByIdAsc(slotId);
        Context ctx = new Context(slot, actions, questionsInExam(examId));

        List<LineageDto> lineages = new ArrayList<>();
        List<GenerationJob> jobs = generationService.generateJobsForSlot(slotId);
        for (GenerationJob job : jobs) {
            List<VersionDto> versions = job.getQuestionId() == null ? List.of()
                    : chainFrom(job.getQuestionId(), ctx);
            lineages.add(new LineageDto("generated", jobDto(job), versions));
        }
        if (jobs.isEmpty() && slot.getBankItemId() != null) {
            int source = bankService.getItem(slot.getBankItemId()).sourceQuestionId();
            lineages.add(new LineageDto("bank", null, chainFrom(generationService.originalQuestionId(source), ctx)));
        }

        return new QcTraceDto(examId, slotId, cell.getId(), slot.getSlotNo(), cell.getPart(), cell.getTopic(),
                slot.getQuestionType(), slot.getState(), ctx.currentQuestionId == null ? null
                        : slot.getGeneratedQuestionId() != null ? "generated" : "bank",
                ctx.currentQuestionId, slot.getBankItemId(), slot.getAttempts(), slot.getSolverRetries(), lineages,
                actions.stream().map(a -> actionDto(a, ctx)).toList());
    }

    // ---------- phiên bản ----------

    /** Thông tin dùng chung khi dựng một vết. */
    private final class Context {
        final ExamSlot slot;
        final List<ExamSlotAction> actions;
        /** Câu nguồn/câu sinh đang nằm trong các chỗ của đề (để biết câu bị so trùng có "trong đề này" không). */
        final Set<Integer> inExam;
        final Integer currentQuestionId;
        final Map<Integer, UserRef> users = new HashMap<>();

        Context(ExamSlot slot, List<ExamSlotAction> actions, Set<Integer> inExam) {
            this.slot = slot;
            this.actions = actions;
            this.inExam = inExam;
            if (!ExamBuildService.hasQuestion(slot)) {
                this.currentQuestionId = null;
            } else if (slot.getGeneratedQuestionId() != null) {
                this.currentQuestionId = slot.getGeneratedQuestionId();
            } else if (slot.getBankItemId() != null) {
                this.currentQuestionId = bankService.getItem(slot.getBankItemId()).sourceQuestionId();
            } else {
                this.currentQuestionId = null;
            }
        }

        UserRef user(Integer id) {
            if (id == null) {
                return null;
            }
            return users.computeIfAbsent(id, k -> new UserRef(k, userService.findById(k)
                    .map(u -> u.getName()).orElse(null)));
        }
    }

    /** Các id câu (generated_question) đang nằm trong mọi chỗ của đề: câu hiện tại và câu nguồn của câu ngân hàng. */
    private Set<Integer> questionsInExam(Integer examId) {
        Set<Integer> ids = new HashSet<>();
        for (ExamSlot s : slotRepository.findByExamIdOrderByCellIdAscSlotNoAsc(examId)) {
            if (!ExamBuildService.hasQuestion(s)) {
                continue;
            }
            if (s.getGeneratedQuestionId() != null) {
                ids.add(s.getGeneratedQuestionId());
            }
            if (s.getBankItemId() != null) {
                ids.add(bankService.getItem(s.getBankItemId()).sourceQuestionId());
            }
        }
        return ids;
    }

    /** Câu gốc {@code rootId} rồi các bản giáo viên sửa tiếp theo thứ tự (chuỗi thẳng, UNIQUE ở DB). */
    private List<VersionDto> chainFrom(Integer rootId, Context ctx) {
        List<VersionDto> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        Optional<GeneratedQuestion> cur = Optional.of(generationService.getQuestionById(rootId));
        while (cur.isPresent() && seen.add(cur.get().getId())) {
            out.add(version(cur.get(), ctx));
            cur = generationService.editedChild(cur.get().getId());
        }
        return out;
    }

    private VersionDto version(GeneratedQuestion q, Context ctx) {
        boolean teacherEdit = q.getEditedFromQuestionId() != null;
        UserRef editedBy = null;
        List<String> changedFields = null;
        Integer editDistance = null;
        if (teacherEdit) {
            for (ExamSlotAction a : ctx.actions) {
                if (ExamSlotAction.EDIT.equals(a.getAction()) && q.getId().equals(a.getGeneratedQuestionId())) {
                    editedBy = ctx.user(a.getActorId());
                    changedFields = JsonStringArray.read(a.getChangedFields());
                    editDistance = a.getEditDistance();
                }
            }
        }
        QcDetail detail = qcSummaryService.detail(q.getId());
        Optional<GenerationJob> check = generationService.latestAnswerCheckJob(q.getId());
        List<String> reasons = q.getStatus() == QuestionStatus.IN_BANK ? List.of()
                : reviewReasons(q, check);
        return new VersionDto(q.getId(), teacherEdit ? "teacher_edit" : "llm", q.getCreatedAt(),
                q.getStatus().getDbValue(), q.getId().equals(ctx.currentQuestionId), editedBy, changedFields,
                editDistance, answer(q), new QcDto(format(detail.format()), dedup(detail.dedup(), ctx),
                        answerCheck(q, detail, check)), reasons);
    }

    private List<String> reviewReasons(GeneratedQuestion q, Optional<GenerationJob> check) {
        List<String> reasons = new ArrayList<>(qcSummaryService.summarize(q.getId()).reviewReasons());
        int i = reasons.indexOf(QcSummaryService.SOLVER_NOT_RUN);
        if (i >= 0 && check.isPresent()) {
            JobStatus s = check.get().getStatus();
            if (s == JobStatus.PENDING || s == JobStatus.RUNNING) {
                reasons.set(i, ExamViewService.SOLVER_PENDING);
            } else if (s == JobStatus.FAILED) {
                reasons.set(i, ExamViewService.SOLVER_FAILED);
            }
        }
        return reasons;
    }

    private AnswerDto answer(GeneratedQuestion q) {
        QuestionType type = q.getQuestionType() != null ? q.getQuestionType() : QuestionType.MCQ;
        return switch (type) {
            case MCQ -> new AnswerDto(q.getCorrectOption(), null, null);
            case SA -> new AnswerDto(null, q.getSaAnswer(), null);
            case TF -> new AnswerDto(null, null, generationService.getStatements(q.getId()).stream()
                    .map(s -> new LabelDto(s.getStatementKey(), s.getLlmLabel())).toList());
        };
    }

    // ---------- từng bước QC ----------

    private static FormatDto format(FormatStep f) {
        if (f == null) {
            return null;
        }
        List<String> repairs = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String w : f.warnings()) {
            if (w.startsWith(LlmOutputRepair.ESCAPE_WARNING_PREFIX) || w.endsWith(AUTO_CLOSE_SUFFIX)) {
                repairs.add(w);
            } else {
                others.add(w);
            }
        }
        return new FormatDto(f.passed(), f.errors(), others, repairs, f.checkedAt());
    }

    private DedupDto dedup(DedupStep d, Context ctx) {
        if (d == null) {
            return null;
        }
        ComparedToDto compared = d.comparedToQuestionId() == null ? null
                : new ComparedToDto(d.comparedToQuestionId(), ctx.inExam.contains(d.comparedToQuestionId()),
                        bankService.hasBankItemForQuestion(d.comparedToQuestionId()));
        return new DedupDto(d.passed(), d.similarity(), d.threshold(), d.checkedAt(), compared);
    }

    private AnswerCheckDto answerCheck(GeneratedQuestion q, QcDetail detail, Optional<GenerationJob> check) {
        JobDto job = check.map(ExamQcTraceService::jobDto).orElse(null);
        SolverStep s = detail.solver();
        if (s != null) {
            List<JudgeLabelDto> labels = s.judgeLabels() == null ? null : s.judgeLabels().stream()
                    .map((TfJudgement j) -> new JudgeLabelDto(j.key(), j.judgeLabel())).toList();
            return new AnswerCheckDto(s.passed() ? "consistent" : "inconsistent", null, s.judgeModel(),
                    s.judgeAnswer(), s.judgeSaKind(), s.judgeSaAnswer(), labels, s.reasoning(), null, s.checkedAt(),
                    job);
        }
        if (check.isPresent()) {
            JobStatus js = check.get().getStatus();
            if (js == JobStatus.PENDING || js == JobStatus.RUNNING) {
                return notRun("pending", null, null, job);
            }
            if (js == JobStatus.FAILED) {
                return notRun("error", null, check.get().getLastError(), job);
            }
        }
        if (q.getSolverError() != null) {
            return notRun("error", null, q.getSolverError(), job);
        }
        String reason;
        if (detail.format() == null) {
            reason = "not_recorded";
        } else if (!detail.format().passed()) {
            reason = "format_failed";
        } else if (detail.dedup() == null) {
            reason = "dedup_not_concluded";
        } else if (!detail.dedup().passed()) {
            reason = "duplicate";
        } else if (check.isPresent() && check.get().getLastError() != null
                && check.get().getLastError().startsWith("skipped: superseded")) {
            reason = "superseded";
        } else {
            reason = "solver_not_run";
        }
        return notRun("not_run", reason, null, job);
    }

    private static AnswerCheckDto notRun(String state, String reason, String error, JobDto job) {
        return new AnswerCheckDto(state, reason, null, null, null, null, null, null, error, null, job);
    }

    private static JobDto jobDto(GenerationJob j) {
        return new JobDto(j.getId(), j.getCreatedAt(), j.getStatus().getDbValue(), j.getAttempt(), j.getLastError());
    }

    private ActionDto actionDto(ExamSlotAction a, Context ctx) {
        return new ActionDto(a.getId(), a.getAction(), a.getCreatedAt(), ctx.user(a.getActorId()),
                a.getGeneratedQuestionId(), a.getBankItemId(),
                a.getChangedFields() == null ? null : JsonStringArray.read(a.getChangedFields()),
                a.getEditDistance(), a.getInstruction());
    }
}
