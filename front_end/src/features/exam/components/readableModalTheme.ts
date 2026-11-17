import type { ThemeConfig } from "antd";

/**
 * Cỡ chữ và màu "dễ đọc" chỉ cho hộp Sửa câu và Đổi câu (rà soát giao diện A1, B3, mục 7.4). Bọc riêng từng hộp bằng
 * ConfigProvider, không đổi giao diện chung: chữ 16px, ô nhập và nút cao 40px, chữ lỗi đỏ đậm #cf1322 (khoảng 5,9:1).
 */
export const READABLE_MODAL_THEME: ThemeConfig = {
    token: {
        fontSize: 16,
        controlHeight: 40,
        colorError: "#cf1322",
    },
};
