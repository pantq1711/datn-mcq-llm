package com.datn.mcq.bankimport;

import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.bankimport.repository.ImportBatchRepository;
import com.datn.mcq.bankimport.repository.ImportDedupRepository;
import com.datn.mcq.bankimport.service.BankImportProperties;
import com.datn.mcq.bankimport.service.BankImportService;
import com.datn.mcq.bankimport.service.ImportBatchWriter;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.config.PandocLimiter;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.generation.repository.GeneratedQuestionStatementRepository;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.material.extract.DocxInputGuard;
import com.datn.mcq.review.repository.TeacherReviewRepository;
import com.datn.mcq.review.repository.TeacherReviewStatementRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BankImportServiceClockTest {

    @Test
    void purgeExpiredUsesInjectedClock() {
        ImportBatchRepository batches = mock(ImportBatchRepository.class);
        Instant fixed = Instant.parse("2026-10-29T10:00:00Z");
        Clock clock = Clock.fixed(fixed, ZoneOffset.UTC);

        BankImportService service = new BankImportService(
                new BankImportProperties(), mock(DocxInputGuard.class), mock(PandocLimiter.class),
                batches, mock(ImportDedupRepository.class), mock(CatalogService.class),
                mock(GenerationService.class), mock(GeneratedQuestionRepository.class),
                mock(GeneratedQuestionStatementRepository.class), mock(TeacherReviewRepository.class),
                mock(TeacherReviewStatementRepository.class), mock(BankService.class),
                mock(ImportBatchWriter.class), clock);

        service.purgeExpired();

        verify(batches).expirePreviews(LocalDateTime.ofInstant(fixed, ZoneOffset.UTC));
    }
}
