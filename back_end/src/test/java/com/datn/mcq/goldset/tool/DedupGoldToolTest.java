package com.datn.mcq.goldset.tool;

import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.SamplingStratum;
import com.datn.mcq.goldset.tool.DedupGoldPairSampler.Item;
import com.datn.mcq.goldset.tool.DedupGoldPairSampler.Pair;
import com.datn.mcq.goldset.tool.DedupGoldPairSampler.Quotas;
import com.datn.mcq.goldset.tool.DedupGoldPairSampler.SampledPair;
import com.datn.mcq.qc.checker.DedupChecker;
import com.datn.mcq.qc.checker.DedupChecker.PairDiagnostics;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.qc.dto.DedupStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Công cụ D6 (chọn cặp gán nhãn, dự đoán theo ngưỡng): thuần, không Spring, không DB. */
class DedupGoldToolTest {

    private static DedupCandidate mcq(int id, String stem) {
        return new DedupCandidate(id, stem, "$1$", "$2$", "$3$", "$4$");
    }

    private static final String[] STEMS = {
            "Kết quả của phép nhân đơn thức $%dx$ với $%dx^2$ là",
            "Bậc của đa thức $x^%d + %dx + 1$ là",
            "Hình thang cân có một góc bằng $%d^\\circ$ và góc kề đáy kia bằng $%d^\\circ$. Tổng hai góc là",
            "Khai triển hằng đẳng thức $(x + %d)^2$ với hệ số $%d$ ta được",
            "Giá trị của biểu thức $%dx - %d$ tại $x = 2$ là",
    };

    /** 6 run × 5 template: cùng template khác run = cùng dạng đổi số liệu; run lẻ của template 0 có phủ định. */
    private static List<Item> pool() {
        List<Item> items = new ArrayList<>();
        int id = 1;
        for (int run = 1; run <= 6; run++) {
            for (int t = 0; t < STEMS.length; t++) {
                String stem = String.format(STEMS[t], run + 1, run + 2);
                if (t == 0 && run % 2 == 1) {
                    stem = stem.replace("Kết quả", "Kết quả nào sau đây không phải");
                }
                if (t == 4 && run == 2) {
                    stem = String.format(STEMS[4], 2, 3); // đúng số liệu của run 1 (run + 1, run + 2) -> trùng thật
                }
                items.add(new Item(mcq(id++, stem), run, t));
            }
        }
        return items;
    }

    @Test
    void diagnoseUsesSameGuardsAsCheck() {
        PairDiagnostics same = DedupChecker.diagnose(mcq(1, "Tính $2+3$"), mcq(2, "Tính  $2 + 3$"));
        assertThat(same.sameFingerprint()).isTrue();
        assertThat(same.cosine()).isEqualTo(1.0);
        PairDiagnostics numbers = DedupChecker.diagnose(mcq(1, "Tính giá trị của $2x + 3$ tại $x = 1$"),
                mcq(2, "Tính giá trị của $2x + 5$ tại $x = 1$"));
        assertThat(numbers.sameSignature()).isFalse();
        assertThat(numbers.cosine()).isGreaterThan(0.8);
        PairDiagnostics negation = DedupChecker.diagnose(mcq(1, "Khẳng định nào sau đây đúng?"),
                mcq(2, "Khẳng định nào sau đây không đúng?"));
        assertThat(negation.sameNegation()).isFalse();
        assertThatThrownBy(() -> DedupChecker.diagnose(mcq(1, "a"),
                new DedupCandidate(2, "b", null, null, null, null, QuestionType.SA, "1", List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void samplerMeetsQuotasAndStrataAreConsistent() {
        Quotas q = new Quotas(10, 12, 10, 6, 5, 3, 0.7);
        DedupGoldPairSampler.Result r = DedupGoldPairSampler.sample(pool(), q, 42);
        List<SampledPair> pairs = r.pairs();
        assertThat(r.log()).isEmpty();
        assertThat(pairs).hasSize(q.total());
        assertThat(pairs.stream().map(SampledPair::pairNo).toList())
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, q.total()).boxed().toList());
        Map<SamplingStratum, Long> byStratum = pairs.stream()
                .collect(Collectors.groupingBy(p -> p.pair().stratum(), Collectors.counting()));
        assertThat(byStratum).containsEntry(SamplingStratum.SAME_RUN, 10L).containsEntry(SamplingStratum.CROSS_RUN, 12L)
                .containsEntry(SamplingStratum.CROSS_TEMPLATE, 10L).containsEntry(SamplingStratum.RANDOM, 6L);
        for (SampledPair sp : pairs) {
            Pair p = sp.pair();
            assertThat(p.first().id()).isLessThan(p.second().id());
            switch (p.stratum()) {
                case SAME_RUN -> assertThat(p.first().runId()).isEqualTo(p.second().runId());
                case CROSS_RUN -> {
                    assertThat(p.first().runId()).isNotEqualTo(p.second().runId());
                    assertThat(p.first().templateId()).isEqualTo(p.second().templateId());
                }
                case CROSS_TEMPLATE -> assertThat(p.first().templateId()).isNotEqualTo(p.second().templateId());
                case RANDOM -> { }
            }
        }
        assertThat(pairs.stream().filter(p -> p.pair().changedNumbers()).count()).isGreaterThanOrEqualTo(5);
        assertThat(pairs.stream().filter(p -> p.pair().negationDiffers()).count()).isGreaterThanOrEqualTo(3);
        assertThat(pairs.stream().map(p -> p.pair().key()).distinct().count()).isEqualTo(q.total());
    }

    @Test
    void samplerIsDeterministicForSeedAndInputOrder() {
        Quotas q = new Quotas(8, 8, 8, 5, 3, 2, 0.7);
        List<Item> items = pool();
        List<Item> reversed = new ArrayList<>(items);
        java.util.Collections.reverse(reversed);
        List<String> a = keys(DedupGoldPairSampler.sample(items, q, 7));
        List<String> b = keys(DedupGoldPairSampler.sample(reversed, q, 7));
        List<String> c = keys(DedupGoldPairSampler.sample(items, q, 8));
        assertThat(a).containsExactlyElementsOf(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void shortStratumIsLoggedNotHidden() {
        List<Item> tiny = pool().subList(0, 5); // một run: không có cross_run hay cross_template
        DedupGoldPairSampler.Result r = DedupGoldPairSampler.sample(tiny, Quotas.defaults(), 1);
        assertThat(r.log()).anyMatch(s -> s.startsWith("Tầng cross_run: cần 45, chỉ có 0"));
        assertThat(r.pairs()).hasSize(10); // C(5,2)
    }

    @Test
    void predictionMatchesPipelineAndIsMonotonic() {
        List<Item> items = pool();
        List<SampledPair> pairs = DedupGoldPairSampler.sample(items, new Quotas(10, 12, 10, 6, 5, 3, 0.7), 3).pairs();
        for (SampledPair sp : pairs) {
            Pair p = sp.pair();
            boolean previous = true;
            for (double t : DedupGoldPairSampler.THRESHOLDS) {
                boolean dup = p.predictedDuplicate(t);
                assertThat(dup).isEqualTo(DedupChecker.check(p.second().candidate(), List.of(p.first().candidate()), t)
                        .status() == DedupStatus.DUPLICATE);
                assertThat(!dup || previous).as("trùng ở ngưỡng cao thì trùng ở ngưỡng thấp hơn").isTrue();
                previous = dup;
            }
            if (p.changedNumbers()) {
                assertThat(p.predictedDuplicate(0.5)).as("đổi số liệu không bao giờ bị báo trùng").isFalse();
            }
        }
        assertThat(DedupGoldPairSampler.THRESHOLDS).hasSize(10).startsWith(0.5).endsWith(0.95);
        // id 5 (run 1, template 4) và id 10 (run 2, template 4) cùng số liệu: trùng ở mọi ngưỡng
        Pair same = new Pair(items.get(4), items.get(9), SamplingStratum.CROSS_RUN,
                DedupChecker.diagnose(items.get(4).candidate(), items.get(9).candidate()));
        assertThat(same.diagnostics().sameFingerprint()).isTrue();
        assertThat(same.predictedDuplicate(0.95)).isTrue();
    }

    @Test
    void csvAndContentFilesAndRoundTrip() {
        List<Item> items = pool();
        List<SampledPair> pairs = DedupGoldPairSampler.sample(items, new Quotas(4, 4, 4, 3, 2, 1, 0.7), 5).pairs();
        List<String> csv = DedupGoldRunner.keyCsv(pairs);
        assertThat(csv.get(0)).startsWith("cap,q1,q2,dang,tang,cosine,cung_so_lieu,cung_phu_dinh,cung_van_tay,du_doan_050")
                .endsWith(",du_doan_090,du_doan_095,lo,nhom,da_chan");
        assertThat(csv).hasSize(pairs.size() + 1);
        assertThat(csv.get(1).split(",")).hasSize(9 + 10 + 3);
        assertThat(csv.get(1)).endsWith(",1,lo1,0");

        List<String> content = DedupGoldRunner.contentJsonl(pairs);
        assertThat(content).hasSize(pairs.size());
        assertThat(String.join("\n", content)).doesNotContain("cosine", "same_run", "cross_run", "random", "du_doan");

        Map<Integer, Item> poolById = new HashMap<>();
        items.forEach(i -> poolById.put(i.id(), i));
        List<SampledPair> again = DedupGoldRunner.readPairs("﻿" + String.join("\n", csv), poolById);
        assertThat(DedupGoldRunner.keyCsv(again)).containsExactlyElementsOf(csv);

        poolById.remove(pairs.get(0).pair().first().id());
        assertThatThrownBy(() -> DedupGoldRunner.readPairs(String.join("\n", csv), poolById))
                .hasMessageContaining("không còn trong kho");
    }

    /** pool() cộng các câu cùng số liệu, khác cách diễn đạt (id 100-103) để có cặp "cùng số liệu". */
    private static List<Item> poolWithParaphrases() {
        List<Item> items = pool();
        items.add(new Item(mcq(100, "Giá trị của biểu thức $3x - 4$ tại $x = 2$ là"), 7, 4));
        items.add(new Item(mcq(101, "Tính giá trị biểu thức $3x - 4$ tại $x = 2$"), 8, 4));
        items.add(new Item(mcq(102, "Giá trị của biểu thức $3x - 4$ tại $x = 2$ là bao nhiêu"), 9, 4));
        items.add(new Item(mcq(103, "Cho biểu thức $3x - 4$. Tìm giá trị của biểu thức khi $x = 2$"), 9, 3));
        return items;
    }

    @Test
    void batch2CoversBlockedAndSameNumberPairsWithoutOverlap() {
        List<Item> items = poolWithParaphrases();
        List<SampledPair> b1 = DedupGoldPairSampler.sample(items, new Quotas(4, 4, 4, 3, 2, 1, 0.7), 5).pairs();
        Set<String> batch1 = b1.stream().map(p -> p.pair().key()).collect(Collectors.toSet());
        String inBatch1 = batch1.iterator().next();
        int a = Integer.parseInt(inBatch1.split("-")[0]);
        int b = Integer.parseInt(inBatch1.split("-")[1]);
        List<DedupGoldPairSampler.BlockedPair> blocked = List.of(
                new DedupGoldPairSampler.BlockedPair(102, 100),   // thứ tự (bị loại, khớp) tùy ý
                new DedupGoldPairSampler.BlockedPair(b, a),       // đã có ở lô 1: không thêm lần nữa
                new DedupGoldPairSampler.BlockedPair(102, 100),   // dòng lặp
                new DedupGoldPairSampler.BlockedPair(9999, 100)); // câu không còn trong kho
        DedupGoldPairSampler.Batch2Quotas q = new DedupGoldPairSampler.Batch2Quotas(6, 2);

        DedupGoldPairSampler.Result r = DedupGoldPairSampler.sampleBatch2(items, batch1, blocked, q, 11);
        List<SampledPair> b2 = r.pairs();
        assertThat(b2).hasSizeLessThanOrEqualTo(q.total());
        assertThat(b2).allMatch(p -> p.batch() == 2 && p.pairNo() == 0);
        assertThat(b2.stream().map(p -> p.pair().key()).toList()).doesNotContainAnyElementsOf(batch1)
                .doesNotHaveDuplicates();
        assertThat(r.log().get(0)).contains("4 dòng").contains("không còn trong kho/không cùng dạng 1")
                .contains("đã có ở lô 1 1").contains("thêm vào lô 2 1");
        SampledPair blockedPair = b2.stream().filter(p -> p.pair().key().equals("100-102")).findFirst().orElseThrow();
        assertThat(blockedPair.group()).isEqualTo(DedupGoldPairSampler.GROUP_BLOCKED);
        assertThat(blockedPair.pair().first().id()).isLessThan(blockedPair.pair().second().id());
        for (SampledPair sp : b2) {
            PairDiagnostics d = sp.pair().diagnostics();
            if (DedupGoldPairSampler.GROUP_SAME_NUMBERS_HIGH.equals(sp.group())) {
                assertThat(d.sameSignature() && d.cosine() >= 0.90).isTrue();
            } else if (DedupGoldPairSampler.GROUP_SAME_NUMBERS_MID.equals(sp.group())) {
                assertThat(d.sameSignature() && d.cosine() >= 0.60 && d.cosine() < 0.90).isTrue();
            } else {
                assertThat(sp.group()).isEqualTo(DedupGoldPairSampler.GROUP_BLOCKED);
            }
        }
        assertThat(b2.stream().filter(p -> DedupGoldPairSampler.GROUP_SAME_NUMBERS_HIGH.equals(p.group())).count())
                .isLessThanOrEqualTo(q.maxSameHigh());
        // cùng seed và dữ liệu thì cùng lô 2
        assertThat(DedupGoldPairSampler.sampleBatch2(items, batch1, blocked, q, 11).pairs().stream()
                .map(p -> p.pair().key() + p.group()).toList())
                .containsExactlyElementsOf(b2.stream().map(p -> p.pair().key() + p.group()).toList());

        // gộp: xáo, mã liên tục 1..N, giữ lô và nhóm; CSV ghi lo/nhom/da_chan và đọc lại được
        List<SampledPair> merged = DedupGoldPairSampler.merge(b1, b2, 11);
        assertThat(merged).hasSize(b1.size() + b2.size());
        assertThat(merged.stream().map(SampledPair::pairNo).toList())
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, merged.size()).boxed().toList());
        assertThat(merged.stream().filter(p -> p.batch() == 1).count()).isEqualTo(b1.size());
        assertThat(merged.stream().map(p -> p.pair().key()).toList()).doesNotHaveDuplicates();
        assertThat(merged.stream().map(SampledPair::batch).toList()).isNotEqualTo(
                merged.stream().map(SampledPair::batch).sorted().toList()); // đã xáo, không xếp lô 1 rồi lô 2

        List<String> csv = DedupGoldRunner.keyCsv(merged, Set.of("100-102"));
        assertThat(csv.stream().filter(l -> l.endsWith(",1")).count()).isEqualTo(1);
        assertThat(csv.stream().filter(l -> l.contains(",2,da_chan,")).count()).isEqualTo(1);
        Map<Integer, Item> byId = new HashMap<>();
        items.forEach(i -> byId.put(i.id(), i));
        List<SampledPair> again = DedupGoldRunner.readPairs(String.join("\n", csv), byId);
        assertThat(again.stream().map(p -> p.batch() + p.group()).toList())
                .containsExactlyElementsOf(merged.stream().map(p -> p.batch() + p.group()).toList());
        // nội dung phiếu không lộ lô hay nhóm
        assertThat(String.join("\n", DedupGoldRunner.contentJsonl(merged))).doesNotContain("da_chan", "cung_so_lieu",
                "lo1", "\"lo\"", "nhom");
    }

    @Test
    void batch2IsLoggedWhenSameNumberPairsAreScarce() {
        DedupGoldPairSampler.Result r = DedupGoldPairSampler.sampleBatch2(pool(), Set.of(), List.of(),
                new DedupGoldPairSampler.Batch2Quotas(60, 15), 1);
        assertThat(r.log()).anyMatch(s -> s.startsWith("Cùng số liệu, cosine 0,60-0,90: cần"));
        assertThat(r.pairs().size()).isLessThan(60);
    }

    @Test
    void outDirMustBeOutsideRepoOrUnderDataPrivate(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        Path wd = Files.createDirectories(repo.resolve("back_end"));
        assertThatThrownBy(() -> DedupGoldRunner.resolveOutDir(repo.resolve("docs").toString(), wd))
                .isInstanceOf(IllegalStateException.class);
        assertThat(DedupGoldRunner.resolveOutDir(repo.resolve("data/private/do-luong").toString(), wd))
                .isEqualTo(repo.resolve("data/private/do-luong").toAbsolutePath().normalize());
        assertThat(DedupGoldRunner.resolveOutDir(tmp.resolve("ngoai").toString(), wd))
                .isEqualTo(tmp.resolve("ngoai").toAbsolutePath().normalize());
        assertThatThrownBy(() -> DedupGoldRunner.resolveOutDir(" ", wd)).isInstanceOf(IllegalStateException.class);
    }

    private static List<String> keys(DedupGoldPairSampler.Result r) {
        return r.pairs().stream().map(p -> p.pairNo() + ":" + p.pair().key() + ":" + p.pair().stratum()).toList();
    }
}
