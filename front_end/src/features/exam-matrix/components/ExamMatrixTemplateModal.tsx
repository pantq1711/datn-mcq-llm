import { useEffect, useMemo, useState } from 'react';
import { Button, Empty, Input, Modal, Select, Space, Spin, Typography } from 'antd';
import { CopyOutlined, SearchOutlined } from '@ant-design/icons';

import {
    ExamMatrixTemplateCard,
    type ExamMatrixTemplate,
} from './ExamMatrixTemplateCard';
import './ExamMatrixTemplateModal.css';

const { Text } = Typography;
const ALL_SUBJECTS = '__all_subjects__';

export interface ExamMatrixTemplateModalProps {
    open: boolean;
    onCancel: () => void;
    /** Called after the user picks a template and confirms the selection. */
    onSelectTemplate: (templateId: string) => void;
    /** Templates loaded by the parent from the backend. */
    templates?: ExamMatrixTemplate[];
    /** Displays a loading state while templates are retrieved. */
    loading?: boolean;
}

function normalizeSearchValue(value: string): string {
    return value
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/đ/g, 'd')
        .replace(/Đ/g, 'D')
        .toLocaleLowerCase('vi-VN')
        .trim();
}

/**
 * Modal for choosing an existing matrix template.
 * It does not fetch data itself; the parent provides templates and handles navigation.
 */
export function ExamMatrixTemplateModal({
    open,
    onCancel,
    onSelectTemplate,
    templates = [],
    loading = false,
}: ExamMatrixTemplateModalProps) {
    const [searchText, setSearchText] = useState('');
    const [subjectFilter, setSubjectFilter] = useState(ALL_SUBJECTS);
    const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);

    useEffect(() => {
        if (open) {
            setSearchText('');
            setSubjectFilter(ALL_SUBJECTS);
            setSelectedTemplateId(null);
        }
    }, [open]);

    const subjectOptions = useMemo(() => {
        const subjectNames = Array.from(
            new Set(templates.map((template) => template.subjectName.trim()).filter(Boolean)),
        ).sort((a, b) => a.localeCompare(b, 'vi'));

        return [
            { label: 'Tất cả môn học', value: ALL_SUBJECTS },
            ...subjectNames.map((subjectName) => ({
                label: subjectName,
                value: subjectName,
            })),
        ];
    }, [templates]);

    const filteredTemplates = useMemo(() => {
        const keyword = normalizeSearchValue(searchText);

        return templates.filter((template) => {
            const matchesSubject =
                subjectFilter === ALL_SUBJECTS || template.subjectName === subjectFilter;
            const searchableText = normalizeSearchValue(
                [
                    template.name,
                    template.subjectName,
                    template.grade == null ? '' : String(template.grade),
                    template.description ?? '',
                ].join(' '),
            );
            const matchesSearch = !keyword || searchableText.includes(keyword);

            return matchesSubject && matchesSearch;
        });
    }, [templates, searchText, subjectFilter]);

    const handleConfirm = () => {
        if (!selectedTemplateId || loading) return;
        onSelectTemplate(selectedTemplateId);
    };

    const hasTemplates = templates.length > 0;
    const emptyDescription = !hasTemplates
        ? 'Chưa có mẫu ma trận. Hãy tạo hoặc cấu hình mẫu trước khi sử dụng chức năng này.'
        : 'Không tìm thấy mẫu phù hợp với từ khóa hoặc môn học đã chọn.';

    return (
        <Modal
            className="exam-matrix-template-modal"
            title={
                <Space size={10}>
                    <CopyOutlined />
                    <span>Tạo ma trận từ mẫu</span>
                </Space>
            }
            open={open}
            onCancel={onCancel}
            width={900}
            centered
            destroyOnClose
            footer={
                <div className="exam-matrix-template-modal__footer">
                    <Text type="secondary" className="exam-matrix-template-modal__selection-hint">
                        {selectedTemplateId
                            ? 'Đã chọn một mẫu ma trận.'
                            : 'Chọn một mẫu để tiếp tục.'}
                    </Text>
                    <Space>
                        <Button onClick={onCancel}>Hủy</Button>
                        <Button
                            type="primary"
                            disabled={!selectedTemplateId || loading}
                            onClick={handleConfirm}
                        >
                            Sử dụng mẫu
                        </Button>
                    </Space>
                </div>
            }
        >
            <div className="exam-matrix-template-modal__content">
                <div className="exam-matrix-template-modal__intro">
                    <Text>
                        Chọn một mẫu để tạo ma trận mới. Thông tin từ mẫu sẽ được dùng để khởi tạo
                        biểu mẫu và có thể chỉnh sửa ở bước tiếp theo.
                    </Text>
                </div>

                <div className="exam-matrix-template-modal__filters">
                    <Input
                        allowClear
                        value={searchText}
                        onChange={(event) => setSearchText(event.target.value)}
                        placeholder="Tìm theo tên mẫu, môn học hoặc khối lớp..."
                        prefix={<SearchOutlined />}
                        aria-label="Tìm kiếm mẫu ma trận"
                    />
                    <Select
                        value={subjectFilter}
                        onChange={setSubjectFilter}
                        options={subjectOptions}
                        aria-label="Lọc mẫu theo môn học"
                        className="exam-matrix-template-modal__subject-filter"
                    />
                </div>

                <div className="exam-matrix-template-modal__results-meta">
                    <Text type="secondary">
                        {loading
                            ? 'Đang tải mẫu ma trận…'
                            : `${filteredTemplates.length} mẫu ma trận`}
                    </Text>
                </div>

                <Spin spinning={loading}>
                    {filteredTemplates.length > 0 ? (
                        <div className="exam-matrix-template-modal__grid">
                            {filteredTemplates.map((template) => (
                                <ExamMatrixTemplateCard
                                    key={template.id}
                                    template={template}
                                    selected={selectedTemplateId === template.id}
                                    onSelect={setSelectedTemplateId}
                                />
                            ))}
                        </div>
                    ) : (
                        !loading && (
                            <div className="exam-matrix-template-modal__empty">
                                <Empty
                                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                                    description={emptyDescription}
                                />
                            </div>
                        )
                    )}
                </Spin>
            </div>
        </Modal>
    );
}

export default ExamMatrixTemplateModal;
