package com.datn.mcq.exam.service;

import com.datn.mcq.exam.repository.ExamRepository;
import com.datn.mcq.matrix.service.MatrixUsageLookup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** W1-4: số đề theo ma trận cho module matrix (sửa/xóa ma trận đã có đề bị khóa). Chỉ đọc. */
@Component
public class ExamMatrixUsage implements MatrixUsageLookup {

    private final ExamRepository examRepository;

    public ExamMatrixUsage(ExamRepository examRepository) {
        this.examRepository = examRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public int examCount(Integer matrixId) {
        return (int) examRepository.countByMatrixId(matrixId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, Integer> examCounts(Collection<Integer> matrixIds) {
        Map<Integer, Integer> out = new HashMap<>();
        if (matrixIds.isEmpty()) {
            return out;
        }
        for (Object[] row : examRepository.countByMatrixIds(matrixIds)) {
            out.put((Integer) row[0], ((Number) row[1]).intValue());
        }
        return out;
    }
}
