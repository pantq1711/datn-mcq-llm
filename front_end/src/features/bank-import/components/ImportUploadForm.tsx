import { useState } from "react";
import { Alert, Button, Input, Select, Space, Typography, Upload } from "antd";
import { FileWordOutlined, InboxOutlined } from "@ant-design/icons";
import type { ClassifiedImportError } from "../utils/importErrors";
import type { ImportLevel, ImportUploadOptions } from "../types/bankImport.types";

const { Dragger } = Upload;
const { Text } = Typography;

/** Khớp spring.servlet.multipart.max-file-size của backend (docs/api mục 10.1). */
export const MAX_IMPORT_FILE_MB = 10;

const GRADE_OPTIONS = [6, 7, 8, 9].map((grade) => ({ value: grade, label: `Lớp ${grade}` }));
const LEVEL_OPTIONS: { value: ImportLevel; label: string }[] = [
    { value: "easy", label: "Nhận biết" },
    { value: "medium", label: "Thông hiểu" },
    { value: "hard", label: "Vận dụng" },
];

interface ImportUploadFormProps {
    /** Đang đọc file: yêu cầu tải lên chưa trả về (có thể mất tới khoảng một phút). */
    uploading: boolean;
    /** Lỗi của lần tải lên trước (nếu có). */
    error: ClassifiedImportError | null;
    onSubmit: (file: File, options: ImportUploadOptions) => void;
}

/** Bước 1: chọn file .docx và tùy chọn lớp, chủ đề, mức mặc định. File được giữ lại khi lỗi để chọn mức rồi đọc lại. */
export default function ImportUploadForm({ uploading, error, onSubmit }: ImportUploadFormProps) {
    const [file, setFile] = useState<File | null>(null);
    const [fileError, setFileError] = useState<string | null>(null);
    const [gradeLevel, setGradeLevel] = useState<number | null>(null);
    const [topic, setTopic] = useState("");
    const [defaultLevel, setDefaultLevel] = useState<ImportLevel | null>(null);

    const pick = (picked: File) => {
        if (!picked.name.toLowerCase().endsWith(".docx")) {
            setFile(null);
            setFileError(`File "${picked.name}" không đúng định dạng: chỉ nhận file Word (.docx).`);
            return;
        }
        if (picked.size > MAX_IMPORT_FILE_MB * 1024 * 1024) {
            setFile(null);
            setFileError(
                `File "${picked.name}" nặng ${(picked.size / 1024 / 1024).toFixed(1)} MB, vượt quá giới hạn ${MAX_IMPORT_FILE_MB} MB.`
            );
            return;
        }
        setFileError(null);
        setFile(picked);
    };

    const submit = () => {
        if (file) onSubmit(file, { gradeLevel, topic, defaultLevel });
    };

    return (
        <div>
            <Dragger
                accept=".docx"
                multiple={false}
                showUploadList={false}
                disabled={uploading}
                beforeUpload={(picked) => {
                    pick(picked);
                    return false; // không tự tải lên: chờ bấm "Đọc file"
                }}
            >
                <p className="ant-upload-drag-icon">
                    <InboxOutlined />
                </p>
                <p className="ant-upload-text">Kéo thả hoặc bấm để chọn file Word (.docx)</p>
                <p className="ant-upload-hint">
                    Tối đa {MAX_IMPORT_FILE_MB} MB và 200 câu mỗi lần. Đặt tên file theo mẫu "KHỐI 8 - CHƯƠNG 1 - BÀI 2 - Tên bài" để hệ
                    thống tự lấy lớp và chủ đề.
                </p>
            </Dragger>

            {fileError && <Alert type="error" showIcon style={{ marginTop: 12 }} title={fileError} />}
            {file && (
                <div style={{ marginTop: 12 }}>
                    <FileWordOutlined /> <Text strong>{file.name}</Text>
                </div>
            )}

            <div className="import-upload-options">
                <div className="import-upload-field">
                    <label htmlFor="import-grade">Lớp (không bắt buộc)</label>
                    <Select
                        id="import-grade"
                        allowClear
                        placeholder="Lấy từ tên file"
                        options={GRADE_OPTIONS}
                        value={gradeLevel}
                        onChange={(value) => setGradeLevel(value ?? null)}
                        disabled={uploading}
                    />
                </div>
                <div className="import-upload-field">
                    <label htmlFor="import-topic">Chủ đề</label>
                    <Input
                        id="import-topic"
                        placeholder="Lấy từ tên bài trong tên file"
                        maxLength={200}
                        value={topic}
                        onChange={(event) => setTopic(event.target.value)}
                        disabled={uploading}
                    />
                </div>
                <div className="import-upload-field">
                    <label htmlFor="import-level">Mức mặc định cho câu chưa ghi mức</label>
                    <Select
                        id="import-level"
                        allowClear
                        placeholder="Chỉ cần khi file có câu chưa ghi [NB]/[TH]/[VD]"
                        options={LEVEL_OPTIONS}
                        value={defaultLevel}
                        onChange={(value) => setDefaultLevel(value ?? null)}
                        status={error?.kind === "level_required" ? "error" : undefined}
                        disabled={uploading}
                    />
                </div>
            </div>

            {error && (
                <Alert
                    type={error.kind === "busy" ? "warning" : "error"}
                    showIcon
                    style={{ marginTop: 12 }}
                    title={error.message}
                    action={
                        error.kind === "busy" ? (
                            <Button size="small" disabled={!file || uploading} onClick={submit}>
                                Thử lại
                            </Button>
                        ) : undefined
                    }
                />
            )}

            <Space orientation="vertical" size={8} style={{ marginTop: 16 }}>
                <Button type="primary" disabled={!file || uploading} loading={uploading} onClick={submit}>
                    {uploading ? "Đang đọc file…" : "Đọc file"}
                </Button>
                {uploading && (
                    <Text type="secondary">Đang đọc file, có thể mất tới một phút nếu có hình. Thầy/cô đừng đóng trang.</Text>
                )}
            </Space>
        </div>
    );
}
