import { useEffect, useState } from "react";
import { Button, Card, Col, Empty, Modal, Row, Tabs, Typography, message } from "antd";
import { ArrowLeftOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import ExamGenerationForm from "../components/ExamGenerationForm";
import ExamPreview from "../components/ExamPreview";
import ExamQuestionList from "../components/ExamQuestionList";

import "./GenerateExamPage.css";
import type { ExamSubjectOption, GeneratedExamCode, ExamQuestionItem, ExamGenerationValues } from "../types/exam.types";
import { getCatalogSubjects, generateExam } from "../services/examApi";

const { Title, Text } = Typography;

export default function GenerateExamPage() {
    const navigate = useNavigate();

    const [generatedExamCodes, setGeneratedExamCodes] = useState<GeneratedExamCode[]>([]);
    const [generating, setGenerating] = useState<boolean>(false);
    const [selectedExamId, setSelectedExamId] = useState<string | null>(null);
    const [activeDetailTab, setActiveDetailTab] = useState("preview");

    const selectedExam =
        generatedExamCodes.find((exam) => exam.id === selectedExamId) ?? null;

    const [subjectOptions, setSubjectOptions] = useState<ExamSubjectOption[]>([]);
    const [loadingSubjects, setLoadingSubjects] = useState<boolean>(false);

    useEffect(() => {
        const fetchSubjects = async () => {
            setLoadingSubjects(true);
            try {
                const subjects = await getCatalogSubjects();
                const options = subjects.map((s) => ({
                    label: s.name,
                    value: s.id,
                }));
                setSubjectOptions(options);
            } catch (error) {
                console.error("Lỗi khi tải danh sách môn học:", error);
                message.error("Không thể tải danh sách môn học");
            } finally {
                setLoadingSubjects(false);
            }
        };

        fetchSubjects();
    }, []);


    const handleGenerateExam = async (values: ExamGenerationValues) => {
        setGenerating(true);
        try {
            const result = await generateExam(values);
            setGeneratedExamCodes(result);
            message.success(`Đã tạo thành công ${result.length} mã đề thi từ ngân hàng câu hỏi!`);

            // Tự động mở xem trước mã đề đầu tiên nếu có
            if (result.length > 0) {
                setSelectedExamId(result[0].id);
            }
        } catch (error: any) {
            console.error("Lỗi khi sinh đề thi:", error);
            const errMsg = error?.response?.data?.message || "Không thể sinh đề thi. Vui lòng kiểm tra số lượng câu hỏi trong ngân hàng.";
            message.error(errMsg);
        } finally {
            setGenerating(false);
        }
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
                        loadingSubjects={loadingSubjects}
                        loading={generating}
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
