package com.datn.mcq.catalog.repository;

import com.datn.mcq.catalog.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Integer> {
    Optional<Subject> findByNameIgnoreCase(String name);
}

