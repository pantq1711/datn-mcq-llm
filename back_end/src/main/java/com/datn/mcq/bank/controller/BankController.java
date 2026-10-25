package com.datn.mcq.bank.controller;

import com.datn.mcq.bank.dto.BankSearchDtos.BankItemDetailDto;
import com.datn.mcq.bank.dto.BankSearchDtos.BankPageDto;
import com.datn.mcq.bank.dto.BankSearchDtos.TopicDto;
import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.common.security.CurrentUser;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ngân hàng câu hỏi dùng chung (quyết định H1): giáo viên và admin chỉ ĐỌC. Sửa, xóa câu ngân hàng chưa làm (Q-4c).
 */
@RestController
@RequestMapping("/api/bank-items")
public class BankController {

    private final BankService bankService;

    public BankController(BankService bankService) {
        this.bankService = bankService;
    }

    /**
     * Ngân hàng có lọc, phân trang, sắp xếp; mỗi câu kèm số đề đã dùng và lần dùng gần nhất. Giữ các key cũ của
     * {@code BankItemDto}.
     *
     * @param subjectId    môn
     * @param gradeLevel   khối 6-9
     * @param questionType mcq, tf, sa
     * @param topic        chủ đề (không phân biệt hoa thường)
     * @param content      nội dung của ô ma trận đã sinh câu
     * @param difficulty   easy, medium, hard (câu tf: có ít nhất một ý ở mức đó)
     * @param levels       (tf) đúng 4 mức a-d, ví dụ easy,easy,medium,hard
     * @param origin       ai (câu AI đã duyệt, mặc định) hoặc import (để dành cho nhập từ Word)
     * @param q            tìm trong nội dung (tối đa 100 ký tự)
     * @param usage        used hoặc unused
     * @param sort         newest (mặc định), oldest, least_used, recently_used
     * @param page         từ 0
     * @param size         từ 1 đến 50 (mặc định 20)
     */
    @GetMapping
    public BankPageDto list(@RequestParam(required = false) Integer subjectId,
                            @RequestParam(required = false) Integer gradeLevel,
                            @RequestParam(required = false) QuestionType questionType,
                            @RequestParam(required = false) String topic,
                            @RequestParam(required = false) String content,
                            @RequestParam(required = false) Difficulty difficulty,
                            @RequestParam(required = false) String levels,
                            @RequestParam(required = false) String origin,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) String usage,
                            @RequestParam(required = false) String sort,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return bankService.search(subjectId, gradeLevel, questionType, topic, content, difficulty, levels, origin, q,
                usage, sort, page, size);
    }

    /** Chủ đề có câu trong ngân hàng kèm số câu, để dựng bộ lọc. */
    @GetMapping("/topics")
    public List<TopicDto> topics(@RequestParam(required = false) Integer subjectId) {
        return bankService.topics(subjectId);
    }

    /**
     * Chi tiết một câu: đáp án, lời giải, nguồn gốc (câu LLM gốc, các bản sửa, người Giữ) và mức dùng. {@code myExams}
     * chỉ gồm đề của người gọi. Không có: 404.
     */
    @GetMapping("/{id}")
    public BankItemDetailDto detail(CurrentUser user, @PathVariable Integer id) {
        return bankService.detail(id, user.id());
    }
}
