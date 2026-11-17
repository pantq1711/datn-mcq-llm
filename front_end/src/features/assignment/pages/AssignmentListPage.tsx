import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Alert, Button, Modal, Space, Table, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { ArrowLeftOutlined, PlusOutlined } from "@ant-design/icons";

import AssignExamModal from "../components/AssignExamModal";
import { AssignmentStatusTag } from "../components/assignmentLabels";
import { closeAssignment, deleteAssignment, listAssignments } from "../services/assignmentApi";
import type { Assignment } from "../types/assignment.types";
import { classSummary } from "../utils/labels";
import { formatServerTimeShort } from "../utils/timeFormat";
import { showApiError } from "../../../utils/apiError";

import "./assignment.css";

const { Title } = Typography;

/** Các bài đã giao của một đề. Cũng là nơi thầy/cô được dẫn tới khi xóa đề bị chặn vì còn bài giao. */
export default function AssignmentListPage() {
    const navigate = useNavigate();
    const { id: examId = "" } = useParams<{ id: string }>();
    const [items, setItems] = useState<Assignment[]>([]);
    const [loading, setLoading] = useState(true);
    const [assignOpen, setAssignOpen] = useState(false);

    // Mỗi lần tăng reloadTick là tải lại danh sách
    const [reloadTick, setReloadTick] = useState(0);
    const load = useCallback(() => setReloadTick((t) => t + 1), []);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                const list = await listAssignments(examId);
                if (!cancelled) setItems(list);
            } catch (error) {
                if (!cancelled) showApiError(error, "Không tải được danh sách bài giao.");
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [examId, reloadTick]);

    const confirmClose = (row: Assignment) => {
        Modal.confirm({
            title: "Đóng bài giao?",
            content: "Sau khi đóng, học sinh không vào làm được nữa. Học sinh đang làm mà chưa nộp sẽ không nộp được.",
            okText: "Đóng bài",
            cancelText: "Quay lại",
            onOk: async () => {
                try {
                    await closeAssignment(examId, row.assignmentId);
                    message.success("Đã đóng bài giao.");
                    load();
                } catch (error) {
                    showApiError(error, "Chưa đóng được bài giao.");
                }
            },
        });
    };

    const confirmDelete = (row: Assignment) => {
        Modal.confirm({
            title: "Xóa bài giao này?",
            content: "Mọi bài làm và điểm của học sinh trong bài giao sẽ bị xóa và không khôi phục được.",
            okText: "Xóa bài giao",
            okButtonProps: { danger: true },
            cancelText: "Quay lại",
            onOk: async () => {
                try {
                    await deleteAssignment(examId, row.assignmentId);
                    message.success("Đã xóa bài giao.");
                    load();
                } catch (error) {
                    showApiError(error, "Chưa xóa được bài giao.");
                }
            },
        });
    };

    const columns: ColumnsType<Assignment> = [
        {
            title: "Giao lúc",
            key: "createdAt",
            render: (_, row) => formatServerTimeShort(row.createdAt),
        },
        { title: "Các lớp", key: "classes", render: (_, row) => classSummary(row.classes) },
        { title: "Trạng thái", key: "status", render: (_, row) => <AssignmentStatusTag status={row.status} /> },
        {
            title: "Đã vào / đã nộp",
            key: "counts",
            render: (_, row) => `${row.startedCount} / ${row.submittedCount}`,
        },
        { title: "Hạn nộp", key: "closesAt", render: (_, row) => formatServerTimeShort(row.closesAt) },
        {
            title: "",
            key: "actions",
            render: (_, row) => (
                <Space wrap>
                    <Button type="primary" onClick={() => navigate(`/exams/${examId}/assignments/${row.assignmentId}`)}>
                        Mở
                    </Button>
                    {row.status === "open" && <Button onClick={() => confirmClose(row)}>Đóng bài</Button>}
                    <Button danger onClick={() => confirmDelete(row)}>
                        Xóa
                    </Button>
                </Space>
            ),
        },
    ];

    return (
        <div className="assignment-page">
            <Button type="text" icon={<ArrowLeftOutlined />} className="assignment-back" onClick={() => navigate(`/exams/${examId}`)}>
                Quay lại đề
            </Button>
            <div className="assignment-header">
                <Title level={2} className="assignment-title">
                    Các bài đã giao
                </Title>
                <Button type="primary" icon={<PlusOutlined />} onClick={() => setAssignOpen(true)}>
                    Giao bài online
                </Button>
            </div>

            {items.length > 0 && (
                <Alert
                    type="info"
                    showIcon
                    style={{ marginBottom: 16 }}
                    message="Muốn xóa đề này, thầy/cô cần xóa hết các bài giao ở đây trước."
                />
            )}

            <Table<Assignment>
                rowKey="assignmentId"
                loading={loading}
                columns={columns}
                dataSource={items}
                pagination={false}
                scroll={{ x: 640 }}
                locale={{ emptyText: "Đề này chưa được giao bài online." }}
            />

            <AssignExamModal
                open={assignOpen}
                examId={examId}
                onClose={() => setAssignOpen(false)}
                onCreated={load}
            />
        </div>
    );
}
