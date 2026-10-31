import { useParams, useNavigate } from 'react-router-dom';
import { Button, Card, Spin, Space, Tag, Typography, message } from "antd";
import { ArrowLeftOutlined, FileWordOutlined, LineChartOutlined, WarningOutlined } from "@ant-design/icons";

import ExamPreview from "../components/ExamPreview";
import ExamQuestionList from "../components/ExamQuestionList";
import type { ExamPreviewData, ExamQuestionItem, ChangeQuestionRequest, ExamDetail, ExamItemDto } from "../types/exam.types";
import { examKindConfig, examStatusConfig, resolveQuestionType } from "../../../utils/exam.utils";
import { keepQuestion, replaceQuestion, getExamDetail, getExamExportDocx, getCatalogSubjects } from '../services/examApi';
import { getExamMatrix } from '../../exam-matrix/services/examMatrixService';

import "./ExamDetailPage.css";
import { useCallback, useEffect, useMemo, useState, useRef } from 'react';
import ChangeQuestionModal, { type ChangeQuestionResult } from "../components/ChangeQuestionModal";
import EditQuestionModal from "../components/EditQuestionModal";
import { ExamProgressDetail } from '../components/ExamProgressDetail';
import { QcTraceModal } from '../components/QcTraceModal';

const { Title, Text } = Typography;

const POLL_INTERVAL_MS = 8000;
const MAX_CONSECUTIVE_ERRORS = 3;

const partTitles: Record<string, string> = {
    I: "Phần I. Trắc nghiệm nhiều phương án",
    II: "Phần II. Trắc nghiệm đúng sai",
    III: "Phần III. Trả lời ngắn",
};

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
    };
}

export default function ExamDetailPage() {
    const navigate = useNavigate();

    // Route khai báo "exams/:id" (AppRoutes.tsx)
    const { id: examId } = useParams<{ id: string }>();

    const [changeQuestion, setChangeQuestion] = useState<ExamQuestionItem | null>(null);

    const [editingQuestion, setEditingQuestion] = useState<ExamQuestionItem | null>(null);

    const [detail, setDetail] = useState<ExamDetail | null>(null);
    const [loading, setLoading] = useState(true);

    const [showProgress, setShowProgress] = useState(false);
    const [exportLoading, setExportLoading] = useState(false);

    const [qcModalVisible, setQcModalVisible] = useState(false);
    const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null);

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
            const data = await getExamDetail(examId);

            // Chỉ cập nhật state nếu đây là request mới nhất
            if (currentFetchId !== fetchSequenceRef.current) return;

            setDetail(data);
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

    useEffect(() => {
        if (!examId) {
            setLoading(false);
            return;
        }
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
    const handleExportWord = async () => {
        if (!examId) return;
        try {
            setExportLoading(true);
            const blob = await getExamExportDocx(examId); // Trả về Blob .docx
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `exam-${examId}.docx`;
            a.click();
            window.URL.revokeObjectURL(url);
            message.success('Xuất file Word thành công!');
        } catch (err: any) {
            message.error(err?.response?.data?.error ?? 'Không xuất được file');
        } finally {
            setExportLoading(false);
        }
    };

    // Hàm mở Modal Xem vết QC cho slot
    const openQcTrace = (targetSlotId: string | number) => {
        setSelectedSlotId(String(targetSlotId));
        setQcModalVisible(true);
    };

    if (loading) return <Spin tip="Đang tải đề…" />;
    if (!detail) return <p>Không có dữ liệu đề.</p>;

    const allQuestions = parts.flatMap((p) => p.questions);
    // Câu tf có difficulty null (mức ở từng ý) nên đếm riêng để các dòng cộng đủ tổng
    const leveledQuestions = allQuestions.filter((q) => resolveQuestionType(q) !== "tf");
    const questionCount = parts.reduce((sum, p) => sum + p.required, 0);
    const exam: ExamPreviewData = {
        title: detail.title,
        subject: subjectName,
        questionCount,
        easyCount: leveledQuestions.filter((q) => q.difficulty === "easy").length,
        mediumCount: leveledQuestions.filter((q) => q.difficulty === "medium").length,
        hardCount: leveledQuestions.filter((q) => q.difficulty === "hard").length,
        tfCount: allQuestions.length - leveledQuestions.length,
        missingCount: Math.max(0, questionCount - allQuestions.length),
    };

    const handleGenerateReplacement = async (request: ChangeQuestionRequest): Promise<ChangeQuestionResult> => {
        if (!examId || request.question.slotId === undefined) return;
        try {
            const source = request.source || "generate";
            await replaceQuestion(examId, request.question.slotId, {
                source,
                ...(source === "generate" && request.instruction ? { instruction: request.instruction } : {}),
            });
            message.success(
                source === "bank"
                    ? "Đã đổi câu hỏi từ ngân hàng thành công!"
                    : "Đã yêu cầu sinh câu thay thế qua AI!"
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
                const backendMsg =
                    err?.response?.data?.error ||
                    err?.response?.data?.message ||
                    err?.message ||
                    "Đã xảy ra xung đột dữ liệu.";
                message.warning(`${backendMsg} Đã tải lại đề.`);
                setChangeQuestion(null);
                await loadDetail();
                return;
            }

            const errMsg =
                err?.response?.data?.error ||
                err?.response?.data?.message ||
                err?.message ||
                "Lỗi khi đổi câu hỏi";
            message.error(errMsg);
        }
    };

    const handleKeep = async (question: ExamQuestionItem) => {
        if (!examId || question.slotId === undefined) return;
        try {
            await keepQuestion(Number(examId), question.slotId);
            message.success('Đã giữ câu hỏi thành công');
            await loadDetail();
        } catch (e: any) {
            console.error(e);
            message.error(e?.response?.data?.error || e?.message || 'Gặp lỗi khi giữ câu hỏi');
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
                        {pendingSummary.generatingCount > 0 ? ` ${pendingSummary.generatingCount} chỗ đang sinh` : ""}
                        {pendingSummary.generatingCount > 0 && pendingSummary.checkingCount > 0 ? "," : ""}
                        {pendingSummary.checkingCount > 0 ? ` ${pendingSummary.checkingCount} câu đang kiểm lại` : ""}
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
                            <Tag color={examStatusConfig[detail.status]?.color ?? "default"}>
                                {examStatusConfig[detail.status]?.label ?? detail.status}
                            </Tag>
                            {detail.kind && (
                                <Tag color={examKindConfig[detail.kind]?.color ?? "default"}>
                                    {examKindConfig[detail.kind]?.label ?? detail.kind}
                                </Tag>
                            )}
                            {detail.needsReviewCount > 0 && (
                                <Tag color="volcano" icon={<WarningOutlined />}>
                                    {detail.needsReviewCount} câu cần xem kỹ
                                </Tag>
                            )}
                            {subjectName && <Text type="secondary">{subjectName}</Text>}
                            <Text type="secondary">Mã đề: {detail.examCode}</Text>
                        </Space>
                    </div>

                    <Space size={8} className="exam-detail-page-actions">
                        <Button
                            icon={<LineChartOutlined />}
                            onClick={() => setShowProgress((prev) => !prev)}
                            disabled={!examId}
                        >
                            {showProgress ? "Ẩn tiến độ" : "Xem tiến độ chi tiết"}
                        </Button>

                        <Button
                            type="primary"
                            icon={<FileWordOutlined />}
                            loading={exportLoading}
                            onClick={handleExportWord}
                        >
                            Xuất file Word
                        </Button>
                    </Space>
                </div>
            </div>

            <div className="exam-detail-page-overview">
                <Card title="Thông tin đề thi">
                    <ExamPreview exam={exam} />
                </Card>
            </div>

            {showProgress && examId && (
                <div className="exam-detail-page-progress">
                    <ExamProgressDetail
                        examId={examId}
                        onClose={() => setShowProgress(false)}
                    />
                </div>
            )}

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
                                onViewQcTrace={(question) => {
                                    if (question.slotId !== undefined) openQcTrace(question.slotId);
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
                            visible={qcModalVisible}
                            onClose={() => setQcModalVisible(false)}
                        />
                    )}
                </Card>
            </div>
        </div>
    );
}