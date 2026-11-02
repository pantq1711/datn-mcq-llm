import { useEffect, useMemo, useRef, useState } from "react";
import { Alert, Button, Card, Col, Collapse, Empty, Form, Input, Modal, Row, Select, Space, Spin, Table, Tabs, Tag, Tooltip, Typography, message, } from "antd";
import type { TableColumnsType } from "antd";
import { ArrowLeftOutlined, BookOutlined, BulbOutlined, CopyOutlined, EditOutlined, PaperClipOutlined, PlusOutlined, RocketOutlined, SettingOutlined, TableOutlined, } from "@ant-design/icons";
import { useNavigate, useSearchParams } from "react-router-dom";

import BuildByLessonForm from "../components/BuildByLessonForm";
import MaterialManagerModal from "../../materials/components/MaterialManagerModal";
import type { MaterialDto } from "../../materials/types/material.types";
import type { ExamSubjectOption, BuildResultDto, CellContextInput, BuildExamRequest } from "../types/exam.types";
import type { ExamMatrix, ExamMatrixDetail, ExamMatrixCellPayload } from "../../exam-matrix/types/examMatrix.types";
import { getCatalogSubjects, buildExamByMatrix } from "../services/examApi";
import { describeLevel } from "../../../utils/exam.utils";
import { showApiError } from "../../../utils/apiError";
import { listExamMatrices, getExamMatrix, duplicateExamMatrix, } from "../../exam-matrix/services/examMatrixService";

import "./GenerateExamPage.css";

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

interface CellContextState {
    materialIds: number[];
    materialNames: string[];
    extraInstruction: string;
}

// docs/api mục 7.2: tối đa 5 tài liệu KHÁC NHAU cho cả lần tạo đề (tài liệu chung và tài liệu từng ô, trùng id tính một)
const MAX_MATERIALS_PER_BUILD = 5;

function collectMaterialIds(
    globalIds: number[],
    contexts: Record<number, CellContextState>,
    excludeCellKey?: number
): Set<number> {
    const ids = new Set(globalIds);
    Object.entries(contexts).forEach(([key, ctx]) => {
        if (Number(key) !== excludeCellKey) ctx.materialIds.forEach((id) => ids.add(id));
    });
    return ids;
}

// Cùng nhãn và màu với cột "Dạng câu" của bảng ô ma trận
const QUESTION_TYPE_TAGS: Record<ExamMatrixCellPayload["questionType"], { label: string; color: string }> = {
    mcq: { label: "Trắc nghiệm", color: "geekblue" },
    tf: { label: "Đúng / Sai", color: "cyan" },
    sa: { label: "Trả lời ngắn", color: "purple" },
};

const EMPTY_CELL_CONTEXT: CellContextState = { materialIds: [], materialNames: [], extraInstruction: "" };

function removeMaterialFromContext(ctx: CellContextState, removedId: number): CellContextState {
    const keep = ctx.materialIds.map((id) => id !== removedId);
    return {
        ...ctx,
        materialIds: ctx.materialIds.filter((_, i) => keep[i]),
        materialNames: ctx.materialNames.filter((_, i) => keep[i]),
    };
}

export default function GenerateExamPage() {
    const navigate = useNavigate();
    // Lối tắt từ màn ma trận: /exams/generate?tab=by-matrix&matrixId=...
    const [searchParams, setSearchParams] = useSearchParams();
    const initialMatrixId = searchParams.get("matrixId");

    const [activeTab, setActiveTab] = useState<string>(
        searchParams.get("tab") === "by-matrix" || initialMatrixId ? "by-matrix" : "by-lesson"
    );
    // Giáo viên đã tự bấm đổi tab thì không tự chuyển sang "Theo ma trận" nữa
    const tabPickedRef = useRef(false);
    // Chỉ tải danh sách ma trận một lần lúc mở trang (tránh gọi hai lần ở chế độ dev)
    const matricesRequestedRef = useRef(false);
    const [subjectOptions, setSubjectOptions] = useState<ExamSubjectOption[]>([]);
    const [loadingSubjects, setLoadingSubjects] = useState<boolean>(false);

    // State cho Tab ma trận
    const [matrices, setMatrices] = useState<ExamMatrix[]>([]);
    const [loadingMatrices, setLoadingMatrices] = useState<boolean>(false);
    const [selectedMatrixId, setSelectedMatrixId] = useState<string | null>(initialMatrixId);
    const [matrixDetail, setMatrixDetail] = useState<ExamMatrixDetail | null>(null);
    const [loadingMatrixDetail, setLoadingMatrixDetail] = useState<boolean>(false);
    const [matrixExamTitle, setMatrixExamTitle] = useState<string>("");
    const [buildingMatrixExam, setBuildingMatrixExam] = useState<boolean>(false);
    const [duplicating, setDuplicating] = useState<boolean>(false);

    // Tài liệu chung cho toàn bộ ma trận
    const [globalMaterials, setGlobalMaterials] = useState<MaterialDto[]>([]);
    const [isGlobalMaterialModalOpen, setIsGlobalMaterialModalOpen] = useState(false);

    // Ngữ cảnh riêng cho từng ô ma trận: key là cellId hoặc index
    const [cellContexts, setCellContexts] = useState<Record<number, CellContextState>>({});

    // State cho Modal cấu hình ô cụ thể
    const [activeConfigCell, setActiveConfigCell] = useState<{
        cell: ExamMatrixCellPayload;
        cellKey: number;
    } | null>(null);
    const [isCellMaterialModalOpen, setIsCellMaterialModalOpen] = useState(false);
    // Bản nháp cấu hình của ô đang mở: chỉ ghi vào cellContexts khi bấm Lưu, Hủy thì bỏ
    const [cellDraft, setCellDraft] = useState<CellContextState>(EMPTY_CELL_CONTEXT);

    const openCellConfig = (cell: ExamMatrixCellPayload, cellKey: number) => {
        setCellDraft(cellContexts[cellKey] ?? EMPTY_CELL_CONTEXT);
        setActiveConfigCell({ cell, cellKey });
    };

    const saveCellConfig = () => {
        if (!activeConfigCell) return;
        const key = activeConfigCell.cellKey;
        const isEmpty = cellDraft.materialIds.length === 0 && !cellDraft.extraInstruction.trim();
        const saved = cellContexts[key] ?? EMPTY_CELL_CONTEXT;
        const changed =
            saved.extraInstruction.trim() !== cellDraft.extraInstruction.trim() ||
            [...saved.materialIds].sort().join(",") !== [...cellDraft.materialIds].sort().join(",");
        if (!changed) {
            setActiveConfigCell(null);
            return;
        }
        setCellContexts((prev) => {
            const next = { ...prev };
            if (isEmpty) {
                delete next[key];
            } else {
                next[key] = cellDraft;
            }
            return next;
        });
        message.success("Đã lưu ngữ cảnh riêng cho ô");
        setActiveConfigCell(null);
    };

    // Tài liệu đã bị xóa thì gỡ khỏi tài liệu chung, mọi ô và bản nháp, dù xóa từ modal nào
    const handleMaterialRemoved = (removedId: number) => {
        setGlobalMaterials((prev) => prev.filter((m) => m.id !== removedId));
        setCellContexts((prev) => {
            const next: Record<number, CellContextState> = {};
            Object.entries(prev).forEach(([key, ctx]) => {
                next[Number(key)] = removeMaterialFromContext(ctx, removedId);
            });
            return next;
        });
        setCellDraft((prev) => removeMaterialFromContext(prev, removedId));
    };

    const globalMaterialIds = useMemo(() => globalMaterials.map((m) => m.id), [globalMaterials]);
    // Tổng số tài liệu khác nhau đang dùng trong lần tạo đề
    const usedMaterialCount = useMemo(
        () => collectMaterialIds(globalMaterialIds, cellContexts).size,
        [globalMaterialIds, cellContexts]
    );
    // Id đã tính ở chỗ khác: với modal chung là tài liệu của mọi ô; với modal của ô là tài liệu chung và các ô còn lại
    const reservedForGlobal = useMemo(() => [...collectMaterialIds([], cellContexts)], [cellContexts]);
    const reservedForActiveCell = useMemo(
        () => (activeConfigCell
            ? [...collectMaterialIds(globalMaterialIds, cellContexts, activeConfigCell.cellKey)]
            : []),
        [globalMaterialIds, cellContexts, activeConfigCell]
    );

    // Tham số URL chỉ đọc một lần lúc mở trang, sau đó xóa để URL không lệch với lựa chọn trên giao diện
    useEffect(() => {
        if (searchParams.has("tab") || searchParams.has("matrixId")) {
            setSearchParams({}, { replace: true });
        }
    }, []);

    useEffect(() => {
        const fetchSubjects = async () => {
            setLoadingSubjects(true);
            try {
                const subjects = await getCatalogSubjects();
                const options = subjects.map((s) => ({
                    label: s.name,
                    value: s.id,
                }));
                setSubjectOptions(options);
            } catch (error) {
                console.error("Lỗi khi tải danh sách môn học:", error);
                message.error("Không thể tải danh sách môn học");
            } finally {
                setLoadingSubjects(false);
            }
        };

        fetchSubjects();
    }, []);

    // Tải danh sách ma trận ngay khi mở trang để biết có mở sẵn tab "Theo ma trận" hay không
    useEffect(() => {
        if (matricesRequestedRef.current) return;
        matricesRequestedRef.current = true;
        fetchMatrices();
    }, []);

    const fetchMatrices = async () => {
        setLoadingMatrices(true);
        try {
            const data = await listExamMatrices();
            setMatrices(data);
            // Có ít nhất một ma trận thì mở sẵn tab "Theo ma trận"
            if (data.length > 0 && !tabPickedRef.current) setActiveTab("by-matrix");
            if (!selectedMatrixId) {
                if (data.length > 0) setSelectedMatrixId(String(data[0].id));
            } else if (!data.some((m) => String(m.id) === String(selectedMatrixId))) {
                // Ma trận từ URL đã bị xóa hoặc id sai; có key nên chỉ hiện một thông báo
                message.warning({
                    content: "Không tìm thấy ma trận đã chọn, đã chuyển sang ma trận đầu tiên.",
                    key: "matrix-not-found",
                });
                setSelectedMatrixId(data[0] ? String(data[0].id) : null);
            }
        } catch (err) {
            console.error("Lỗi khi tải ma trận:", err);
            message.error("Không thể tải danh sách ma trận");
        } finally {
            setLoadingMatrices(false);
        }
    };

    // Khi chọn ma trận khác, tải chi tiết ô của ma trận đó
    useEffect(() => {
        if (!selectedMatrixId) {
            setMatrixDetail(null);
            return;
        }

        let isMounted = true;
        setLoadingMatrixDetail(true);

        getExamMatrix(selectedMatrixId)
            .then((detail) => {
                if (isMounted) {
                    setMatrixDetail(detail);
                    setCellContexts({}); // reset cell contexts khi đổi ma trận
                }
            })
            .catch((err) => {
                console.warn("Lỗi tải chi tiết ma trận:", err);
            })
            .finally(() => {
                if (isMounted) setLoadingMatrixDetail(false);
            });

        return () => {
            isMounted = false;
        };
    }, [selectedMatrixId]);

    const handleExamCreated = (result: BuildResultDto) => {
        navigate(`/exams/${result.examId}`);
    };

    const handleDuplicateSelectedMatrix = async () => {
        if (!selectedMatrixId) return;
        setDuplicating(true);
        try {
            const newMatrix = await duplicateExamMatrix(selectedMatrixId);
            message.success(`Đã nhân bản ma trận thành công: ${newMatrix.name}`);
            await fetchMatrices();
            setSelectedMatrixId(String(newMatrix.id));
        } catch (err: any) {
            message.error(err?.response?.data?.error || "Không thể nhân bản ma trận");
        } finally {
            setDuplicating(false);
        }
    };

    const handleBuildByMatrix = async () => {
        if (!selectedMatrixId) {
            message.warning("Vui lòng chọn một ma trận đề thi.");
            return;
        }

        if (usedMaterialCount > MAX_MATERIALS_PER_BUILD) {
            message.error(
                `Đang dùng ${usedMaterialCount} tài liệu khác nhau, tối đa ${MAX_MATERIALS_PER_BUILD}. Hãy bỏ bớt ở tài liệu chung hoặc ở các ô.`
            );
            return;
        }

        // Gom các cell context có cấu hình
        const cellsPayload: CellContextInput[] = Object.entries(cellContexts)
            .filter(([_, ctx]) => ctx.materialIds.length > 0 || ctx.extraInstruction.trim())
            .map(([cellIdStr, ctx]) => ({
                cellId: Number(cellIdStr),
                materialIds: ctx.materialIds.length > 0 ? ctx.materialIds : undefined,
                extraInstruction: ctx.extraInstruction.trim() || undefined,
            }));

        const payload: BuildExamRequest = {
            matrixId: Number(selectedMatrixId),
            title: matrixExamTitle.trim() || undefined,
            materialIds: globalMaterials.length > 0 ? globalMaterials.map((m) => m.id) : undefined,
            cells: cellsPayload.length > 0 ? cellsPayload : undefined,
        };

        setBuildingMatrixExam(true);
        try {
            const result = await buildExamByMatrix(payload);
            message.success(
                `Đã tạo đề (${result.fromBank} câu từ ngân hàng, ${result.jobsQueued} câu đang soạn).`
            );
            navigate(`/exams/${result.examId}`);
        } catch (err: unknown) {
            showApiError(err, "Lỗi khi khởi tạo đề thi từ ma trận.");
        } finally {
            setBuildingMatrixExam(false);
        }
    };

    // id từ URL là chuỗi, id từ API có thể là số: so sánh theo chuỗi
    const selectedMatrix = matrices.find((m) => String(m.id) === String(selectedMatrixId)) || null;

    const matrixSelectOptions = matrices.map((m) => {
        const sub = subjectOptions.find((s) => s.value === m.subjectId);
        return {
            value: String(m.id),
            label: `${m.name} — Khối ${m.gradeLevel}${sub ? ` (${sub.label})` : ""}`,
        };
    });

    // Số câu của ma trận đang chọn theo dạng câu, để hiện dòng tóm tắt cạnh nút Tạo đề
    const matrixSummary = useMemo(() => {
        const counts = { mcq: 0, tf: 0, sa: 0 };
        (matrixDetail?.cells ?? []).forEach((c) => {
            counts[c.questionType] += c.questionCount;
        });
        const parts = [
            counts.mcq > 0 ? `${counts.mcq} trắc nghiệm` : null,
            counts.tf > 0 ? `${counts.tf} đúng/sai` : null,
            counts.sa > 0 ? `${counts.sa} trả lời ngắn` : null,
        ].filter((p): p is string => p !== null);
        return { total: counts.mcq + counts.tf + counts.sa, detail: parts.join(" · ") };
    }, [matrixDetail]);

    // Đã gắn tài liệu hoặc ghi chú riêng cho ô ở mục nâng cao (mục đóng nên nhắc ngay ở tiêu đề)
    const hasAdvancedOptions = Object.keys(cellContexts).length > 0;

    // Cột hiển thị bảng ô ma trận
    const cellColumns: TableColumnsType<ExamMatrixCellPayload & { cellKey: number }> = [
        {
            title: "Phần / Vị trí",
            key: "part",
            width: 100,
            align: "center",
            render: (_, record) => {
                const partLabel =
                    record.part === "I"
                        ? "Phần I"
                        : record.part === "II"
                            ? "Phần II"
                            : "Phần III";
                return <Text>{partLabel}</Text>;
            },
        },
        {
            title: "Dạng câu",
            dataIndex: "questionType",
            key: "questionType",
            width: 130,
            align: "center",
            render: (type: string) => {
                if (type === "tf") return <Text>Đúng / Sai</Text>;
                if (type === "sa") return <Text>Trả lời ngắn</Text>;
                return <Text>Trắc nghiệm</Text>;
            },
        },
        {
            title: "Chủ đề / Nội dung",
            key: "topic",
            render: (_, record) => (
                <div>
                    <Text strong>{record.topic}</Text>
                    {record.content && (
                        <div>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                                {record.content}
                            </Text>
                        </div>
                    )}
                </div>
            ),
        },
        {
            title: "Mức độ",
            key: "difficulty",
            width: 110,
            align: "center",
            render: (_, record) => {
                if (record.questionType === "tf") {
                    return <Text>4 ý a–d</Text>;
                }
                return <Text>{describeLevel(record.difficulty).label}</Text>;
            },
        },
        {
            title: "Số câu",
            dataIndex: "questionCount",
            key: "questionCount",
            width: 80,
            align: "center",
            render: (count: number) => <Text strong>{count}</Text>,
        },
        {
            title: "Gắn tài liệu & ghi chú riêng cho ô",
            key: "context",
            width: 220,
            render: (_, record) => {
                const ctx = cellContexts[record.cellKey];
                const hasMaterials = ctx && ctx.materialIds && ctx.materialIds.length > 0;
                const hasInstruction = ctx && ctx.extraInstruction && ctx.extraInstruction.trim().length > 0;

                return (
                    <Space direction="vertical" size={4} style={{ width: "100%" }}>
                        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                            {hasMaterials && (
                                <Tag color="blue" icon={<PaperClipOutlined />}>
                                    {ctx.materialIds.length} tài liệu
                                </Tag>
                            )}
                            {hasInstruction && (
                                <Tag color="gold" icon={<BulbOutlined />}>
                                    Có ghi chú AI
                                </Tag>
                            )}
                            {!hasMaterials && !hasInstruction && (globalMaterials.length > 0 ? (
                                // Tài liệu hiệu lực của ô = tài liệu chung ∪ tài liệu ô (docs/api mục 7.2)
                                <Tag icon={<PaperClipOutlined />}>Dùng tài liệu chung</Tag>
                            ) : (
                                <Text type="secondary" style={{ fontSize: 12 }}>
                                    Chưa gắn
                                </Text>
                            ))}
                        </div>
                        {/* Ô chưa có id thật thì không gắn được: cellId gửi lên phải thuộc ma trận */}
                        <Tooltip title={record.id === undefined ? "Ô chưa có mã trên máy chủ, không gắn riêng được" : undefined}>
                            <Button
                                size="small"
                                icon={<SettingOutlined />}
                                disabled={record.id === undefined}
                                onClick={() => openCellConfig(record, record.cellKey)}
                            >
                                {hasMaterials || hasInstruction ? "Sửa gắn riêng" : "Gắn cho ô này"}
                            </Button>
                        </Tooltip>
                    </Space>
                );
            },
        },
    ];

    const matrixCellsWithKeys = (matrixDetail?.cells || []).map((c, idx) => ({
        ...c,
        // Số âm cho ô chưa có id để rowKey không trùng id thật; ô đó không gắn ngữ cảnh được
        cellKey: c.id ?? -(idx + 1),
    }));

    return (
        <div className="generate-exam-page">
            <div className="generate-exam-page-header">
                <div className="generate-exam-page-header-top">
                    <Button
                        type="text"
                        icon={<ArrowLeftOutlined />}
                        className="generate-exam-back-button"
                        onClick={() => navigate("/exams")}
                    >
                        Quay lại danh sách đề thi
                    </Button>
                </div>

                <div className="generate-exam-header-content">
                    <Title level={2} className="generate-exam-page-title">
                        Tạo đề thi
                    </Title>
                    <Paragraph type="secondary" className="generate-exam-page-subtitle">
                        Tạo đề thi tự động kết hợp câu hỏi ngân hàng và mô hình AI. Hỗ trợ theo từng bài học hoặc theo ma trận chuẩn.
                    </Paragraph>
                </div>
            </div>

            <div className="generate-exam-tabs-container">
                <Tabs
                    activeKey={activeTab}
                    onChange={(key) => {
                        tabPickedRef.current = true;
                        setActiveTab(key);
                    }}
                    type="card"
                    size="large"
                    items={[
                        {
                            key: "by-lesson",
                            label: (
                                <Space size={8}>
                                    <BookOutlined />
                                    <span>Theo bài học (Kiểm tra thường xuyên, ôn tập)</span>
                                </Space>
                            ),
                            children: (
                                <div className="generate-exam-tab-content">
                                    <BuildByLessonForm
                                        subjects={subjectOptions}
                                        loadingSubjects={loadingSubjects}
                                        onSuccess={handleExamCreated}
                                    />
                                </div>
                            ),
                        },
                        {
                            key: "by-matrix",
                            label: (
                                <Space size={8}>
                                    <TableOutlined />
                                    <span>Theo ma trận (Giữa kỳ, cuối kỳ)</span>
                                </Space>
                            ),
                            children: (
                                <div className="generate-exam-tab-content">
                                    <Card className="generate-exam-matrix-card" bordered={false}>
                                        {loadingMatrices ? (
                                            <div style={{ textAlign: "center", padding: "40px 0" }}>
                                                <Spin tip="Đang tải danh sách ma trận..." />
                                            </div>
                                        ) : matrices.length === 0 ? (
                                            <Empty
                                                description="Bạn chưa có ma trận đề thi nào."
                                                style={{ margin: "40px 0" }}
                                            >
                                                <Space>
                                                    <Button
                                                        type="primary"
                                                        icon={<PlusOutlined />}
                                                        onClick={() => navigate("/exam-matrices/new")}
                                                    >
                                                        Tạo ma trận mới
                                                    </Button>
                                                    <Button
                                                        icon={<CopyOutlined />}
                                                        onClick={() => navigate("/exam-matrices")}
                                                    >
                                                        Xem mẫu ma trận
                                                    </Button>
                                                </Space>
                                            </Empty>
                                        ) : (
                                            <Form layout="vertical">
                                                {/* Chọn và quản lý ma trận */}
                                                <Row gutter={16}>
                                                    <Col xs={24} md={16}>
                                                        <Form.Item
                                                            label="Chọn ma trận đề thi"
                                                            required
                                                            tooltip="Chọn ma trận đã tạo sẵn để tạo đề thi tương ứng"
                                                        >
                                                            <Select
                                                                size="large"
                                                                value={selectedMatrixId}
                                                                onChange={setSelectedMatrixId}
                                                                options={matrixSelectOptions}
                                                                placeholder="Chọn ma trận..."
                                                                showSearch
                                                                filterOption={(input, opt) =>
                                                                    (opt?.label ?? "")
                                                                        .toLowerCase()
                                                                        .includes(input.toLowerCase())
                                                                }
                                                            />
                                                        </Form.Item>
                                                    </Col>
                                                    <Col xs={24} md={8} style={{ display: "flex", alignItems: "flex-end" }}>
                                                        <Form.Item style={{ width: "100%" }}>
                                                            <Space wrap>
                                                                <Button
                                                                    icon={<CopyOutlined />}
                                                                    loading={duplicating}
                                                                    disabled={!selectedMatrixId}
                                                                    onClick={handleDuplicateSelectedMatrix}
                                                                >
                                                                    Nhân bản
                                                                </Button>
                                                                <Button
                                                                    icon={<PlusOutlined />}
                                                                    onClick={() => navigate("/exam-matrices/new")}
                                                                >
                                                                    Tạo mới
                                                                </Button>
                                                            </Space>
                                                        </Form.Item>
                                                    </Col>
                                                </Row>

                                                {/* Tóm tắt ma trận đã chọn và nút Tạo đề ở ngay đầu, không phải cuộn xuống */}
                                                {selectedMatrix && (
                                                    <div className="generate-exam-summary-bar">
                                                        <Text strong className="generate-exam-summary-text">
                                                            {selectedMatrix.name}
                                                            {loadingMatrixDetail
                                                                ? " · đang tải…"
                                                                : ` · ${matrixSummary.total} câu${matrixSummary.detail ? ` (${matrixSummary.detail})` : ""}`}
                                                        </Text>
                                                        <Button
                                                            type="primary"
                                                            size="large"
                                                            icon={<RocketOutlined />}
                                                            loading={buildingMatrixExam}
                                                            disabled={!selectedMatrixId || loadingMatrixDetail}
                                                            onClick={handleBuildByMatrix}
                                                        >
                                                            Tạo đề
                                                        </Button>
                                                    </div>
                                                )}

                                                <Form.Item
                                                    label="Tài liệu áp dụng cho cả ma trận"
                                                    tooltip="Đính kèm tối đa 5 tài liệu dùng chung cho toàn bộ các ô trong đề thi"
                                                    extra="Không bắt buộc — AI sẽ bám theo tài liệu này khi soạn"
                                                    style={{ marginTop: 16 }}
                                                >
                                                    <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                                                        <Button
                                                            icon={<PaperClipOutlined />}
                                                            onClick={() => setIsGlobalMaterialModalOpen(true)}
                                                        >
                                                            {globalMaterials.length > 0 ? "Thay đổi tài liệu" : "Đính kèm tài liệu"}
                                                        </Button>
                                                        {globalMaterials.length > 0 && (
                                                            <Tag color="blue">
                                                                {globalMaterials.length} tài liệu chung
                                                            </Tag>
                                                        )}
                                                        {usedMaterialCount > 0 && (
                                                            <Tooltip title="Tài liệu chung và tài liệu từng ô, trùng nhau tính một">
                                                                <Tag color={usedMaterialCount > MAX_MATERIALS_PER_BUILD ? "error" : "default"}>
                                                                    Đang dùng {usedMaterialCount}/{MAX_MATERIALS_PER_BUILD} tài liệu
                                                                </Tag>
                                                            </Tooltip>
                                                        )}
                                                    </div>
                                                </Form.Item>

                                                {globalMaterials.length > 0 && (
                                                    <div style={{ marginBottom: 16, padding: "8px 12px", background: "#f6faff", borderRadius: 6 }}>
                                                        <Text strong style={{ fontSize: 13, marginRight: 8 }}>
                                                            Tài liệu chung:
                                                        </Text>
                                                        <Space size={[6, 6]} wrap>
                                                            {globalMaterials.map((m) => (
                                                                <Tag
                                                                    key={m.id}
                                                                    closable
                                                                    onClose={() =>
                                                                        setGlobalMaterials((prev) =>
                                                                            prev.filter((item) => item.id !== m.id)
                                                                        )
                                                                    }
                                                                    color="blue"
                                                                >
                                                                    📄 {m.fileName}
                                                                </Tag>
                                                            ))}
                                                        </Space>
                                                        <Alert
                                                            type="warning"
                                                            showIcon
                                                            style={{ marginTop: 8 }}
                                                            message="Có tài liệu chung: mọi câu sẽ được soạn mới, không lấy câu từ ngân hàng."
                                                        />
                                                    </div>
                                                )}

                                                {selectedMatrix && (
                                                    <div className="selected-matrix-info-box">
                                                        <Row gutter={16} align="middle">
                                                            <Col xs={24} sm={8}>
                                                                <Text type="secondary">Tên ma trận: </Text>
                                                                <Text strong>{selectedMatrix.name}</Text>
                                                            </Col>
                                                            <Col xs={12} sm={8}>
                                                                <Text type="secondary">Khối lớp: </Text>
                                                                <Text>Khối {selectedMatrix.gradeLevel}</Text>
                                                            </Col>
                                                            <Col xs={12} sm={8}>
                                                                <Button
                                                                    type="link"
                                                                    size="small"
                                                                    icon={<EditOutlined />}
                                                                    onClick={() =>
                                                                        navigate(`/exam-matrices/${selectedMatrix.id}/edit`)
                                                                    }
                                                                >
                                                                    Chỉnh sửa ma trận gốc
                                                                </Button>
                                                            </Col>
                                                        </Row>
                                                    </div>
                                                )}

                                                <Row gutter={16} style={{ marginTop: 16 }}>
                                                    <Col xs={24} md={14}>
                                                        <Form.Item
                                                            label="Tên đề thi hiển thị (tùy chọn)"
                                                            tooltip="Mặc định lấy theo tên của ma trận đã chọn"
                                                        >
                                                            <Input
                                                                size="middle"
                                                                value={matrixExamTitle}
                                                                onChange={(e) => setMatrixExamTitle(e.target.value)}
                                                                placeholder="Mặc định lấy theo tên ma trận"
                                                                maxLength={255}
                                                            />
                                                        </Form.Item>
                                                    </Col>
                                                </Row>

                                                {/* Chỉ phần "Gắn cho ô này" (tài liệu, ghi chú theo ô) nằm trong mục nâng cao, mặc định đóng */}
                                                <Collapse
                                                    ghost
                                                    items={[{
                                                        key: "advanced",
                                                        label: (
                                                            <Text strong>
                                                                Tùy chọn nâng cao
                                                                {hasAdvancedOptions && (
                                                                    <Tag color="blue" style={{ marginLeft: 8 }}>Đã chọn</Tag>
                                                                )}
                                                            </Text>
                                                        ),
                                                        children: (
                                                            <>
                                                {/* Danh sách các ô ma trận */}
                                                <Card
                                                    size="small"
                                                    title={
                                                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                                                            <span>Cấu hình từng ô ma trận [Gắn tài liệu + Ghi chú cho AI]</span>
                                                            <Text type="secondary" style={{ fontSize: 12, fontWeight: 400 }}>
                                                                Ô có tài liệu hoặc yêu cầu riêng sẽ không lấy câu từ ngân hàng mà soạn câu mới
                                                            </Text>
                                                        </div>
                                                    }
                                                    style={{ marginTop: 8, marginBottom: 20 }}
                                                >
                                                    {loadingMatrixDetail ? (
                                                        <div style={{ textAlign: "center", padding: "20px 0" }}>
                                                            <Spin tip="Đang tải danh sách ô ma trận..." />
                                                        </div>
                                                    ) : (
                                                        <Table
                                                            columns={cellColumns}
                                                            dataSource={matrixCellsWithKeys}
                                                            rowKey="cellKey"
                                                            pagination={false}
                                                            size="small"
                                                            scroll={{ x: 700 }}
                                                        />
                                                    )}
                                                </Card>
                                                            </>
                                                        ),
                                                    }]}
                                                />
                                            </Form>
                                        )}
                                    </Card>
                                </div>
                            ),
                        },
                    ]}
                />
            </div>

            {/* Modal chọn tài liệu chung cho ma trận */}
            <MaterialManagerModal
                open={isGlobalMaterialModalOpen}
                selectedIds={globalMaterialIds}
                reservedIds={reservedForGlobal}
                onClose={() => setIsGlobalMaterialModalOpen(false)}
                onSelect={(newSelected) => setGlobalMaterials(newSelected)}
                onRemoved={handleMaterialRemoved}
                maxSelect={5}
                title="Chọn tài liệu chung cho đề thi theo ma trận"
            />

            {/* Modal cấu hình tài liệu và ghi chú riêng cho 1 ô ma trận */}
            <Modal
                open={activeConfigCell !== null}
                title={
                    activeConfigCell
                        ? `Ghi chú soạn câu: Ô ${activeConfigCell.cell.part} — ${activeConfigCell.cell.topic}`
                        : ""
                }
                onCancel={() => setActiveConfigCell(null)}
                footer={[
                    <Button key="cancel" onClick={() => setActiveConfigCell(null)}>
                        Hủy
                    </Button>,
                    <Button
                        key="clear"
                        danger
                        onClick={() => {
                            if (!activeConfigCell) return;
                            setCellContexts((prev) => {
                                const next = { ...prev };
                                delete next[activeConfigCell.cellKey];
                                return next;
                            });
                            message.info("Đã xóa ngữ cảnh riêng cho ô này");
                            setActiveConfigCell(null);
                        }}
                    >
                        Xóa gắn riêng
                    </Button>,
                    <Button
                        key="save"
                        type="primary"
                        onClick={saveCellConfig}
                    >
                        Lưu ngữ cảnh ô
                    </Button>,
                ]}
                width={650}
            >
                {activeConfigCell && (
                    <div>
                        <div style={{ marginBottom: 16 }}>
                            <Text>
                                {[
                                    `Phần ${activeConfigCell.cell.part}`,
                                    QUESTION_TYPE_TAGS[activeConfigCell.cell.questionType].label,
                                    activeConfigCell.cell.questionType === "tf"
                                        ? (activeConfigCell.cell.statementDifficulties ?? [])
                                            .map((d) => describeLevel(d).label)
                                            .join(", ")
                                        : describeLevel(activeConfigCell.cell.difficulty).label,
                                    `${activeConfigCell.cell.questionCount} câu`,
                                ]
                                    .filter(Boolean)
                                    .join(" · ")}
                            </Text>
                            {activeConfigCell.cell.content && (
                                <div style={{ marginTop: 8 }}>
                                    <Text type="secondary" style={{ fontSize: 13 }}>
                                        Phạm vi: {activeConfigCell.cell.content}
                                    </Text>
                                </div>
                            )}
                        </div>

                        {/* Tài liệu bài học riêng cho ô */}
                        <div style={{ marginBottom: 16 }}>
                            <Text strong>Tài liệu bài học riêng cho ô này: </Text>
                            <div style={{ marginTop: 8 }}>
                                <Button
                                    size="small"
                                    icon={<PaperClipOutlined />}
                                    onClick={() => setIsCellMaterialModalOpen(true)}
                                >
                                    {cellDraft.materialIds.length > 0
                                        ? "Thay đổi tài liệu cho ô"
                                        : "Đính kèm tài liệu cho ô"}
                                </Button>
                            </div>

                            {cellDraft.materialNames.length > 0 && (
                                <div style={{ marginTop: 8 }}>
                                    <Space size={[6, 6]} wrap>
                                        {cellDraft.materialNames.map((name, i) => (
                                            <Tag key={cellDraft.materialIds[i]} color="cyan">
                                                📄 {name}
                                            </Tag>
                                        ))}
                                    </Space>
                                </div>
                            )}

                            {/* Tài liệu chung cũng áp cho ô này (hợp hai danh sách, docs/api mục 7.2) */}
                            {globalMaterials.length > 0 && (
                                <div style={{ marginTop: 8 }}>
                                    <Text type="secondary" style={{ fontSize: 12, marginRight: 6 }}>
                                        Kèm tài liệu chung:
                                    </Text>
                                    <Space size={[6, 6]} wrap>
                                        {globalMaterials.map((m) => (
                                            <Tag key={m.id}>📄 {m.fileName}</Tag>
                                        ))}
                                    </Space>
                                </div>
                            )}
                        </div>

                        {/* Yêu cầu thêm cho AI riêng cho ô */}
                        <div>
                            <Text strong>Ghi chú / Yêu cầu thêm cho AI: </Text>
                            <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 8 }}>
                                Chỉ dẫn cụ thể về bối cảnh, dạng bài tập thực tế (tối đa 1000 ký tự).
                            </Paragraph>
                            <TextArea
                                rows={4}
                                maxLength={1000}
                                showCount
                                value={cellDraft.extraInstruction}
                                onChange={(e) => {
                                    const val = e.target.value;
                                    setCellDraft((prev) => ({ ...prev, extraInstruction: val }));
                                }}
                                placeholder="Ví dụ: Lấy ví dụ thực tế liên quan đến tính toán tiền tệ hoặc nhiệt độ..."
                            />
                        </div>

                        <Alert
                            type="warning"
                            showIcon
                            icon={<BulbOutlined />}
                            message="Lưu ý quan trọng"
                            description="Ô có tài liệu hoặc ghi chú riêng sẽ không lấy câu từ ngân hàng, mọi câu của ô đều được soạn mới."
                            style={{ marginTop: 16 }}
                        />
                    </div>
                )}
            </Modal>

            {/* Modal chọn tài liệu riêng cho 1 ô */}
            {activeConfigCell && (
                <MaterialManagerModal
                    open={isCellMaterialModalOpen}
                    selectedIds={cellDraft.materialIds}
                    reservedIds={reservedForActiveCell}
                    onRemoved={handleMaterialRemoved}
                    onClose={() => setIsCellMaterialModalOpen(false)}
                    onSelect={(newSelected) => {
                        setCellDraft((prev) => ({
                            ...prev,
                            materialIds: newSelected.map((m) => m.id),
                            materialNames: newSelected.map((m) => m.fileName),
                        }));
                    }}
                    maxSelect={5}
                    title={`Chọn tài liệu cho ô: ${activeConfigCell.cell.topic}`}
                />
            )}
        </div>
    );
}