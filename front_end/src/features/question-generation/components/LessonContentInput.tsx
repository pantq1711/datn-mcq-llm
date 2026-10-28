
import { useState } from "react";

import {
    Alert,
    Button,
    Input,
    Space,
    Tabs,
    Typography,
    Upload,
    message,
} from "antd";

import type {
    RcFile,
    UploadFile,
    UploadProps,
} from "antd/es/upload/interface";

import {
    DeleteOutlined,
    FileTextOutlined,
    InboxOutlined,
} from "@ant-design/icons";

import "./LessonContentInput.css";

const { Text } = Typography;
const { TextArea } = Input;
const { Dragger } = Upload;

const ACCEPTED_FILE_TYPES =
    ".doc,.docx,.pdf,.png,.jpg,.jpeg,.webp,.tif,.tiff,.txt";

const ALLOWED_EXTENSIONS = new Set([
    ".doc",
    ".docx",
    ".pdf",
    ".png",
    ".jpg",
    ".jpeg",
    ".webp",
    ".tif",
    ".tiff",
    ".txt",
]);

type InputMode = "text" | "file";

export interface LessonContentInputProps {
    value: string;
    onChange: (content: string) => void;
    onFileUpload?: (file: File) => Promise<string>;
    disabled?: boolean;
    maxFileSizeMB?: number;
}

function getFileExtension(fileName: string): string {
    const lastDotIndex = fileName.lastIndexOf(".");

    if (lastDotIndex === -1) {
        return "";
    }

    return fileName.slice(lastDotIndex).toLowerCase();
}

export default function LessonContentInput({
    value,
    onChange,
    onFileUpload,
    disabled = false,
    maxFileSizeMB = 20,
}: LessonContentInputProps) {
    const [activeTab, setActiveTab] = useState<InputMode>("text");
    const [fileList, setFileList] = useState<UploadFile[]>([]);
    const [isProcessing, setIsProcessing] = useState(false);

    const wordCount = value.trim()
        ? value.trim().split(/\s+/).length
        : 0;

    const handleTextChange = (
        event: React.ChangeEvent<HTMLTextAreaElement>,
    ) => {
        onChange(event.target.value);
    };

    const handleClearContent = () => {
        onChange("");
        setFileList([]);
    };

    const processFile = async (file: RcFile) => {
        setIsProcessing(true);

        setFileList([
            {
                uid: file.uid,
                name: file.name,
                status: "uploading",
            },
        ]);

        try {
            let extractedText: string;

            if (onFileUpload) {
                // Gọi callback để upload và trích xuất nội dung tài liệu.
                extractedText = await onFileUpload(file);
            } else {
                // Chỉ có thể đọc trực tiếp tệp văn bản thuần.
                const extension = getFileExtension(file.name);

                if (
                    extension === ".txt" ||
                    file.type === "text/plain"
                ) {
                    extractedText = await file.text();
                } else {
                    throw new Error(
                        "Chưa kết nối bộ xử lý tài liệu. " +
                        "Cần truyền prop onFileUpload để trích xuất " +
                        "nội dung Word, PDF hoặc ảnh.",
                    );
                }
            }

            if (!extractedText.trim()) {
                throw new Error(
                    "Không tìm thấy nội dung văn bản trong tài liệu.",
                );
            }

            onChange(extractedText);

            setFileList([
                {
                    uid: file.uid,
                    name: file.name,
                    status: "done",
                },
            ]);

            setActiveTab("text");

            message.success("Đã trích xuất nội dung tài liệu.");
        } catch (error) {
            const errorMessage =
                error instanceof Error
                    ? error.message
                    : "Không thể xử lý tài liệu. Vui lòng thử lại.";

            setFileList([
                {
                    uid: file.uid,
                    name: file.name,
                    status: "error",
                },
            ]);

            message.error(errorMessage);
        } finally {
            setIsProcessing(false);
        }
    };

    const handleBeforeUpload: UploadProps["beforeUpload"] = (file) => {
        const extension = getFileExtension(file.name);

        if (!ALLOWED_EXTENSIONS.has(extension)) {
            message.error(
                "Định dạng tệp không được hỗ trợ. " +
                "Vui lòng chọn Word, PDF, ảnh hoặc TXT.",
            );

            return Upload.LIST_IGNORE;
        }

        const maxFileSize = maxFileSizeMB * 1024 * 1024;

        if (file.size > maxFileSize) {
            message.error(
                `Dung lượng tệp không được vượt quá ${maxFileSizeMB} MB.`,
            );

            return Upload.LIST_IGNORE;
        }

        void processFile(file as RcFile);

        // Không để Upload tự gửi tệp. Việc xử lý được thực hiện
        // thông qua callback onFileUpload của component.
        return Upload.LIST_IGNORE;
    };

    const handleRemoveFile: UploadProps["onRemove"] = () => {
        setFileList([]);
        onChange("");

        return true;
    };

    return (
        <div className="lesson-content-input">
            <Tabs
                activeKey={activeTab}
                onChange={(key) => setActiveTab(key as InputMode)}
                items={[
                    {
                        key: "text",
                        label: (
                            <Space size={8}>
                                <FileTextOutlined />
                                <span>Nhập văn bản</span>
                            </Space>
                        ),
                        children: (
                            <div className="lesson-content-input-text">
                                <TextArea
                                    value={value}
                                    onChange={handleTextChange}
                                    placeholder={
                                        "Nhập hoặc dán nội dung bài học tại đây...\n" +
                                        "Bạn có thể nhập phần lý thuyết, định nghĩa, " +
                                        "công thức hoặc các nội dung kiến thức cần kiểm tra."
                                    }
                                    autoSize={{
                                        minRows: 8,
                                        maxRows: 18,
                                    }}
                                    showCount
                                    disabled={disabled || isProcessing}
                                    className="lesson-content-input-textarea"
                                />

                                <div className="lesson-content-input-footer">
                                    <Text type="secondary">
                                        {wordCount.toLocaleString("vi-VN")} từ
                                    </Text>

                                    <Button
                                        type="text"
                                        danger
                                        icon={<DeleteOutlined />}
                                        disabled={
                                            disabled ||
                                            isProcessing ||
                                            !value
                                        }
                                        onClick={handleClearContent}
                                    >
                                        Xóa nội dung
                                    </Button>
                                </div>
                            </div>
                        ),
                    },
                    {
                        key: "file",
                        label: (
                            <Space size={8}>
                                <InboxOutlined />
                                <span>Tải tài liệu</span>
                            </Space>
                        ),
                        children: (
                            <div className="lesson-content-input-upload">
                                <Dragger
                                    accept={ACCEPTED_FILE_TYPES}
                                    beforeUpload={handleBeforeUpload}
                                    fileList={fileList}
                                    maxCount={1}
                                    multiple={false}
                                    disabled={disabled || isProcessing}
                                    onRemove={handleRemoveFile}
                                    showUploadList={{
                                        showPreviewIcon: false,
                                        showDownloadIcon: false,
                                        showRemoveIcon: true,
                                    }}
                                >
                                    <p className="ant-upload-drag-icon">
                                        <InboxOutlined />
                                    </p>

                                    <p className="ant-upload-text">
                                        Nhấn để chọn tệp hoặc kéo thả tệp vào đây
                                    </p>

                                    <p className="ant-upload-hint">
                                        Hỗ trợ Word (.doc, .docx), PDF,
                                        ảnh (.png, .jpg, .jpeg, .webp, .tif)
                                        và văn bản (.txt).
                                    </p>

                                    <p className="ant-upload-hint">
                                        Dung lượng tối đa: {maxFileSizeMB} MB.
                                    </p>
                                </Dragger>

                                {isProcessing && (
                                    <Alert
                                        className="lesson-content-input-alert"
                                        type="info"
                                        showIcon
                                        message="Đang xử lý tài liệu"
                                        description="Vui lòng chờ hệ thống trích xuất nội dung văn bản."
                                    />
                                )}

                                {!onFileUpload && (
                                    <Alert
                                        className="lesson-content-input-alert"
                                        type="warning"
                                        showIcon
                                        message="Chưa kết nối bộ xử lý tài liệu"
                                        description={
                                            "Hiện tại chỉ có thể đọc trực tiếp tệp TXT. " +
                                            "Để trích xuất Word, PDF và ảnh, cần kết nối " +
                                            "callback onFileUpload với API xử lý tài liệu."
                                        }
                                    />
                                )}

                                {onFileUpload && (
                                    <Alert
                                        className="lesson-content-input-alert"
                                        type="info"
                                        showIcon
                                        message="Kiểm tra nội dung sau khi trích xuất"
                                        description={
                                            "Sau khi xử lý thành công, nội dung văn bản " +
                                            "sẽ hiển thị ở tab Nhập văn bản để bạn kiểm tra."
                                        }
                                    />
                                )}
                            </div>
                        ),
                    },
                ]}
            />
        </div>
    );
}
