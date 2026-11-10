import { useParams, useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Dropdown, Progress, Spin, Space, Tag, Tooltip, Typography, message } from "antd";
import { ArrowLeftOutlined, DownOutlined, FileWordOutlined, SyncOutlined, WarningOutlined } from "@ant-design/icons";

import ExamPreview from "../components/ExamPreview";
import ExamQuestionList from "../components/ExamQuestionList";
import type { ExamPreviewData, ExamProgress, ExamQuestionItem, ChangeQuestionRequest, ExamDetail, ExamItemDto } from "../types/exam.types";
import { examKindConfig, examStatusConfig } from "../../../utils/exam.utils";
import { keepQuestion, replaceQuestion, getExamDetail, getExamProgress, getExamExportDocx, getCatalogSubjects, type ExportEquationFormat } from '../services/examApi';
import { getExamMatrix } from '../../exam-matrix/services/examMatrixService';
import { getApiErrorMessage, showApiError } from "../../../utils/apiError";

import "./ExamDetailPage.css";
import { useCallback, useEffect, useMemo, useState, useRef } from 'react';
import ChangeQuestionModal, { type ChangeQuestionResult } from "../components/ChangeQuestionModal";
import EditQuestionModal from "../components/EditQuestionModal";
import { QcTraceModal } from '../components/QcTraceModal';

const { Title, Text } = Typography;

/** Tên file an toàn từ tên đề: bỏ ký tự Windows không cho phép trong tên file; rỗng thì dùng {@code fallback}. */
function toFileName(title: string | undefined, fallback: string): string {
    // eslint-disable-next-line no-control-regex
    const cleaned = (title ?? "").replace(/[\\/:*?"<>|\u0000-\u001f]/g, " ").replace(/\s+/g, " ").trim().replace(/[. ]+$/, "");
    return cleaned || fallback;
}

const POLL_INTERVAL_MS = 8000;
const MAX_CONSECUTIVE_ERRORS = 3;

const partTitles: Record<string, string> = {
    I: "Phần I. Trắc nghiệm nhiều phương án",
    II: "Phần II. Trắc nghiệm đúng sai",
    III: "Phần III. Trả lời ngắn",
};

// Tên ngắn của Phần trong bảng ma trận cấu trúc
const partSummaryLabels: Record<string, string> = {
    I: "Phần I – Trắc nghiệm",
    II: "Phần II – Đúng/Sai",
    III: "Phần III – Trả lời ngắn",
};

/** Câu chữ và màu của thanh tiến độ ở đầu trang đề (docs/api mục 7.3). */
function describeBuildProgress(
    progress: ExamProgress,
    createdAt: string
): { type: "info" | "success" | "warning"; text: string } {
    const done = `${progress.ready}/${progress.total}`;
    const stayNote = "Thầy/cô có thể rời trang, đề vẫn tiếp tục được soạn.";
    if (progress.status === "generating") {
        // Chưa xong câu nào thì chưa có cơ sở để ước lượng: không hiện thời gian
        if (progress.ready <= 0) {
            return { type: "info", text: `Đang soạn ${done} câu… ${stayNote}` };
        }
        // Thời gian trung bình mỗi câu đã xong (từ lúc tạo đề) nhân số câu còn lại
        const elapsedSeconds = Math.max(0, (Date.now() - new Date(createdAt).getTime()) / 1000);
        if (!Number.isFinite(elapsedSeconds)) {
            return { type: "info", text: `Đang soạn ${done} câu… ${stayNote}` };
        }
        const remainingSeconds = (elapsedSeconds / progress.ready) * Math.max(0, progress.generating);
        // Làm tròn lên phút, ít nhất 1 phút khi còn câu đang soạn
        const minutes = Math.max(1, Math.ceil(remainingSeconds / 60));
        return {
            type: "info",
            text: `Đang soạn ${done} câu · khoảng ${minutes} phút nữa. ${stayNote}`,
        };
    }
    if (progress.status === "partial" && progress.failed > 0) {
        return {
            type: "warning",
            text: `Đã soạn xong ${done} câu · ${progress.failed} câu chưa soạn được (xem bên dưới).`,
        };
    }
    return { type: "success", text: `Đã soạn xong ${done} câu.` };
}

/** Chuyển một chỗ của đề (ExamItemDto) sang dạng câu hiển thị trong danh sách. */
function toQuestionItem(item: ExamItemDto): ExamQuestionItem {
    return {
        id: item.questionId ?? item.slotId,
        slotId: item.slotId,
        questionId: item.questionId,
        questionType: item.questionType,
        part: item.part,
        topic: item.topic ?? undefined,
        state: item.state,
        source: item.source,
        bankItemId: item.bankItemId,
        questionText: item.questionText ?? "",
        optionA: item.optionA,
        optionB: item.optionB,
        optionC: item.optionC,
        optionD: item.optionD,
        correctOption: item.correctOption,
        saAnswer: item.saAnswer,
        statements: item.statements,
        explanation: item.explanation,
        difficulty: item.difficulty,
        needsReview: item.needsReview,
        reviewReasons: item.reviewReasons,
        qcState: item.qcState,
        edited: item.edited,
        originalQuestionId: item.originalQuestionId,
        failureReason: item.failureReason,
    };
}

export default function ExamDetailPage() {
    const navigate = useNavigate();

    // Route khai báo "exams/:id" (AppRoutes.tsx)
    const { id: examId } = useParams<{ id: string }>();

    const [changeQuestion, setChangeQuestion] = useState<ExamQuestionItem | null>(null);

    const [editingQuestion, setEditingQuestion] = useState<ExamQuestionItem | null>(null);

    const [detail, setDetail] = useState<ExamDetail | null>(null);
    const [progress, setProgress] = useState<ExamProgress | null>(null);
    const [loading, setLoading] = useState(true);

    const [exportLoading, setExportLoading] = useState(false);

    const [qcModalVisible, setQcModalVisible] = useState(false);
    const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null);
    // Số câu trên thẻ câu (thứ tự trong Phần) để hộp kết quả tự kiểm tra ghi cùng số
    const [selectedQuestionNo, setSelectedQuestionNo] = useState<number | undefined>(undefined);

    const [subjectName, setSubjectName] = useState<string | undefined>(undefined);

    // Bộ đếm thứ tự request để chống phản hồi cũ đến muộn (stale response guard)
    const fetchSequenceRef = useRef(0);

    // Bộ đếm lỗi liên tiếp để ngắt polling khi mất mạng
    const consecutiveErrorsRef = useRef(0);
    const [pollingStoppedDueToError, setPollingStoppedDueToError] = useState(false);

    const loadDetail = useCallback(async (options?: { silent?: boolean }) => {
        if (!examId) return;
        const currentFetchId = ++fetchSequenceRef.current;
        try {
            // Tiến độ lỗi thì thôi, vẫn hiện đề; chỉ lỗi tải đề mới tính vào bộ đếm lỗi
            const [data, progressData] = await Promise.all([
                getExamDetail(examId),
                getExamProgress(examId).catch(() => null),
            ]);

            // Chỉ cập nhật state nếu đây là request mới nhất
            if (currentFetchId !== fetchSequenceRef.current) return;

            setDetail(data);
            if (progressData) setProgress(progressData);
            consecutiveErrorsRef.current = 0;
            setPollingStoppedDueToError(false);
        } catch (err: any) {
            if (currentFetchId !== fetchSequenceRef.current) return;

            if (!options?.silent) {
                message.error(err?.response?.data?.error || 'Không tải được đề');
            } else {
                consecutiveErrorsRef.current += 1;
                if (consecutiveErrorsRef.current >= MAX_CONSECUTIVE_ERRORS) {
                    setPollingStoppedDueToError(true);
                    message.error('Mất kết nối mạng. Đã tạm dừng cập nhật tự động.');
                }
            }
        }
    }, [examId]);

    // Tính toán số lượng các chỗ đang sinh hoặc đang kiểm lại để hiển thị thông báo tiến độ
    const pendingSummary = useMemo(() => {
        if (!detail?.parts) return { generatingCount: 0, checkingCount: 0, total: 0 };
        let generatingCount = 0;
        let checkingCount = 0;

        detail.parts.forEach(p => {
            p.items.forEach(i => {
                if (i.state === "pending" || i.state === "generating") {
                    generatingCount++;
                }
                if (i.qcState === "checking") {
                    checkingCount++;
                }
            });
        });

        return {
            generatingCount,
            checkingCount,
            total: generatingCount + checkingCount,
        };
    }, [detail]);

    const hasPendingWork = !pollingStoppedDueToError && pendingSummary.total > 0;
    // Còn câu đang soạn thì chưa xuất Word được
    const isGenerating = pendingSummary.generatingCount > 0 || progress?.status === "generating";

    useEffect(() => {
        if (!examId) {
            setLoading(false);
            return;
        }
        setProgress(null);
        setLoading(true);
        loadDetail().finally(() => setLoading(false));
    }, [examId, loadDetail]);

    useEffect(() => {
        if (!hasPendingWork) return;
        let cancelled = false;
        let timer: number;
        const tick = async () => {
            if (document.visibilityState === "visible") {
                await loadDetail({ silent: true });
            }
            if (!cancelled && !pollingStoppedDueToError) {
                timer = window.setTimeout(tick, POLL_INTERVAL_MS);
            }
        };
        timer = window.setTimeout(tick, POLL_INTERVAL_MS);
        return () => { cancelled = true; clearTimeout(timer); };
    }, [hasPendingWork, loadDetail, pollingStoppedDueToError]);

    // API xem đề không trả môn: lấy subjectId qua ma trận rồi tra tên trong danh mục môn.
    // Đề theo bài (matrixId = null, ma trận ẩn trả 404) và đề cũ không có ma trận thì không hiện môn.
    const matrixId = detail?.matrixId ?? null;
    useEffect(() => {
        setSubjectName(undefined);
        if (matrixId === null) return;
        let cancelled = false;
        (async () => {
            try {
                const [matrix, subjects] = await Promise.all([
                    getExamMatrix(String(matrixId)),
                    getCatalogSubjects(),
                ]);
                const subject = subjects.find((s) => s.id === matrix.subjectId);
                if (!cancelled) setSubjectName(subject?.name);
            } catch (err) {
                // Không có tên môn thì chỉ ẩn tag môn, không báo lỗi cho giáo viên
                console.warn("Không lấy được môn học của đề", err);
            }
        })();
        return () => { cancelled = true; };
    }, [matrixId]);

    // Câu theo từng phần I-III, giữ thứ tự backend trả về
    const parts = useMemo(
        () => (detail?.parts ?? []).map((p) => ({ ...p, questions: p.items.map(toQuestionItem) })),
        [detail]
    );

    // === Hàm xử lý xuất file Word đã được tách riêng ===
    const handleExportWord = async (equation: ExportEquationFormat = 'omml') => {
        if (!examId) return;
        try {
            setExportLoading(true);
            const { blob, keptOmml, scaled, filename } = await getExamExportDocx(examId, equation);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            // Luôn đặt tên theo tiêu đề đề thi; chỉ khi không có tiêu đề mới dùng tên backend gửi (Content-Disposition)
            const titleName = toFileName(detail?.title, "");
            a.download = titleName
                ? `${titleName}${equation === 'mathtype' ? '-mathtype' : ''}.docx`
                : filename
                ?? `${toFileName(undefined, detail?.examCode ?? `exam-${examId}`)}${equation === 'mathtype' ? '-mathtype' : ''}.docx`;
            a.click();
            window.URL.revokeObjectURL(url);
            message.success(equation === 'mathtype'
                ? 'Xuất file Word (công thức MathType) thành công!'
                : 'Xuất file Word (công thức Word Equation) thành công!');
            // Chỉ có khi xuất MathType (backend không gửi header ở chế độ Equation)
            if (keptOmml > 0) {
                message.warning(`${keptOmml} công thức không chuyển được sang MathType, vẫn ở dạng công thức Word`, 8);
            }
            if (scaled > 0) {
                message.info(`${scaled} công thức dài đã được thu nhỏ cho vừa trang`, 8);
            }
        } catch (err: unknown) {
            // responseType 'blob': thân lỗi JSON cũng là Blob, phải đọc ra chữ mới thấy thông báo của backend
            let errorMsg = 'Không xuất được file';
            const data = (err as { response?: { data?: unknown } })?.response?.data;
            try {
                const body = data instanceof Blob ? JSON.parse(await data.text()) : data;
                const serverMsg = (body as { error?: unknown } | null | undefined)?.error;
                if (serverMsg) errorMsg = String(serverMsg);
            } catch {
                // thân lỗi không phải JSON: giữ câu mặc định
            }
            message.error(errorMsg);
        } finally {
            setExportLoading(false);
        }
    };

    // Hàm mở Modal Xem vết QC cho slot
    const openQcTrace = (targetSlotId: string | number, questionNo: number) => {
        setSelectedSlotId(String(targetSlotId));
        setSelectedQuestionNo(questionNo);
        setQcModalVisible(true);
    };

    if (loading) return <Spin tip="Đang tải đề…" />;
    if (!detail) return <p>Không có dữ liệu đề.</p>;

    // Số câu theo Phần lấy từ ma trận (required), nên luôn cộng đủ tổng dù câu đang soạn hay lỗi
    const questionCount = parts.reduce((sum, p) => sum + p.required, 0);
    const exam: ExamPreviewData = {
        questionCount,
        parts: parts.map((p) => ({
            part: p.part,
            label: partSummaryLabels[p.part] ?? `Phần ${p.part}`,
            count: p.required,
        })),
    };
    const progressView = progress ? describeBuildProgress(progress, detail.createdAt) : null;
    const progressPercent = progress && progress.total > 0
        ? Math.min(100, Math.round((progress.ready / progress.total) * 100))
        : 0;

    const handleGenerateReplacement = async (request: ChangeQuestionRequest): Promise<ChangeQuestionResult> => {
        if (!examId || request.question.slotId === undefined) return;
        try {
            const source = request.source || "generate";
            await replaceQuestion(examId, request.question.slotId, {
                source,
                ...(source === "generate" && request.instruction ? { instruction: request.instruction } : {}),
                ...(request.reason ? { reason: request.reason } : {}),
            });
            message.success(
                source === "bank"
                    ? "Đã đổi câu hỏi từ ngân hàng thành công!"
                    : request.question.state === "failed"
                        ? "Đã yêu cầu soạn lại câu."
                        : "Đã yêu cầu soạn câu thay thế!"
            );
            setChangeQuestion(null);
            await loadDetail();
        } catch (err: any) {
            // Hết câu ngân hàng (docs/api mục 7.5): đặt lên trước để modal vẫn chuyển sang "Sinh câu mới"
            if (err?.response?.data?.code === "BANK_EXHAUSTED") {
                return "bank_exhausted";
            }

            // Xử lý các lỗi 409 còn lại
            if (err?.response?.status === 409) {
                message.warning(`${getApiErrorMessage(err, "Đã xảy ra xung đột dữ liệu.")} Đã tải lại đề.`);
                setChangeQuestion(null);
                await loadDetail();
                return;
            }

            // Chạm giới hạn soạn câu (429): hiện nổi bật, không tự thử lại
            showApiError(err, "Không đổi được câu hỏi.");
        }
    };

    const handleKeep = async (question: ExamQuestionItem) => {
        if (!examId || question.slotId === undefined) return;
        try {
            await keepQuestion(Number(examId), question.slotId);
            message.success('Đã lưu câu vào ngân hàng');
            await loadDetail();
        } catch (e: any) {
            console.error(e);
            message.error(e?.response?.data?.error || e?.message || 'Gặp lỗi khi lưu câu vào ngân hàng');
        }
    };

    const handleSaveEditedQuestion = async () => {
        setEditingQuestion(null);
        await loadDetail();
    };

    // Tiêu đề card Danh sách câu hỏi kèm thông báo trạng thái cập nhật tự động
    const questionListCardTitle = (
        <Space size={12}>
            <span>Danh sách câu hỏi</span>
            {hasPendingWork && (
                <Space size={6}>
                    <Spin size="small" />
                    <Text type="secondary" style={{ fontSize: 13, fontWeight: "normal" }}>
                        Đang cập nhật:
                        {pendingSummary.generatingCount > 0 ? ` ${pendingSummary.generatingCount} câu đang soạn` : ""}
                        {pendingSummary.generatingCount > 0 && pendingSummary.checkingCount > 0 ? "," : ""}
                        {pendingSummary.checkingCount > 0 ? ` ${pendingSummary.checkingCount} câu đang tự kiểm tra lại` : ""}
                    </Text>
                </Space>
            )}
        </Space>
    );

    return (
        <div className="exam-detail-page">
            <div className="exam-detail-page-header">
                <div className="exam-detail-page-header-top">
                    <Button
                        type="text"
                        icon={<ArrowLeftOutlined />}
                        className="exam-detail-page-back-button"
                        onClick={() => navigate("/exams")}
                    >
                        Quay lại danh sách
                    </Button>
                </div>

                <div className="exam-detail-page-header-content">
                    <div className="exam-detail-page-title-section">
                        <Title level={2} className="exam-detail-page-title">
                            {detail.title}
                        </Title>

                        <Space size={8} wrap className="exam-detail-page-meta">
                            <Text>{examStatusConfig[detail.status]?.label ?? detail.status}</Text>
                            {detail.kind && (
                                <Text>{examKindConfig[detail.kind]?.label ?? detail.kind}</Text>
                            )}
                            {detail.needsReviewCount > 0 && (
                                <Tag color="volcano" icon={<WarningOutlined />}>
                                    {detail.needsReviewCount} câu cần xem kỹ
                                </Tag>
                            )}
                            {subjectName && <Text type="secondary">{subjectName}</Text>}
                            <Text type="secondary">Mã hệ thống: {detail.examCode}</Text>
                        </Space>
                    </div>

                    <Space size={8} className="exam-detail-page-actions">
                        <Tooltip title={isGenerating ? "Đề đang được soạn, chờ xong rồi xuất Word." : undefined}>
                            {/* Nút bị khóa không nhận chuột nên bọc span để tooltip vẫn hiện */}
                            <span>
                                {/* Bấm nút chính: xuất công thức Word (Equation) như trước; mũi tên mở menu chọn cách ghi công thức */}
                                <Space.Compact>
                                    <Button
                                        type="primary"
                                        icon={<FileWordOutlined />}
                                        loading={exportLoading}
                                        disabled={isGenerating}
                                        onClick={() => handleExportWord('omml')}
                                    >
                                        Xuất file Word
                                    </Button>
                                    <Dropdown
                                        trigger={["click"]}
                                        disabled={isGenerating || exportLoading}
                                        menu={{
                                            items: [
                                                { key: "omml", label: "Công thức Word (Equation)" },
                                                { key: "mathtype", label: "Công thức MathType (thử nghiệm)" },
                                            ],
                                            selectedKeys: ["omml"],
                                            onClick: ({ key }) => handleExportWord(key === "mathtype" ? "mathtype" : "omml"),
                                        }}
                                    >
                                        <Button
                                            type="primary"
                                            icon={<DownOutlined />}
                                            disabled={isGenerating || exportLoading}
                                            aria-label="Chọn cách ghi công thức khi xuất Word"
                                        />
                                    </Dropdown>
                                </Space.Compact>
                            </span>
                        </Tooltip>
                    </Space>
                </div>
            </div>

            {progress && progressView && (
                <div className="exam-detail-page-progress">
                    <Alert
                        type={progressView.type}
                        showIcon
                        icon={progress.status === "generating" ? <SyncOutlined spin /> : undefined}
                        message={progressView.text}
                        description={progress.status === "generating"
                            ? <Progress percent={progressPercent} showInfo={false} />
                            : undefined}
                    />
                </div>
            )}

            <div className="exam-detail-page-overview">
                <Card>
                    <ExamPreview exam={exam} />
                </Card>
            </div>

            <div className="exam-detail-page-question-list">
                <Card title={questionListCardTitle}>
                    {parts.length === 0 && (
                        <ExamQuestionList questions={[]} />
                    )}

                    {parts.map((p) => (
                        <div key={p.part} className="question-group">
                            <Title level={5} className="question-group-title">
                                {partTitles[p.part] ?? `Phần ${p.part}`}{" "}
                                <Text type="secondary">({p.ready}/{p.required} câu)</Text>
                            </Title>
                            <ExamQuestionList
                                questions={p.questions}
                                onChangeQuestion={(question) => {
                                    setChangeQuestion(question);
                                }}
                                onKeep={handleKeep}
                                onEdit={(question) => setEditingQuestion(question)}
                                onViewQcTrace={(question, index) => {
                                    if (question.slotId !== undefined) openQcTrace(question.slotId, index + 1);
                                }}
                            />
                        </div>
                    ))}

                    <ChangeQuestionModal
                        open={changeQuestion !== null}
                        question={changeQuestion}
                        loading={false}
                        onCancel={() => setChangeQuestion(null)}
                        onGenerate={handleGenerateReplacement}
                    />

                    <EditQuestionModal
                        open={editingQuestion !== null}
                        question={editingQuestion}
                        examId={Number(examId)}
                        onCancel={() => setEditingQuestion(null)}
                        onSave={handleSaveEditedQuestion}
                    />

                    {/* Modal hiển thị vết QC cho slot được chọn */}
                    {selectedSlotId && (
                        <QcTraceModal
                            examId={examId!}
                            slotId={selectedSlotId}
                            questionNo={selectedQuestionNo}
                            visible={qcModalVisible}
                            onClose={() => setQcModalVisible(false)}
                        />
                    )}
                </Card>
            </div>
        </div>
    );
}