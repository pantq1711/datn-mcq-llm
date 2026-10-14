import {
    Button,
    Checkbox,
    Col,
    Form,
    Input,
    InputNumber,
    Row,
    Select,
    Space,
} from "antd";
import {
    ClearOutlined,
    PlayCircleOutlined,
} from "@ant-design/icons";

import "./ExamGenerationForm.css";

export type ExamDifficulty = "ALL" | "EASY" | "MEDIUM" | "HARD";

export interface ExamGenerationValues {
    title: string;
    subjectId: number;
    questionCount: number;
    duration: number;
    difficulty: ExamDifficulty;
    shuffleQuestions: boolean;
    shuffleOptions: boolean;
}

export interface ExamSubjectOption {
    label: string;
    value: number;
}

interface ExamGenerationFormProps {
    subjects: ExamSubjectOption[];
    loading?: boolean;
    initialValues?: Partial<ExamGenerationValues>;
    onGenerate?: (values: ExamGenerationValues) => void;
    onReset?: () => void;
}

const defaultValues: ExamGenerationValues = {
    title: "",
    subjectId: 0,
    questionCount: 20,
    duration: 30,
    difficulty: "ALL",
    shuffleQuestions: true,
    shuffleOptions: false,
};

export default function ExamGenerationForm({
    subjects,
    loading = false,
    initialValues,
    onGenerate,
    onReset,
}: ExamGenerationFormProps) {
    const [form] = Form.useForm<ExamGenerationValues>();

    const handleFinish = (values: ExamGenerationValues) => {
        onGenerate?.(values);
    };

    const handleReset = () => {
        form.resetFields();
        onReset?.();
    };

    return (
        <Form
            form={form}
            layout="vertical"
            initialValues={{
                ...defaultValues,
                ...initialValues,
            }}
            onFinish={handleFinish}
            className="exam-generation-form"
        >
            <Row gutter={[16, 0]}>
                <Col xs={24} lg={16}>
                    <Form.Item
                        label="Tên đề thi"
                        name="title"
                        rules={[
                            {
                                required: true,
                                message: "Vui lòng nhập tên đề thi.",
                            },
                            {
                                max: 200,
                                message:
                                    "Tên đề thi không được vượt quá 200 ký tự.",
                            },
                        ]}
                    >
                        <Input
                            placeholder="Nhập tên đề thi"
                            size="large"
                            maxLength={200}
                            showCount
                        />
                    </Form.Item>
                </Col>

                <Col xs={24} lg={8}>
                    <Form.Item
                        label="Môn học"
                        name="subjectId"
                        rules={[
                            {
                                required: true,
                                message: "Vui lòng chọn môn học.",
                            },
                            {
                                validator: (_, value) => {
                                    if (value && value > 0) {
                                        return Promise.resolve();
                                    }

                                    return Promise.reject(
                                        new Error(
                                            "Vui lòng chọn môn học.",
                                        ),
                                    );
                                },
                            },
                        ]}
                    >
                        <Select
                            size="large"
                            placeholder="Chọn môn học"
                            options={subjects}
                        />
                    </Form.Item>
                </Col>
            </Row>

            <Row gutter={[16, 0]}>
                <Col xs={24} sm={8}>
                    <Form.Item
                        label="Số câu hỏi"
                        name="questionCount"
                        rules={[
                            {
                                required: true,
                                message: "Vui lòng nhập số câu hỏi.",
                            },
                            {
                                type: "number",
                                min: 1,
                                max: 200,
                                message:
                                    "Số câu hỏi phải từ 1 đến 200.",
                            },
                        ]}
                    >
                        <InputNumber
                            size="large"
                            min={1}
                            max={200}
                            className="exam-generation-form-number"
                            placeholder="Số câu"
                        />
                    </Form.Item>
                </Col>

                <Col xs={24} sm={8}>
                    <Form.Item
                        label="Thời gian làm bài"
                        name="duration"
                        rules={[
                            {
                                required: true,
                                message:
                                    "Vui lòng nhập thời gian làm bài.",
                            },
                            {
                                type: "number",
                                min: 1,
                                max: 300,
                                message:
                                    "Thời gian phải từ 1 đến 300 phút.",
                            },
                        ]}
                    >
                        <InputNumber
                            size="large"
                            min={1}
                            max={300}
                            addonAfter="phút"
                            className="exam-generation-form-number"
                        />
                    </Form.Item>
                </Col>

                <Col xs={24} sm={8}>
                    <Form.Item
                        label="Mức độ"
                        name="difficulty"
                    >
                        <Select
                            size="large"
                            options={[
                                {
                                    label: "Tất cả mức độ",
                                    value: "ALL",
                                },
                                {
                                    label: "Dễ",
                                    value: "EASY",
                                },
                                {
                                    label: "Trung bình",
                                    value: "MEDIUM",
                                },
                                {
                                    label: "Khó",
                                    value: "HARD",
                                },
                            ]}
                        />
                    </Form.Item>
                </Col>
            </Row>

            <Form.Item
                label="Tùy chọn"
                className="exam-generation-form-options"
            >
                <Space direction="vertical" size={8}>
                    <Form.Item
                        name="shuffleQuestions"
                        valuePropName="checked"
                        noStyle
                    >
                        <Checkbox>
                            Trộn thứ tự câu hỏi
                        </Checkbox>
                    </Form.Item>

                    <Form.Item
                        name="shuffleOptions"
                        valuePropName="checked"
                        noStyle
                    >
                        <Checkbox>
                            Trộn thứ tự đáp án
                        </Checkbox>
                    </Form.Item>
                </Space>
            </Form.Item>

            <div className="exam-generation-form-actions">
                <Space>
                    <Button
                        icon={<ClearOutlined />}
                        onClick={handleReset}
                        disabled={loading}
                    >
                        Đặt lại
                    </Button>

                    <Button
                        type="primary"
                        htmlType="submit"
                        icon={<PlayCircleOutlined />}
                        loading={loading}
                    >
                        Tạo đề thi
                    </Button>
                </Space>
            </div>
        </Form>
    );
}