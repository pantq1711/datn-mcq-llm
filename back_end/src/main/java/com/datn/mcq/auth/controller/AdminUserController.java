package com.datn.mcq.auth.controller;

import com.datn.mcq.auth.dto.AdminUserDtos.AdminUserDto;
import com.datn.mcq.auth.dto.AdminUserDtos.CreateTeacherRequest;
import com.datn.mcq.auth.dto.AdminUserDtos.ResetPasswordRequest;
import com.datn.mcq.auth.dto.AdminUserDtos.UpdateUserRequest;
import com.datn.mcq.auth.service.AdminUserService;
import com.datn.mcq.common.security.CurrentUser;
import com.datn.mcq.enums.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Quản trị tài khoản (W1-1 đợt 2). Chỉ ADMIN (SecurityConfig chặn /api/admin/**). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** Danh sách tài khoản theo id tăng dần; {@code role} (teacher, admin, researcher) bỏ trống là tất cả. */
    @GetMapping
    public List<AdminUserDto> list(@RequestParam(required = false) UserRole role) {
        return adminUserService.list(role);
    }

    /** Tạo tài khoản giáo viên. Email đã dùng: 409. */
    @PostMapping
    public ResponseEntity<AdminUserDto> create(@RequestBody CreateTeacherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.createTeacher(request));
    }

    /** Sửa tên và/hoặc bật-khóa. Khóa thì thu hồi refresh token; tự khóa mình hoặc khóa admin cuối cùng: 409. */
    @PatchMapping("/{id}")
    public AdminUserDto update(CurrentUser admin, @PathVariable Integer id, @RequestBody UpdateUserRequest request) {
        return adminUserService.update(admin.id(), id, request);
    }

    /** Đặt lại mật khẩu (thu hồi mọi phiên của người đó). */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable Integer id, @RequestBody ResetPasswordRequest request) {
        adminUserService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
