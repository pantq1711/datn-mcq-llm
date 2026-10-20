package com.datn.mcq.user.repository;

import com.datn.mcq.enums.UserRole;
import com.datn.mcq.user.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Integer> {
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findFirstByRoleOrderByIdAsc(UserRole role);
}

