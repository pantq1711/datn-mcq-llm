import { useRef, useState } from "react";
import { Alert, Button, Checkbox, Col, Form, Input, InputNumber, Row, Select, Space, Typography, } from "antd";
import { ClearOutlined, PlayCircleOutlined, } from "@ant-design/icons";

import "./ExamGenerationForm.css";

export interface ExamGenerationValues {
    title: string;
    subjectId?: number;
    questionCount?: number;
    duration?: number;
    examCodeCount?: number;
    easyCount?: number;
    mediumCount?: number;
    hardCount?: number;
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
    subjectId: undefined,
    questionCount: undefined,
    duration: undefined,
    easyCount: undefined,
    mediumCount: undefined,
    hardCount: undefined,
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
    const [matrixError, setMatrixError] = useState<string | null>(null);

    type LevelField = "easyCount" | "mediumCount" | "hardCount";
    const levelFields: LevelField[] = ["easyCount", "mediumCount", "hardCount"];

    const questionCountManuallySet = useRef(
        initialValues?.questionCount !== undefined && initialValues?.questionCount !== null,
    );

    const autoCalculatedLevel = useRef<LevelField | null>(null);

    const handleValuesChange = (
        changedValues: Partial<ExamGenerationValues>,
        allValues: ExamGenerationValues,
    ) => {
        const changedKeys = Object.keys(changedValues);
        const questionCountChanged = changedKeys.includes("questionCount");
        const changedLevel = levelFields.find((field) => changedKeys.includes(field));

        if (questionCountChanged) {
            const hasEnteredTotal =
                allValues.questionCount !== undefined && allValues.questionCount !== null;
            questionCountManuallySet.current = hasEnteredTotal;

            if (!hasEnteredTotal) {
                autoCalculatedLevel.current = null;
            }
        }

        if (changedLevel && autoCalculatedLevel.current === changedLevel) {
            autoCalculatedLevel.current = null;
        }

        const hasValue = (value: number | undefined | null): value is number =>
            value !== undefined && value !== null && Number.isFinite(value);

        const values = form.getFieldsValue();
        const levelValues: Record<LevelField, number | null> = {
            easyCount: hasValue(values.easyCount) ? Number(values.easyCount) : null,
            mediumCount: hasValue(values.mediumCount) ? Number(values.mediumCount) : null,
            hardCount: hasValue(values.hardCount) ? Number(values.hardCount) : null,
        };
        const hasTotal = hasValue(values.questionCount);
        const total = hasTotal ? Number(values.questionCount) : null;
        const filledLevelFields = levelFields.filter((field) => levelValues[field] !== null);
        const allLevelsFilled = filledLevelFields.length === levelFields.length;

        if (!questionCountManuallySet.current && allLevelsFilled) {
            const calculatedTotal = levelFields.reduce(
                (sum, field) => sum + (levelValues[field] ?? 0),
                0,
            );

            if (values.questionCount !== calculatedTotal) {
                form.setFieldsValue({ questionCount: calculatedTotal });
            }
        } else if (questionCountManuallySet.current && hasTotal && autoCalculatedLevel.current) {
            const autoField = autoCalculatedLevel.current;
            const otherFields = levelFields.filter((field) => field !== autoField);
            const firstField = otherFields[0];
            const secondField = otherFields[1];

            if (!firstField || !secondField) return;

            const first = levelValues[firstField];
            const second = levelValues[secondField];

            if (first !== null && second !== null) {
                const remaining = total! - first - second;
                if (remaining >= 0 && levelValues[autoField] !== remaining) {
                    form.setFieldsValue({ [autoField]: remaining });
                }
            }
        } else if (questionCountManuallySet.current && hasTotal && filledLevelFields.length === 2) {
            const missingField = levelFields.find((field) => levelValues[field] === null);
            if (missingField) {
                const subtotal = filledLevelFields.reduce(
                    (sum, field) => sum + (levelValues[field] ?? 0),
                    0,
                );
                const remaining = total! - subtotal;

                if (remaining >= 0) {
                    autoCalculatedLevel.current = missingField;
                    form.setFieldsValue({ [missingField]: remaining });
                }
            }
        }

        const updatedValues = form.getFieldsValue();
        const finalValues = [
            updatedValues.easyCount,
            updatedValues.mediumCount,
            updatedValues.hardCount,
        ];
        const finalTotal = updatedValues.questionCount;
        const everyValueFilled =
            finalValues.every((value) => hasValue(value)) && hasValue(finalTotal);

        if (everyValueFilled) {
            const sum = finalValues.reduce((acc, value) => acc + Number(value), 0);
            if (sum !== Number(finalTotal)) {
                setMatrixError(
                    `Tổng số câu theo mức độ (${sum}) không bằng Tổng số câu đề thi (${finalTotal}).`,
                );
            } else {
                setMatrixError(null);
            }
        } else {
            setMatrixError(null);
        }
    };

    const handleFinish = (values: ExamGenerationValues) => {
        const e = Number(values.easyCount ?? 0);
        const m = Number(values.mediumCount ?? 0);
        const h = Number(values.hardCount ?? 0);
        const t = Number(values.questionCount ?? 0);

        if (e + m + h !== t) {
            setMatrixError(`Tổng số câu theo mức độ (${e + m + h}) không bằng Tổng số câu đề thi (${t}).`);
            return;
        }

        setMatrixError(null);
        onGenerate?.(values);
    };

    const handleReset = () => {
        form.resetFields();
        questionCountManuallySet.current =
            initialValues?.questionCount !== undefined && initialValues?.questionCount !== null;
        autoCalculatedLevel.current = null;
        setMatrixError(null);
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
            onValuesChange={handleValuesChange}
            onFinish={handleFinish}
            className="exam-generation-form"
        >
            <Row gutter={[16, 0]}>
                <Col xs={24} md={12}>
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
                                message: "Tên đề thi không được vượt quá 200 ký tự.",
                            },
                        ]}
                    >
                        <Input
                            placeholder="Nhập tên đề thi"
                            size="large"
                            maxLength={200}
                        />
                    </Form.Item>
                </Col>

                <Col xs={24} sm={12} md={5}>
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
                                    return Promise.reject(new Error("Vui lòng chọn môn học."));
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

                <Col xs={24} sm={12} md={4}>
                    <Form.Item
                        label="Thời gian làm bài"
                        name="duration"
                        rules={[
                            {
                                required: true,
                                message: "Vui lòng nhập thời gian làm bài.",
                            },
                            {
                                type: "number",
                                min: 1,
                                max: 300,
                                message: "Thời gian phải từ 1 đến 300 phút.",
                            },
                        ]}
                    >
                        <InputNumber
                            size="large"
                            min={1}
                            max={300}
                            addonAfter="phút"
                            className="exam-generation-form-number"
                            placeholder="30"
                        />
                    </Form.Item>
                </Col>

                <Col xs={24} sm={12} md={3}>
                    <Form.Item
                        label="Số mã đề"
                        name="examCodeCount"
                        rules={[
                            {
                                required: true,
                                message: "Vui lòng nhập số mã đề.",
                            },
                            {
                                type: "number",
                                min: 1,
                                max: 20,
                                message: "Số mã đề phải từ 1 đến 20.",
                            },
                        ]}
                    >
                        <InputNumber
                            size="large"
                            min={1}
                            max={20}
                            precision={0}
                            className="exam-generation-form-number"
                            placeholder="1"
                        />
                    </Form.Item>
                </Col>
            </Row>

            {/* Cảnh báo lỗi ma trận nếu tham số không hợp lệ */}
            {matrixError && (
                <Alert
                    type="error"
                    message="Lỗi ma trận đề thi"
                    description={matrixError}
                    showIcon
                    style={{ marginBottom: 16 }}
                />
            )}

            <Row gutter={[16, 16]} className="exam-matrix-options-row">
                <Col xs={24} lg={8}>
                    <div className="exam-matrix-container">
                        <Typography.Title level={5} className="exam-matrix-heading">
                            Ma trận đề thi
                        </Typography.Title>
                        <table className="exam-matrix-table">
                            <thead>
                                <tr>
                                    <th>Mức độ nhận thức</th>
                                    <th>Số câu</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>Biết</td>
                                    <td>
                                        <Form.Item
                                            name="easyCount"
                                            noStyle
                                            rules={[
                                                { required: true, message: "Vui lòng nhập số câu" },
                                                { type: "number", min: 0, message: "Không được âm" },
                                            ]}
                                        >
                                            <InputNumber min={0} max={200} className="exam-matrix-input" placeholder="0" />
                                        </Form.Item>
                                    </td>
                                </tr>
                                <tr>
                                    <td>Hiểu</td>
                                    <td>
                                        <Form.Item
                                            name="mediumCount"
                                            noStyle
                                            rules={[
                                                { required: true, message: "Vui lòng nhập số câu" },
                                                { type: "number", min: 0, message: "Không được âm" },
                                            ]}
                                        >
                                            <InputNumber min={0} max={200} className="exam-matrix-input" placeholder="0" />
                                        </Form.Item>
                                    </td>
                                </tr>
                                <tr>
                                    <td>Vận dụng</td>
                                    <td>
                                        <Form.Item
                                            name="hardCount"
                                            noStyle
                                            rules={[
                                                { required: true, message: "Vui lòng nhập số câu" },
                                                { type: "number", min: 0, message: "Không được âm" },
                                            ]}
                                        >
                                            <InputNumber min={0} max={200} className="exam-matrix-input" placeholder="0" />
                                        </Form.Item>
                                    </td>
                                </tr>
                                <tr className="exam-matrix-total-row">
                                    <td>Tổng số câu</td>
                                    <td>
                                        <Form.Item
                                            name="questionCount"
                                            noStyle
                                            rules={[
                                                { required: true, message: "Vui lòng nhập tổng số câu" },
                                                { type: "number", min: 1, max: 200, message: "Số câu từ 1 - 200" },
                                            ]}
                                        >
                                            <InputNumber min={1} max={200} className="exam-matrix-input" placeholder="0" />
                                        </Form.Item>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </Col>

                <Col xs={24} lg={9} className="exam-right-column">
                    <div className="exam-options-container">
                        <Typography.Title level={5} className="exam-matrix-heading">
                            Tùy chọn
                        </Typography.Title>
                        <Space direction="vertical" size={12} className="exam-options-space">
                            <Form.Item
                                name="shuffleQuestions"
                                valuePropName="checked"
                                noStyle
                            >
                                <Checkbox>Trộn thứ tự câu hỏi</Checkbox>
                            </Form.Item>

                            <Form.Item
                                name="shuffleOptions"
                                valuePropName="checked"
                                noStyle
                            >
                                <Checkbox>Trộn thứ tự đáp án</Checkbox>
                            </Form.Item>
                        </Space>
                    </div>

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
                </Col>
            </Row>
        </Form>
    );
}