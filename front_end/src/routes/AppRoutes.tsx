import { Routes, Route } from "react-router-dom";

import ReviewQuestionDetailPage from "../features/teacher-review/pages/ReviewQuestionDetailPage";

export default function AppRoutes() {
    return (
        <Routes>
            <Route path="/" element={<ReviewQuestionDetailPage />} />
        </Routes>
    );
}