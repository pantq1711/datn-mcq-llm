package com.datn.mcq.qc.service;

import com.datn.mcq.qc.dto.GroundingResult;
import com.datn.mcq.qc.entity.QuestionGrounding;
import com.datn.mcq.qc.repository.QuestionGroundingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Lưu và đọc kết quả QC "bám tài liệu" (W2-3). Không gọi mạng. {@link #record} chạy trong transaction của nơi gọi (job
 * grounding: cùng transaction với việc đánh dấu job xong).
 */
@Service
public class GroundingService {

    private final QuestionGroundingRepository repository;

    public GroundingService(QuestionGroundingRepository repository) {
        this.repository = repository;
    }

    /**
     * Ghi kết quả chấm một câu.
     *
     * @param refsJson            mảng JSON các đoạn tài liệu đã dùng (chép từ job)
     * @param generatedWithMaterial true khi câu được sinh có tài liệu trong prompt (luồng thật)
     * @param sourceJobId         job grounding đã chấm; null nếu chấm ngoài hàng đợi
     */
    @Transactional
    public QuestionGrounding record(Integer questionId, GroundingResult result, String refsJson,
                                    boolean generatedWithMaterial, Integer sourceJobId) {
        QuestionGrounding g = new QuestionGrounding();
        g.setQuestionId(questionId);
        g.setVerdict(result.verdict().dbValue());
        g.setReason(result.reason());
        g.setEvidenceQuote(result.evidenceQuote());
        g.setEvidenceFound(result.evidenceFound());
        g.setJudgeModel(result.judgeModel());
        g.setMaterialRefs(refsJson);
        g.setGeneratedWithMaterial(generatedWithMaterial);
        g.setSourceJobId(sourceJobId);
        if (result.usage() != null) {
            g.setTokenCount(result.usage().tokenCount());
            g.setLatencyMs(result.usage().latencyMs());
        }
        // Postgres lưu tới micro giây: cắt trước để giá trị đọc lại giống giá trị vừa ghi.
        g.setCheckedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        return repository.saveAndFlush(g);
    }

    @Transactional(readOnly = true)
    public Optional<QuestionGrounding> find(Integer questionId) {
        return repository.findByQuestionId(questionId);
    }
}
