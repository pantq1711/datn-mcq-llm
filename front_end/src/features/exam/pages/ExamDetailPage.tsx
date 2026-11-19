import { useParams, useNavigate } from 'react-router-dom';
import { Button, Card, Empty, Result, Spin, Space, Tooltip, Typography, message, Modal } from "antd";
import { ArrowLeftOutlined, FileWordOutlined, ProfileOutlined, SendOutlined } from "@ant-design/icons";

import ExamQuestionList from "../components/ExamQuestionList";
import ExamQuestionNav from "../components/ExamQuestionNav";
import ExamSummaryBar, { type ExamSummaryCounts } from "../components/ExamSummaryBar";
import type { ExamProgress, ExamQuestionItem, ChangeQuestionRequest, ExamDetail, ExamItemDto } from "../types/exam.types";
import { describeBuildProgress, examKindConfig, examStatusConfig, describeReviewReasonList, examSlotAnchorId, hasContentFlag, isBulkKeepCandidate, contentReviewReasons, needsReviewNow, pendingCheckOf, PART_QUESTION_TYPE, PART_TITLES, QUESTION_TYPE_SHORT_LABELS } from "../../../utils/exam.utils";
import { keepQuestion, replaceQuestion, getExamDetail, getExamProgress, getCatalogSubjects } from '../services/examApi';
import { getExamMatrix } from '../../exam-matrix/services/examMatrixService';
import { getApiErrorMessage, showApiError } from "../../../utils/apiError";

import "./ExamDetailPage.css";
import { useCallback, useEffect, useMemo, useState, useRef } from 'react';
import ChangeQuestionModal, { type ChangeQuestionResult } from "../components/ChangeQuestionModal";
import EditQuestionForm from "../components/EditQuestionForm";
import { clearEditDraft, findEditDraftSlots, loadEditDraft } from "../utils/editDraft";
import { QcTraceModal } from '../components/QcTraceModal';
import AssignExamModal from "../../assignment/components/AssignExamModal";
import ExportWordDialog from '../components/ExportWordDialog';

const { Title, Text } = Typography;

const POLL_INTERVAL_MS = 8000;
const SHOW_ALL_EXPLANATIONS_KEY = "examShowAllExplanations";
const MAX_CONSECUTIVE_ERRORS = 3;

// Tên Phần và dạng câu dùng chung (G25): PART_TITLES cho tiêu đề Phần; dạng ngắn cho dòng
// "12 trắc nghiệm · 2 đúng/sai · 4 trả lời ngắn"
const partCountLabels: Record<string, string> = Object.fromEntries(
    Object.entries(PART_QUESTION_TYPE).map(([part, type]) => [part, QUESTION_TYPE_SHORT_LABELS[type]])
);

/** Câu giáo viên cần xử lý: có cờ cần xem kỹ hoặc chưa soạn được. */
function needsAttention(question: ExamQuestionItem): boolean {
    // G14: chỉ còn lý do "đang chờ" (hệ thống đang kiểm tra) thì chưa phải việc của giáo viên
    return question.state === "failed" || (question.state === "ready" && needsReviewNow(question));
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
        infoReasons: item.infoReasons ?? [],
        requests: item.requests,
        inheritedRequests: item.inheritedRequests,
    };
}

export default function ExamDetailPage() {
    const navigate = useNavigate();

    // Route khai báo "exams/:id" (AppRoutes.tsx)
    const { id: examId } = useParams<{ id: string }>();

    const [changeQuestion, setChangeQuestion] = useState<ExamQuestionItem | null>(null);

    // CT-2: sửa ngay trên thẻ câu; mỗi lần một câu. editDirty: form đang có thay đổi chưa lưu
    const [editingSlotId, setEditingSlotId] = useState<number | undefined>(undefined);
    const [editDirty, setEditDirty] = useState(false);

    // Đóng tab hoặc tải lại trang khi đang sửa dở: trình duyệt hỏi lại (bấm menu sang trang khác thì chưa chặn được)
    useEffect(() => {
        if (!editDirty) return;
        const warn = (e: BeforeUnloadEvent) => {
            e.preventDefault();
            e.returnValue = "";
        };
        window.addEventListener("beforeunload", warn);
        return () => window.removeEventListener("beforeunload", warn);
    }, [editDirty]);

    const [detail, setDetail] = useState<ExamDetail | null>(null);
    const [progress, setProgress] = useState<ExamProgress | null>(null);
    const [loading, setLoading] = useState(true);

    // Hộp xuất Word (ExportWordDialog): bấm "Xuất file Word" luôn mở hộp
    const [exportOpen, setExportOpen] = useState(false);

    const [assignOpen, setAssignOpen] = useState(false);

    const [qcModalVisible, setQcModalVisible] = useState(false);
    const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null);
    // Số câu trên thẻ câu (thứ tự trong Phần) để hộp kết quả tự kiểm tra ghi cùng số
    const [selectedQuestionNo, setSelectedQuestionNo] = useState<number | undefined>(undefined);

    const [subjectName, setSubjectName] = useState<string | undefined>(undefined);

    // Ô tóm tắt đầu trang: lọc câu cần xem và hiện mọi lời giải
    const [onlyNeedsAttention, setOnlyNeedsAttention] = useState(false);
    // "Hiện tất cả lời giải" ghi nhớ trong trình duyệt (dùng chung mọi đề); trình duyệt chặn lưu trữ thì mặc định tắt
    const [showAllExplanations, setShowAllExplanationsState] = useState<boolean>(() => {
        try {
            return localStorage.getItem(SHOW_ALL_EXPLANATIONS_KEY) === "1";
        } catch {
            return false;
        }
    });
    const setShowAllExplanations = (value: boolean) => {
        setShowAllExplanationsState(value);
        try {
            localStorage.setItem(SHOW_ALL_EXPLANATIONS_KEY, value ? "1" : "0");
        } catch {
            // không lưu được thì chỉ áp dụng trong lần mở trang này
        }
    };

    // Lưu hàng loạt (CT-7): đang chạy thì tạm dừng tự tải lại; rời trang thì dừng vòng lưu
    const [bulkSaving, setBulkSaving] = useState(false);
    const unmountedRef = useRef(false);
    useEffect(() => {
        unmountedRef.current = false;
        return () => {
            unmountedRef.current = true;
        };
    }, []);

    // Đã xét mở lại bản sửa dở sau khi tải lại trang chưa (chỉ một lần mỗi lần mở trang)
    const draftRestoreCheckedRef = useRef(false);

    // Bộ đếm thứ tự request để chống phản hồi cũ đến muộn (stale response guard)
    const fetchSequenceRef = useRef(0);

    // Bộ đếm lỗi liên tiếp để ngắt polling khi mất mạng
    const consecutiveErrorsRef = useRef(0);
    const [pollingStoppedDueToError, setPollingStoppedDueToError] = useState(false);

    // G24: lỗi của lần tải đề đầu tiên (chưa có dữ liệu đề) hiện thành khối thông báo thay cho trang; đã có đề rồi thì
    // lỗi tải lại vẫn chỉ là thông báo nhỏ như trước
    const [loadError, setLoadError] = useState<{ kind: "not_found" | "network" | "other"; text?: string } | null>(null);
    const detailLoadedRef = useRef(false);
    // Mã đề trên địa chỉ phải là số nguyên dương; sai dạng thì coi như không tìm thấy, không gọi API
    const invalidExamId = examId !== undefined && !/^[1-9]\d*$/.test(examId);

    const loadDetail = useCallback(async (options?: { silent?: boolean }) => {
        if (!examId || !/^[1-9]\d*$/.test(examId)) return;
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
            detailLoadedRef.current = true;
            setLoadError(null);
            if (progressData) setProgress(progressData);
            // Lần tải đầu: còn bản sửa dở của câu nào (tải lại trang khi đang sửa) thì mở lại form ở câu đó
            if (!draftRestoreCheckedRef.current) {
                draftRestoreCheckedRef.current = true;
                const examNumber = Number(examId);
                const items = data.parts.flatMap((p) => p.items);
                const target = findEditDraftSlots(examNumber)
                    .map((slotId) => items.find((i) => i.slotId === slotId))
                    .find((item) => item && item.state === "ready" && item.questionId != null
                        && loadEditDraft(examNumber, item.slotId, item.questionId) !== null);
                if (target) {
                    setEditingSlotId(target.slotId);
                    window.setTimeout(() => {
                        document.getElementById(examSlotAnchorId(target.slotId))
                            ?.scrollIntoView({ behavior: "smooth", block: "start" });
                    }, 300);
                }
            }
            consecutiveErrorsRef.current = 0;
            setPollingStoppedDueToError(false);
        } catch (err: any) {
            if (currentFetchId !== fetchSequenceRef.current) return;

            if (!options?.silent) {
                if (!detailLoadedRef.current) {
                    // Chưa có đề: thay cả trang bằng khối thông báo, không hiện chữ kỹ thuật của backend
                    // ("Không tìm thấy đề id=…", "Yêu cầu không hợp lệ…"); 404 và 400 gộp là "không tìm thấy"
                    const status = err?.response?.status;
                    if (status === 404 || status === 400) {
                        setLoadError({ kind: "not_found" });
                    } else if (!err?.response) {
                        setLoadError({ kind: "network" });
                    } else {
                        setLoadError({ kind: "other", text: err?.response?.data?.error });
                    }
                    return;
                }
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

    // Giao bài online cần mọi câu đã Giữ hoặc Sửa
    const notKeptCount = (detail?.parts ?? [])
        .flatMap((p) => p.items)
        .filter((i) => i.state !== "kept" && i.state !== "edited").length;

    // Đang lưu hàng loạt thì không tự tải lại giữa chừng (tải lại một lần khi xong)
    const hasPendingWork = !pollingStoppedDueToError && !bulkSaving && pendingSummary.total > 0;
    // Còn câu đang soạn thì chưa xuất Word được
    const isGenerating = pendingSummary.generatingCount > 0 || progress?.status === "generating";

    useEffect(() => {
        if (!examId) {
            setLoading(false);
            return;
        }
        // Chỉ lần tải mới nhất được tắt trạng thái đang tải: StrictMode (dev) chạy effect 2 lần, request đầu bị bỏ qua
        // (fetchSequenceRef) nhưng nếu nó tắt loading thì trang hiện "Không có dữ liệu đề." trong lúc request sau chưa xong
        let cancelled = false;
        setProgress(null);
        setLoading(true);
        loadDetail().finally(() => {
            if (!cancelled) setLoading(false);
        });
        return () => {
            cancelled = true;
        };
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

    // Số "Câu k" đếm lại từ 1 ở mỗi Phần, đúng như file Word (cấu trúc đề theo CV 7991) và lý do similar_in_exam;
    // tính trên danh sách đầy đủ để khi lọc "Chỉ hiện câu cần xem" số câu không đổi
    const questionNumbers = useMemo(() => {
        const numbers = new Map<string | number, number>();
        parts.forEach((p) => p.questions.forEach((q, i) => numbers.set(q.slotId ?? q.id, i + 1)));
        return numbers;
    }, [parts]);
    
    const getNumber = useCallback(
        (question: ExamQuestionItem, index: number) => questionNumbers.get(question.slotId ?? question.id) ?? index + 1,
        [questionNumbers]
    );
    // Vị trí câu ghi thống nhất "Phần II – Câu 3" ở hộp xác nhận, thông báo, tiêu đề hộp đổi câu
    const describePosition = (q: ExamQuestionItem) => `Phần ${q.part} – Câu ${getNumber(q, 0)}`;

    // Câu vừa nhảy tới: viền nổi 1,5 giây (G5); đặt lại về undefined trước để bấm lại cùng câu vẫn chạy hiệu ứng
    const [flashSlotId, setFlashSlotId] = useState<number | undefined>(undefined);
    const flashTimerRef = useRef<number | undefined>(undefined);
    useEffect(() => () => window.clearTimeout(flashTimerRef.current), []);
    const flash = (slotId: number) => {
        window.clearTimeout(flashTimerRef.current);
        setFlashSlotId(undefined);
        window.setTimeout(() => setFlashSlotId(slotId), 0);
        flashTimerRef.current = window.setTimeout(() => setFlashSlotId(undefined), 1600);
    };

    // Mục lục: cuộn tới câu; câu đang bị ẩn bởi bộ lọc thì tắt lọc trước
    const jumpToQuestion = (question: ExamQuestionItem) => {
        if (question.slotId === undefined) return;
        const slotId = question.slotId;
        const anchor = examSlotAnchorId(slotId);
        const scroll = () => {
            document.getElementById(anchor)?.scrollIntoView({ behavior: "smooth", block: "start" });
            flash(slotId);
        };
        if (onlyNeedsAttention && !needsAttention(question)) {
            setOnlyNeedsAttention(false);
            window.setTimeout(scroll, 0);
            return;
        }
        scroll();
    };

    // Liên kết "Phần P – Câu k" trong lý do gần giống (G5): câu thứ k của Phần P, đếm như trang đề
    const jumpToPosition = (part: string, number: number) => {
        const target = parts.find((p) => p.part === part)?.questions[number - 1];
        if (!target) {
            message.info(`Không tìm thấy Phần ${part} – Câu ${number} trong đề.`);
            return;
        }
        jumpToQuestion(target);
    };

    // Hàm mở Modal Xem vết QC cho slot
    const openQcTrace = (targetSlotId: string | number, questionNo: number) => {
        setSelectedSlotId(String(targetSlotId));
        setSelectedQuestionNo(questionNo);
        setQcModalVisible(true);
    };

    // G24: không tìm thấy đề (404, địa chỉ sai dạng) hoặc không tải được (mất mạng, lỗi máy chủ)
    const retryLoad = () => {
        setLoadError(null);
        setLoading(true);
        loadDetail().finally(() => setLoading(false));
    };
    if (invalidExamId || (!detail && loadError?.kind === "not_found")) {
        return (
            <div className="exam-detail-page-result">
                <Result
                    status="warning"
                    title="Không tìm thấy đề này"
                    subTitle="Có thể đề đã bị xóa hoặc đường dẫn chưa đúng."
                    extra={
                        <Button type="primary" onClick={() => navigate("/exams")}>
                            Về danh sách đề
                        </Button>
                    }
                />
            </div>
        );
    }

    if (loading) {
        return (
            <div className="exam-detail-page-loading">
                <Spin size="large" />
                <Text type="secondary">Đang tải đề…</Text>
            </div>
        );
    }

    if (!detail && loadError) {
        return (
            <div className="exam-detail-page-result">
                <Result
                    status="error"
                    title="Không tải được đề"
                    subTitle={
                        loadError.kind === "network"
                            ? "Thầy/cô kiểm tra kết nối mạng rồi thử lại."
                            : loadError.text || "Máy chủ đang gặp lỗi. Thầy/cô thử lại sau ít phút."
                    }
                    extra={[
                        <Button key="retry" type="primary" onClick={retryLoad}>
                            Thử lại
                        </Button>,
                        <Button key="back" onClick={() => navigate("/exams")}>
                            Về danh sách đề
                        </Button>,
                    ]}
                />
            </div>
        );
    }
    if (!detail) return <p>Không có dữ liệu đề.</p>;

    // Số câu theo Phần lấy từ ma trận (required), nên luôn cộng đủ tổng dù câu đang soạn hay lỗi
    const questionCount = parts.reduce((sum, p) => sum + p.required, 0);
    const allQuestions = parts.flatMap((p) => p.questions);
    const summaryCounts: ExamSummaryCounts = {
        total: questionCount,
        ready: parts.reduce((sum, p) => sum + p.ready, 0),
        kept: allQuestions.filter((q) => q.state === "kept" || q.state === "edited").length,
        // G14: đếm theo cùng quy tắc với thẻ câu và mục lục (bỏ câu chỉ còn lý do "đang chờ"), không dùng needsReviewCount
        needsReview: allQuestions.filter((q) => q.state === "ready" && needsReviewNow(q)).length,
        failed: allQuestions.filter((q) => q.state === "failed").length,
        generating: allQuestions.filter((q) => q.state === "pending" || q.state === "generating").length,
    };
    const partsBreakdown = parts
        .map((p) => `${p.required} ${partCountLabels[p.part] ?? `câu Phần ${p.part}`}`)
        .join(" · ");
    // Câu đang sửa luôn hiện, kể cả khi lọc "Chỉ hiện câu cần xem" (ẩn đi là mất chữ đang gõ)
    const visibleParts = onlyNeedsAttention
        ? parts
            .map((p) => ({ ...p, questions: p.questions.filter((q) => needsAttention(q) || q.slotId === editingSlotId) }))
            .filter((p) => p.questions.length > 0)
        : parts;
    const progressView = progress ? describeBuildProgress(progress) : null;
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
                // PR B / D-08: hộp Đổi chỉ đặt requestCodes (kể cả []) khi khác yêu cầu thừa hưởng: lượt Đổi tự đặt yêu cầu
                ...(source === "generate" && request.requestCodes ? { requestCodes: request.requestCodes } : {}),
                ...(request.reason ? { reason: request.reason } : {}),
            });
            const position = describePosition(request.question);
            message.success(
                source === "bank"
                    ? `Đã đổi ${position} bằng câu từ ngân hàng.`
                    : request.question.state === "failed"
                        ? `Đã yêu cầu soạn lại ${position}.`
                        : `Đã yêu cầu soạn câu thay thế cho ${position}.`
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

    const doKeep = async (question: ExamQuestionItem, flagged: boolean) => {
        try {
            const kept = await keepQuestion(Number(examId), question.slotId!);
            // NV-28: backend báo câu vừa lưu vào ngân hàng nào (cờ, tài liệu, hoặc cấu hình lưu riêng)
            const isPrivate = kept?.bankVisibility ? kept.bankVisibility === "private" : flagged;
            message.success(isPrivate
                ? `Đã lưu ${describePosition(question)} vào ngân hàng riêng của thầy/cô (chỉ thầy/cô thấy và dùng lại).`
                : `Đã lưu ${describePosition(question)} vào ngân hàng chung (các giáo viên khác cũng dùng lại được).`);
            await loadDetail();
        } catch (e: any) {
            const code = e?.response?.data?.code;
            if (code === "QC_CHECKING") {
                // D-01: backend nói rõ lý do (kể cả giờ chạy tiếp khi AI hết lượt trong ngày); câu cố định chỉ để dự phòng
                message.info(getApiErrorMessage(e, "Hệ thống đang tự kiểm tra bản sửa. Thầy/cô thử lưu lại sau ít phút."));
                await loadDetail();
                return;
            }
            if (e?.response?.status === 409) {
                message.warning(`${getApiErrorMessage(e, "Không lưu được câu.")} Đã tải lại đề.`);
                await loadDetail();
                return;
            }
            showApiError(e, "Gặp lỗi khi lưu câu vào ngân hàng");
        }
    };

    const handleKeep = async (question: ExamQuestionItem) => {
        if (!examId || question.slotId === undefined) return;
        // Chỉ cờ về nội dung mới đưa câu vào ngân hàng riêng; câu chỉ có "gần giống câu khác trong đề" vào ngân hàng chung
        if (!hasContentFlag(question.needsReview, question.reviewReasons)) {
            await doKeep(question, false);
            return;
        }
        // Câu có cờ nội dung vẫn Giữ được nhưng vào ngân hàng riêng (docs/api mục 7.5, 10/11, làm rõ 11/11)
        // G14: lý do "đang chờ" không liệt kê như lý do cần xem kỹ; hoãn kiểm lại (solver_pending) thì nói riêng một dòng
        const reasons = contentReviewReasons(question.reviewReasons);
        const deferred = pendingCheckOf(question) === "deferred";
        // G8: chữ theo bảng thuật ngữ ("Lưu", "Cần xem kỹ"); nút được chọn sẵn là nút an toàn
        Modal.confirm({
            title: `Lưu ${describePosition(question)} (cần xem kỹ)?`,
            content: (
                <div>
                    {reasons.length > 0 && (
                        <>
                            <p>Lý do cần xem kỹ:</p>
                            <ul style={{ paddingLeft: 20, margin: 0 }}>
                                {/* Gộp cảnh báo cùng mẫu (G4), bỏ câu trùng */}
                                {describeReviewReasonList(reasons).map((r) => <li key={r.text}>{r.text}</li>)}
                            </ul>
                        </>
                    )}
                    {deferred && (
                        <p style={{ marginTop: reasons.length > 0 ? 8 : 0 }}>
                            Hệ thống chưa kiểm tra lại xong đáp án (tạm hoãn vì hết lượt trong ngày).
                        </p>
                    )}
                    <p style={{ marginTop: 8 }}>Nếu vẫn lưu, câu chỉ vào ngân hàng riêng của thầy/cô.</p>
                </div>
            ),
            okText: "Vẫn lưu",
            cancelText: "Để tôi xem lại",
            // Lỡ nhấn Enter thì đóng hộp, không lưu
            autoFocusButton: "cancel",
            // onOk trả về Promise từ doKeep giúp nút "Vẫn lưu" tự động hiển thị hiệu ứng loading
            onOk: () => doKeep(question, true),
        });
    };

    // ===== Lưu hàng loạt (CT-7): các câu đã soạn xong, chưa lưu, không có lý do cần xem kỹ =====
    // Câu đang sửa trên thẻ không được lưu hàng loạt (bản đang gõ chưa lưu)
    const bulkCandidates = allQuestions.filter(
        (q) => q.slotId !== undefined && q.slotId !== editingSlotId && isBulkKeepCandidate(q)
    );
    const runBulkKeep = async (targets: ExamQuestionItem[]) => {
        if (!examId) return;
        const progressKey = "bulk-keep";
        setBulkSaving(true);
        let shared = 0;
        let privateCount = 0;
        const failures: string[] = [];
        try {
            // Lần lượt từng câu (mỗi lần Giữ là một giao dịch ghi ở backend), không gửi song song
            for (let i = 0; i < targets.length; i++) {
                if (unmountedRef.current) return;
                const q = targets[i];
                message.loading({ key: progressKey, content: `Đang lưu ${i + 1}/${targets.length} câu…`, duration: 0 });
                try {
                    const kept = await keepQuestion(Number(examId), q.slotId!);
                    if (kept?.bankVisibility === "private") privateCount++;
                    else shared++;
                } catch (e: unknown) {
                    // Đã được lưu (ví dụ ở tab khác; backend trả 409 "Câu hỏi này đã được giữ."): coi như xong;
                    // lỗi khác thì ghi lại rồi lưu tiếp câu sau
                    const response = (e as { response?: { status?: number; data?: { code?: string } } })?.response;
                    if (response?.status === 409 && response.data?.code !== "QC_CHECKING"
                        && /đã được giữ/i.test(getApiErrorMessage(e, ""))) {
                        continue;
                    }
                    failures.push(`${describePosition(q)}: ${getApiErrorMessage(e, "không lưu được")}`);
                }
            }
        } finally {
            if (!unmountedRef.current) {
                message.destroy(progressKey);
                setBulkSaving(false);
            }
        }
        if (unmountedRef.current) return;

        const saved = shared + privateCount;
        if (saved > 0) {
            message.success(
                privateCount === 0
                    ? `Đã lưu ${saved} câu vào ngân hàng chung.`
                    : shared === 0
                        ? `Đã lưu ${saved} câu vào ngân hàng riêng của thầy/cô.`
                        : `Đã lưu ${saved} câu: ${shared} câu vào ngân hàng chung, ${privateCount} câu vào ngân hàng riêng.`
            );
        }
        if (failures.length > 0) {
            Modal.warning({
                title: `${failures.length} câu chưa lưu được`,
                content: (
                    <ul style={{ paddingLeft: 20, margin: 0 }}>
                        {failures.map((f) => <li key={f}>{f}</li>)}
                    </ul>
                ),
                okText: "Đã hiểu",
            });
        }
        await loadDetail();
    };

    const handleBulkKeep = () => {
        if (bulkCandidates.length === 0) return;
        const targets = [...bulkCandidates];
        const bankLabel = detail.bankKeepVisibility === "private" ? "ngân hàng riêng của thầy/cô" : "ngân hàng chung";
        // Liệt kê theo Phần: "Phần I – Câu 1, 2, 4"
        const byPart = parts
            .map((p) => ({
                part: p.part,
                numbers: targets.filter((t) => t.part === p.part).map((t) => getNumber(t, 0)),
            }))
            .filter((p) => p.numbers.length > 0);
        Modal.confirm({
            title: `Lưu ${targets.length} câu chưa có cảnh báo vào ${bankLabel}?`,
            content: (
                <div>
                    <ul style={{ paddingLeft: 20, margin: 0 }}>
                        {byPart.map((p) => <li key={p.part}>Phần {p.part} – Câu {p.numbers.join(", ")}</li>)}
                    </ul>
                    <p style={{ marginTop: 8, marginBottom: 0 }}>
                        Câu cần xem kỹ, câu đang soạn và câu đang tự kiểm tra lại không được lưu ở bước này.
                    </p>
                </div>
            ),
            okText: `Lưu ${targets.length} câu`,
            cancelText: "Để tôi xem lại",
            // Tiêu điểm mặc định ở nút an toàn (G8)
            autoFocusButton: "cancel",
            onOk: () => {
                void runBulkKeep(targets);
            },
        });
    };

    // ===== Soạn lại hàng loạt (G20): chỉ câu chưa soạn được vì hệ thống bận (llm_error) =====
    // Câu qc_exhausted đã hỏng 3 lần: soạn lại không kèm yêu cầu dễ hỏng tiếp, nên giáo viên soạn lại riêng từng câu
    const regenerateCandidates = allQuestions.filter(
        (q) => q.slotId !== undefined && q.state === "failed" && q.failureReason === "llm_error"
    );
    const otherFailedCount = allQuestions.filter((q) => q.state === "failed").length - regenerateCandidates.length;

    const runBulkRegenerate = async (targets: ExamQuestionItem[]) => {
        if (!examId) return;
        const progressKey = "bulk-regenerate";
        setBulkSaving(true);
        let sent = 0;
        let stoppedError: unknown = null;
        const failures: string[] = [];
        try {
            for (let i = 0; i < targets.length; i++) {
                if (unmountedRef.current) return;
                const q = targets[i];
                message.loading({ key: progressKey, content: `Đang gửi soạn lại ${i + 1}/${targets.length} câu…`, duration: 0 });
                try {
                    // Không gửi yêu cầu, không gửi requestCodes: backend dùng yêu cầu thừa hưởng của ô/đề như hộp Soạn lại
                    await replaceQuestion(examId, q.slotId!, { source: "generate" });
                    sent++;
                } catch (e: unknown) {
                    const status = (e as { response?: { status?: number } })?.response?.status;
                    // Chạm giới hạn hàng chờ / hạn mức ngày (429): dừng hẳn, câu sau cũng sẽ bị từ chối
                    if (status === 429) {
                        stoppedError = e;
                        break;
                    }
                    // Chỗ vừa chuyển sang đang soạn (409, ví dụ tab khác): bỏ qua; lỗi khác ghi lại rồi chạy tiếp
                    if (status !== 409) {
                        failures.push(`${describePosition(q)}: ${getApiErrorMessage(e, "không gửi được")}`);
                    }
                }
            }
        } finally {
            if (!unmountedRef.current) {
                message.destroy(progressKey);
                setBulkSaving(false);
            }
        }
        if (unmountedRef.current) return;

        if (stoppedError) {
            // Thông báo hạn mức của backend có sẵn số liệu và giờ thử lại
            showApiError(stoppedError, "Chưa soạn lại được lúc này.");
            message.warning(
                `Đã gửi soạn lại ${sent}/${targets.length} câu, ${targets.length - sent} câu còn lại thầy/cô thử lại sau.`,
                8
            );
        } else if (sent > 0) {
            message.success(`Đã gửi soạn lại ${sent} câu. Trang sẽ tự cập nhật khi soạn xong.`);
        }
        if (failures.length > 0) {
            Modal.warning({
                title: `${failures.length} câu chưa gửi soạn lại được`,
                content: (
                    <ul style={{ paddingLeft: 20, margin: 0 }}>
                        {failures.map((f) => <li key={f}>{f}</li>)}
                    </ul>
                ),
                okText: "Đã hiểu",
            });
        }
        await loadDetail();
    };

    const handleBulkRegenerate = () => {
        if (regenerateCandidates.length === 0) return;
        const targets = [...regenerateCandidates];
        const byPart = parts
            .map((p) => ({
                part: p.part,
                numbers: targets.filter((t) => t.part === p.part).map((t) => getNumber(t, 0)),
            }))
            .filter((p) => p.numbers.length > 0);
        Modal.confirm({
            title: `Soạn lại ${targets.length} câu chưa soạn được vì hệ thống bận?`,
            content: (
                <div>
                    <ul style={{ paddingLeft: 20, margin: 0 }}>
                        {byPart.map((p) => <li key={p.part}>Phần {p.part} – Câu {p.numbers.join(", ")}</li>)}
                    </ul>
                    <p style={{ marginTop: 8, marginBottom: 0 }}>
                        Mỗi câu soạn lại tính 1 lượt trong hạn mức soạn câu mỗi ngày.
                    </p>
                    {otherFailedCount > 0 && (
                        <p style={{ marginTop: 8, marginBottom: 0 }}>
                            {otherFailedCount} câu khác chưa đạt yêu cầu sau nhiều lần soạn: thầy/cô bấm Soạn lại ở từng câu
                            để ghi thêm yêu cầu.
                        </p>
                    )}
                </div>
            ),
            okText: `Soạn lại ${targets.length} câu`,
            cancelText: "Để tôi xem lại",
            autoFocusButton: "cancel",
            onOk: () => {
                void runBulkRegenerate(targets);
            },
        });
    };

    // Hộp xuất Word → "Xem các câu này" (G23, phác thảo 7.5): bật lọc câu cần xem và cuộn tới câu cần xem kỹ đầu tiên
    const showFlaggedQuestions = () => {
        setOnlyNeedsAttention(true);
        const first = allQuestions.find((q) => q.state === "ready" && needsReviewNow(q));
        if (first) window.setTimeout(() => jumpToQuestion(first), 0);
    };

    // ===== Sửa ngay trên thẻ (CT-2) =====
    /** Đang sửa dở thì hỏi bỏ thay đổi trước khi làm `then`; không sửa dở thì làm ngay. */
    const confirmDiscardEdit = (then: () => void) => {
        if (!editDirty) {
            then();
            return;
        }
        Modal.confirm({
            title: "Bỏ các thay đổi chưa lưu?",
            content: "Thầy/cô đang sửa dở một câu. Tiếp tục thì bản đang sửa sẽ mất.",
            okText: "Bỏ thay đổi",
            cancelText: "Tiếp tục sửa",
            okButtonProps: { danger: true },
            autoFocusButton: "cancel",
            onOk: () => {
                setEditDirty(false);
                then();
            },
        });
    };

    const startEdit = (question: ExamQuestionItem) => {
        if (question.slotId === undefined || question.slotId === editingSlotId) return;
        const slotId = question.slotId;
        confirmDiscardEdit(() => {
            // Bỏ bản sửa dở (lưu tạm trong tab) của câu đang sửa trước đó
            if (editingSlotId !== undefined) clearEditDraft(Number(examId), editingSlotId);
            setEditDirty(false);
            setEditingSlotId(slotId);
            // Đưa thẻ lên đầu màn hình để thấy cả form
            window.setTimeout(() => {
                document.getElementById(examSlotAnchorId(slotId))?.scrollIntoView({ behavior: "smooth", block: "start" });
            }, 0);
        });
    };

    const closeEditor = () => {
        setEditDirty(false);
        setEditingSlotId(undefined);
    };

    const handleSaveEditedQuestion = async () => {
        closeEditor();
        await loadDetail();
    };

    // Dòng "Đang cập nhật" (trước nằm ở tiêu đề card Danh sách câu hỏi, đã bỏ để câu đầu lên màn đầu, G1): nay ở ô tóm tắt
    const pendingText = hasPendingWork
        ? [
            pendingSummary.generatingCount > 0 ? `${pendingSummary.generatingCount} câu đang soạn` : "",
            pendingSummary.checkingCount > 0 ? `${pendingSummary.checkingCount} câu đang tự kiểm tra lại` : "",
        ].filter(Boolean).join(", ")
        : undefined;

    return (
        <div className="exam-detail-page">
            <div className="exam-detail-page-header">
                <div className="exam-detail-page-header-content">
                    <div className="exam-detail-page-title-section">
                        {/* Nút quay lại nhỏ cùng hàng tiêu đề (G1: bớt một hàng ở đầu trang) */}
                        <div className="exam-detail-page-title-row">
                            <Button
                                type="text"
                                icon={<ArrowLeftOutlined />}
                                className="exam-detail-page-back-button"
                                onClick={() => navigate("/exams")}
                            >
                                Đề thi
                            </Button>
                            <Title level={2} className="exam-detail-page-title">
                                {detail.title}
                            </Title>
                        </div>

                        <Space size={8} wrap className="exam-detail-page-meta">
                            <Text>{examStatusConfig[detail.status]?.label ?? detail.status}</Text>
                            {detail.kind && (
                                <Text>{examKindConfig[detail.kind]?.label ?? detail.kind}</Text>
                            )}
                            {subjectName && <Text type="secondary">{subjectName}</Text>}
                            <Text type="secondary">Mã hệ thống: {detail.examCode}</Text>
                        </Space>
                    </div>

                    <Space size={8} className="exam-detail-page-actions">
                        {/* Giao bài online: cần mọi câu đã Giữ/Sửa (backend cũng kiểm, 409 ASSIGN_NOT_ALL_KEPT) */}
                        <Tooltip title={notKeptCount > 0 ? `Còn ${notKeptCount} câu chưa Giữ. Giữ hết các câu rồi mới giao bài được.` : undefined}>
                            <span>
                                <Button
                                    icon={<SendOutlined />}
                                    disabled={notKeptCount > 0}
                                    onClick={() => setAssignOpen(true)}
                                >
                                    Giao bài online
                                </Button>
                            </span>
                        </Tooltip>
                        <Button icon={<ProfileOutlined />} onClick={() => navigate(`/exams/${examId}/assignments`)}>
                            Bài đã giao
                        </Button>
                        {/* Bấm là mở hộp xuất Word (thời gian, mã đề, tên file, công thức, chỉ đề; cảnh báo NV-40/NV-41 trong hộp) */}
                        <Button type="primary" icon={<FileWordOutlined />} onClick={() => setExportOpen(true)}>
                            Xuất file Word
                        </Button>
                    </Space>
                    <ExportWordDialog
                        open={exportOpen}
                        examId={examId!}
                        detail={detail}
                        onClose={() => setExportOpen(false)}
                        onShowFlagged={showFlaggedQuestions}
                    />
                </div>
            </div>

            {/* Ô tóm tắt thay cho dòng tiến độ và bảng "Ma trận cấu trúc đề thi" (CT-1, CT-4) */}
            <div className="exam-detail-page-summary">
                <ExamSummaryBar
                    counts={summaryCounts}
                    partsBreakdown={partsBreakdown}
                    progressText={progress?.status === "generating" ? progressView?.text : undefined}
                    progressPercent={progress?.status === "generating" ? progressPercent : undefined}
                    pendingText={pendingText}
                    // G19: lời nhắc thường trực khi đề còn câu đang soạn (trước nằm cạnh nút ở đầu trang, làm phình hàng nút)
                    exportNote={isGenerating ? 'Vẫn xuất Word được; câu chưa soạn xong sẽ ghi "Chưa có câu".' : undefined}
                    onlyNeedsAttention={onlyNeedsAttention}
                    onOnlyNeedsAttentionChange={setOnlyNeedsAttention}
                    showAllExplanations={showAllExplanations}
                    onShowAllExplanationsChange={setShowAllExplanations}
                    onExport={() => setExportOpen(true)}
                    exportLoading={false}
                    bulkKeepCount={bulkCandidates.length}
                    bulkSaving={bulkSaving}
                    onBulkKeep={handleBulkKeep}
                    bulkRegenerateCount={regenerateCandidates.length}
                    onBulkRegenerate={handleBulkRegenerate}
                />
            </div>

            <AssignExamModal
                open={assignOpen}
                examId={examId!}
                onClose={() => setAssignOpen(false)}
                onCreated={() => undefined}
            />

            {/* Bàn làm việc: mục lục cố định bên trái, danh sách câu bên phải */}
            <div className="exam-detail-page-workspace">
                {parts.length > 0 && (
                    <aside className="exam-detail-page-nav">
                        <ExamQuestionNav parts={parts} getNumber={getNumber} onJump={jumpToQuestion} />
                    </aside>
                )}

            <div className="exam-detail-page-question-list">
                <Card className="exam-detail-page-question-card">
                    {parts.length === 0 && (
                        <ExamQuestionList questions={[]} />
                    )}

                    {onlyNeedsAttention && parts.length > 0 && visibleParts.length === 0 && (
                        <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description="Không còn câu cần xem kỹ hay câu chưa soạn được."
                        />
                    )}

                    {visibleParts.map((p) => (
                        <div key={p.part} className="question-group">
                            <Title level={5} className="question-group-title">
                                {PART_TITLES[p.part] ?? `Phần ${p.part}`}{" "}
                                <Text type="secondary">({p.ready}/{p.required} câu)</Text>
                            </Title>
                            <ExamQuestionList
                                questions={p.questions}
                                getNumber={getNumber}
                                showAllExplanations={showAllExplanations}
                                onJumpTo={jumpToPosition}
                                flashSlotId={flashSlotId}
                                onChangeQuestion={(question) => {
                                    setChangeQuestion(question);
                                }}
                                onKeep={handleKeep}
                                bankKeepVisibility={detail.bankKeepVisibility}
                                onEdit={startEdit}
                                editingSlotId={editingSlotId}
                                renderEditor={(question) => (
                                    <EditQuestionForm
                                        // Đổi chỗ thì mở form mới; trang tự tải lại đề không xóa chữ đang gõ
                                        key={question.slotId}
                                        question={question}
                                        positionLabel={describePosition(question)}
                                        examId={Number(examId)}
                                        onSaved={handleSaveEditedQuestion}
                                        onCancel={closeEditor}
                                        onDirtyChange={setEditDirty}
                                    />
                                )}
                                onViewQcTrace={(question, index) => {
                                    if (question.slotId !== undefined) openQcTrace(question.slotId, getNumber(question, index));
                                }}
                            />
                        </div>
                    ))}

                    <ChangeQuestionModal
                        open={changeQuestion !== null}
                        question={changeQuestion}
                        positionLabel={changeQuestion ? describePosition(changeQuestion) : undefined}
                        loading={false}
                        onCancel={() => setChangeQuestion(null)}
                        onGenerate={handleGenerateReplacement}
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
        </div>
    );
}