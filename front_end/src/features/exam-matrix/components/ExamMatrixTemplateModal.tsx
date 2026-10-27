import { useEffect, useMemo, useState } from 'react';
import { Button, Empty, Input, List, Modal, Select, Space, Spin, Typography, message } from 'antd';
import { CopyOutlined, SearchOutlined } from '@ant-design/icons';

import { listExamMatrixSamples } from '../services/examMatrixService';
import { getSubjectById } from '../services/subjectService';
import type { ExamMatrixTemplate } from '../types/examMatrix.types';
import './ExamMatrixTemplateModal.css';

const { Text } = Typography;
const ALL_SUBJECTS = '__all_subjects__';

interface ExamMatrixTemplateModalProps {
    open: boolean;
    onCancel: () => void;
    onSelectTemplate: (templateId: string) => void;
    onCreateFromSample?: (sampleKey: string) => void;
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
 * It fetches templates internally when opened and resolves subject names dynamically.
 */
export function ExamMatrixTemplateModal({
    open,
    onCancel,
    onSelectTemplate,
    onCreateFromSample,
}: ExamMatrixTemplateModalProps) {
    const [searchText, setSearchText] = useState('');
    const [subjectFilter, setSubjectFilter] = useState(ALL_SUBJECTS);

    const [templates, setTemplates] = useState<ExamMatrixTemplate[]>([]);
    const [loading, setLoading] = useState(false);
    const [subjectMap, setSubjectMap] = useState<Record<string | number, string>>({});

    useEffect(() => {
        if (!open) return;          // Chỉ chạy khi modal thực sự được mở
        const fetchTemplates = async () => {
            setLoading(true);
            try {
                const data = await listExamMatrixSamples();
                setTemplates(data);
            } catch (err: any) {
                const msg =
                    err?.response?.data?.error ||
                    err?.message ||
                    'Không thể tải danh sách mẫu.';
                message.error(msg);
            } finally {
                setLoading(false);
            }
        };

        fetchTemplates();
    }, [open]);

    useEffect(() => {
        const missingIds = Array.from(
            new Set(templates.map(t => t.subjectId).filter(Boolean))
        ).filter(id => !(id in subjectMap));

        if (missingIds.length === 0) return;

        (async () => {
            try {
                const results = await Promise.all(
                    missingIds.map(id => getSubjectById(id))
                );
                setSubjectMap(prev => {
                    const next = { ...prev };
                    results.forEach(sub => {
                        if (sub?.id && sub?.name) {
                            next[sub.id] = sub.name;
                        }
                    });
                    return next;
                });
            } catch (err: any) {
                console.error('Failed to load subject names for templates:', err);
            }
        })();
    }, [templates, subjectMap]);

    const templatesWithName = useMemo(() => {
        return templates.map(t => ({
            ...t,
            subjectName: t.subjectName || subjectMap[t.subjectId] || String(t.subjectId),
        }));
    }, [templates, subjectMap]);

    const subjectOptions = useMemo(() => {
        const subjectNames = Array.from(
            new Set(templatesWithName.map((template) => template.subjectName?.trim()).filter(Boolean)),
        ).sort((a, b) => a.localeCompare(b, 'vi'));

        return [
            { label: 'Tất cả môn học', value: ALL_SUBJECTS },
            ...subjectNames.map((subjectName) => ({
                label: subjectName,
                value: subjectName,
            })),
        ];
    }, [templatesWithName]);

    const filteredTemplates = useMemo(() => {
        const keyword = normalizeSearchValue(searchText);

        return templatesWithName.filter((template) => {
            const matchesSubject =
                subjectFilter === ALL_SUBJECTS || template.subjectName === subjectFilter;
            const searchableText = normalizeSearchValue(
                [
                    template.name,
                    template.subjectName,
                    template.gradeLevel == null ? '' : String(template.gradeLevel),
                    template.description ?? '',
                ].join(' '),
            );
            const matchesSearch = !keyword || searchableText.includes(keyword);

            return matchesSubject && matchesSearch;
        });
    }, [templatesWithName, searchText, subjectFilter]);

    const emptyDescription = templates.length === 0
        ? 'Chưa có mẫu ma trận. Hãy tạo hoặc cấu hình mẫu trước khi sử dụng chức năng này.'
        : 'Không tìm thấy mẫu phù hợp với từ khóa hoặc môn học đã chọn.';

    return (
        <Modal
            className="exam-matrix-template-modal"
            title={
                <Space size={10}>
                    <CopyOutlined />
                    <span>Chọn mẫu ma trận</span>
                </Space>
            }
            open={open}
            onCancel={onCancel}
            footer={null}
            width={800}
            centered
            destroyOnClose
        >
            <div className="exam-matrix-template-modal__content">
                <div className="exam-matrix-template-modal__intro" style={{ marginBottom: 16 }}>
                    <Text type="secondary">
                        Chọn một mẫu để khởi tạo biểu mẫu ma trận mới. Bạn có thể điều chỉnh lại thông tin sau khi chọn.
                    </Text>
                </div>

                <div className="exam-matrix-template-modal__filters" style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
                    <Input
                        allowClear
                        value={searchText}
                        onChange={(event) => setSearchText(event.target.value)}
                        placeholder="Tìm theo tên mẫu, môn học..."
                        prefix={<SearchOutlined />}
                        style={{ flex: 1 }}
                    />
                    <Select
                        value={subjectFilter}
                        onChange={setSubjectFilter}
                        options={subjectOptions}
                        style={{ width: 200 }}
                    />
                </div>

                {loading ? (
                    <div style={{ textAlign: 'center', padding: '40px 0' }}>
                        <Spin size="large" />
                        <p style={{ marginTop: 12 }}>Đang tải mẫu…</p>
                    </div>
                ) : filteredTemplates.length > 0 ? (
                    <List
                        dataSource={filteredTemplates}
                        pagination={{ pageSize: 5 }}
                        renderItem={(item) => (
                            <List.Item
                                actions={[
                                    <Button
                                        key="use"
                                        type="primary"
                                        onClick={() => {
                                            // Truyền id về component cha → chuyển hướng
                                            onSelectTemplate(item.id);
                                        }}
                                    >
                                        Sử dụng mẫu này
                                    </Button>,
                                    <Button
                                        type="link"
                                        onClick={() => onCreateFromSample?.(item.id)}   // id === key
                                    >
                                        Tạo từ mẫu
                                    </Button>,
                                ]}
                            >
                                <List.Item.Meta
                                    title={item.name}
                                    description={
                                        <>
                                            <Text strong>Môn học:</Text> {item.subjectName}
                                            <br />
                                            <Text strong>Số ô cấu trúc:</Text> {item.cells?.length ?? 0}
                                        </>
                                    }
                                />
                            </List.Item>
                        )}
                    />
                ) : (
                    <div style={{ padding: '40px 0' }}>
                        <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description={emptyDescription}
                        />
                    </div>
                )}
            </div>
        </Modal>
    );
}

export default ExamMatrixTemplateModal;