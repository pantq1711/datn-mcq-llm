
import { useState } from "react";

import {
    Button,
    Card,
    Col,
    Form,
    InputNumber,
    Row,
    Select,
    Space,
    Typography,
} from "antd";

import {
    HistoryOutlined,
    ThunderboltOutlined,
} from "@ant-design/icons";

import { useNavigate } from "react-router-dom";

import LessonContentInput from "../components/LessonContentInput";
import TextSegmentSelector, {
    type TextSegment,
} from "../components/TextSegmentSelector";
import TemplateSelector from "../components/TemplateSelector";

import StrategySelector, {
    type GenerationStrategy,
} from "../components/StrategySelector";

import GenerationProgress, {
    type GenerationStatus,
} from "../components/GenerationProgress";

import GenerationResult, {
    type GenerationResultItem,
} from "../components/GenerationResult";

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
    const [selectedSegments, setSelectedSegments] = useState<
        SelectedTextSegment[]
    >([]);

    const [selectedTemplateId, setSelectedTemplateId] =
        useState<number | null>(null);

    const [strategy, setStrategy] =
        useState<GenerationStrategy | null>(null);

    const [questionCount, setQuestionCount] = useState(10);

    // Trạng thái minh họa cho bố cục.
    // Khi tích hợp backend, lấy các giá trị này từ tiến trình sinh thực tế.
    const generationStatus: GenerationStatus = "RUNNING";
    const completedCount = 0;

    const generatedQuestions: GenerationResultItem[] = [];

    const handleGeneration = (values: GenerationSettings) => {
        console.log({
            ...values,
            lessonContent,
            selectedSegments,
            strategy,
            selectedTemplateId,
        });

        // TODO: Gọi API sinh câu hỏi và cập nhật tiến trình/kết quả.
    };

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
                    <GenerationResult data={generatedQuestions} />
                </Card>
            </div>
        </div>
    );
}
