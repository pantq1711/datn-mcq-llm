import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Alert, Button, Card, Descriptions, Modal, Space, Spin, Table, Tabs, Tag, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { ArrowLeftOutlined, DeleteOutlined, LockOutlined, ReloadOutlined } from "@ant-design/icons";

import AssignmentLinkCard from "../components/AssignmentLinkCard";
import ItemStatsTab from "../components/ItemStatsTab";
import { AssignmentStatusTag } from "../components/assignmentLabels";
import { attemptStateLabel, classSummary } from "../utils/labels";
import { bulkDeleteAttempts, closeAssignment, deleteAssignment, getAssignment } from "../services/assignmentApi";
import type { Assignment, AttemptRow, PartCode } from "../types/assignment.types";
import { formatPoints, formatServerTimeShort, formatServerTime } from "../utils/timeFormat";
import { showApiError } from "../../../utils/apiError";

import "./assignment.css";

const { Title, Text } = Typography;

const REFRESH_MS = 15000;

function partCell(row: AttemptRow, part: PartCode) {
    const p = row.parts?.find((x) => x.part === part);
    if (!p) return <Text type="secondary">—</Text>;
    return (
        <span>
            {formatPoints(p.score)} <Text type="secondary">({p.correct}/{p.total})</Text>
        </span>
    );
}

export default function AssignmentDetailPage() {
    const navigate = useNavigate();
    const { id: examId = "", assignmentId = "" } = useParams<{ id: string; assignmentId: string }>();

    const [data, setData] = useState<Assignment | null>(null);
    const [loading, setLoading] = useState(true);
    const [notFound, setNotFound] = useState(false);
    const [selected, setSelected] = useState<number[]>([]);
    const [busy, setBusy] = useState(false);
    const [tab, setTab] = useState<"scores" | "items">("scores");

    // Mỗi lần tăng reloadTick là tải lại bài giao; lần làm mới tự động (silent) không báo lỗi để khỏi làm phiền
    const [reloadTick, setReloadTick] = useState(0);
    const silentRef = useRef(false);
    const load = useCallback(() => setReloadTick((t) => t + 1), []);

    useEffect(() => {
        let cancelled = false;
        const silent = silentRef.current;
        silentRef.current = false;
        (async () => {
            try {
                const value = await getAssignment(examId, assignmentId);
                if (cancelled) return;
                setData(value);
                setNotFound(false);
                // Giữ lựa chọn chỉ với các lượt còn tồn tại
                const alive = new Set((value.attempts ?? []).map((a) => a.attemptId));
                setSelected((prev) => prev.filter((id) => alive.has(id)));
            } catch (error) {
                if (cancelled) return;
                if ((error as { response?: { status?: number } })?.response?.status === 404) setNotFound(true);
                else if (!silent) showApiError(error, "Không tải được bài giao.");
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [examId, assignmentId, reloadTick]);

    // Bài còn mở thì làm mới bảng điểm 15 giây một lần (khi tab đang hiện)
    const isOpen = data?.status === "open";
    useEffect(() => {
        if (!isOpen) return;
        const timer = window.setInterval(() => {
            if (document.visibilityState === "visible") {
                silentRef.current = true;
                load();
            }
        }, REFRESH_MS);
        return () => window.clearInterval(timer);
    }, [isOpen, load]);

    const attempts = data?.attempts ?? [];
    const inProgress = attempts.filter((a) => a.state === "in_progress").length;
    const duplicates = attempts.filter((a) => a.duplicate);

    const handleClose = () => {
        if (!data) return;
        const resultNote =
            data.showScore || data.showSolutions
                ? " Học sinh xem được kết quả sau 2 phút kể từ lúc đóng."
                : "";
        Modal.confirm({
            title: "Đóng bài giao?",
            content:
                inProgress > 0
                    ? `Còn ${inProgress} học sinh đang làm. Đóng bây giờ thì các em chưa nộp sẽ không nộp được nữa và không có điểm.${resultNote}`
                    : `Sau khi đóng, học sinh không vào làm được nữa.${resultNote}`,
            okText: "Đóng bài",
            cancelText: "Quay lại",
            okButtonProps: { danger: inProgress > 0 },
            onOk: async () => {
                try {
                    await closeAssignment(examId, assignmentId);
                    message.success("Đã đóng bài giao.");
                    load();
                } catch (error) {
                    showApiError(error, "Chưa đóng được bài giao.");
                }
            },
        });
    };

    const handleDeleteAssignment = () => {
        Modal.confirm({
            title: "Xóa bài giao này?",
            content: "Mọi bài làm và điểm của học sinh trong bài giao sẽ bị xóa và không khôi phục được. Link và mã QR cũng không dùng được nữa.",
            okText: "Xóa bài giao",
            okButtonProps: { danger: true },
            cancelText: "Quay lại",
            onOk: async () => {
                try {
                    await deleteAssignment(examId, assignmentId);
                    message.success("Đã xóa bài giao.");
                    navigate(`/exams/${examId}/assignments`, { replace: true });
                } catch (error) {
                    showApiError(error, "Chưa xóa được bài giao.");
                }
            },
        });
    };

    const handleBulkDelete = () => {
        if (selected.length === 0) return;
        Modal.confirm({
            title: `Xóa ${selected.length} lượt làm đã chọn?`,
            content: "Bài làm của các lượt này bị xóa và không khôi phục được. Học sinh có thể vào làm lại bằng lớp và số thứ tự đó.",
            okText: "Xóa các lượt đã chọn",
            okButtonProps: { danger: true },
            cancelText: "Quay lại",
            onOk: async () => {
                setBusy(true);
                try {
                    const deleted = await bulkDeleteAttempts(examId, assignmentId, selected);
                    message.success(`Đã xóa ${deleted} lượt làm.`);
                    setSelected([]);
                    load();
                } catch (error) {
                    showApiError(error, "Chưa xóa được các lượt đã chọn.");
                } finally {
                    setBusy(false);
                }
            },
        });
    };

    const columns: ColumnsType<AttemptRow> = useMemo(
        () => [
            { title: "Lớp", dataIndex: "classLabel", key: "classLabel", width: 90 },
            {
                title: "STT",
                key: "seatNo",
                width: 130,
                render: (_, row) => (
                    <Space size={4}>
                        <span>{row.seatNo}</span>
                        {row.duplicate && <Tag color="red">Trùng STT</Tag>}
                    </Space>
                ),
            },
            { title: "Trạng thái", key: "state", width: 150, render: (_, row) => attemptStateLabel(row.state) },
            { title: "Phần I", key: "p1", render: (_, row) => partCell(row, "I") },
            { title: "Phần II", key: "p2", render: (_, row) => partCell(row, "II") },
            { title: "Phần III", key: "p3", render: (_, row) => partCell(row, "III") },
            {
                title: "Tổng",
                key: "score",
                render: (_, row) => (row.score === undefined ? "—" : <strong>{formatPoints(row.score)}</strong>),
            },
            {
                title: "Thang 10",
                key: "score10",
                render: (_, row) => (row.score10 === undefined ? "—" : <strong>{formatPoints(row.score10)}</strong>),
            },
            {
                title: "Nộp lúc",
                key: "submittedAt",
                render: (_, row) => (row.submittedAt ? formatServerTimeShort(row.submittedAt) : "—"),
            },
        ],
        []
    );

    if (loading && !data) return <Spin description="Đang tải bài giao…" />;

    if (notFound || !data) {
        return (
            <div className="assignment-page">
                <Alert
                    type="warning"
                    showIcon
                    message="Không tìm thấy bài giao này."
                    description="Có thể bài giao đã bị xóa."
                    action={<Button onClick={() => navigate(`/exams/${examId}/assignments`)}>Về danh sách bài giao</Button>}
                />
            </div>
        );
    }

    return (
        <div className="assignment-page">
            <Button
                type="text"
                icon={<ArrowLeftOutlined />}
                className="assignment-back"
                onClick={() => navigate(`/exams/${examId}/assignments`)}
            >
                Các bài đã giao
            </Button>

            <div className="assignment-header">
                <div className="assignment-header-main">
                    <Title level={2} className="assignment-title">
                        {data.title}
                    </Title>
                    <Space size={8} wrap>
                        <AssignmentStatusTag status={data.status} />
                        <Text type="secondary">
                            Đã vào {data.startedCount} · đã nộp {data.submittedCount}
                        </Text>
                    </Space>
                </div>
                <Space wrap>
                    {data.status === "open" && (
                        <Button icon={<LockOutlined />} onClick={handleClose}>
                            Đóng bài
                        </Button>
                    )}
                    <Button danger icon={<DeleteOutlined />} onClick={handleDeleteAssignment}>
                        Xóa bài giao
                    </Button>
                </Space>
            </div>

            <div className="assignment-grid">
                <Card title="Link cho học sinh">
                    <AssignmentLinkCard publicCode={data.publicCode} fileName={data.title} />
                </Card>
                <Card title="Thông tin bài giao">
                    <Descriptions column={1} size="small">
                        <Descriptions.Item label="Các lớp">{classSummary(data.classes)}</Descriptions.Item>
                        <Descriptions.Item label="Số câu">{data.questionCount} câu, tối đa {formatPoints(data.maxScore)} điểm</Descriptions.Item>
                        <Descriptions.Item label="Thời gian làm">{data.durationMinutes} phút</Descriptions.Item>
                        <Descriptions.Item label="Hạn nộp">{formatServerTime(data.closesAt)}</Descriptions.Item>
                        {data.closedAt && <Descriptions.Item label="Đã đóng lúc">{formatServerTime(data.closedAt)}</Descriptions.Item>}
                        <Descriptions.Item label="Hiện điểm sau khi đóng">{data.showScore ? "Có" : "Không"}</Descriptions.Item>
                        <Descriptions.Item label="Hiện đáp án và lời giải">{data.showSolutions ? "Có" : "Không"}</Descriptions.Item>
                    </Descriptions>
                </Card>
            </div>

            <Tabs
                activeKey={tab}
                onChange={(key) => setTab(key === "items" ? "items" : "scores")}
                items={[
                    { key: "scores", label: "Bảng điểm" },
                    { key: "items", label: "Phân tích câu" },
                ]}
            />

            {tab === "items" && (
                <Card className="assignment-scores" title="Phân tích câu">
                    <ItemStatsTab examId={examId} assignmentId={assignmentId} status={data.status} />
                </Card>
            )}

            {tab === "scores" && (
                <Card
                    className="assignment-scores"
                    title="Bảng điểm"
                    extra={
                        <Button icon={<ReloadOutlined />} onClick={load}>
                            Làm mới
                        </Button>
                    }
                >
                    {data.duplicateCount > 0 && (
                        <Alert
                            type="warning"
                            showIcon
                            style={{ marginBottom: 12 }}
                            message={`Có ${data.duplicateCount} lượt trùng số thứ tự`}
                            description="Hai học sinh nhập cùng lớp và số thứ tự thì cả hai lượt đều được giữ và đánh dấu “Trùng STT”. Thầy/cô xem lại rồi xóa lượt nhập nhầm; lượt còn lại sẽ hết dấu trùng."
                            action={
                                <Button size="small" onClick={() => setSelected(duplicates.map((a) => a.attemptId))}>
                                    Chọn các lượt trùng
                                </Button>
                            }
                        />
                    )}

                    <div className="assignment-bulk">
                        <div>
                            <Text type="secondary">
                                {selected.length > 0
                                    ? `Đã chọn ${selected.length} lượt`
                                    : "Tích chọn các lượt cần xóa (ví dụ lượt nhập nhầm số)."}
                            </Text>
                            {data.status === "closed" && (
                                <div>
                                    <Text type="secondary">Xóa lượt sẽ tính lại phần Phân tích câu.</Text>
                                </div>
                            )}
                        </div>
                        <Button danger disabled={selected.length === 0} loading={busy} onClick={handleBulkDelete}>
                            Xóa các lượt đã chọn
                        </Button>
                    </div>

                    <Table<AttemptRow>
                        rowKey="attemptId"
                        size="small"
                        columns={columns}
                        dataSource={attempts}
                        pagination={attempts.length > 50 ? { pageSize: 50, showSizeChanger: false } : false}
                        scroll={{ x: 760 }}
                        rowClassName={(row) => (row.duplicate ? "assignment-row-duplicate" : "")}
                        rowSelection={{
                            selectedRowKeys: selected,
                            onChange: (keys) => setSelected(keys.map(Number)),
                        }}
                        locale={{ emptyText: "Chưa có học sinh nào vào làm." }}
                    />
                    <p className="assignment-hint">
                        Bảng điểm không có tên học sinh: thầy/cô đối chiếu theo lớp và số thứ tự. Ô điểm phần ghi điểm và (số câu đúng/tổng);
                        phần II đếm theo ý.
                    </p>
                </Card>
            )}
        </div>
    );
}
