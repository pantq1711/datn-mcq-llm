package com.datn.mcq.bank.controller;

import com.datn.mcq.bank.entity.BankItem;
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

    @GetMapping
    public List<BankItem> listBankItems() {
        return bankService.listAll();
    }
}

