import { Input, Modal, Typography, message } from "antd";
import { useEffect, useState } from "react";

import { renameExam } from "../services/examApi";
import { MAX_NAME_LENGTH } from "../utils/examName";

const { Text } = Typography;

export interface RenameExamTarget {
    examId: number | string;
    title: string;
}

interface RenameExamModalProps {
    /** Đề cần đổi tên; null thì hộp đóng. */
    target: RenameExamTarget | null;
    onCancel: () => void;
    /** Đổi tên xong: tên đã lưu (backend đã trim). */
    onRenamed: (title: string) => void;
}

/**
 * Hộp đổi tên đề (đợt 3), dùng ở danh sách đề và đầu trang đề. Gọi PATCH /api/exams/{id}. Bài giao đã có giữ tên lúc giao;
 * xuất Word lần sau dùng tên mới.
 */
export function RenameExamModal({ target, onCancel, onRenamed }: RenameExamModalProps) {
    const [name, setName] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [touched, setTouched] = useState(false);

    useEffect(() => {
        if (target) {
            setName(target.title);
            setTouched(false);
        }
    }, [target]);

    const empty = name.trim() === "";

    const handleOk = async () => {
        if (!target || submitting) return;
        if (empty) {
            setTouched(true);
            return;
        }
        if (name.trim() === target.title) {
            onCancel();
            return;
        }
        setSubmitting(true);
        try {
            const result = await renameExam(target.examId, name.trim());
            message.success("Đã đổi tên đề.");
            onRenamed(result.title);
        } catch (err) {
            message.error(err instanceof Error ? err.message : "Không đổi được tên đề");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Modal
            open={target !== null}
            title="Đổi tên đề"
            okText="Lưu tên"
            cancelText="Hủy"
            onOk={handleOk}
            onCancel={onCancel}
            confirmLoading={submitting}
            destroyOnHidden
        >
            <label htmlFor="rename-exam-name">Tên đề</label>
            <Input
                id="rename-exam-name"
                value={name}
                maxLength={MAX_NAME_LENGTH}
                status={touched && empty ? "error" : undefined}
                onChange={(e) => setName(e.target.value)}
                onPressEnter={handleOk}
                autoFocus
                style={{ marginTop: 8 }}
            />
            {touched && empty && <Text type="danger">Vui lòng nhập tên đề.</Text>}
            <Text type="secondary" style={{ display: "block", marginTop: 8 }}>
                Bài đã giao cho học sinh vẫn giữ tên cũ. File Word xuất từ lần sau dùng tên mới.
            </Text>
        </Modal>
    );
}
