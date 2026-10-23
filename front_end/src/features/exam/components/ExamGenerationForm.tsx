import { useMemo, useState } from "react";
import {
    Alert,
    Button,
    Checkbox,
    Col,
    Form,
    Input,
    InputNumber,
    Row,
    Select,
    Space,
    Typography,
} from "antd";
import { ClearOutlined, PlayCircleOutlined } from "@ant-design/icons";
import type { ExamSubjectOption, ExamGenerationValues } from "../types/exam.types";
import "./ExamGenerationForm.css";

type QuestionType = "MULTIPLE_CHOICE" | "TRUE_FALSE" | "SHORT_ANSWER";
type CognitiveLevel = "BIET" | "HIEU" | "VAN_DUNG";

type MatrixCounts = Record<QuestionType, Record<CognitiveLevel, number | undefined>>;

type ExamGenerationFormValues = ExamGenerationValues & {
    matrixCounts: MatrixCounts;
    questionCount: number;
    examCodeCount?: number;
};

interface ExamGenerationFormProps {
    subjects: ExamSubjectOption[];
    loading?: boolean;
    loadingSubjects?: boolean;
    initialValues?: Partial<ExamGenerationValues> & {
        matrixCounts?: MatrixCounts;
    };
    onGenerate?: (values: ExamGenerationFormValues) => void;
    onReset?: () => void;
}

const cognitiveLevels: { key: CognitiveLevel; label: string }[] = [
    { key: "BIET", label: "Biết" },
    { key: "HIEU", label: "Hiểu" },
    { key: "VAN_DUNG", label: "Vận dụng" },
];

const questionTypes: { key: QuestionType; label: string }[] = [
    { key: "MULTIPLE_CHOICE", label: "Trắc nghiệm nhiều lựa chọn" },
    { key: "TRUE_FALSE", label: "Đúng – Sai" },
    { key: "SHORT_ANSWER", label: "Trả lời ngắn" },
];

const defaultMatrixCounts: MatrixCounts = {
    MULTIPLE_CHOICE: { BIET: 0, HIEU: 0, VAN_DUNG: 0 },
    TRUE_FALSE: { BIET: 0, HIEU: 0, VAN_DUNG: 0 },
    SHORT_ANSWER: { BIET: 0, HIEU: 0, VAN_DUNG: 0 },
};

const defaultValues: Partial<ExamGenerationFormValues> = {
    title: "",
    subjectId: undefined,
    questionCount: 0,
    duration: undefined,
    easyCount: undefined,
    mediumCount: undefined,
    hardCount: undefined,
    examCodeCount: 1,
    matrixCounts: defaultMatrixCounts,
    shuffleQuestions: true,
    shuffleOptions: false,
};

const getMatrixTotal = (matrix?: Partial<MatrixCounts>): number =>
    questionTypes.reduce(
        (typeTotal, type) =>
            typeTotal +
            cognitiveLevels.reduce(
                (levelTotal, level) =>
                    levelTotal + Math.max(0, Number(matrix?.[type.key]?.[level.key] ?? 0)),
                0,
            ),
        0,
    );

const getQuestionTypeTotal = (
    matrix: Partial<MatrixCounts> | undefined,
    questionType: QuestionType,
): number =>
    cognitiveLevels.reduce(
        (total, level) => total + Math.max(0, Number(matrix?.[questionType]?.[level.key] ?? 0)),
        0,
    );

export default function ExamGenerationForm({
    subjects,
    loading = false,
    loadingSubjects = false,
    initialValues,
    onGenerate,
    onReset,
}: ExamGenerationFormProps) {
    const [form] = Form.useForm<ExamGenerationFormValues>();
    const [matrixError, setMatrixError] = useState<string | null>(null);

    const initialMatrixCounts = initialValues?.matrixCounts ?? defaultMatrixCounts;
    const watchedMatrixCounts = Form.useWatch("matrixCounts", form) as MatrixCounts | undefined;
    const matrixCounts = watchedMatrixCounts ?? initialMatrixCounts;

    const totalQuestionCount = useMemo(() => getMatrixTotal(matrixCounts), [matrixCounts]);

    const handleValuesChange = (_changedValues: Partial<ExamGenerationFormValues>, allValues: ExamGenerationFormValues) => {
        const total = getMatrixTotal(allValues.matrixCounts);
        form.setFieldsValue({ questionCount: total });
        setMatrixError(null);
    };

    const handleFinish = (values: ExamGenerationFormValues) => {
        const total = getMatrixTotal(values.matrixCounts);

        if (total < 1) {
            setMatrixError("Vui lòng nhập ít nhất 1 câu hỏi trong ma trận.");
            return;
        }

        if (total > 200) {
            setMatrixError("Tổng số câu không được vượt quá 200.");
            return;
        }

        setMatrixError(null);
        onGenerate?.({
            ...values,
            questionCount: total,
            matrixCounts: values.matrixCounts,
        });
    };

    const handleReset = () => {
        form.resetFields();
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
                // Tổng số câu luôn được tính từ 9 ô ma trận, không lấy giá trị nhập cũ.
                questionCount: getMatrixTotal(initialMatrixCounts),
                matrixCounts: initialMatrixCounts,
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
                            { required: true, message: "Vui lòng nhập tên đề thi." },
                            { max: 200, message: "Tên đề thi không được vượt quá 200 ký tự." },
                        ]}
                    >
                        <Input placeholder="Nhập tên đề thi" size="large" maxLength={200} />
                    </Form.Item>
                </Col>

                <Col xs={24} sm={12} md={5}>
                    <Form.Item
                        label="Môn học"
                        name="subjectId"
                        rules={[
                            { required: true, message: "Vui lòng chọn môn học." },
                            {
                                validator: (_, value) =>
                                    value && value > 0
                                        ? Promise.resolve()
                                        : Promise.reject(new Error("Vui lòng chọn môn học.")),
                            },
                        ]}
                    >
                        <Select
                            size="large"
                            placeholder="Chọn môn học"
                            loading={loadingSubjects}
                            options={subjects}
                        />
                    </Form.Item>
                </Col>

                <Col xs={24} sm={12} md={4}>
                    <Form.Item
                        label="Thời gian làm bài"
                        name="duration"
                        rules={[
                            { required: true, message: "Vui lòng nhập thời gian làm bài." },
                            { type: "number", min: 1, max: 300, message: "Thời gian phải từ 1 đến 300 phút." },
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
                            { required: true, message: "Vui lòng nhập số mã đề." },
                            { type: "number", min: 1, max: 20, message: "Số mã đề phải từ 1 đến 20." },
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
                <Col xs={24} lg={14}>
                    <div className="exam-matrix-container">
                        <Typography.Title level={5} className="exam-matrix-heading">
                            Ma trận đề thi
                        </Typography.Title>

                        <div className="exam-matrix-table-wrapper">
                            <table className="exam-matrix-table">
                                <thead>
                                    <tr>
                                        <th>Dạng câu hỏi</th>
                                        <th>Các mức độ nhận thức</th>
                                        <th>Số câu</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {questionTypes.map((type) =>
                                        cognitiveLevels.map((level, levelIndex) => (
                                            <tr key={`${type.key}-${level.key}`}>
                                                {levelIndex === 0 && (
                                                    <td rowSpan={cognitiveLevels.length} className="exam-matrix-type-cell">
                                                        <span className="exam-matrix-type-label">{type.label}</span>
                                                        <span className="exam-matrix-type-total">
                                                            ({getQuestionTypeTotal(matrixCounts, type.key)} câu)
                                                        </span>
                                                    </td>
                                                )}
                                                <td>{level.label}</td>
                                                <td className="exam-matrix-count-cell">
                                                    <Form.Item
                                                        name={["matrixCounts", type.key, level.key]}
                                                        noStyle
                                                        rules={[
                                                            { required: true, message: "Nhập số câu hoặc nhập 0." },
                                                            { type: "number", min: 0, max: 200, message: "Số câu từ 0 đến 200." },
                                                        ]}
                                                    >
                                                        <InputNumber
                                                            min={0}
                                                            max={200}
                                                            precision={0}
                                                            className="exam-matrix-input"
                                                            placeholder="0"
                                                            disabled={loading}
                                                            defaultValue={0}
                                                            onFocus={(e) => {
                                                                const input = e.target as HTMLInputElement;
                                                                if (input.value === '0') {
                                                                    input.select();
                                                                }
                                                            }}
                                                        />
                                                    </Form.Item>
                                                </td>
                                            </tr>
                                        )),
                                    )}
                                    <tr className="exam-matrix-total-row">
                                        <td colSpan={2}>Tổng số câu</td>
                                        <td className="exam-matrix-grand-total">{totalQuestionCount}</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>

                        {/* Lưu tổng đã tính vào payload nhưng không hiển thị trường nhập liệu. */}
                        <Form.Item name="questionCount" hidden>
                            <InputNumber />
                        </Form.Item>
                    </div>
                </Col>

                <Col xs={24} lg={10} className="exam-right-column">
                    <div className="exam-options-container">
                        <Typography.Title level={5} className="exam-matrix-heading">
                            Tùy chọn
                        </Typography.Title>
                        <Space direction="vertical" size={12} className="exam-options-space">
                            <Form.Item name="shuffleQuestions" valuePropName="checked" noStyle>
                                <Checkbox>Trộn thứ tự câu hỏi</Checkbox>
                            </Form.Item>
                            <Form.Item name="shuffleOptions" valuePropName="checked" noStyle>
                                <Checkbox>Trộn thứ tự đáp án</Checkbox>
                            </Form.Item>
                        </Space>
                    </div>

                    <div className="exam-generation-form-actions">
                        <Space>
                            <Button icon={<ClearOutlined />} onClick={handleReset} disabled={loading}>
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
