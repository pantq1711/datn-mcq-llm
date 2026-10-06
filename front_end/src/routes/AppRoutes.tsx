import { Routes, Route } from "react-router-dom";

import TeacherLayout from "../layouts/TeacherLayout";
import ReviewQuestionDetailPage from "../features/teacher-review/pages/ReviewQuestionDetailPage";
import ReviewQuestionsPage from "../features/teacher-review/pages/ReviewQuestionsPage";
import DashboardPage from "../features/dashboard/pages/DashboardPage";
import AuthLayout from "../layouts/AuthLayout";
import LoginPage from "../features/auth/pages/LoginPage";
import RegisterPage from "../features/auth/pages/RegisterPage";
import ForgotPassword from "../features/auth/pages/ForgotPassword";

export default function AppRoutes() {
    return (
        <Routes>
            <Route path="/auth" element={<AuthLayout />}>
                <Route path="login" element={<LoginPage />} />
                <Route path="register" element={<RegisterPage />} />
                <Route path="forgot-password" element={<ForgotPassword />} />
            </Route>

            <Route path="" element={<TeacherLayout />}>
                <Route path="" element={<DashboardPage />} />
                <Route path="review" element={<ReviewQuestionsPage />} />
                <Route path="review/:id" element={<ReviewQuestionDetailPage />} />
            </Route>
        </Routes>
    );
}