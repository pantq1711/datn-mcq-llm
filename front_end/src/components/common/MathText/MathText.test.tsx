import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import MathText from "./MathText";
import { isBlankMath, splitMath } from "./mathSegments";
import type { QuestionImageInfo } from "./questionImage.types";

const image = (id: number, extra: Partial<QuestionImageInfo> = {}): QuestionImageInfo => ({
    id,
    role: "stem",
    url: `/api/question-images/${id}?exp=1&u=7&sig=abc`,
    ...extra,
});

afterEach(() => {
    vi.unstubAllEnvs();
});

describe("splitMath: token ảnh", () => {
    it("token riêng một dòng là đoạn ảnh khối, dòng xuống kề nó không thành dòng trống", () => {
        expect(splitMath("⟦img:6⟧\nLăng Chủ tịch")).toEqual([
            { kind: "image", value: "6", block: true },
            { kind: "text", value: "Lăng Chủ tịch" },
        ]);
        expect(splitMath("Cho hình sau:\n⟦img:2⟧\nTính x.")).toEqual([
            { kind: "text", value: "Cho hình sau:" },
            { kind: "image", value: "2", block: true },
            { kind: "text", value: "Tính x." },
        ]);
        expect(splitMath("Xem hình vẽ.\n⟦img:1⟧")).toEqual([
            { kind: "text", value: "Xem hình vẽ." },
            { kind: "image", value: "1", block: true },
        ]);
    });

    it("token giữa câu là ảnh cùng dòng (không khối)", () => {
        expect(splitMath("Hình ⟦img:3⟧ cho thấy")).toEqual([
            { kind: "text", value: "Hình " },
            { kind: "image", value: "3", block: false },
            { kind: "text", value: " cho thấy" },
        ]);
        // sau công thức trên cùng dòng cũng không phải khối
        expect(splitMath("$x$ ⟦img:3⟧")).toEqual([
            { kind: "inline", value: "x" },
            { kind: "text", value: " " },
            { kind: "image", value: "3", block: false },
        ]);
    });

    it("không đụng tới nội dung bên trong công thức", () => {
        expect(splitMath("$a⟦img:1⟧b$")).toEqual([{ kind: "inline", value: "a⟦img:1⟧b" }]);
    });

    it("tiền tố ⟦img: không hợp lệ thành đoạn ảnh hỏng, không để lại ký tự token", () => {
        for (const raw of ["⟦img:abc⟧ đề", "đề ⟦img:", "đề ⟦img:12", "⟦img:⟧"]) {
            const segments = splitMath(raw);
            expect(segments.some((s) => s.kind === "image" && s.value === "")).toBe(true);
            for (const s of segments.filter((x) => x.kind === "text")) {
                expect(s.value).not.toMatch(/⟦|⟧|img:/);
            }
        }
    });

    it("ngoặc vuông thường [img:1] là chữ bình thường", () => {
        expect(splitMath("[img:1] đề")).toEqual([{ kind: "text", value: "[img:1] đề" }]);
    });
});

describe("isBlankMath", () => {
    it("chữ trống, công thức rỗng, hay chỉ khoảng trắng đều là trống", () => {
        expect(isBlankMath("")).toBe(true);
        expect(isBlankMath(null)).toBe(true);
        expect(isBlankMath("  \n ")).toBe(true);
        expect(isBlankMath("$ $")).toBe(true);
        expect(isBlankMath("$$ $$ $ $")).toBe(true);
    });

    it("có chữ, công thức, hay ảnh thì không trống", () => {
        expect(isBlankMath("Tính $x$")).toBe(false);
        expect(isBlankMath("$x$")).toBe(false);
        expect(isBlankMath("⟦img:1⟧")).toBe(false);
        expect(isBlankMath("⟦img:")).toBe(false);
    });
});

describe("MathText: hiển thị ảnh", () => {
    it("token có trong images thành thẻ img với URL ghép gốc API, không còn chuỗi token", () => {
        vi.stubEnv("VITE_API_BASE_URL", "http://api.test/");
        const { container } = render(<MathText text={"⟦img:6⟧\nLăng Chủ tịch"} images={[image(6)]} />);

        const img = screen.getByRole("img");
        expect(img).toHaveAttribute("src", "http://api.test/api/question-images/6?exp=1&u=7&sig=abc");
        expect(container).toHaveTextContent("Lăng Chủ tịch");
        expect(container.textContent).not.toMatch(/⟦|⟧|img:/);
    });

    it("token riêng một dòng thành ảnh khối, token giữa câu thành ảnh cùng dòng", () => {
        const { container, rerender } = render(<MathText text={"⟦img:1⟧"} images={[image(1)]} />);
        expect(container.querySelector(".question-image--block")).not.toBeNull();

        rerender(<MathText text={"Hình ⟦img:1⟧ này"} images={[image(1)]} />);
        expect(container.querySelector(".question-image--block")).toBeNull();
        expect(container.querySelector(".question-image")).not.toBeNull();
    });

    it("token không có trong images hiện chữ thay thế, không hiện chuỗi token", () => {
        const { container } = render(<MathText text={"Cho hình ⟦img:9⟧ sau"} images={[image(1)]} />);
        expect(screen.queryByRole("img")).toBeNull();
        expect(container).toHaveTextContent("[Hình không tải được]");
        expect(container.textContent).not.toMatch(/⟦|⟧|img:/);
    });

    it("không truyền images (màn học sinh, bảng cũ) thì token cũng hiện chữ thay thế", () => {
        const { container } = render(<MathText text={"⟦img:6⟧\nĐề"} />);
        expect(container).toHaveTextContent("[Hình không tải được]");
        expect(container.textContent).not.toMatch(/⟦|⟧|img:/);
    });

    it("tiền tố ⟦img: hỏng hiện chữ thay thế", () => {
        const { container } = render(<MathText text={"Đề ⟦img:abc"} images={[image(1)]} />);
        expect(container).toHaveTextContent("[Hình không tải được]");
        expect(container.textContent).not.toMatch(/⟦|img:/);
    });

    it("ảnh tải lỗi (URL hết hạn) đổi sang chữ thay thế", () => {
        const { container } = render(<MathText text={"⟦img:6⟧"} images={[image(6)]} />);
        fireEvent.error(screen.getByRole("img"));
        expect(screen.queryByRole("img")).toBeNull();
        expect(container).toHaveTextContent("[Hình không tải được]");
    });

    it("chế độ compact: ảnh có trong images là nhãn [Hình], không tải ảnh; ảnh thiếu vẫn là chữ thay thế", () => {
        const { container } = render(
            <MathText text={"⟦img:6⟧\nLăng ⟦img:9⟧"} images={[image(6)]} compact />,
        );
        expect(screen.queryByRole("img")).toBeNull();
        expect(container).toHaveTextContent("[Hình]");
        expect(container).toHaveTextContent("[Hình không tải được]");
        expect(container.textContent).not.toMatch(/⟦|⟧|img:/);
    });

    it("công thức và chữ thường không đổi", () => {
        const { container } = render(<MathText text={"Tính $x^2$ nếu $x=2$"} />);
        expect(container.querySelectorAll(".katex")).toHaveLength(2);
        expect(container).toHaveTextContent("Tính");
    });
});
