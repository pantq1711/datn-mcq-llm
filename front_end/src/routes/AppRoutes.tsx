import { Routes, Route } from "react-router-dom";

import TeacherLayout from "../layouts/TeacherLayout";
import ReviewQuestionDetailPage from "../features/teacher-review/pages/ReviewQuestionDetailPage";
import ReviewQuestionsPage from "../features/teacher-review/pages/ReviewQuestionsPage";
import DashboardPage from "../features/dashboard/pages/DashboardPage";
import AuthLayout from "../layouts/AuthLayout";
import LoginPage from "../features/auth/pages/LoginPage";
import RegisterPage from "../features/auth/pages/RegisterPage";
import ForgotPasswordPage from "../features/auth/pages/ForgotPasswordPage";
import QuestionBankPage from "../features/question-bank/pages/QuestionBankPage";
import GenerateExamPage from "../features/exam/pages/GenerateExamPage";
import ExamListPage from "../features/exam/pages/ExamListPage";
import ExamDetailPage from "../features/exam/pages/ExamDetailPage";
import ExperimentListPage from "../features/experiment/pages/ExperimentListPage";
import ExperimentDetailPage from "../features/experiment/pages/ExperimentDetailPage";
import ExperimentComparisonPage from "../features/experiment/pages/ExperimentComparisonPage";
import GenerateQuestionPage from "../features/question-generation/pages/GenerateQuestionPage";
import GenerationHistoryPage from "../features/question-generation/pages/GenerationHistoryPage";

export default function AppRoutes() {
    return (
        <Routes>
            <Route path="auth" element={<AuthLayout />}>
                <Route path="login" element={<LoginPage />} />
                <Route path="register" element={<RegisterPage />} />
                <Route path="forgot-password" element={<ForgotPasswordPage />} />
            </Route>

            <Route path="" element={<TeacherLayout />}>
                <Route path="" element={<DashboardPage />} />
                <Route path="review" element={<ReviewQuestionsPage />} />
                <Route path="review/:id" element={<ReviewQuestionDetailPage />} />
                <Route path="question-bank" element={<QuestionBankPage />} />
                <Route path="exams" element={<ExamListPage />} />
                <Route path="exams/generate" element={<GenerateExamPage />} />
                <Route path="exams/:id" element={<ExamDetailPage />} />
                <Route path="experiments" element={<ExperimentListPage />} />
                <Route path="experiments/:id" element={<ExperimentDetailPage />} />
                <Route path="experiments/comparison" element={<ExperimentComparisonPage />} />
                <Route path="generate" element={<GenerateQuestionPage />} />
                <Route path="generation-history" element={<GenerationHistoryPage />} />
            </Route>
        </Routes>
    );
}