import { Input, Modal, Typography, message } from "antd";
import { useEffect, useState } from "react";

import { duplicateExamMatrix } from "../services/examMatrixService";
import type { ExamMatrixDetail } from "../types/examMatrix.types";
import { MAX_NAME_LENGTH, datedName } from "../../exam/utils/examName";
import { getApiErrorMessage } from "../../../utils/apiError";

const { Text } = Typography;

export interface DuplicateMatrixSource {
    id: string | number;
    name: string;
}

interface DuplicateMatrixModalProps {
    /** Ma trận cần sao chép; null thì hộp đóng. */
    source: DuplicateMatrixSource | null;
    onCancel: () => void;
    /** Sao chép xong: bản sao backend trả về. */
    onDuplicated: (copy: ExamMatrixDetail) => void;
}

/**
 * Hộp hỏi tên khi sao chép ma trận (đợt 3, rà soát D2): điền sẵn "<tên gốc> – DD/MM" (thay ngày cũ nếu tên đã có), gửi
 * tên tới POST /api/exam-matrices/{id}/duplicate. Dùng chung cho trang danh sách, trang sửa ma trận và trang tạo đề.
 */
export function DuplicateMatrixModal({ source, onCancel, onDuplicated }: DuplicateMatrixModalProps) {
    const [name, setName] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [touched, setTouched] = useState(false);

    useEffect(() => {
        if (source) {
            setName(datedName(source.name));
            setTouched(false);
        }
    }, [source]);

    const empty = name.trim() === "";

    const handleOk = async () => {
        if (!source || submitting) return;
        if (empty) {
            setTouched(true);
            return;
        }
        setSubmitting(true);
        try {
            const copy = await duplicateExamMatrix(String(source.id), name.trim());
            onDuplicated(copy);
        } catch (err) {
            message.error(getApiErrorMessage(err, "Không sao chép được ma trận"));
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Modal
            open={source !== null}
            title="Sao chép ma trận"
            okText="Sao chép"
            cancelText="Hủy"
            onOk={handleOk}
            onCancel={onCancel}
            confirmLoading={submitting}
            destroyOnHidden
        >
            <label htmlFor="duplicate-matrix-name">Tên ma trận mới</label>
            <Input
                id="duplicate-matrix-name"
                value={name}
                maxLength={MAX_NAME_LENGTH}
                status={touched && empty ? "error" : undefined}
                onChange={(e) => setName(e.target.value)}
                onPressEnter={handleOk}
                autoFocus
                style={{ marginTop: 8 }}
            />
            {touched && empty && <Text type="danger">Vui lòng nhập tên ma trận.</Text>}
            {source && (
                <Text type="secondary" style={{ display: "block", marginTop: 8 }}>
                    Bản sao có cùng các ô với "{source.name}" và sửa được độc lập.
                </Text>
            )}
        </Modal>
    );
}
