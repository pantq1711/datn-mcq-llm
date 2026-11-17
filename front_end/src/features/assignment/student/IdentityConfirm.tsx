import { Alert, Button } from "antd";

interface IdentityConfirmProps {
    classLabel: string;
    seatNo: number;
    /** Đã có lượt khác cùng lớp + số thứ tự (chỉ biết sau khi lượt được tạo hoặc khi mở lại). */
    duplicateSeat?: boolean;
    /** Chữ thêm dưới câu hỏi, ví dụ "Bấm Bắt đầu là đồng hồ chạy ngay". */
    note?: string;
    confirmText: string;
    rejectText: string;
    busy?: boolean;
    onConfirm: () => void;
    onReject: () => void;
}

/**
 * Màn xác nhận danh tính (AN-06): "Em là lớp X, số Y — đúng không?". Hiện trước khi bắt đầu và mỗi khi mở lại bài,
 * để em dùng máy chung không vô tình làm tiếp hoặc xem kết quả của bạn trước.
 */
export default function IdentityConfirm({
    classLabel,
    seatNo,
    duplicateSeat,
    note,
    confirmText,
    rejectText,
    busy,
    onConfirm,
    onReject,
}: IdentityConfirmProps) {
    return (
        <section className="student-card" aria-labelledby="identity-confirm-title">
            <h1 id="identity-confirm-title" className="student-heading">
                Em là lớp {classLabel}, số {seatNo} — đúng không?
            </h1>
            {note && <p className="student-muted">{note}</p>}
            {duplicateSeat && (
                <Alert
                    type="warning"
                    showIcon
                    message="Đã có bạn khác vào làm với lớp và số này."
                    description="Nếu em nhập nhầm số thứ tự, hãy bấm không phải em rồi nhập lại. Nếu đúng là em, em vẫn làm bình thường và báo thầy cô."
                    style={{ marginBottom: 16 }}
                />
            )}
            <div className="student-actions">
                <Button type="primary" size="large" block loading={busy} onClick={onConfirm}>
                    {confirmText}
                </Button>
                <Button size="large" block disabled={busy} onClick={onReject}>
                    {rejectText}
                </Button>
            </div>
        </section>
    );
}
