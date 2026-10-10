
import { useEffect, useMemo, useState } from "react";

import {
    Alert,
    Button,
    Divider,
    Empty,
    Input,
    List,
    Space,
    Tag,
    Typography,
    message,
} from "antd";

import {
    DeleteOutlined,
    PlusOutlined,
} from "@ant-design/icons";

import "./TextSegmentSelector.css";

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

export interface TextSegment {
    id: string;
    text: string;
    startOffset: number;
    endOffset: number;
}

interface TextSegmentSelectorProps {
    content: string;
    value: TextSegment[];
    onChange: (segments: TextSegment[]) => void;
    disabled?: boolean;
}

interface TextRange {
    start: number;
    end: number;
}

export default function TextSegmentSelector({
    content,
    value,
    onChange,
    disabled = false,
}: TextSegmentSelectorProps) {
    const [selection, setSelection] = useState<TextRange | null>(null);

    // Xóa vùng đang chọn khi nội dung nguồn thay đổi.
    useEffect(() => {
        setSelection(null);
    }, [content]);

    // Chuẩn hóa phạm vi chọn, loại bỏ khoảng trắng ở hai đầu.
    const normalizedSelection = useMemo(() => {
        if (!selection || !content) {
            return null;
        }

        const start = Math.max(
            0,
            Math.min(selection.start, content.length),
        );

        const end = Math.max(
            start,
            Math.min(selection.end, content.length),
        );

        const selectedText = content.slice(start, end);
        const trimmedText = selectedText.trim();

        if (!trimmedText) {
            return null;
        }

        const leadingWhitespace =
            selectedText.length - selectedText.trimStart().length;

        const startOffset = start + leadingWhitespace;
        const endOffset = startOffset + trimmedText.length;

        return {
            text: trimmedText,
            startOffset,
            endOffset,
        };
    }, [content, selection]);

    const totalCharacters = value.reduce(
        (total, segment) => total + segment.text.length,
        0,
    );

    const handleTextSelection = (
        event: React.SyntheticEvent<HTMLTextAreaElement>,
    ) => {
        const target = event.currentTarget;
        const start = target.selectionStart;
        const end = target.selectionEnd;

        setSelection(
            end > start
                ? { start, end }
                : null,
        );
    };

    const handleAddSegment = () => {
        if (!normalizedSelection || disabled) {
            return;
        }

        const { text, startOffset, endOffset } = normalizedSelection;

        // Không cho phép các đoạn được chọn chồng lấn nhau.
        const hasOverlap = value.some(
            (segment) =>
                startOffset < segment.endOffset &&
                endOffset > segment.startOffset,
        );

        if (hasOverlap) {
            message.warning(
                "Đoạn được chọn chồng lấn với một đoạn đã có. " +
                "Vui lòng chọn phạm vi khác hoặc xóa đoạn cũ.",
            );
            return;
        }

        const newSegment: TextSegment = {
            id: `segment-${startOffset}-${endOffset}`,
            text,
            startOffset,
            endOffset,
        };

        const nextSegments = [...value, newSegment].sort(
            (a, b) => a.startOffset - b.startOffset,
        );

        onChange(nextSegments);
        setSelection(null);

        message.success("Đã thêm đoạn cần kiểm tra.");
    };

    const handleRemoveSegment = (segmentId: string) => {
        onChange(
            value.filter((segment) => segment.id !== segmentId),
        );
    };

    const handleClearSegments = () => {
        onChange([]);
        setSelection(null);
    };

    return (
        <div className="text-segment-selector">
            {!content.trim() ? (
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="Vui lòng nhập hoặc tải nội dung bài học trước."
                />
            ) : (
                <>
                    <Alert
                        className="text-segment-selector-instruction"
                        type="info"
                        showIcon
                        message="Cách chọn đoạn cần kiểm tra"
                        description={
                            "Bôi đen đoạn văn bản trong vùng nội dung bên dưới, " +
                            "sau đó nhấn “Thêm đoạn đã chọn”. Bạn có thể chọn " +
                            "nhiều đoạn không liền nhau."
                        }
                    />

                    <div className="text-segment-selector-source">
                        <div className="text-segment-selector-source-header">
                            <Text strong>Nội dung bài học</Text>
                            <Text type="secondary">
                                {content.length.toLocaleString("vi-VN")} ký tự
                            </Text>
                        </div>

                        <TextArea
                            value={content}
                            readOnly
                            disabled={disabled}
                            onSelect={handleTextSelection}
                            autoSize={{
                                minRows: 8,
                                maxRows: 20,
                            }}
                            className="text-segment-selector-textarea"
                            placeholder="Nội dung bài học sẽ hiển thị tại đây."
                        />
                    </div>

                    <div className="text-segment-selector-selection">
                        <div className="text-segment-selector-selection-header">
                            <Text strong>Đoạn đang chọn</Text>

                            {normalizedSelection && (
                                <Text type="secondary">
                                    {normalizedSelection.text.length} ký tự
                                </Text>
                            )}
                        </div>

                        {normalizedSelection ? (
                            <div className="text-segment-selector-selection-preview">
                                <Paragraph className="text-segment-selector-preview-text">
                                    {normalizedSelection.text}
                                </Paragraph>

                                <Text type="secondary">
                                    Vị trí ký tự{" "}
                                    {normalizedSelection.startOffset + 1}
                                    {" – "}
                                    {normalizedSelection.endOffset}
                                </Text>
                            </div>
                        ) : (
                            <div className="text-segment-selector-empty-selection">
                                <Text type="secondary">
                                    Chưa có đoạn văn bản nào được bôi đen.
                                </Text>
                            </div>
                        )}

                        <div className="text-segment-selector-add-action">
                            <Button
                                type="primary"
                                icon={<PlusOutlined />}
                                disabled={
                                    disabled ||
                                    !normalizedSelection
                                }
                                onClick={handleAddSegment}
                            >
                                Thêm đoạn đã chọn
                            </Button>
                        </div>
                    </div>
                </>
            )}

            <Divider />

            <div className="text-segment-selector-list-header">
                <div>
                    <Text strong>Danh sách đoạn cần kiểm tra</Text>

                    <div className="text-segment-selector-summary">
                        <Text type="secondary">
                            Đã chọn {value.length} đoạn, tổng cộng{" "}
                            {totalCharacters.toLocaleString("vi-VN")} ký tự
                        </Text>
                    </div>
                </div>

                <Button
                    danger
                    type="text"
                    icon={<DeleteOutlined />}
                    disabled={disabled || value.length === 0}
                    onClick={handleClearSegments}
                >
                    Xóa tất cả
                </Button>
            </div>

            <List
                className="text-segment-selector-list"
                dataSource={value}
                rowKey="id"
                locale={{
                    emptyText: (
                        <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description="Chưa có đoạn nào được chọn."
                        />
                    ),
                }}
                renderItem={(segment, index) => (
                    <List.Item
                        key={segment.id}
                        className="text-segment-selector-list-item"
                        actions={[
                            <Button
                                key={`remove-${segment.id}`}
                                type="text"
                                danger
                                icon={<DeleteOutlined />}
                                disabled={disabled}
                                onClick={() =>
                                    handleRemoveSegment(segment.id)
                                }
                            >
                                Xóa
                            </Button>,
                        ]}
                    >
                        <div className="text-segment-selector-item-content">
                            <Space
                                wrap
                                className="text-segment-selector-item-meta"
                            >
                                <Tag color="blue">
                                    Đoạn {index + 1}
                                </Tag>

                                <Text type="secondary">
                                    Ký tự {segment.startOffset + 1}
                                    {" – "}
                                    {segment.endOffset}
                                </Text>

                                <Text type="secondary">
                                    {segment.text.length} ký tự
                                </Text>
                            </Space>

                            <Paragraph
                                className="text-segment-selector-item-text"
                                ellipsis={{
                                    rows: 4,
                                    expandable: true,
                                    symbol: "Xem thêm",
                                }}
                            >
                                {segment.text}
                            </Paragraph>
                        </div>
                    </List.Item>
                )}
            />
        </div>
    );
}
