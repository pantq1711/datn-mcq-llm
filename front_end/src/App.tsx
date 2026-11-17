import { lazy, Suspense } from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Spin } from "antd";
import AppRoutes from "./routes/AppRoutes";
import ScrollToTop from "./components/common/ScrollToTop/ScrollToTop";
import { AuthProvider } from "./context/AuthContext";
import { STUDENT_ROUTE } from "./features/assignment/studentPath";

// Trang học sinh làm bài: công khai, nạp lười, nằm ngoài AuthProvider (học sinh không đăng nhập nên không cần
// thử refresh phiên giáo viên mỗi lần mở link).
const StudentAssignmentPage = lazy(() => import("./features/assignment/student/StudentAssignmentPage"));

function App() {
  return (
    <BrowserRouter>
      <ScrollToTop />
      <Routes>
        <Route
          path={STUDENT_ROUTE}
          element={
            <Suspense fallback={<div style={{ display: "flex", justifyContent: "center", padding: "96px 0" }}><Spin size="large" /></div>}>
              <StudentAssignmentPage />
            </Suspense>
          }
        />
        <Route
          path="*"
          element={
            <AuthProvider>
              <AppRoutes />
            </AuthProvider>
          }
        />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
