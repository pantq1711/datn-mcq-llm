import {
    Alert,
    Button,
    Col,
    Form,
    InputNumber,
    Row,
    Select,
    Typography,
} from "antd";
import { ThunderboltOutlined } from "@ant-design/icons";

import "./GenerationForm.css";

const { Text } = Typography;

export interface GenerationFormValues {
    model: string;
    questionCount: number;
    temperature: number;
}

interface GenerationFormProps {
    loading?: boolean;
    disabled?: boolean;
    initialValues?: Partial<GenerationFormValues>;
    onSubmit?: (values: GenerationFormValues) => void;
}

const modelOptions = [
    {
        value: "gpt-4o-mini",
        label: "GPT-4o-mini",
    },
    {
        value: "gemini-flash",
        label: "Gemini Flash",
    },
];

const defaultValues: GenerationFormValues = {
    model: "gpt-4o-mini",
    questionCount: 10,
    temperature: 0.7,
};

export default function GenerationForm({
    loading = false,
    disabled = false,
    initialValues,
    onSubmit,
}: GenerationFormProps) {
    const [form] = Form.useForm<GenerationFormValues>();

    const mergedInitialValues = {
        ...defaultValues,
        ...initialValues,
    };

    const handleFinish = (values: GenerationFormValues) => {
        onSubmit?.(values);
    };

    return (
        <div className="generation-form">
            <div className="generation-form-header">
                <Text strong>Cấu hình sinh</Text>
            </div>

            <Alert
                type="info"
                showIcon
                message="Mỗi lần gọi LLM sinh một câu hỏi"
                description="Số lượng câu hỏi yêu cầu sẽ tương ứng với số lần sinh được thực hiện."
                className="generation-form-info"
            />

            <Form
                form={form}
                layout="vertical"
                initialValues={mergedInitialValues}
                onFinish={handleFinish}
                disabled={disabled || loading}
            >
                <Row gutter={[16, 0]}>
                    <Col xs={24} md={12}>
                        <Form.Item
                            label="Model"
                            name="model"
                            rules={[
                                {
                                    required: true,
                                    message: "Vui lòng chọn model.",
                                },
                            ]}
                        >
                            <Select
                                placeholder="Chọn model"
                                options={modelOptions}
                            />
                        </Form.Item>
                    </Col>

                    <Col xs={24} md={12}>
                        <Form.Item
                            label="Số lượng câu hỏi"
                            name="questionCount"
                            rules={[
                                {
                                    required: true,
                                    message:
                                        "Vui lòng nhập số lượng câu hỏi.",
                                },
                                {
                                    type: "number",
                                    min: 1,
                                    max: 200,
                                    message:
                                        "Số lượng câu hỏi phải từ 1 đến 200.",
                                },
                            ]}
                            extra="Số câu hỏi sẽ được sinh trong lần thực hiện này."
                        >
                            <InputNumber
                                min={1}
                                max={200}
                                precision={0}
                                className="generation-form-number-input"
                            />
                        </Form.Item>
                    </Col>

                    <Col xs={24} md={12}>
                        <Form.Item
                            label="Temperature"
                            name="temperature"
                            rules={[
                                {
                                    required: true,
                                    message: "Vui lòng nhập temperature.",
                                },
                                {
                                    type: "number",
                                    min: 0,
                                    max: 1,
                                    message:
                                        "Temperature phải nằm trong khoảng 0 đến 1.",
                                },
                            ]}
                            extra="Giá trị cao hơn tạo ra kết quả đa dạng hơn."
                        >
                            <InputNumber
                                min={0}
                                max={1}
                                step={0.1}
                                precision={1}
                                className="generation-form-number-input"
                            />
                        </Form.Item>
                    </Col>
                </Row>

                <div className="generation-form-actions">
                    <Button
                        type="primary"
                        htmlType="submit"
                        icon={<ThunderboltOutlined />}
                        loading={loading}
                    >
                        Sinh câu hỏi
                    </Button>
                </div>
            </Form>
        </div>
    );
}