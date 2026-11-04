import { Col, Form, Input, Row, Select } from 'antd';
import type { FormInstance } from 'antd';

import './ExamMatrixBasicInfoForm.css';

const { TextArea } = Input;

/** Giá trị phần thông tin chung, đúng tên trường của CreateMatrixRequest (trừ notes nhập dạng nhiều dòng). */
export interface ExamMatrixBasicInfoValues {
    name: string;
    subjectId?: number;
    gradeLevel?: number;
    source?: string;
    /** Mỗi dòng là một ghi chú; trang tách thành mảng notes khi gửi. */
    notesText?: string;
}

export interface ExamMatrixSubjectOption {
    label: string;
    value: number;
}

export interface ExamMatrixBasicInfoFormProps {
    /** Danh sách môn từ GET /api/catalog/subjects. */
    subjects?: ExamMatrixSubjectOption[];
    loadingSubjects?: boolean;
    /** Ma trận đã có đề: khóa môn và khối (docs/api mục 7.1a). */
    lockStructure?: boolean;
    disabled?: boolean;
    form: FormInstance<ExamMatrixBasicInfoValues>;
}

// Backend chỉ nhận khối 6-9
const GRADE_OPTIONS = [6, 7, 8, 9].map((grade) => ({ label: `Khối ${grade}`, value: grade }));

/**
 * Phần thông tin chung của ma trận: tên, môn, khối, nguồn, ghi chú.
 */
export function ExamMatrixBasicInfoForm({
    subjects = [],
    loadingSubjects = false,
    lockStructure = false,
    disabled = false,
    form,
}: ExamMatrixBasicInfoFormProps) {
    return (
        <Form<ExamMatrixBasicInfoValues>
            form={form}
            layout="vertical"
            requiredMark="optional"
            initialValues={{ name: '', source: '', notesText: '' }}
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
                            { max: 255, message: 'Tên ma trận không được vượt quá 255 ký tự.' },
                        ]}
                    >
                        <Input
                            placeholder="Ví dụ: Ma trận kiểm tra giữa học kỳ I"
                            maxLength={255}
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
                            loading={loadingSubjects}
                            disabled={lockStructure}
                            showSearch
                            optionFilterProp="label"
                        />
                    </Form.Item>
                </Col>

                <Col xs={24} md={12}>
                    <Form.Item
                        label="Khối lớp"
                        name="gradeLevel"
                        rules={[{ required: true, message: 'Vui lòng chọn khối lớp.' }]}
                    >
                        <Select
                            placeholder="Chọn khối lớp"
                            options={GRADE_OPTIONS}
                            disabled={lockStructure}
                        />
                    </Form.Item>
                </Col>

                <Col xs={24}>
                    <Form.Item label="Nguồn" name="source">
                        <Input placeholder="Ví dụ: Sở GD&ĐT, đề tham khảo… (không bắt buộc)" />
                    </Form.Item>
                </Col>

                <Col xs={24}>
                    <Form.Item label="Ghi chú" name="notesText" tooltip="Mỗi dòng là một ghi chú.">
                        <TextArea
                            placeholder="Ghi chú về phạm vi, cách dùng ma trận (không bắt buộc)"
                            autoSize={{ minRows: 3, maxRows: 8 }}
                        />
                    </Form.Item>
                </Col>
            </Row>
        </Form>
    );
}

export default ExamMatrixBasicInfoForm;
