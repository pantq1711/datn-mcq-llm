import { useEffect } from 'react';
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
    /** Ma trận cũ chưa có khối: vẫn cho chọn khối dù đã khóa cấu trúc. */
    allowGradeFill?: boolean;
    disabled?: boolean;
    form: FormInstance<ExamMatrixBasicInfoValues>;
}

// Backend chỉ nhận khối 6-9; giao diện gọi là "Lớp" như giáo viên vẫn gọi
const GRADE_OPTIONS = [6, 7, 8, 9].map((grade) => ({ label: `Lớp ${grade}`, value: grade }));

/**
 * Phần thông tin chung của ma trận: tên, môn, khối, nguồn, ghi chú.
 */
export function ExamMatrixBasicInfoForm({
    subjects = [],
    loadingSubjects = false,
    lockStructure = false,
    allowGradeFill = false,
    disabled = false,
    form,
}: ExamMatrixBasicInfoFormProps) {
    // Hệ thống chỉ có đúng 1 môn: tự chọn môn đó và ẩn ô Môn (rà soát giao diện mục 18).
    // Từ 2 môn trở lên, hoặc ma trận cũ thuộc môn khác, ô Môn hiện như cũ.
    const onlySubject = subjects.length === 1 ? subjects[0] : null;
    const watchedSubjectId = Form.useWatch('subjectId', form);
    useEffect(() => {
        if (onlySubject && form.getFieldValue('subjectId') == null) {
            form.setFieldsValue({ subjectId: onlySubject.value });
        }
    }, [onlySubject?.value, form]);
    const hideSubject = onlySubject !== null && (watchedSubjectId == null || watchedSubjectId === onlySubject.value);

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
                            placeholder="Ví dụ: Giữa kì I – Toán 8"
                            maxLength={255}
                            showCount
                        />
                    </Form.Item>
                </Col>

                {/* Ẩn bằng display, không gỡ Form.Item: validateFields vẫn trả subjectId */}
                <Col xs={24} md={12} style={hideSubject ? { display: 'none' } : undefined}>
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
                        label="Lớp"
                        name="gradeLevel"
                        rules={[{ required: true, message: 'Vui lòng chọn lớp.' }]}
                    >
                        <Select
                            placeholder="Chọn lớp"
                            options={GRADE_OPTIONS}
                            disabled={lockStructure && !allowGradeFill}
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
