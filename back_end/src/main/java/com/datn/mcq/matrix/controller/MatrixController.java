package com.datn.mcq.matrix.controller;

import com.datn.mcq.common.security.CurrentUser;
import com.datn.mcq.matrix.dto.MatrixDtos.CreateFromSampleRequest;
import com.datn.mcq.matrix.dto.MatrixDtos.CreateMatrixRequest;
import com.datn.mcq.matrix.dto.MatrixDtos.MatrixDto;
import com.datn.mcq.matrix.dto.MatrixDtos.MatrixSummaryDto;
import com.datn.mcq.matrix.dto.MatrixDtos.SampleDto;
import com.datn.mcq.matrix.service.MatrixService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ma trận đề kiểm tra (V6): tạo bằng JSON, xem, và tạo từ mẫu có sẵn. W1-1: chỉ giáo viên; ma trận thuộc người tạo
 * (lấy từ token), ma trận của người khác trả 404.
 */
@RestController
@RequestMapping("/api/exam-matrices")
public class MatrixController {

    private final MatrixService matrixService;

    public MatrixController(MatrixService matrixService) {
        this.matrixService = matrixService;
    }

    @PostMapping
    public MatrixDto create(CurrentUser user, @RequestBody CreateMatrixRequest request) {
        return matrixService.create(request, user.id());
    }

    @GetMapping
    public List<MatrixSummaryDto> list(CurrentUser user) {
        return matrixService.list(user.id());
    }

    @GetMapping("/{id}")
    public MatrixDto get(CurrentUser user, @PathVariable Integer id) {
        return matrixService.get(id, user.id());
    }

    @GetMapping("/samples")
    public List<SampleDto> samples() {
        return matrixService.samples();
    }

    /** Tạo ma trận từ mẫu có sẵn (ví dụ "giuaki1-toan8"); body có thể bỏ trống. */
    @PostMapping("/samples/{key}")
    public MatrixDto createFromSample(CurrentUser user, @PathVariable String key,
                                      @RequestBody(required = false) CreateFromSampleRequest request) {
        return matrixService.createFromSample(key, request, user.id());
    }
}
