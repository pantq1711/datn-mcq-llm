import { Empty, Select, Space, Tag, Typography } from "antd";
import type { TemplateOption } from "../types/generation.types";
import "./TemplateSelector.css";

const { Text, Paragraph } = Typography;

interface TemplateSelectorProps {
    value: number | null;
    options?: TemplateOption[];
    loading?: boolean;
    disabled?: boolean;
    onChange: (value: number | null) => void;
}

export default function TemplateSelector({
    value,
    options = [],
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