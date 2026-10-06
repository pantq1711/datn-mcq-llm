import { Routes, Route } from "react-router-dom";

import TeacherLayout from "../layouts/TeacherLayout";
import ReviewQuestionDetailPage from "../features/teacher-review/pages/ReviewQuestionDetailPage";
import ReviewQuestionsPage from "../features/teacher-review/pages/ReviewQuestionsPage";
import DashboardPage from "../features/dashboard/pages/DashboardPage";

export default function AppRoutes() {
    return (
        <Routes>
            <Route path="" element={<TeacherLayout />}>
                <Route path="" element={<DashboardPage />} />
                <Route path="review" element={<ReviewQuestionsPage />} />
                <Route path="review/:id" element={<ReviewQuestionDetailPage />} />
            </Route>
        </Routes>
    );
}