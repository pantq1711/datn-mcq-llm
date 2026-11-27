import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import {
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { ConfigProvider } from "antd";
import viVN from "antd/locale/vi_VN";

import "katex/dist/katex.min.css";
import "./index.css"

import App from "./App";

// Chữ có sẵn của antd (phân trang, "Không có dữ liệu", lịch…) hiện tiếng Việt
const queryClient = new QueryClient();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ConfigProvider
      locale={viVN}
      // Bộ chữ và màu "dễ đọc" cho giáo viên lớn tuổi (.local/ux/02-ra-soat-giao-dien.md mục 7.4):
      // chữ nền 16px (chữ nhỏ antd tự suy ra 14px), nút và ô nhập cao 40px, màu chữ trạng thái đạt 4,5:1 trên nền trắng.
      theme={{
        token: {
          fontSize: 16,
          controlHeight: 40,
          // Chữ phụ đủ đậm để đọc rõ (≥ rgba(0,0,0,.65)); antd mặc định nhạt hơn
          colorTextSecondary: "rgba(0, 0, 0, 0.65)",
          colorTextDescription: "rgba(0, 0, 0, 0.65)",
          colorLink: "#0958d9",
          colorError: "#cf1322",
          colorErrorText: "#cf1322",
          colorSuccessText: "#237804",
          colorWarningText: "#ad4e00",
        },
        components: {
          // Mục đang chọn ở thanh bên: chữ xanh đậm trên nền xanh nhạt
          Menu: { itemSelectedColor: "#0958d9" },
          // Thẻ cảnh báo (vd. "Có công thức MathType không đọc được"): chữ vàng #faad14 trên nền vàng nhạt
          // chỉ ~1,9:1; đổi sang nâu đậm ~5,2:1, nền và viền vẫn vàng nhạt như cũ
          Tag: { colorWarning: "#ad4e00" },
          // Hàng tiêu đề bảng xanh nhạt #e3effa; cột đang sắp xếp hoặc di chuột vào đậm hơn một chút
          Table: { headerBg: "#e3effa", headerSortActiveBg: "#d4e5f7", headerSortHoverBg: "#d4e5f7" },
        },
      }}
    >
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </ConfigProvider>
  </StrictMode>
);