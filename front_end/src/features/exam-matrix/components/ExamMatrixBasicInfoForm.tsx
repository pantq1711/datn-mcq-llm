import { useEffect } from 'react';
import { Col, Form, Input, Row, Select } from 'antd';
import type { FormInstance } from 'antd';

import './ExamMatrixBasicInfoForm.css';

const { TextArea } = Input;

/** Values collected by the basic-information section of an exam matrix. */
export interface ExamMatrixBasicInfoValues {
    name: string;
    subjectId?: string;
    grade?: number;
    description?: string;
}

export interface ExamMatrixSubjectOption {
    label: string;
    value: string;
}

export interface ExamMatrixBasicInfoFormProps {
    /** Optional values used to populate the form when creating from a template or editing. */
    initialValues?: Partial<ExamMatrixBasicInfoValues>;
    /** Subject options loaded by the page or a data hook. */
    subjects?: ExamMatrixSubjectOption[];
    /** Receives all current values whenever the user changes a field. */
    onValuesChange?: (values: Partial<ExamMatrixBasicInfoValues>) => void;
    /** Allows the parent page to disable all fields, for example while saving. */
    disabled?: boolean;
    /** Exposes the Ant Design form instance when the parent needs to validate/read values on save. */
    form?: FormInstance<ExamMatrixBasicInfoValues>;
}

const DEFAULT_SUBJECT_OPTIONS: ExamMatrixSubjectOption[] = [
    { label: 'Toán', value: 'math' },
    { label: 'Ngữ văn', value: 'literature' },
    { label: 'Vật lý', value: 'physics' },
    { label: 'Hóa học', value: 'chemistry' },
    { label: 'Sinh học', value: 'biology' },
    { label: 'Lịch sử', value: 'history' },
    { label: 'Địa lý', value: 'geography' },
    { label: 'Tiếng Anh', value: 'english' },
];

const GRADE_OPTIONS = Array.from({ length: 12 }, (_, index) => {
    const grade = index + 1;
    return { label: `Lớp ${grade}`, value: grade };
});

/**
 * Form section for the matrix name, subject, grade and optional description.
 * It can own its form instance or use the instance supplied by the parent page.
 */
export function ExamMatrixBasicInfoForm({
    initialValues,
    subjects = DEFAULT_SUBJECT_OPTIONS,
    onValuesChange,
    disabled = false,
    form: externalForm,
}: ExamMatrixBasicInfoFormProps) {
    const [internalForm] = Form.useForm<ExamMatrixBasicInfoValues>();
    const form = externalForm ?? internalForm;

    const hasInitialValues = initialValues !== undefined;
    const initialName = initialValues?.name;
    const initialSubjectId = initialValues?.subjectId;
    const initialGrade = initialValues?.grade;
    const initialDescription = initialValues?.description;

    useEffect(() => {
        if (!hasInitialValues) return;

        form.setFieldsValue({
            ...(initialName !== undefined ? { name: initialName } : {}),
            ...(initialSubjectId !== undefined ? { subjectId: initialSubjectId } : {}),
            ...(initialGrade !== undefined ? { grade: initialGrade } : {}),
            ...(initialDescription !== undefined ? { description: initialDescription } : {}),
        });
    }, [
        form,
        hasInitialValues,
        initialName,
        initialSubjectId,
        initialGrade,
        initialDescription,
    ]);

    const handleValuesChange = (
        _changedValues: Partial<ExamMatrixBasicInfoValues>,
        allValues: Partial<ExamMatrixBasicInfoValues>,
    ) => {
        onValuesChange?.(allValues);
    };

    return (
        <Form<ExamMatrixBasicInfoValues>
            form={form}
            layout="vertical"
            requiredMark="optional"
            initialValues={{
                name: '',
                subjectId: undefined,
                grade: undefined,
                description: '',
                ...initialValues,
            }}
            onValuesChange={handleValuesChange}
            disabled={disabled}
            className="exam-matrix-basic-info-form"
        >
            <Row gutter={[16, 16]}>
                <Col xs={24}>
                    <Form.Item
                        label="Tên ma trận"
                        name="name"
                        rules={[
                            { required: true, whitespace: true, message: 'Vui lòng nhập tên ma trận.' },
                            { max: 150, message: 'Tên ma trận không được vượt quá 150 ký tự.' },
                        ]}
                    >
                        <Input
                            placeholder="Ví dụ: Ma trận kiểm tra giữa học kỳ I"
                            maxLength={150}
                            showCount
                        />
                    </Form.Item>
                </Col>

                <Col xs={24} md={12}>
                    <Form.Item
                        label="Môn học"
                        name="subjectId"
                        rules={[{ required: true, message: 'Vui lòng chọn môn học.' }]}
                    >
                        <Select
                            placeholder="Chọn môn học"
                            options={subjects}
                            showSearch
                            optionFilterProp="label"
                            allowClear
                        />
                    </Form.Item>
                </Col>

                <Col xs={24} md={12}>
                    <Form.Item
                        label="Khối lớp"
                        name="grade"
                        rules={[{ required: true, message: 'Vui lòng chọn khối lớp.' }]}
                    >
                        <Select
                            placeholder="Chọn khối lớp"
                            options={GRADE_OPTIONS}
                            allowClear
                        />
                    </Form.Item>
                </Col>

                <Col xs={24}>
                    <Form.Item
                        label="Mô tả"
                        name="description"
                        rules={[{ max: 500, message: 'Mô tả không được vượt quá 500 ký tự.' }]}
                    >
                        <TextArea
                            placeholder="Mô tả mục đích hoặc phạm vi sử dụng của ma trận (không bắt buộc)"
                            autoSize={{ minRows: 3, maxRows: 5 }}
                            maxLength={500}
                            showCount
                        />
                    </Form.Item>
                </Col>
            </Row>
        </Form>
    );
}

export default ExamMatrixBasicInfoForm;
