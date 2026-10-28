package com.datn.mcq.material.service;

import com.datn.mcq.material.entity.LessonMaterial;
import com.datn.mcq.material.passage.PassageSelector;
import com.datn.mcq.material.passage.PassageSelector.Selection;
import com.datn.mcq.material.passage.PassageSelector.Source;
import com.datn.mcq.material.repository.LessonMaterialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chọn đoạn tài liệu bài học cho một job sinh câu (W2-3 bước 5): nạp văn bản đã lưu của các tài liệu rồi giao cho
 * {@link PassageSelector}. Tách hai bước để nơi tạo đề nạp một lần cho cả ô rồi chọn riêng từng chỗ. Không kiểm quyền sở
 * hữu: id đã được kiểm khi gắn tài liệu vào đề.
 */
@Service
public class MaterialPassageService {

    /** Văn bản các tài liệu đã nạp, theo đúng thứ tự id được đưa vào. */
    public record Loaded(List<Source> sources) {
        public boolean isEmpty() {
            return sources.isEmpty();
        }
    }

    private final LessonMaterialRepository repository;

    public MaterialPassageService(LessonMaterialRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Loaded load(Collection<Integer> materialIds) {
        if (materialIds.isEmpty()) {
            return new Loaded(List.of());
        }
        Map<Integer, LessonMaterial> byId = new HashMap<>();
        repository.findAllById(materialIds).forEach(m -> byId.put(m.getId(), m));
        List<Source> sources = new ArrayList<>();
        for (Integer id : materialIds) {
            LessonMaterial m = byId.get(id);
            if (m != null) {
                sources.add(new Source(m.getId(), m.getSha256(), m.getExtractedText()));
            }
        }
        return new Loaded(sources);
    }

    /**
     * @param topic     tên bài hoặc chủ đề của ô
     * @param details   nội dung và yêu cầu cần đạt của ô (tùy chọn)
     * @param slotIndex chỗ trong ô, từ 0
     * @param attempt   số job sinh câu đã có của chỗ này (sinh lại, Đổi câu), từ 0
     */
    public Selection select(Loaded loaded, String topic, String details, int slotIndex, int attempt) {
        return PassageSelector.select(loaded.sources(), topic, details, slotIndex, attempt);
    }
}
