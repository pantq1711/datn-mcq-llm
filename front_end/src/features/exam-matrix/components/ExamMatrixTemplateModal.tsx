import { useEffect, useMemo, useState } from 'react';
import { Button, Empty, Input, List, Modal, Space, Spin, Tag, Typography, message } from 'antd';
import { CopyOutlined, SearchOutlined } from '@ant-design/icons';

import { listExamMatrixSamples } from '../services/examMatrixService';
import type { ExamMatrixSample } from '../types/examMatrix.types';
import './ExamMatrixTemplateModal.css';

const { Text, Paragraph } = Typography;

interface ExamMatrixTemplateModalProps {
    open: boolean;
    onCancel: () => void;
    onCreateFromSample: (sampleKey: string) => void;
}

function normalizeSearchValue(value: string): string {
    return value
        .normalize('NFD')
        .replace(/[̀-ͯ]/g, '')
        .replace(/đ/g, 'd')
        .replace(/Đ/g, 'D')
        .toLocaleLowerCase('vi-VN')
        .trim();
}

/**
 * Modal chọn mẫu ma trận có sẵn (GET /api/exam-matrices/samples).
 * Mẫu chỉ có key, tên, khối, nguồn, ghi chú; không có danh sách ô nên chỉ tạo được ma trận từ mẫu rồi sửa sau.
 */
export function ExamMatrixTemplateModal({
    open,
    onCancel,
    onCreateFromSample,
}: ExamMatrixTemplateModalProps) {
    const [searchText, setSearchText] = useState('');
    const [samples, setSamples] = useState<ExamMatrixSample[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (!open) return;          // Chỉ chạy khi modal thực sự được mở
        const fetchSamples = async () => {
            setLoading(true);
            try {
                const data = await listExamMatrixSamples();
                setSamples(data);
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

        fetchSamples();
    }, [open]);

    const filteredSamples = useMemo(() => {
        const keyword = normalizeSearchValue(searchText);
        if (!keyword) return samples;

        return samples.filter((sample) =>
            normalizeSearchValue(
                [
                    sample.name,
                    sample.gradeLevel == null ? '' : `khoi ${sample.gradeLevel}`,
                    sample.source ?? '',
                    ...(sample.notes ?? []),
                ].join(' '),
            ).includes(keyword),
        );
    }, [samples, searchText]);

    const emptyDescription = samples.length === 0
        ? 'Chưa có mẫu ma trận nào.'
        : 'Không tìm thấy mẫu phù hợp với từ khóa.';

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
            footer={null}
            width={800}
            centered
            destroyOnClose
        >
            <div className="exam-matrix-template-modal__content">
                <div className="exam-matrix-template-modal__intro">
                    <Text type="secondary">
                        Chọn một mẫu để tạo ma trận mới (môn mặc định: Toán). Sau khi tạo, bạn có thể chỉnh sửa ma trận như bình thường.
                    </Text>
                </div>

                <Input
                    allowClear
                    value={searchText}
                    onChange={(event) => setSearchText(event.target.value)}
                    placeholder="Tìm theo tên mẫu, khối, nguồn, ghi chú..."
                    prefix={<SearchOutlined />}
                />

                {loading ? (
                    <div style={{ textAlign: 'center', padding: '40px 0' }}>
                        <Spin size="large" />
                        <p style={{ marginTop: 12 }}>Đang tải mẫu…</p>
                    </div>
                ) : filteredSamples.length > 0 ? (
                    <List
                        dataSource={filteredSamples}
                        pagination={{ pageSize: 5, hideOnSinglePage: true }}
                        renderItem={(item) => (
                            <List.Item
                                actions={[
                                    <Button
                                        key="create"
                                        type="primary"
                                        onClick={() => onCreateFromSample(item.key)}
                                    >
                                        Tạo từ mẫu
                                    </Button>,
                                ]}
                            >
                                <List.Item.Meta
                                    title={
                                        <Space size={8} wrap>
                                            <span>{item.name}</span>
                                            {item.gradeLevel != null && (
                                                <Tag color="geekblue">Khối {item.gradeLevel}</Tag>
                                            )}
                                        </Space>
                                    }
                                    description={
                                        <>
                                            {item.source && (
                                                <div>
                                                    <Text strong>Nguồn:</Text> {item.source}
                                                </div>
                                            )}
                                            {item.notes && item.notes.length > 0 && (
                                                <Paragraph
                                                    type="secondary"
                                                    style={{ marginBottom: 0, whiteSpace: 'pre-line' }}
                                                    ellipsis={{ rows: 2, expandable: true, symbol: 'Xem thêm' }}
                                                >
                                                    {item.notes.join('\n')}
                                                </Paragraph>
                                            )}
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
