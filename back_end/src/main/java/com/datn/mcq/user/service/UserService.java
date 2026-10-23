package com.datn.mcq.user.service;

import com.datn.mcq.enums.UserRole;
import com.datn.mcq.user.entity.UserAccount;
import com.datn.mcq.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;

    public UserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public UserAccount getById(Integer id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy người dùng id=" + id));
    }

    @Transactional
    public UserAccount getOrCreateDefaultTeacher() {
        return userAccountRepository.findFirstByRoleOrderByIdAsc(UserRole.TEACHER)
                .orElseGet(() -> userAccountRepository.save(
                        new UserAccount("Giáo viên Toán THCS", "teacher.math@ptit.edu.vn", UserRole.TEACHER)));
    }
}

