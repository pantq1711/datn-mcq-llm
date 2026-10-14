import { useState } from "react";
import { Card, Typography } from "antd";
import QuestionDetail from "../components/QuestionDetail";
import QuestionFilters, { type QuestionFilterValues, } from "../components/QuestionFilters";
import QuestionTable, { type QuestionTableItem, } from "../components/QuestionTable";
import "./QuestionBankPage.css";

const { Title } = Typography;

const subjectOptions = [
    {
        label: "Toán học",
        value: 1,
    },
    {
        label: "Vật lý",
        value: 2,
    },
    {
        label: "Hóa học",
        value: 3,
    },
    {
        label: "Sinh học",
        value: 4,
    },
];

const initialFilters: QuestionFilterValues = {
    keyword: "",
    subjectId: null,
    difficulty: null,
    status: null,
};

const mockQuestions: QuestionTableItem[] = [
    {
        id: 1,
        questionText:
            "Phản ứng hóa học nào sau đây là phản ứng oxi hóa - khử?",
        subject: "Hóa học",
        difficulty: "MEDIUM",
        status: "APPROVED",
        createdAt: "06/10/2026",
    },
    {
        id: 2,
        questionText:
            "Định luật II Newton được biểu diễn bằng công thức nào?",
        subject: "Vật lý",
        difficulty: "EASY",
        status: "APPROVED",
        createdAt: "05/10/2026",
    },
    {
        id: 3,
        questionText:
            "Hàm số nào sau đây đồng biến trên khoảng xác định của nó?",
        subject: "Toán học",
        difficulty: "HARD",
        status: "EDITED",
        createdAt: "04/10/2026",
    },
];

export default function QuestionBankPage() {
    const [filters, setFilters] =
        useState<QuestionFilterValues>(initialFilters);

    const [selectedQuestion, setSelectedQuestion] =
        useState<QuestionTableItem | null>(null);

    const handleFilter = () => {
        console.log("Apply filters:", filters);

        // TODO:
        // Gọi API lấy danh sách câu hỏi theo filters.
    };

    const handleReset = () => {
        setFilters(initialFilters);

        // TODO:
        // Gọi lại API để lấy toàn bộ câu hỏi.
    };

    const handleViewQuestion = (question: QuestionTableItem) => {
        setSelectedQuestion(question);
    };

    const handlePageChange = (page: number, pageSize: number) => {
        console.log("Page:", page, "Page size:", pageSize);

        // TODO:
        // Gọi API với page, pageSize và filters.
    };

    return (
        <div className="question-bank-page">
            <div className="question-bank-page-header">
                <Title level={2} className="question-bank-page-title">
                    Ngân hàng câu hỏi
                </Title>
            </div>

            <div className="question-bank-page-filters">
                <Card>
                    <QuestionFilters
                        value={filters}
                        subjects={subjectOptions}
                        onChange={setFilters}
                        onFilter={handleFilter}
                        onReset={handleReset}
                    />
                </Card>
            </div>

            <div className="question-bank-page-table">
                <Card>
                    <QuestionTable
                        data={mockQuestions}
                        total={mockQuestions.length}
                        page={1}
                        pageSize={10}
                        onPageChange={handlePageChange}
                        onView={handleViewQuestion}
                    />
                </Card>
            </div>

            <QuestionDetail
                open={selectedQuestion !== null}
                question={
                    selectedQuestion
                        ? {
                            ...selectedQuestion,
                            optionA: "Phương án A",
                            optionB: "Phương án B",
                            optionC: "Phương án C",
                            optionD: "Phương án D",
                            correctOption: "A",
                            subject: selectedQuestion.subject,
                            difficulty: selectedQuestion.difficulty,
                            status: selectedQuestion.status,
                            createdAt: selectedQuestion.createdAt,
                        }
                        : null
                }
                onClose={() => setSelectedQuestion(null)}
            />
        </div>
    );
}