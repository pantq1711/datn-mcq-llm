import { useRef } from "react";
import { Button, Input, QRCode, Space, message } from "antd";
import { CopyOutlined, DownloadOutlined } from "@ant-design/icons";

import { buildStudentLink } from "../services/assignmentApi";
import type { IdentityMode } from "../types/assignment.types";
import { isNameMode } from "../utils/identity";

interface AssignmentLinkCardProps {
    publicCode: string;
    /** Tên file khi tải ảnh QR, không kèm đuôi. */
    fileName?: string;
    /** Chế độ của bài giao (quyết định 25/11): chọn câu nhắc học sinh nhập gì; thiếu là số thứ tự. */
    identityMode?: IdentityMode;
}

/** Link học sinh làm bài và mã QR để chiếu lên bảng. Mã QR dùng component có sẵn của antd. */
export default function AssignmentLinkCard({ publicCode, fileName, identityMode }: AssignmentLinkCardProps) {
    const link = buildStudentLink(publicCode);
    const qrRef = useRef<HTMLDivElement>(null);

    const copy = async () => {
        try {
            await navigator.clipboard.writeText(link);
            message.success("Đã sao chép link.");
        } catch {
            // Trình duyệt chặn clipboard (http, không có quyền): chọn sẵn chữ để thầy/cô tự sao chép
            message.warning("Chưa sao chép được. Thầy/cô bôi đen link rồi nhấn Ctrl+C.");
        }
    };

    const downloadQr = () => {
        const canvas = qrRef.current?.querySelector("canvas");
        if (!canvas) {
            message.error("Chưa tải được ảnh mã QR.");
            return;
        }
        const a = document.createElement("a");
        a.href = canvas.toDataURL("image/png");
        a.download = `${fileName || `bai-giao-${publicCode}`}.png`;
        a.click();
    };

    return (
        <div className="assignment-link-card">
            <Space.Compact style={{ width: "100%" }}>
                <Input
                    readOnly
                    value={link}
                    aria-label="Link cho học sinh"
                    onFocus={(e) => e.target.select()}
                />
                <Button icon={<CopyOutlined />} onClick={() => void copy()}>
                    Sao chép
                </Button>
            </Space.Compact>
            <div className="assignment-link-card-qr" ref={qrRef}>
                <QRCode value={link} size={220} type="canvas" bordered={false} errorLevel="M" />
                <Button icon={<DownloadOutlined />} onClick={downloadQr}>
                    Tải ảnh mã QR
                </Button>
            </div>
            <p className="assignment-hint">
                Học sinh quét mã QR hoặc mở link bằng điện thoại. Không cần đăng nhập,{" "}
                {isNameMode(identityMode) ? "chỉ chọn lớp và nhập họ tên." : "không nhập họ tên."}
            </p>
        </div>
    );
}
