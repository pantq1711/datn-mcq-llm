
import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";

import { Button, Card, Col, Form, InputNumber, Row, Select, Space, Typography, message } from "antd";
import { HistoryOutlined, ThunderboltOutlined } from "@ant-design/icons";

import LessonContentInput from "../components/LessonContentInput";
import TextSegmentSelector, { type TextSegment } from "../components/TextSegmentSelector";
import TemplateSelector from "../components/TemplateSelector";

import StrategySelector, { type GenerationStrategy } from "../components/StrategySelector";
import GenerationProgress from "../components/GenerationProgress";
import GenerationResult from "../components/GenerationResult";

import { createGenerationTask, getGenerationStatus, getGenerationResult } from "../services/generationApi";
import type { GenerationResultItem, GenerationStatus } from "../types/generation.types";

import "./GenerateQuestionPage.css";

const { Title, Text } = Typography;

interface GenerationSettings {
    model: string;
    questionCount: number;
    temperature: number;
}

interface SelectedTextSegment {
    id: string;
    text: string;
    startOffset: number;
    endOffset: number;
}

const modelOptions = [
    { label: "GPT-4o-mini", value: "gpt-4o-mini" },
    { label: "Gemini Flash", value: "gemini-flash" },
];

export default function GenerateQuestionPage() {
    const navigate = useNavigate();

    const [form] = Form.useForm<GenerationSettings>();

    const [lessonContent, setLessonContent] = useState("");
    const [taskId, setTaskId] = useState<string | number | null>(null);
    const [generationStatus, setGenerationStatus] = useState<GenerationStatus>("IDLE");
    const [completedCount, setCompletedCount] = useState(0);
    const [currentQuestion, setCurrentQuestion] = useState(0);
    const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);
    const [generatedQuestions, setGeneratedQuestions] = useState<GenerationResultItem[]>([]);
    const [resultLoading, setResultLoading] = useState(false);
    const [selectedSegments, setSelectedSegments] = useState<
        SelectedTextSegment[]
    >([]);

    const [selectedTemplateId, setSelectedTemplateId] =
        useState<number | null>(null);

    const [strategy, setStrategy] =
        useState<GenerationStrategy | null>(null);

    const [questionCount, setQuestionCount] = useState(10);

    const [loading, setLoading] = useState(false);

    const handleGeneration = async (values: GenerationSettings) => {
        try {
            setGenerationStatus("RUNNING");
            setCompletedCount(0);
            setErrorMessage(undefined);

            const payload = {
                lessonContent,
                selectedSegments,
                selectedTemplateId,
                strategy,
                model: values.model,
                questionCount: values.questionCount,
                temperature: values.temperature,
            };

            const response = await createGenerationTask(payload);
            setTaskId(response.taskId);
            message.success("Đã khởi tạo tiến trình sinh câu hỏi!");

        } catch (error) {
            console.error("Lỗi tạo task:", error);
            setGenerationStatus("FAILED");
            setErrorMessage("Không thể kết nối đến máy chủ để tạo task.");
        }
    };

    // Polling kiểm tra tiến độ định kỳ khớp với kiểu dữ liệu trạng thái
    useEffect(() => {
        if (!taskId || generationStatus !== "RUNNING") return;

        const intervalId = setInterval(async () => {
            try {
                const data = await getGenerationStatus(taskId);

                setGenerationStatus(data.status);
                setCompletedCount(data.completedCount);
                if (data.currentQuestion !== undefined) {
                    setCurrentQuestion(data.currentQuestion);
                }
                if (data.errorMessage) {
                    setErrorMessage(data.errorMessage);
                }

                // Dừng polling khi hoàn tất hoặc thất bại
                if (data.status === "COMPLETED" || data.status === "FAILED") {
                    clearInterval(intervalId);

                    if (data.status === "COMPLETED") {
                        message.success("Hoàn thành sinh toàn bộ câu hỏi!");

                        // Gọi tiếp API lấy danh sách câu hỏi kết quả chi tiết
                        try {
                            // Nếu bạn có state loading cho kết quả thì bật lên ở đây, ví dụ: setResultLoading(true);
                            const resultData = await getGenerationResult(taskId);
                            setGeneratedQuestions(resultData.questions);
                        } catch (err) {
                            console.error("Lỗi lấy danh sách câu hỏi kết quả:", err);
                            message.error("Không thể tải danh sách câu hỏi kết quả.");
                        } finally {
                            // Tắt loading nếu có, ví dụ: setResultLoading(false);
                        }

                    } else {
                        message.error("Quá trình sinh câu hỏi gặp sự cố.");
                    }
                }
            } catch (error) {
                console.error("Lỗi lấy tiến độ:", error);
            }
        }, 2500);

        return () => clearInterval(intervalId);
    }, [taskId, generationStatus]);

    return (
        <div className="generate-question-page">
            {/* Page Header */}
            <div className="generate-question-page-header">
                <div className="generate-question-page-header-content">
                    <div className="generate-question-page-header-info">
                        <Title
                            level={2}
                            className="generate-question-page-title"
                        >
                            Sinh câu hỏi
                        </Title>
                    </div>

                    <Button
                        icon={<HistoryOutlined />}
                        onClick={() => navigate("/generation-history")}
                    >
                        Lịch sử sinh câu hỏi
                    </Button>
                </div>
            </div>

            {/* Lesson Content */}
            <div className="generate-question-page-content">
                <Card
                    title="1. Nội dung bài học"
                    className="generate-question-page-card"
                >
                    <LessonContentInput
                        value={lessonContent}
                        onChange={(content) => {
                            setLessonContent(content);
                            setSelectedSegments([]);
                        }}
                    />
                </Card>
            </div>

            {/* Select Text Segments */}
            <div className="generate-question-page-content">
                <Card
                    title="2. Chọn nội dung cần kiểm tra"
                    className="generate-question-page-card"
                >
                    <Text
                        type="secondary"
                        className="generate-question-page-description"
                    >
                        Chọn một hoặc nhiều đoạn trong nội dung bài học
                        làm phạm vi sinh câu hỏi.
                    </Text>

                    <TextSegmentSelector
                        content={lessonContent}
                        value={selectedSegments}
                        onChange={setSelectedSegments}
                        disabled={!lessonContent.trim()}
                    />
                </Card>
            </div>

            {/* Optional Question Template */}
            <div className="generate-question-page-content">
                <Card
                    title="Tham khảo câu hỏi mẫu (không bắt buộc)"
                    className="generate-question-page-card"
                >
                    <Text
                        type="secondary"
                        className="generate-question-page-description"
                    >
                        Có thể chọn câu hỏi mẫu bổ sung nếu nghiệp vụ
                        cần tham khảo cách đặt câu hỏi. Phạm vi nội dung
                        cần kiểm tra vẫn được xác định ở bước trên.
                    </Text>

                    <TemplateSelector
                        value={selectedTemplateId}
                        onChange={setSelectedTemplateId}
                    />
                </Card>
            </div>

            {/* Generation Configuration */}
            <div className="generate-question-page-configuration">
                <Card
                    title="3. Cấu hình sinh câu hỏi"
                    className="generate-question-page-card"
                >
                    <div className="generate-question-page-strategy">
                        <StrategySelector
                            value={strategy}
                            onChange={setStrategy}
                        />
                    </div>

                    <Form
                        form={form}
                        layout="vertical"
                        initialValues={{
                            model: "gpt-4o-mini",
                            questionCount: 10,
                            temperature: 0.7,
                        }}
                        onValuesChange={(changedValues) => {
                            if (
                                typeof changedValues.questionCount ===
                                "number"
                            ) {
                                setQuestionCount(
                                    changedValues.questionCount,
                                );
                            }
                        }}
                        onFinish={handleGeneration}
                    >
                        <Row gutter={[16, 0]}>
                            <Col xs={24} md={12} xl={8}>
                                <Form.Item
                                    label="Mô hình ngôn ngữ"
                                    name="model"
                                    rules={[
                                        {
                                            required: true,
                                            message: "Vui lòng chọn mô hình.",
                                        },
                                    ]}
                                >
                                    <Select options={modelOptions} />
                                </Form.Item>
                            </Col>

                            <Col xs={24} md={12} xl={8}>
                                <Form.Item
                                    label="Số câu hỏi cần sinh"
                                    name="questionCount"
                                    rules={[
                                        {
                                            required: true,
                                            message: "Vui lòng nhập số câu hỏi.",
                                        },
                                    ]}
                                >
                                    <InputNumber
                                        min={1}
                                        max={200}
                                        precision={0}
                                        className="generate-question-page-number"
                                    />
                                </Form.Item>
                            </Col>

                            <Col xs={24} md={12} xl={8}>
                                <Form.Item
                                    label="Temperature"
                                    name="temperature"
                                    rules={[
                                        {
                                            required: true,
                                            message: "Vui lòng nhập temperature.",
                                        },
                                    ]}
                                >
                                    <InputNumber
                                        min={0}
                                        max={1}
                                        step={0.1}
                                        className="generate-question-page-number"
                                    />
                                </Form.Item>
                            </Col>
                        </Row>

                        <div className="generate-question-page-form-actions">
                            <Button
                                type="primary"
                                htmlType="submit"
                                loading={loading}
                                icon={<ThunderboltOutlined />}
                                disabled={
                                    !lessonContent.trim() ||
                                    selectedSegments.length === 0 ||
                                    strategy === null
                                }
                            >
                                Sinh câu hỏi
                            </Button>
                        </div>
                    </Form>
                </Card>
            </div>

            {/* Generation Progress */}
            <div className="generate-question-page-content">
                <Card
                    title="4. Tiến trình sinh câu hỏi"
                    className="generate-question-page-card"
                >
                    <GenerationProgress
                        status={generationStatus}
                        completedCount={completedCount}
                        totalCount={questionCount}
                        currentQuestion={
                            generationStatus === "RUNNING"
                                ? completedCount + 1
                                : 0
                        }
                    />
                </Card>
            </div>

            {/* Generation Result */}
            <div className="generate-question-page-result">
                <Card
                    title={
                        <Space>
                            <span>5. Kết quả sinh câu hỏi</span>
                        </Space>
                    }
                    className="generate-question-page-card"
                >
                    <GenerationResult
                        data={generatedQuestions}
                        loading={resultLoading}
                    />
                </Card>
            </div>
        </div>
    );
}
