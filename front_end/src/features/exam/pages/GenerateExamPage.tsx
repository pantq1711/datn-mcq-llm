import { useState } from "react";
import {
    Button,
    Card,
    Col,
    Empty,
    Modal,
    Row,
    Tabs,
    Typography,
} from "antd";
import { ArrowLeftOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import ExamGenerationForm, {
    type ExamGenerationValues,
} from "../components/ExamGenerationForm";
import ExamPreview, {
    type ExamPreviewData,
} from "../components/ExamPreview";
import ExamQuestionList, {
    type ExamQuestionItem,
} from "../components/ExamQuestionList";

import "./GenerateExamPage.css";

const { Title, Text } = Typography;

interface GeneratedExamCode {
    id: string;
    code: string;
    title: string;
    preview: ExamPreviewData;
    questions: ExamQuestionItem[];
}

/*
 * Dữ liệu mẫu để thể hiện bố cục trang.
 * Khi tích hợp API, thay initialGeneratedExamCodes bằng dữ liệu trả về từ backend.
 */
const initialPreview: ExamPreviewData = {
    title: "Đề thi giữa kỳ Hóa học",
    subject: "Hóa học",
    questionCount: 20,
    duration: 30,
    easyCount: 5,
    mediumCount: 10,
    hardCount: 5,
    instructions:
        "Thí sinh chọn một đáp án đúng cho mỗi câu hỏi. " +
        "Thời gian làm bài là 30 phút.",
};

const initialQuestions: ExamQuestionItem[] = [
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

const initialGeneratedExamCodes: GeneratedExamCode[] = ["101", "102", "103", "104"].map(
    (code) => ({
        id: `exam-${code}`,
        code,
        title: initialPreview.title,
        preview: { ...initialPreview },
        questions: initialQuestions.map((question) => ({ ...question })),
    }),
);

export default function GenerateExamPage() {
    const navigate = useNavigate();

    const [generatedExamCodes, setGeneratedExamCodes] = useState<
        GeneratedExamCode[]
    >(initialGeneratedExamCodes);
    const [selectedExamId, setSelectedExamId] = useState<string | null>(null);
    const [activeDetailTab, setActiveDetailTab] = useState("preview");

    const selectedExam =
        generatedExamCodes.find((exam) => exam.id === selectedExamId) ?? null;

    const subjectOptions = [
        { label: "Toán học", value: 1 },
        { label: "Vật lý", value: 2 },
        { label: "Hóa học", value: 3 },
        { label: "Sinh học", value: 4 },
    ];

    const handleGenerateExam = (values: ExamGenerationValues) => {
        console.log("Generate exam:", values);

        // TODO: gọi API tạo nhiều mã đề.
        // Khi API hoàn tất, cập nhật generatedExamCodes bằng danh sách kết quả.
    };

    const handleOpenExam = (examId: string) => {
        setSelectedExamId(examId);
        setActiveDetailTab("preview");
    };

    const handleCloseExam = () => {
        setSelectedExamId(null);
    };

    const handleRemoveQuestion = (
        question: ExamQuestionItem,
        index: number,
    ) => {
        if (!selectedExam) return;

        setGeneratedExamCodes((previous) =>
            previous.map((exam) => {
                if (exam.id !== selectedExam.id) return exam;

                return {
                    ...exam,
                    questions: exam.questions.filter(
                        (item, itemIndex) =>
                            itemIndex !== index && item.id !== question.id,
                    ),
                    preview: {
                        ...exam.preview,
                        questionCount: Math.max(
                            0,
                            exam.preview.questionCount - 1,
                        ),
                    },
                };
            }),
        );
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

            <section
                className="generate-exam-page-list-section"
                aria-labelledby="generated-exams-title"
            >
                <div className="generate-exam-page-list-header">
                    <div>
                        <Title
                            level={4}
                            id="generated-exams-title"
                            className="generate-exam-page-list-title"
                        >
                            Danh sách mã đề
                        </Title>
                    </div>
                    <Text type="secondary" className="generate-exam-page-count">
                        {generatedExamCodes.length} mã đề
                    </Text>
                </div>

                {generatedExamCodes.length === 0 ? (
                    <Card>
                        <Empty
                            description="Chưa có mã đề nào. Hãy thiết lập thông tin và tạo đề thi."
                        />
                    </Card>
                ) : (
                    <Row gutter={[16, 16]} className="generate-exam-page-code-grid">
                        {generatedExamCodes.map((exam) => (
                            <Col xs={24} md={12} key={exam.id}>
                                <div
                                    className="generate-exam-code-card-trigger"
                                    role="button"
                                    tabIndex={0}
                                    aria-label={`Xem ${exam.title}, mã đề ${exam.code}`}
                                    onClick={() => handleOpenExam(exam.id)}
                                    onKeyDown={(event) => {
                                        if (
                                            event.key === "Enter" ||
                                            event.key === " "
                                        ) {
                                            event.preventDefault();
                                            handleOpenExam(exam.id);
                                        }
                                    }}
                                >
                                    <Card
                                        hoverable
                                        className="generate-exam-code-card"
                                    >
                                        <Text
                                            className="generate-exam-code-title"
                                            strong
                                        >
                                            {exam.title}
                                        </Text>
                                        <Title
                                            level={3}
                                            className="generate-exam-code-value"
                                        >
                                            Mã đề {exam.code}
                                        </Title>
                                    </Card>
                                </div>
                            </Col>
                        ))}
                    </Row>
                )}
            </section>

            <Modal
                open={Boolean(selectedExam)}
                onCancel={handleCloseExam}
                title={
                    selectedExam
                        ? `${selectedExam.title} — Mã đề ${selectedExam.code}`
                        : "Chi tiết mã đề"
                }
                footer={null}
                width={1100}
                centered
                destroyOnClose
                className="generate-exam-detail-modal"
            >
                {selectedExam && (
                    <Tabs
                        activeKey={activeDetailTab}
                        onChange={setActiveDetailTab}
                        items={[
                            {
                                key: "preview",
                                label: "Xem trước đề thi",
                                children: (
                                    <div className="generate-exam-detail-tab">
                                        <ExamPreview
                                            exam={selectedExam.preview}
                                        />
                                    </div>
                                ),
                            },
                            {
                                key: "questions",
                                label: "Danh sách câu hỏi",
                                children: (
                                    <div className="generate-exam-detail-tab">
                                        <ExamQuestionList
                                            questions={selectedExam.questions}
                                            onRemove={handleRemoveQuestion}
                                        />
                                    </div>
                                ),
                            },
                        ]}
                    />
                )}
            </Modal>
        </div>
    );
}
