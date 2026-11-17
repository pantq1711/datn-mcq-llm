import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import * as api from "../services/assignmentApi";
import { axiosError, makeItemStats } from "../test/fixtures";
import ItemStatsTab from "./ItemStatsTab";

vi.mock("../services/assignmentApi");

const mocked = vi.mocked(api);

function renderTab(status: "open" | "closed" = "closed") {
    return render(<ItemStatsTab examId="5" assignmentId="7" status={status} />);
}

beforeEach(() => {
    vi.resetAllMocks();
});

afterEach(() => {
    vi.useRealTimers();
});

describe("ItemStatsTab: trạng thái chờ", () => {
    it("bài còn mở: báo thống kê có sau khi đóng bài và không gọi API", () => {
        renderTab("open");

        expect(screen.getByText("Thống kê có sau khi đóng bài")).toBeInTheDocument();
        expect(mocked.getItemStats).not.toHaveBeenCalled();
    });

    it("API báo ASSIGNMENT_OPEN (bài vừa được mở lại ở nơi khác) cũng hiện câu đó", async () => {
        mocked.getItemStats.mockRejectedValue(axiosError(409, { code: "ASSIGNMENT_OPEN", error: "Bài giao còn mở." }));
        renderTab();

        expect(await screen.findByText("Thống kê có sau khi đóng bài")).toBeInTheDocument();
    });

    it("STATS_NOT_READY: hiện giờ có thống kê rồi tự tải lại khi tới giờ", async () => {
        // 10:01:57Z = 17:01:57 giờ máy chủ (UTC+7); thống kê có lúc 17:02:00
        vi.useFakeTimers({ shouldAdvanceTime: true });
        vi.setSystemTime(new Date("2026-11-20T10:01:57Z"));
        mocked.getItemStats
            .mockRejectedValueOnce(
                axiosError(409, { code: "STATS_NOT_READY", error: "Còn ân hạn.", errors: ["2026-11-20T17:02:00"] })
            )
            .mockResolvedValueOnce(makeItemStats());
        renderTab();

        expect(await screen.findByText("Có sau 17:02 ngày 20/11/2026")).toBeInTheDocument();
        expect(mocked.getItemStats).toHaveBeenCalledTimes(1);

        await vi.advanceTimersByTimeAsync(5000);

        expect(await screen.findByText("Phần I – Câu 1")).toBeInTheDocument();
        expect(mocked.getItemStats).toHaveBeenCalledTimes(2);
    });

    it("lỗi khác: hiện thông báo của máy chủ kèm nút Thử lại, bấm thì tải lại", async () => {
        const user = userEvent.setup();
        mocked.getItemStats
            .mockRejectedValueOnce(axiosError(500, { error: "Máy chủ đang bận." }))
            .mockResolvedValueOnce(makeItemStats());
        renderTab();

        expect(await screen.findByText("Máy chủ đang bận.")).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: /Thử lại/ }));

        expect(await screen.findByText("Phần I – Câu 1")).toBeInTheDocument();
        expect(mocked.getItemStats).toHaveBeenCalledTimes(2);
    });

    it("mất kết nối: có câu thông báo và nút Thử lại", async () => {
        mocked.getItemStats.mockRejectedValue(axiosError(undefined));
        renderTab();

        expect(await screen.findByText(/Không kết nối được máy chủ/)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /Thử lại/ })).toBeInTheDocument();
    });
});

describe("ItemStatsTab: đủ mẫu", () => {
    beforeEach(() => {
        mocked.getItemStats.mockResolvedValue(makeItemStats());
    });

    it("đầu tab ghi số lượt hợp lệ, số lượt bị loại kèm lý do, và không có cảnh báo mẫu", async () => {
        renderTab();

        expect(await screen.findByText(/36 lượt làm hợp lệ/)).toBeInTheDocument();
        expect(screen.getByText(/2 lượt trùng STT/)).toBeInTheDocument();
        expect(screen.getByText(/3 lượt chưa nộp/)).toBeInTheDocument();
        expect(document.querySelector(".ant-alert")).toBeNull();
    });

    it("có chú thích bằng lời cho p và D", async () => {
        renderTab();

        expect(await screen.findByText(/là tỉ lệ học sinh làm đúng câu/)).toBeInTheDocument();
        expect(screen.getByText(/cho biết câu có phân biệt được học sinh khá và học sinh yếu/)).toBeInTheDocument();
    });

    it("bảng ghi số câu theo phần, p, D (dấu phẩy) và cờ bằng tiếng Việt", async () => {
        renderTab();

        const row1 = (await screen.findByText("Phần I – Câu 1")).closest("tr") as HTMLElement;
        expect(within(row1).getByText("0,58")).toBeInTheDocument();
        expect(within(row1).getByText("0,10")).toBeInTheDocument();
        expect(within(row1).getByText("Phân biệt kém")).toBeInTheDocument();
        expect(within(row1).getByText("Phương án D gần như không ai chọn")).toBeInTheDocument();
        expect(within(row1).getByText("Nghi đáp án sai (nhóm điểm cao chọn C)")).toBeInTheDocument();

        expect(screen.getByText("Phần II – Câu 1")).toBeInTheDocument();
        expect(screen.getByText("Phần III – Câu 1")).toBeInTheDocument();
    });

    it("câu không có cờ hiện gạch ngang thay vì để trống", async () => {
        renderTab();

        const row2 = (await screen.findByText("Phần II – Câu 1")).closest("tr") as HTMLElement;
        expect(within(row2).getByText("Không có cờ")).toBeInTheDocument();
    });

    it("mở câu trắc nghiệm: số lượt chọn A–D và tô đáp án đúng", async () => {
        const user = userEvent.setup();
        renderTab();

        await user.click(await screen.findByText("Phần I – Câu 1"));

        const detail = await screen.findByTestId("item-detail-101");
        const rowB = within(detail).getByText("B").closest("tr") as HTMLElement;
        expect(within(rowB).getByText("21")).toBeInTheDocument();
        expect(within(rowB).getByText("Đáp án đúng")).toBeInTheDocument();
        expect(rowB).toHaveClass("item-stat-correct");
        const rowA = within(detail).getByText("A").closest("tr") as HTMLElement;
        expect(within(rowA).getByText("9")).toBeInTheDocument();
        expect(within(rowA).queryByText("Đáp án đúng")).not.toBeInTheDocument();
        expect(within(detail).getByText("Bỏ trống")).toBeInTheDocument();
    });

    it("mở câu đúng/sai: p, D từng ý a–d; câu đã đổi sau khi giao thì có ghi chú", async () => {
        const user = userEvent.setup();
        renderTab();

        // thẻ nhận biết ngay ở dòng, trước khi mở
        const row = (await screen.findByText("Phần II – Câu 1")).closest("tr") as HTMLElement;
        expect(within(row).getByText("Đã đổi sau khi giao")).toBeInTheDocument();

        await user.click(screen.getByText("Phần II – Câu 1"));

        const detail = await screen.findByTestId("item-detail-102");
        expect(within(detail).getByText(/Câu này đã được đổi sau khi giao bài/)).toBeInTheDocument();
        const rowA = within(detail).getByText("Ý a").closest("tr") as HTMLElement;
        expect(within(rowA).getByText("0,83")).toBeInTheDocument();
        expect(within(rowA).getByText("0,40")).toBeInTheDocument();
        expect(within(rowA).getByText("Đúng")).toBeInTheDocument();
        const rowB = within(detail).getByText("Ý b").closest("tr") as HTMLElement;
        expect(within(rowB).getByText("Sai")).toBeInTheDocument();
    });

    it("mở câu trả lời ngắn: số bỏ trống và các đáp số sai hay gặp", async () => {
        const user = userEvent.setup();
        renderTab();

        await user.click(await screen.findByText("Phần III – Câu 1"));

        const detail = await screen.findByTestId("item-detail-103");
        expect(within(detail).getByText(/Đáp số đúng: 2,5/)).toBeInTheDocument();
        expect(within(detail).getByText(/Bỏ trống: 5 lượt/)).toBeInTheDocument();
        expect(within(detail).getByText("25")).toBeInTheDocument();
        expect(within(detail).getByText(/6 lượt/)).toBeInTheDocument();
        expect(within(detail).getByText("0,25")).toBeInTheDocument();
        expect(within(detail).queryByText(/đã được đổi sau khi giao bài/)).not.toBeInTheDocument();
    });

    it("Tính lại: gọi B2 và hiện số liệu mới", async () => {
        const user = userEvent.setup();
        mocked.recomputeItemStats.mockResolvedValue(makeItemStats({ n: 40, groupSize: 11 }));
        renderTab();

        await screen.findByText(/36 lượt làm hợp lệ/);
        await user.click(screen.getByRole("button", { name: /Tính lại/ }));

        expect(await screen.findByText(/40 lượt làm hợp lệ/)).toBeInTheDocument();
        expect(mocked.recomputeItemStats).toHaveBeenCalledWith("5", "7");
    });

    it("Tính lại gặp STATS_NOT_READY thì chuyển sang chờ, không để màn hình cũ", async () => {
        const user = userEvent.setup();
        mocked.recomputeItemStats.mockRejectedValue(
            axiosError(409, { code: "STATS_NOT_READY", error: "Còn ân hạn.", errors: ["2099-01-01T08:00:00"] })
        );
        renderTab();

        await screen.findByText(/36 lượt làm hợp lệ/);
        await user.click(screen.getByRole("button", { name: /Tính lại/ }));

        expect(await screen.findByText("Có sau 08:00 ngày 01/01/2099")).toBeInTheDocument();
        expect(screen.queryByText(/36 lượt làm hợp lệ/)).not.toBeInTheDocument();
    });
});

describe("ItemStatsTab: chưa đủ lượt (n < 20)", () => {
    const small = () =>
        makeItemStats({
            n: 12,
            groupSize: 3,
            sampleSufficient: false,
            sampleNote: "Mới có 12 lượt, cần ít nhất 20 thì D và cờ mới đáng tin.",
            excluded: { duplicate: 0, notSubmitted: 0 },
        });

    it("hiện Alert vàng với sampleNote của máy chủ", async () => {
        mocked.getItemStats.mockResolvedValue(small());
        renderTab();

        const note = await screen.findByText("Mới có 12 lượt, cần ít nhất 20 thì D và cờ mới đáng tin.");
        expect(note.closest(".ant-alert")).toHaveClass("ant-alert-warning");
    });

    it("cờ insufficient_sample hiện là 'Chưa đủ lượt'", async () => {
        const data = small();
        data.items = data.items.map((it) => ({ ...it, flags: ["insufficient_sample"] }));
        mocked.getItemStats.mockResolvedValue(data);
        renderTab();

        await screen.findByText("Phần I – Câu 1");
        expect(screen.getAllByText("Chưa đủ lượt")).toHaveLength(3);
    });

    it("không có lượt nào: báo chưa có số liệu", async () => {
        mocked.getItemStats.mockResolvedValue(
            makeItemStats({ n: 0, groupSize: 0, sampleSufficient: false, sampleNote: "Chưa có lượt nộp.", items: [] })
        );
        renderTab();

        expect(await screen.findByText(/Chưa có lượt nào được tính/)).toBeInTheDocument();
        expect(screen.getByText("Chưa có lượt nộp.")).toBeInTheDocument();
    });
});
