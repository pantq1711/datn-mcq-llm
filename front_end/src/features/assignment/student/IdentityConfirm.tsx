import { Alert, Button } from "antd";

interface IdentityConfirmProps {
    classLabel: string;
    /** Chế độ số thứ tự. */
    seatNo?: number;
    /** Chế độ họ tên (quyết định 25/11); có thì hỏi theo tên thay cho số. */
    studentName?: string;
    /** Đã có lượt khác cùng lớp + số / tên (khi mở lại lượt đã tạo). */
    duplicateSeat?: boolean;
    /**
     * Máy chủ vừa báo trùng TRƯỚC khi tạo lượt (409 ATTEMPT_IDENTITY_TAKEN): thay hai nút thường bằng "Nhập lại" và "Đúng là
     * em, vẫn làm". Đồng hồ chưa chạy.
     */
    taken?: boolean;
    onConfirmDuplicate?: () => void;
    onRetype?: () => void;
    /** Chữ thêm dưới câu hỏi, ví dụ "Bấm Bắt đầu là đồng hồ chạy ngay". */
    note?: string;
    confirmText: string;
    rejectText: string;
    busy?: boolean;
    onConfirm: () => void;
    onReject: () => void;
}

/**
 * Màn xác nhận danh tính (AN-06): "Em là lớp X, số Y — đúng không?" hoặc (25/11) "Em là <tên>, lớp X — đúng không?". Hiện
 * trước khi bắt đầu và mỗi khi mở lại bài, để em dùng máy chung không vô tình làm tiếp hoặc xem kết quả của bạn trước.
 */
export default function IdentityConfirm({
    classLabel,
    seatNo,
    studentName,
    duplicateSeat,
    taken,
    onConfirmDuplicate,
    onRetype,
    note,
    confirmText,
    rejectText,
    busy,
    onConfirm,
    onReject,
}: IdentityConfirmProps) {
    const byName = studentName !== undefined;
    const heading = byName
        ? `Em là ${studentName}, lớp ${classLabel} — đúng không?`
        : `Em là lớp ${classLabel}, số ${seatNo} — đúng không?`;
    return (
        <section className="student-card" aria-labelledby="identity-confirm-title">
            <h1 id="identity-confirm-title" className="student-heading">
                {heading}
            </h1>
            {taken ? (
                <>
                    <Alert
                        type="warning"
                        showIcon
                        message={byName ? "Đã có bạn vào làm với tên này." : "Đã có bạn vào làm với số này."}
                        description={
                            byName
                                ? "Nếu em gõ nhầm, bấm Nhập lại. Nếu đúng là em (em vừa đổi máy, hoặc lớp có bạn trùng tên), bấm Đúng là em, vẫn làm; thầy cô sẽ thấy hai lượt trùng tên."
                                : "Nếu em nhập nhầm, bấm Nhập lại. Nếu đúng là em (em vừa đổi máy), bấm Đúng là em, vẫn làm; thầy cô sẽ thấy hai lượt trùng số."
                        }
                        style={{ marginBottom: 16 }}
                    />
                    <div className="student-actions">
                        <Button type="primary" size="large" block disabled={busy} onClick={onRetype}>
                            Nhập lại
                        </Button>
                        <Button size="large" block loading={busy} onClick={onConfirmDuplicate}>
                            Đúng là em, vẫn làm
                        </Button>
                    </div>
                </>
            ) : (
                <>
                    {note && <p className="student-muted">{note}</p>}
                    {duplicateSeat && (
                        <Alert
                            type="warning"
                            showIcon
                            message={byName ? "Đã có bạn khác vào làm với lớp và tên này." : "Đã có bạn khác vào làm với lớp và số này."}
                            description={
                                byName
                                    ? "Nếu em gõ nhầm tên, hãy bấm không phải em rồi nhập lại. Nếu đúng là em, em vẫn làm bình thường và báo thầy cô."
                                    : "Nếu em nhập nhầm số thứ tự, hãy bấm không phải em rồi nhập lại. Nếu đúng là em, em vẫn làm bình thường và báo thầy cô."
                            }
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
                </>
            )}
        </section>
    );
}
