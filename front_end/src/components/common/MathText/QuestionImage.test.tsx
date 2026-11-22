import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import QuestionImage from "./QuestionImage";
import { resolveImageUrl } from "./questionImage.utils";
import type { QuestionImageInfo } from "./questionImage.types";

const base: QuestionImageInfo = { id: 4, role: "stem", url: "/api/question-images/4?exp=1&u=7&sig=abc" };

afterEach(() => {
    vi.unstubAllEnvs();
});

describe("resolveImageUrl", () => {
    it("ghép gốc API và đường dẫn bắt đầu bằng /api, bỏ dấu / thừa ở gốc", () => {
        vi.stubEnv("VITE_API_BASE_URL", "http://localhost:8080");
        expect(resolveImageUrl("/api/question-images/4?x=1")).toBe("http://localhost:8080/api/question-images/4?x=1");
        vi.stubEnv("VITE_API_BASE_URL", "http://localhost:8080/");
        expect(resolveImageUrl("/api/question-images/4")).toBe("http://localhost:8080/api/question-images/4");
        vi.stubEnv("VITE_API_BASE_URL", "/");
        expect(resolveImageUrl("/api/question-images/4")).toBe("/api/question-images/4");
    });

    it("URL tuyệt đối giữ nguyên", () => {
        vi.stubEnv("VITE_API_BASE_URL", "http://localhost:8080");
        expect(resolveImageUrl("https://cdn.example/a.png")).toBe("https://cdn.example/a.png");
    });
});

describe("QuestionImage", () => {
    it("vẽ thẻ img có alt, tải lười, bề rộng theo displayWidthMm và không vượt khung", () => {
        render(<QuestionImage image={{ ...base, displayWidthMm: 60 }} />);
        const img = screen.getByRole("img", { name: "Hình minh họa" });
        expect(img).toHaveAttribute("loading", "lazy");
        // 60 mm ở 96 dpi ≈ 227 px
        expect(img).toHaveStyle({ width: "227px", maxWidth: "100%" });
    });

    it("không có displayWidthMm thì để bề rộng tự nhiên nhưng vẫn không vượt khung", () => {
        render(<QuestionImage image={base} />);
        const img = screen.getByRole("img");
        expect(img.style.width).toBe("");
        expect(img).toHaveStyle({ maxWidth: "100%" });
    });

    it("ảnh lỗi hiện chữ thay thế và bỏ thẻ img", () => {
        render(<QuestionImage image={base} />);
        fireEvent.error(screen.getByRole("img"));
        expect(screen.queryByRole("img")).toBeNull();
        expect(screen.getByText("[Hình không tải được]")).toBeInTheDocument();
    });
});
