package com.datn.mcq.generation.tool;

import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.llm.dto.LlmUsage;
import com.datn.mcq.qc.checker.AnswerSolverChecker;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.dto.SolverVerdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Công cụ đo Solver (thẩm định 31/10): phần thuần, không gọi mạng hay DB. */
class SolverProbeToolTest {

    private static final String V = AnswerSolverChecker.SOLVER_PROMPT_VERSION;

    private static QuestionQcInput mcq(Option correct) {
        return new QuestionQcInput(37, true, "Phân tích $2x^3 - 6x^2$ thành nhân tử", "a", "b", "c", "d", correct,
                "giải", false, true);
    }

    private static SolverVerdict mcqVerdict(boolean passed, AnswerChoice judge) {
        return new SolverVerdict(QuestionType.MCQ, passed, "gemini-x", "lời giải", judge, null, null, null,
                new LlmUsage(812, 1534.6));
    }

    @Test
    void dongMcqKhopGhiConsistentMotPhuongAnDung() {
        SolverProbeRow row = SolverProbeRow.of(V, mcq(Option.B), mcqVerdict(true, AnswerChoice.B));
        assertEquals("37," + V + ",mcq,B,B,consistent,0,1,812,1535,gemini-x,", row.toCsvLine());
    }

    @Test
    void dongMcqSolverBaoNhieuPhuongAnDungGhiCoVaSoLuong() {
        SolverProbeRow row = SolverProbeRow.of(V, mcq(Option.A), mcqVerdict(false, AnswerChoice.MULTIPLE));
        assertEquals("inconsistent", row.verdict());
        assertEquals("multiple", row.solverAnswer());
        assertEquals("1", row.multipleFlag());
        assertEquals("2+", row.solverCorrectCount());
    }

    @Test
    void dongMcqSolverBaoKhongCoPhuongAnDung() {
        SolverProbeRow row = SolverProbeRow.of(V, mcq(Option.A), mcqVerdict(false, AnswerChoice.NONE));
        assertEquals("0", row.multipleFlag());
        assertEquals("0", row.solverCorrectCount());
    }

    @Test
    void dongTfGhiNhanNoiBangGachDungVaKhongCoCoNhieuPhuongAn() {
        QuestionQcInput tf = QuestionQcInput.trueFalse(5, true, "Cho", List.of("a", "b", "c", "d"),
                List.of("T", "F", "T", "F"), "giải", false, true, null);
        SolverVerdict v = new SolverVerdict(QuestionType.TF, false, "m", "r", null, null, null,
                List.of(TruthChoice.TRUE, TruthChoice.FALSE, TruthChoice.UNDECIDABLE, TruthChoice.FALSE), null);
        SolverProbeRow row = SolverProbeRow.of(V, tf, v);
        assertEquals("T|F|T|F", row.questionAnswer());
        assertEquals("T|F|undecidable|F", row.solverAnswer());
        assertEquals("", row.multipleFlag());
        assertEquals("5," + V + ",tf,T|F|T|F,T|F|undecidable|F,inconsistent,,,,,m,", row.toCsvLine());
    }

    @Test
    void dongLoiMotDongVaTrichNhayKepDauPhay() {
        SolverProbeRow row = SolverProbeRow.error(V, 9, null, "Lỗi \"x\",\ndòng 2");
        assertEquals("9," + V + ",,,,error,,,,,,\"Lỗi \"\"x\"\", dòng 2\"", row.toCsvLine());
    }

    @Test
    void docIdBoChuThichTrungVaPhanTachNhieuKieu() {
        assertEquals(List.of(7, 8, 37, 39), SolverProbeRunner.parseIds("﻿# bỏ sót\n7, 8\n37 39 # cuối\n\n8\n"));
    }

    @Test
    void docIdKhongHopLeThiDung() {
        assertThrows(IllegalStateException.class, () -> SolverProbeRunner.parseIds("7\nabc"));
        assertThrows(IllegalStateException.class, () -> SolverProbeRunner.parseIds("0"));
    }

    @Test
    void phienBanPromptPhaiCoTrongCode() {
        SolverProbeRunner.checkPromptVersion(V);
        assertThrows(IllegalStateException.class, () -> SolverProbeRunner.checkPromptVersion("solver-v9"));
        assertThrows(IllegalStateException.class, () -> SolverProbeRunner.checkPromptVersion(" "));
    }

    @Test
    void thuMucGhiPhaiNamNgoaiRepo(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        Path backEnd = Files.createDirectories(repo.resolve("back_end"));
        assertThrows(IllegalStateException.class,
                () -> SolverProbeRunner.resolveOutDir(repo.resolve("out").toString(), backEnd));
        assertThrows(IllegalStateException.class, () -> SolverProbeRunner.resolveOutDir("", backEnd));
        Path ok = SolverProbeRunner.resolveOutDir(tmp.resolve("ngoai").toString(), backEnd);
        assertTrue(ok.endsWith("ngoai"));
    }
}
