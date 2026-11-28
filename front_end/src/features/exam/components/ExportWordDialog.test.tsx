import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "../services/examApi";
import type { ExamDetail, ExamItemDto } from "../types/exam.types";
import { isYoungMixShortAnswer } from "../../../utils/exam.utils";
import ExportWordDialog from "./ExportWordDialog";

vi.mock("../services/examApi");
vi.setConfig({ testTimeout: 20000 });

const mocked = vi.mocked(api);

const YOUNGMIX_LABEL = "Cho phần mềm trộn đề (YoungMix)";
const YOUNGMIX_HINT =
    "Chỉ nội dung đề, đáp án đúng được gạch chân để YoungMix trộn và tạo đáp án, mã QR chấm. Không có đầu đề, đáp án, lời giải.";
const PHACH_LABEL = "Có tờ phách (thường dùng cho đề cuối kì)";

function sa(slotNo: number, saAnswer: string | undefined): ExamItemDto {
    return {
        slotId: 30 + slotNo, cellId: 3, slotNo, part: "III", topic: "Số", questionType: "sa", state: "ready",
        questionText: `Câu hỏi ${slotNo}`, saAnswer, needsReview: false, reviewReasons: [], edited: false,
    };
}

function detail(answers: (string | undefined)[], title = "Cuối kì I – Toán 8"): ExamDetail {
    return {
        examId: 7, examCode: "MT2611261200007", title, mode: "teacher", status: "draft",
        matrixId: 3, createdAt: "2026-11-26T08:00:00", buildStatus: "ready", needsReviewCount: 0, kind: "matrix",
        totalScore: 10,
        parts: [{ part: "III", required: answers.length, ready: answers.length, items: answers.map((a, i) => sa(i + 1, a)) }],
    } as ExamDetail;
}

function renderDialog(answers: (string | undefined)[] = ["10"], title?: string) {
    return render(<ExportWordDialog open examId="7" detail={detail(answers, title)} onClose={vi.fn()} />);
}

let downloads: string[] = [];

beforeEach(() => {
    vi.resetAllMocks();
    downloads = [];
    mocked.getExamExportDocx.mockResolvedValue({ blob: new Blob(["x"]), keptOmml: 0, scaled: 0, failed: 0, failedAt: [] });
    window.URL.createObjectURL = vi.fn(() => "blob:x");
    window.URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
        downloads.push(this.download);
    });
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe("isYoungMixShortAnswer: đáp án phần III YoungMix nhận được (số, tối đa 4 ký tự)", () => {
    it.each(["10", "2,5", "-1,5", "2.5", "$1,5$", "0", "1234", "−3"])("nhận %s", (v) => {
        expect(isYoungMixShortAnswer(v)).toBe(true);
    });
    it.each([undefined, "", "12,345", "-1234", "\\frac{1}{2}", "$\\frac{1}{2}$", "6 km", "05", "-0", "3,", "1/2"])(
        "không nhận %s",
        (v) => {
            expect(isYoungMixShortAnswer(v)).toBe(false);
        }
    );
});

describe("ExportWordDialog: bản YoungMix, tờ phách, cỡ chữ (26/11)", () => {
    it("có lựa chọn YoungMix kèm mô tả; chọn thì ẩn mã đề, thời gian, tờ phách", async () => {
        const user = userEvent.setup();
        renderDialog();

        expect(screen.getByLabelText("Mã đề", { exact: false })).toBeInTheDocument();
        expect(screen.getByRole("checkbox", { name: PHACH_LABEL })).not.toBeChecked();
        await user.click(screen.getByRole("radio", { name: YOUNGMIX_LABEL }));

        expect(screen.getByText(YOUNGMIX_HINT)).toBeInTheDocument();
        expect(screen.queryByRole("checkbox", { name: PHACH_LABEL })).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Mã đề", { exact: false })).not.toBeInTheDocument();
        expect(screen.queryByLabelText("Thời gian làm bài (phút)")).not.toBeInTheDocument();
        expect(screen.getByRole("radio", { name: "14" })).toBeInTheDocument();
    });

    it("YoungMix: cảnh báo trước khi tải các câu phần III có đáp án không phải số hoặc quá 4 ký tự, không chặn", async () => {
        const user = userEvent.setup();
        renderDialog(["10", "12,345", "\\frac{1}{2}", "2,5", undefined]);

        expect(screen.queryByText(/Câu 2 phần III/)).not.toBeInTheDocument();
        await user.click(screen.getByRole("radio", { name: YOUNGMIX_LABEL }));

        const warning = screen.getByText(/Câu 2 phần III, Câu 3 phần III, Câu 5 phần III/);
        expect(warning).toBeInTheDocument();
        expect(screen.getByText(/tối đa 4 ký tự/)).toBeInTheDocument();
        expect(screen.queryByText(/Câu 1 phần III|Câu 4 phần III/)).not.toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: /Tải về/ }));
        await waitFor(() => expect(mocked.getExamExportDocx).toHaveBeenCalledTimes(1));
        const [, equation, options] = mocked.getExamExportDocx.mock.calls[0];
        expect(equation).toBe("mathtype");
        expect(options).toEqual({ content: "youngmix", durationMinutes: undefined, examCode: undefined, phach: undefined,
            fontSize: undefined });
        expect(downloads[0]).toMatch(/^Cuối kì I – Toán 8 – YoungMix – \d{2}-\d{2}-\d{4}\.docx$/);
    });

    it("đáp án hợp lệ hết thì không cảnh báo", async () => {
        const user = userEvent.setup();
        renderDialog(["10", "2,5", "-3"]);
        await user.click(screen.getByRole("radio", { name: YOUNGMIX_LABEL }));
        expect(screen.queryByText(/phần III/)).not.toBeInTheDocument();
    });

    it("mặc định không phách, cỡ 12: không gửi phach, fontSize", async () => {
        const user = userEvent.setup();
        renderDialog();
        expect(screen.getByRole("radio", { name: "12" })).toBeChecked();
        await user.click(screen.getByRole("button", { name: /Tải về/ }));
        await waitFor(() => expect(mocked.getExamExportDocx).toHaveBeenCalledTimes(1));
        expect(mocked.getExamExportDocx.mock.calls[0][2]).toMatchObject({ phach: undefined, fontSize: undefined });
    });

    it("tích tờ phách và chọn cỡ 14 thì gửi phach = true, fontSize = 14", async () => {
        const user = userEvent.setup();
        renderDialog();
        await user.click(screen.getByRole("radio", { name: "Chỉ đề (không kèm đáp án)" }));
        await user.click(screen.getByRole("checkbox", { name: PHACH_LABEL }));
        await user.click(screen.getByRole("radio", { name: "14" }));
        await user.click(screen.getByRole("button", { name: /Tải về/ }));
        await waitFor(() => expect(mocked.getExamExportDocx).toHaveBeenCalledTimes(1));
        expect(mocked.getExamExportDocx.mock.calls[0][2]).toMatchObject({ content: "exam", phach: true, fontSize: 14 });
    });

    it("bản đặc tả: không có tờ phách và cỡ chữ", async () => {
        const user = userEvent.setup();
        renderDialog();
        await user.click(screen.getByRole("checkbox", { name: PHACH_LABEL }));
        await user.click(screen.getByRole("radio", { name: "14" }));
        await user.click(screen.getByRole("radio", { name: "Bản đặc tả đề" }));
        expect(screen.queryByRole("checkbox", { name: PHACH_LABEL })).not.toBeInTheDocument();
        expect(screen.queryByRole("radio", { name: "14" })).not.toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: /Tải về/ }));
        await waitFor(() => expect(mocked.getExamExportDocx).toHaveBeenCalledTimes(1));
        expect(mocked.getExamExportDocx.mock.calls[0][2]).toMatchObject({ content: "spec", phach: undefined,
            fontSize: undefined });
    });

    it("đợt 3: tên đề mặc định có đuôi '– DD/MM' thì tên file bỏ đuôi đó trước khi ghép ngày xuất", async () => {
        const user = userEvent.setup();
        renderDialog(["10"], "Giữa kì I – Toán 8 – 28/11");

        const fileInput = screen.getByLabelText("Tên file", { exact: false }) as HTMLInputElement;
        expect(fileInput.value).toMatch(/^Giữa kì I – Toán 8 – \d{2}-\d{2}-\d{4}$/);
        expect(fileInput.value).not.toContain("28 11");

        await user.click(screen.getByRole("radio", { name: "Bản đặc tả đề" }));
        expect(fileInput.value).toMatch(/^Giữa kì I – Toán 8 – Bản đặc tả – \d{2}-\d{2}-\d{4}$/);

        await user.click(screen.getByRole("radio", { name: YOUNGMIX_LABEL }));
        expect(fileInput.value).toMatch(/^Giữa kì I – Toán 8 – YoungMix – \d{2}-\d{2}-\d{4}$/);

        await user.click(screen.getByRole("button", { name: /Tải về/ }));
        await waitFor(() => expect(downloads[0]).toMatch(/^Giữa kì I – Toán 8 – YoungMix – \d{2}-\d{2}-\d{4}\.docx$/));
    });
});
