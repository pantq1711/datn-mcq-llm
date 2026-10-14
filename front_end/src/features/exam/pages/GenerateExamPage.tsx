import { Button, Card, Col, Row, Typography } from "antd";
import { ArrowLeftOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import ExamGenerationForm, { type ExamGenerationValues } from "../components/ExamGenerationForm";
import ExamPreview, { type ExamPreviewData } from "../components/ExamPreview";
import ExamQuestionList, { type ExamQuestionItem } from "../components/ExamQuestionList";

import "./GenerateExamPage.css";

const { Title } = Typography;

export default function GenerateExamPage() {
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

    const examPreview: ExamPreviewData = {
        title: "Đề thi giữa kỳ Hóa học",
        subject: "Hóa học",
        questionCount: 20,
        duration: 30,
        difficulty: "MEDIUM",
        instructions:
            "Thí sinh chọn một đáp án đúng cho mỗi câu hỏi. " +
            "Thời gian làm bài là 30 phút.",
    };

    const questions: ExamQuestionItem[] = [
        {
            id: 1,
            questionText:
                "Phản ứng nào sau đây là phản ứng oxi hóa - khử?",
            optionA: "Zn + 2HCl → ZnCl₂ + H₂",
            optionB: "NaOH + HCl → NaCl + H₂O",
            optionC: "AgNO₃ + NaCl → AgCl + NaNO₃",
            optionD: "CaCO₃ → CaO + CO₂",
            correctOption: "A",
            difficulty: "MEDIUM",
        },
        {
            id: 2,
            questionText:
                "Chất nào sau đây có tính oxi hóa mạnh nhất?",
            optionA: "H₂",
            optionB: "O₂",
            optionC: "Na",
            optionD: "Cl₂",
            correctOption: "D",
            difficulty: "EASY",
        },
    ];

    const navigate = useNavigate();

    const handleGenerateExam = (values: ExamGenerationValues) => {
        console.log("Generate exam:", values);

        // TODO:
        // gọi API tạo đề
    };

    const handleRemoveQuestion = (
        question: ExamQuestionItem,
        index: number,
    ) => {
        console.log("Remove question:", question);
        console.log("Index:", index);

        // TODO:
        // cập nhật danh sách câu hỏi
    };

    return (
        <div className="generate-exam-page">
            <div className="generate-exam-page-header">
                <div className="generate-exam-page-header-top">
                    <Button
                        type="text"
                        icon={<ArrowLeftOutlined />}
                        className="generate-exam-back-button"
                        onClick={() => navigate("/exams")}
                    >
                        Quay lại
                    </Button>
                </div>

                <Title level={2} className="generate-exam-page-title">
                    Tạo đề thi
                </Title>
            </div>

            <div className="generate-exam-page-form">
                <Card title="Thiết lập đề thi">
                    <ExamGenerationForm
                        subjects={subjectOptions}
                        onGenerate={handleGenerateExam}
                    />
                </Card>
            </div>

            <Row
                gutter={[16, 16]}
                className="generate-exam-page-content"
            >
                <Col xs={24} xl={10}>
                    <Card title="Xem trước đề thi">
                        <ExamPreview exam={examPreview} />
                    </Card>
                </Col>

                <Col xs={24} xl={14}>
                    <Card title="Danh sách câu hỏi">
                        <ExamQuestionList
                            questions={questions}
                            onRemove={handleRemoveQuestion}
                        />
                    </Card>
                </Col>
            </Row>
        </div>
    );
}