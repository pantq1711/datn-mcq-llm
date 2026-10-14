import { Empty, Select, Space, Tag, Typography } from "antd";

import "./TemplateSelector.css";

const { Text, Paragraph } = Typography;

export interface TemplateOption {
    id: number;
    label: string;
    sourceQuestion: string;
    subject: string;
    version?: number;
}

interface TemplateSelectorProps {
    value: number | null;
    options?: TemplateOption[];
    loading?: boolean;
    disabled?: boolean;
    onChange: (value: number | null) => void;
}

const defaultTemplates: TemplateOption[] = [
    {
        id: 1,
        label: "TEMPLATE-001",
        subject: "Toán học",
        version: 1,
        sourceQuestion:
            "Cho hàm số f(x) = x² - 2x + 1. Giá trị nhỏ nhất của hàm số trên R là bao nhiêu?",
    },
    {
        id: 2,
        label: "TEMPLATE-002",
        subject: "Vật lý",
        version: 1,
        sourceQuestion:
            "Một vật có khối lượng 2 kg chuyển động với gia tốc 3 m/s². Độ lớn của lực tác dụng lên vật là bao nhiêu?",
    },
    {
        id: 3,
        label: "TEMPLATE-003",
        subject: "Hóa học",
        version: 2,
        sourceQuestion:
            "Trong phản ứng este hóa giữa axit axetic và etanol, sản phẩm hữu cơ thu được là chất nào?",
    },
    {
        id: 4,
        label: "TEMPLATE-004",
        subject: "Sinh học",
        version: 1,
        sourceQuestion:
            "Bào quan nào là nơi diễn ra quá trình hô hấp tế bào ở sinh vật nhân thực?",
    },
];

export default function TemplateSelector({
    value,
    options = defaultTemplates,
    loading = false,
    disabled = false,
    onChange,
}: TemplateSelectorProps) {
    const selectedTemplate =
        options.find((template) => template.id === value) ?? null;

    const selectOptions = options.map((template) => ({
        value: template.id,
        label: `${template.label} - ${template.subject}`,
    }));

    return (
        <div className="template-selector">
            <div className="template-selector-label">
                <Text strong>Câu hỏi mẫu</Text>
            </div>

            <Select
                value={value ?? undefined}
                options={selectOptions}
                loading={loading}
                disabled={disabled}
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="Chọn câu hỏi mẫu"
                onChange={(selectedValue) => {
                    onChange(selectedValue ?? null);
                }}
                className="template-selector-input"
            />

            {selectedTemplate ? (
                <div className="template-selector-preview">
                    <div className="template-selector-preview-header">
                        <div className="template-selector-preview-title">
                            <Text strong>{selectedTemplate.label}</Text>
                        </div>

                        <Space size={6} wrap>
                            <Tag>{selectedTemplate.subject}</Tag>

                            {selectedTemplate.version !== undefined && (
                                <Tag>
                                    Phiên bản {selectedTemplate.version}
                                </Tag>
                            )}
                        </Space>
                    </div>

                    <div className="template-selector-preview-content">
                        <Text type="secondary">
                            Nội dung câu hỏi mẫu
                        </Text>

                        <Paragraph className="template-selector-source-question">
                            {selectedTemplate.sourceQuestion}
                        </Paragraph>
                    </div>
                </div>
            ) : (
                <div className="template-selector-empty">
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="Chưa chọn câu hỏi mẫu"
                    />
                </div>
            )}
        </div>
    );
}