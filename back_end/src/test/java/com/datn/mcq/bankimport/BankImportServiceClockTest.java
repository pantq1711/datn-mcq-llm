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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankImportServiceClockTest {

    private static final byte[] VALID_PNG_1X1 = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R',
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
            0x00, 0x00, 0x00, 0x0A, 'I', 'D', 'A', 'T',
            0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
            0x0D, 0x0A, 0x2D, (byte) 0xB4,
            0x00, 0x00, 0x00, 0x00, 'I', 'E', 'N', 'D',
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

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
        verify(batches).deleteExpiredOrphanImages();
    }

    @Test
    void chuanHoaAnhChayTrongPermitPandocLimiter() throws Exception {
        BankImportProperties props = new BankImportProperties();
        assertEquals(24L * 1024 * 1024, props.getMaxImageTotalBytes());

        PandocLimiter limiter = mock(PandocLimiter.class);
        // Lần 1 (parseLimited) thành công, lần 2 (chuẩn hóa ảnh) hết chỗ -> 503 EXTRACTOR_BUSY.
        when(limiter.tryAcquire(any())).thenReturn(true, false);

        DocxInputGuard guard = mock(DocxInputGuard.class);
        when(guard.writeCheckedDocx(any(), any(), any(long.class))).thenAnswer(inv -> {
            byte[] content = inv.getArgument(0);
            java.nio.file.Path dir = inv.getArgument(1);
            java.nio.file.Path out = dir.resolve("in.docx");
            java.nio.file.Files.write(out, content);
            return out;
        });

        BankImportService service = new BankImportService(
                props, guard, limiter,
                mock(ImportBatchRepository.class), mock(ImportDedupRepository.class), mock(CatalogService.class),
                mock(GenerationService.class), mock(GeneratedQuestionRepository.class),
                mock(GeneratedQuestionStatementRepository.class), mock(TeacherReviewRepository.class),
                mock(TeacherReviewStatementRepository.class), mock(BankService.class),
                mock(ImportBatchWriter.class),
                new com.datn.mcq.questionimage.service.ImageHeaderInspector(
                        new com.datn.mcq.questionimage.service.ImageImportProperties()),
                mock(com.datn.mcq.questionimage.service.ImageNormalizer.class),
                null, null, null, Clock.systemUTC());

        DocxBuilder d = new DocxBuilder();
        d.p("PHẦN I. CÂU TRẮC NGHIỆM");
        d.numbered(DocxBuilder.cy("[NB]"), DocxBuilder.t(" Cho hình vẽ bên: "));
        d.p(d.drawing("png", VALID_PNG_1X1, 5.0, 4.0));
        d.p(DocxBuilder.t("A. "), DocxBuilder.y("1")).p("B. 2").p("C. 3").p("D. 4");

        com.datn.mcq.common.exception.ApiErrorException ex = assertThrows(
                com.datn.mcq.common.exception.ApiErrorException.class,
                () -> service.upload(1, FixtureDoc.FILE_NAME, d.bytes(), null, null));

        assertEquals("EXTRACTOR_BUSY", ex.getCode());
        verify(limiter, times(2)).tryAcquire(any());
        verify(limiter, times(1)).release();
    }
}
