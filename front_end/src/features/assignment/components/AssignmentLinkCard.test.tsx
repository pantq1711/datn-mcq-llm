import { render, screen } from "@testing-library/react";
import { matchPath } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { buildStudentLink } from "../services/assignmentApi";
import { STUDENT_ROUTE, studentPath } from "../studentPath";
import AssignmentLinkCard from "./AssignmentLinkCard";

// jsdom không vẽ canvas: thay QRCode của antd bằng thẻ ghi lại giá trị mã hóa để kiểm link trong mã QR.
vi.mock("antd", async (importOriginal) => {
    const actual = await importOriginal<typeof import("antd")>();
    return {
        ...actual,
        QRCode: ({ value }: { value: string }) => <div data-testid="qr" data-value={value} />,
    };
});

describe("đường dẫn học sinh /lam-bai/:code", () => {
    it("link ghép từ origin + /lam-bai/<mã>", () => {
        expect(buildStudentLink("K7M2QX9RTB", "https://soande.app")).toBe("https://soande.app/lam-bai/K7M2QX9RTB");
    });

    it("đường dẫn của link khớp route trang học sinh và lấy lại đúng mã", () => {
        const url = new URL(buildStudentLink("K7M2QX9RTB", "https://soande.app"));
        expect(matchPath(STUDENT_ROUTE, url.pathname)?.params.code).toBe("K7M2QX9RTB");
        expect(matchPath(STUDENT_ROUTE, "/a/K7M2QX9RTB")).toBeNull();
        expect(studentPath("K7M2QX9RTB")).toBe("/lam-bai/K7M2QX9RTB");
    });

    it("hộp giao bài: ô link và mã QR cùng mang link /lam-bai/<mã>", () => {
        render(<AssignmentLinkCard publicCode="K7M2QX9RTB" fileName="Đề giữa kì I" />);
        const expected = `${window.location.origin}/lam-bai/K7M2QX9RTB`;
        expect(screen.getByLabelText("Link cho học sinh")).toHaveValue(expected);
        expect(screen.getByTestId("qr")).toHaveAttribute("data-value", expected);
    });
});
