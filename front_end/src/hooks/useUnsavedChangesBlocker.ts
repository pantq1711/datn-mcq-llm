import { useCallback } from "react";
import { useBlocker, type BlockerFunction } from "react-router-dom";

export interface UnsavedChangesBlocker {
    /** Đang chặn một lần chuyển trang: trang hiện hộp hỏi lại. */
    blocked: boolean;
    /** Ở lại trang, hủy lần chuyển trang đang chặn. */
    stay: () => void;
    /** Đi tiếp tới đúng nơi người dùng định tới (menu, nút Back, liên kết…). */
    leave: () => void;
}

/**
 * Chặn chuyển trang trong ứng dụng khi còn thay đổi chưa lưu: bấm menu, logo, nút Back/Forward, navigate(...).
 * Chỉ chặn khi đường dẫn thật sự đổi (đổi tham số trên địa chỉ trong cùng trang thì không hỏi). Đóng tab, tải lại trang
 * không đi qua đây: trang tự gắn beforeunload. Cần data router (createBrowserRouter / createMemoryRouter).
 * `hasUnsavedChanges` được gọi đúng lúc chuyển trang, nên có thể đọc ref.
 */
export function useUnsavedChangesBlocker(hasUnsavedChanges: () => boolean): UnsavedChangesBlocker {
    const shouldBlock = useCallback<BlockerFunction>(
        ({ currentLocation, nextLocation }) =>
            currentLocation.pathname !== nextLocation.pathname && hasUnsavedChanges(),
        [hasUnsavedChanges]
    );
    const blocker = useBlocker(shouldBlock);

    return {
        blocked: blocker.state === "blocked",
        stay: () => blocker.reset?.(),
        leave: () => blocker.proceed?.(),
    };
}
