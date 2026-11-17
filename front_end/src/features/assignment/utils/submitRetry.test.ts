import { describe, expect, it, vi } from "vitest";

import { axiosError } from "../test/fixtures";
import {
    classifySubmitError,
    getRetryAfterSeconds,
    nextDelayMs,
    submitWithRetry,
} from "./submitRetry";

const OK = { submittedAt: "2026-11-20T09:30:00", resultsAt: "2026-11-20T17:02:00" };
const noJitter = () => 0;

describe("nextDelayMs", () => {
    it("lùi dần 1, 2, 4, 8, 15 giây (không quá 15 giây)", () => {
        const delays = [0, 1, 2, 3, 4, 5].map((i) => nextDelayMs(i, axiosError(undefined), noJitter));
        expect(delays).toEqual([1000, 2000, 4000, 8000, 15000, 15000]);
    });

    it("không chờ ngắn hơn Retry-After của máy chủ", () => {
        const error = axiosError(429, { code: "TOO_MANY_REQUESTS" }, { "retry-after": "20" });
        expect(nextDelayMs(0, error, noJitter)).toBe(20000);
    });

    it("vẫn dùng khoảng lùi dần khi nó dài hơn Retry-After", () => {
        const error = axiosError(429, {}, { "retry-after": "1" });
        expect(nextDelayMs(3, error, noJitter)).toBe(8000);
    });

    it("có header Retry-After thì dùng header, bỏ qua số giây trong câu báo", () => {
        const error = axiosError(429, { error: "Em bấm nộp quá nhiều lần. Thử lại sau 7 giây." }, { "retry-after": "12" });
        expect(getRetryAfterSeconds(error)).toBe(12);
        expect(nextDelayMs(0, error, noJitter)).toBe(12000);
    });

    it("đọc số giây từ câu báo khi trình duyệt không đọc được header (khác origin)", () => {
        const error = axiosError(429, { error: "Em bấm nộp quá nhiều lần. Thử lại sau 7 giây." });
        expect(getRetryAfterSeconds(error)).toBe(7);
        expect(nextDelayMs(0, error, noJitter)).toBe(7000);
    });

    it("giới hạn Retry-After ở 60 giây", () => {
        const error = axiosError(429, {}, { "retry-after": "600" });
        expect(nextDelayMs(0, error, noJitter)).toBe(60000);
    });
});

describe("classifySubmitError", () => {
    it("phân loại các lỗi nộp bài", () => {
        expect(classifySubmitError(axiosError(undefined))).toBe("network");
        expect(classifySubmitError(axiosError(429))).toBe("rate_limited");
        expect(classifySubmitError(axiosError(503))).toBe("server");
        expect(classifySubmitError(axiosError(401))).toBe("token_invalid");
        expect(classifySubmitError(axiosError(400))).toBe("invalid");
        expect(classifySubmitError(axiosError(409, { code: "ASSIGNMENT_CLOSED" }))).toBe("closed");
        expect(classifySubmitError(axiosError(409, { code: "ATTEMPT_EXPIRED" }))).toBe("expired");
    });
});

describe("submitWithRetry", () => {
    it("thử lại khi gặp 429 và thành công ở lần sau, chờ đúng theo Retry-After", async () => {
        const send = vi
            .fn()
            .mockRejectedValueOnce(axiosError(429, { code: "TOO_MANY_REQUESTS" }, { "retry-after": "3" }))
            .mockResolvedValueOnce(OK);
        const sleep = vi.fn().mockResolvedValue(undefined);
        const onRetry = vi.fn();

        const outcome = await submitWithRetry(send, { sleep, random: noJitter, onRetry });

        expect(outcome.result).toEqual(OK);
        expect(outcome.attempts).toBe(2);
        expect(send).toHaveBeenCalledTimes(2);
        expect(sleep).toHaveBeenCalledWith(3000);
        expect(onRetry).toHaveBeenCalledWith({ attempt: 1, waitMs: 3000, kind: "rate_limited" });
    });

    it("thử lại khi mất mạng (không có phản hồi)", async () => {
        const send = vi
            .fn()
            .mockRejectedValueOnce(axiosError(undefined))
            .mockRejectedValueOnce(axiosError(undefined))
            .mockResolvedValueOnce(OK);
        const sleep = vi.fn().mockResolvedValue(undefined);

        const outcome = await submitWithRetry(send, { sleep, random: noJitter });

        expect(outcome.attempts).toBe(3);
        expect(sleep.mock.calls.map((c) => c[0])).toEqual([1000, 2000]);
    });

    it("coi 'đã nộp rồi' là xong: lần gửi trước có thể đã tới máy chủ", async () => {
        const send = vi
            .fn()
            .mockRejectedValueOnce(axiosError(undefined))
            .mockRejectedValueOnce(axiosError(409, { code: "ATTEMPT_ALREADY_SUBMITTED" }));
        const sleep = vi.fn().mockResolvedValue(undefined);

        const outcome = await submitWithRetry(send, { sleep, random: noJitter });

        expect(outcome.alreadySubmitted).toBe(true);
        expect(outcome.result).toBeUndefined();
    });

    it.each([
        ["đóng bài", axiosError(409, { code: "ASSIGNMENT_CLOSED" })],
        ["quá ân hạn", axiosError(409, { code: "ATTEMPT_EXPIRED" })],
        ["token sai", axiosError(401)],
        ["dữ liệu sai", axiosError(400)],
    ])("không thử lại khi %s", async (_name, error) => {
        const send = vi.fn().mockRejectedValue(error);
        const sleep = vi.fn().mockResolvedValue(undefined);

        await expect(submitWithRetry(send, { sleep })).rejects.toBe(error);
        expect(send).toHaveBeenCalledTimes(1);
        expect(sleep).not.toHaveBeenCalled();
    });

    it("hết số lần thử thì ném lỗi cuối", async () => {
        const error = axiosError(429);
        const send = vi.fn().mockRejectedValue(error);
        const sleep = vi.fn().mockResolvedValue(undefined);

        await expect(submitWithRetry(send, { sleep, maxAttempts: 3, random: noJitter })).rejects.toBe(error);
        expect(send).toHaveBeenCalledTimes(3);
        expect(sleep).toHaveBeenCalledTimes(2);
    });

    it("dừng thử lại khi được báo dừng (rời trang)", async () => {
        const error = axiosError(undefined);
        const send = vi.fn().mockRejectedValue(error);
        const sleep = vi.fn().mockResolvedValue(undefined);
        let stop = false;

        await expect(
            submitWithRetry(send, {
                sleep: async (ms) => {
                    await sleep(ms);
                    stop = true;
                },
                shouldStop: () => stop,
                random: noJitter,
            })
        ).rejects.toBe(error);
        expect(send).toHaveBeenCalledTimes(1);
    });
});
