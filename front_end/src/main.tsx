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
      // Chữ phụ đủ đậm để đọc rõ (≥ rgba(0,0,0,.65)); antd mặc định nhạt hơn
      theme={{
        token: {
          colorTextSecondary: "rgba(0, 0, 0, 0.65)",
          colorTextDescription: "rgba(0, 0, 0, 0.65)",
        },
      }}
    >
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </ConfigProvider>
  </StrictMode>
);