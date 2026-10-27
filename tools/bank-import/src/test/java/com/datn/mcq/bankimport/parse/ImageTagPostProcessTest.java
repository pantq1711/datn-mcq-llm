package com.datn.mcq.bankimport.parse;

import com.datn.mcq.bankimport.model.Question;
import com.datn.mcq.bankimport.model.Question.ImageRef;
import com.datn.mcq.bankimport.model.Question.Option;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageTagPostProcessTest {

    private ImageRef img(String id, String role) {
        ImageRef im = new ImageRef();
        im.id = id;
        im.role = role;
        return im;
    }

    @Test
    void stemChuaCoTheNoiVaoCuoi() {
        Question q = new Question();
        q.stem = "abc";
        q.images.add(img("img1", "stem"));
        DocParser.attachMissingImageTags(q);
        assertEquals("abc\n⟦img1⟧", q.stem);
    }

    @Test
    void stemRongBangDungThe() {
        Question q = new Question();
        q.stem = "";
        q.images.add(img("img1", "stem"));
        DocParser.attachMissingImageTags(q);
        assertEquals("⟦img1⟧", q.stem);
    }

    @Test
    void stemNullBangDungThe() {
        Question q = new Question();
        q.stem = null;
        q.images.add(img("img1", "stem"));
        DocParser.attachMissingImageTags(q);
        assertEquals("⟦img1⟧", q.stem);
    }

    @Test
    void daCoTheTrongStemKhongDoiGi() {
        Question q = new Question();
        q.stem = "abc ⟦img1⟧ def";
        q.images.add(img("img1", "stem"));
        DocParser.attachMissingImageTags(q);
        assertEquals("abc ⟦img1⟧ def", q.stem);
    }

    @Test
    void daCoTheTrongPhuongAnKhongNoiVaoStem() {
        Question q = new Question();
        q.stem = "abc";
        q.options = List.of(new Option("A", "⟦img1⟧ phương án A"));
        q.images.add(img("img1", "stem"));
        DocParser.attachMissingImageTags(q);
        assertEquals("abc", q.stem);
    }

    @Test
    void haiAnhThieuTheNoiDungThuTu() {
        Question q = new Question();
        q.stem = "abc";
        q.images.add(img("img1", "stem"));
        q.images.add(img("img2", "stem"));
        DocParser.attachMissingImageTags(q);
        assertEquals("abc\n⟦img1⟧\n⟦img2⟧", q.stem);
    }

    @Test
    void roleSolutionNoiVaoSolution() {
        Question q = new Question();
        q.stem = "abc";
        q.solution = "hướng dẫn";
        q.images.add(img("img1", "solution"));
        DocParser.attachMissingImageTags(q);
        assertEquals("abc", q.stem);
        assertEquals("hướng dẫn\n⟦img1⟧", q.solution);
    }
}

