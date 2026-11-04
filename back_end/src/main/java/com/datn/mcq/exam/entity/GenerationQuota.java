package com.datn.mcq.exam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * V24 (D1): số câu một giáo viên đã yêu cầu sinh trong ngày {@code usageDate} (giờ Việt Nam). Chỉ ghi bằng truy vấn native
 * của {@code GenerationQuotaRepository} (khóa dòng rồi kiểm trần), không nạp entity.
 */
@Entity
@Table(name = "generation_quota")
public class GenerationQuota {

    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "requested", nullable = false)
    private Integer requested = 0;

    public Integer getUserId() {
        return userId;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public Integer getRequested() {
        return requested;
    }
}
