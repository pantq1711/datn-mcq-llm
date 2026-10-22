package com.datn.mcq.bank.controller;

import com.datn.mcq.bank.dto.BankItemDto;
import com.datn.mcq.bank.service.BankService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bank-items")
public class BankController {

    private final BankService bankService;

    public BankController(BankService bankService) {
        this.bankService = bankService;
    }

    /** Trả DTO (giữ nguyên 12 key cũ của entity, thêm questionType; xem {@link BankItemDto}). */
    @GetMapping
    public List<BankItemDto> listBankItems() {
        return bankService.listAll();
    }
}

