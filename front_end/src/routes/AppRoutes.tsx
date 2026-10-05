import { Routes, Route } from "react-router-dom";

import TeacherLayout from "../layouts/TeacherLayout";
import ReviewQuestionDetailPage from "../features/teacher-review/pages/ReviewQuestionDetailPage";
import ReviewQuestionsPage from "../features/teacher-review/pages/ReviewQuestionsPage";

export default function AppRoutes() {
    return (
        <Routes>
            <Route path="" element={<TeacherLayout />}>
                <Route path="review" element={<ReviewQuestionsPage />} />
                <Route path="review/:id" element={<ReviewQuestionDetailPage />} />
            </Route>
        </Routes>
    );
}