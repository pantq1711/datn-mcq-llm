import { Routes, Route } from "react-router-dom";

import AppLayout from "../layouts/AppLayout";
import DashboardPage from "../features/dashboard/pages/DashboardPage";
import AuthLayout from "../layouts/AuthLayout";
import LoginPage from "../features/auth/pages/LoginPage";
import QuestionBankPage from "../features/question-bank/pages/QuestionBankPage";
import GenerateExamPage from "../features/exam/pages/GenerateExamPage";
import ExamListPage from "../features/exam/pages/ExamListPage";
import ExamDetailPage from "../features/exam/pages/ExamDetailPage";
import ExamMatrixListPage from "../features/exam-matrix/pages/ExamMatrixListPage";
import TestKaTeX from "../features/test-katex/pages/TestKaTeX";
import ExamMatrixFormPage from "../features/exam-matrix/pages/ExamMatrixFormPage";
import MaterialListPage from "../features/materials/pages/MaterialListPage";
import { RequireAuth } from "../components/common/RequireAuth/RequireAuth";
import { GuestOnly } from "../components/common/RequireAuth/GuestOnly";
import ProfilePage from "../features/auth/pages/ProfilePage";
import UserManagementPage from "../features/admin/pages/UserManagementPage";
import NotFoundPage from "../components/common/NotFound/NotFoundPage";
import { ROLE_ADMIN, ROLE_TEACHER } from "../utils/auth.utils";

export default function AppRoutes() {
    return (
        <Routes>
            <Route path="auth" element={<AuthLayout />}>
                <Route element={<GuestOnly />}>
                    <Route path="login" element={<LoginPage />} />
                </Route>
            </Route>

            <Route element={<RequireAuth allowedRoles={[ROLE_TEACHER]} />}>
                <Route element={<AppLayout />}>
                    <Route path="" element={<DashboardPage />} />
                    <Route path="question-bank" element={<QuestionBankPage />} />
                    <Route path="exams" element={<ExamListPage />} />
                    <Route path="exams/generate" element={<GenerateExamPage />} />
                    <Route path="exams/:id" element={<ExamDetailPage />} />
                    <Route path="exam-matrices" element={<ExamMatrixListPage />} />
                    <Route path="exam-matrices/new" element={<ExamMatrixFormPage />} />
                    <Route path="exam-matrices/:id/edit" element={<ExamMatrixFormPage />} />
                    <Route path="materials" element={<MaterialListPage />} />
                    <Route path="test-katex" element={<TestKaTeX />} />
                </Route>
            </Route>

            <Route element={<RequireAuth allowedRoles={[ROLE_ADMIN]} />}>
                {/* Dùng chung layout để admin có Header (đăng xuất, hồ sơ) và Sidebar */}
                <Route element={<AppLayout />}>
                    <Route path="admin/users" element={<UserManagementPage />} />
                </Route>
            </Route>

            {/* Mọi vai trò đã đăng nhập đều xem được hồ sơ, trong layout chung */}
            <Route element={<RequireAuth />}>
                <Route element={<AppLayout />}>
                    <Route path="profile" element={<ProfilePage />} />
                </Route>
            </Route>

            <Route path="*" element={<NotFoundPage />} />
        </Routes>
    );
}