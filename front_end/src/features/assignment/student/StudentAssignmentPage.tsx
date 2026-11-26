import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { Alert, Button, ConfigProvider, Spin, message } from "antd";

import { getCurrentAttempt, getPublicAssignment, startAttempt } from "../services/publicAttemptApi";
import { ATTEMPT_IDENTITY_TAKEN, type AttemptView, type PublicAssignmentInfo } from "../types/assignment.types";
import { clearDraft, clearSession, loadDraft, loadSession, saveSession } from "../utils/attemptStorage";
import { NAME_MAX, NAME_MIN, isNameMode, normalizeStudentName } from "../utils/identity";
import type { SubmitOutcome } from "../utils/submitRetry";
import { formatServerTime } from "../utils/timeFormat";
import AttemptOutcome from "./AttemptOutcome";
import ExamSession from "./ExamSession";
import IdentityConfirm from "./IdentityConfirm";

import "./student.css";

type Phase =
    | { name: "loading" }
    | { name: "not_found" }
    | { name: "load_error"; text: string }
    | { name: "welcome" }
    // Chế độ số thứ tự có seatNo, chế độ họ tên có studentName. taken: máy chủ báo trùng trước khi tạo lượt (25/11).
    | { name: "confirm_new"; classLabel: string; seatNo?: number; studentName?: string; taken?: boolean }
    | { name: "resume_confirm"; view: AttemptView; token: string }
    | { name: "exam"; view: AttemptView; token: string }
    | { name: "outcome"; view: AttemptView; token: string; anchorServer: string; anchorLocalMs: number }
    | { name: "left" };

type ErrorLike = { response?: { status?: number; data?: { error?: string; code?: string } } };

function statusOf(error: unknown): number | undefined {
    return (error as ErrorLike)?.response?.status;
}

/** Câu báo lỗi cho học sinh: lấy câu tiếng Việt của backend; không có phản hồi là mất mạng. */
function errorText(error: unknown, fallback: string): string {
    const e = error as ErrorLike;
    if (!e?.response) return "Không kết nối được máy chủ. Em kiểm tra mạng rồi thử lại.";
    return e.response.data?.error || fallback;
}

/** Khoảng chờ tối đa giữa các lần tự kiểm tra kết quả; sau MAX_AUTO_CHECKS lần thì em tự bấm "Xem kết quả". */
const AUTO_CHECK_MARGIN_MS = 3000;
const AUTO_CHECK_MIN_MS = 30000;
const MAX_AUTO_CHECKS = 10;

export default function StudentAssignmentPage() {
    const { code = "" } = useParams<{ code: string }>();
    const [info, setInfo] = useState<PublicAssignmentInfo | null>(null);
    const [phase, setPhase] = useState<Phase>({ name: "loading" });
    const [notice, setNotice] = useState<string | undefined>();
    const [storageBlocked, setStorageBlocked] = useState(false);
    const [reloadKey, setReloadKey] = useState(0);
    const [busy, setBusy] = useState(false);

    // Chọn lớp và nhập số thứ tự hoặc họ tên (màn chào), theo chế độ của bài giao
    const [classLabel, setClassLabel] = useState<string>("");
    const [seatText, setSeatText] = useState("");
    const [nameText, setNameText] = useState("");
    const [formError, setFormError] = useState<string | undefined>();
    const nameMode = isNameMode(info?.identityMode);

    // Mở trang: tải thông tin bài; nếu máy này có token thì tải luôn lượt làm để hỏi lại "Em là lớp X, số Y"
    useEffect(() => {
        let cancelled = false;
        (async () => {
            setPhase({ name: "loading" });
            const session = loadSession(code);
            const infoPromise = getPublicAssignment(code).then(
                (value) => ({ ok: true as const, value }),
                (error: unknown) => ({ ok: false as const, error })
            );
            const viewPromise = session
                ? getCurrentAttempt(session.token).then(
                    (value) => ({ ok: true as const, value }),
                    (error: unknown) => ({ ok: false as const, error })
                )
                : Promise.resolve(null);
            const [infoResult, viewResult] = await Promise.all([infoPromise, viewPromise]);
            if (cancelled) return;

            if (!infoResult.ok) {
                if (statusOf(infoResult.error) === 404) setPhase({ name: "not_found" });
                else setPhase({ name: "load_error", text: errorText(infoResult.error, "Chưa mở được bài.") });
                return;
            }
            setInfo(infoResult.value);

            if (session && viewResult) {
                if (viewResult.ok) {
                    setPhase({ name: "resume_confirm", view: viewResult.value, token: session.token });
                    return;
                }
                if (statusOf(viewResult.error) === 401) {
                    // Lượt cũ không còn (thầy cô đã xóa): bỏ token trên máy, cho em vào lại từ đầu
                    clearSession(code);
                    setNotice(
                        `Bài làm cũ trên máy này không còn dùng được. Em chọn lớp và nhập ${
                            isNameMode(infoResult.value.identityMode) ? "họ tên" : "số thứ tự"
                        } để vào lại.`
                    );
                } else {
                    // Mạng yếu hoặc máy chủ bận: giữ token trên máy, cho em thử lại
                    setPhase({ name: "load_error", text: errorText(viewResult.error, "Chưa mở lại được bài làm của em.") });
                    return;
                }
            }
            setPhase({ name: "welcome" });
        })();
        return () => {
            cancelled = true;
        };
    }, [code, reloadKey]);

    const selectedClass = info?.classes.find((c) => c.classLabel === classLabel);

    const submitWelcome = () => {
        if (!selectedClass) {
            setFormError("Em chọn lớp của em.");
            return;
        }
        if (nameMode) {
            const studentName = normalizeStudentName(nameText);
            if (!studentName) {
                setFormError(`Em nhập họ và tên của em (từ ${NAME_MIN} đến ${NAME_MAX} ký tự).`);
                return;
            }
            setFormError(undefined);
            setNotice(undefined);
            setPhase({ name: "confirm_new", classLabel: selectedClass.classLabel, studentName });
            return;
        }
        const seatNo = Number(seatText);
        if (!seatText || !Number.isInteger(seatNo) || seatNo < 1 || seatNo > selectedClass.size) {
            setFormError(`Số thứ tự của lớp ${selectedClass.classLabel} phải từ 1 đến ${selectedClass.size}.`);
            return;
        }
        setFormError(undefined);
        setNotice(undefined);
        setPhase({ name: "confirm_new", classLabel: selectedClass.classLabel, seatNo });
    };

    /**
     * Gửi P2. Trùng lớp + tên / số mà chưa xác nhận (409 ATTEMPT_IDENTITY_TAKEN, quyết định 25/11): ở lại màn xác nhận,
     * hỏi "Nhập lại" hay "Đúng là em, vẫn làm"; chưa có lượt nào, đồng hồ chưa chạy.
     */
    const beginAttempt = async (
        identity: { classLabel: string; seatNo?: number; studentName?: string },
        confirmDuplicate: boolean
    ) => {
        setBusy(true);
        try {
            const view = await startAttempt(code, {
                classLabel: identity.classLabel,
                ...(identity.studentName !== undefined ? { studentName: identity.studentName } : { seatNo: identity.seatNo }),
                confirmDuplicate,
            });
            if (!view.attemptToken) throw new Error("Máy chủ không trả mã lượt làm.");
            // Không lưu họ tên trên máy (chỉ token, lớp và số thứ tự nếu có)
            const saved = saveSession(code, {
                token: view.attemptToken,
                classLabel: view.classLabel,
                ...(typeof view.seatNo === "number" ? { seatNo: view.seatNo } : {}),
            });
            setStorageBlocked(!saved);
            setPhase({ name: "exam", view, token: view.attemptToken });
        } catch (error) {
            const status = statusOf(error);
            const errorCode = (error as ErrorLike).response?.data?.code;
            if (status === 409 && errorCode === ATTEMPT_IDENTITY_TAKEN) {
                setPhase({ name: "confirm_new", ...identity, taken: true });
                return;
            }
            if (status === 409 && errorCode === "ASSIGNMENT_CLOSED") {
                setInfo((prev) => (prev ? { ...prev, status: "closed" } : prev));
            }
            setFormError(errorText(error, "Chưa bắt đầu được. Em thử lại hoặc báo thầy cô."));
            setPhase({ name: "welcome" });
        } finally {
            setBusy(false);
        }
    };

    const leave = useCallback(() => {
        // Chỉ xóa token và câu trả lời trên máy này; lượt làm trên máy chủ vẫn còn (AN-06)
        clearSession(code);
        setClassLabel("");
        setSeatText("");
        setNameText("");
        setPhase({ name: "left" });
    }, [code]);

    const refreshOutcome = useCallback(
        async (token: string, quiet = false): Promise<boolean> => {
            setBusy(true);
            try {
                const view = await getCurrentAttempt(token);
                setPhase({ name: "outcome", view, token, anchorServer: view.serverNow, anchorLocalMs: Date.now() });
                // Em bấm "Xem kết quả" mà chưa tới giờ: màn hình không đổi nên phải báo, kẻo em tưởng nút hỏng
                if (!quiet && view.submitted && !view.resultsReleased) {
                    message.info(
                        view.resultsAt ? `Kết quả có sau ${formatServerTime(view.resultsAt)}` : "Chưa có kết quả. Em thử lại sau ít phút."
                    );
                }
                return true;
            } catch (error) {
                if (!quiet) message.warning(errorText(error, "Chưa xem được kết quả. Em thử lại sau ít phút."));
                return false;
            } finally {
                setBusy(false);
            }
        },
        []
    );

    const handleSubmitted = async (outcome: SubmitOutcome, view: AttemptView, token: string) => {
        // Máy chủ đã nhận bài: không cần giữ câu trả lời tạm trên máy nữa
        clearDraft(code);
        if (outcome.result) {
            setPhase({
                name: "outcome",
                token,
                view: {
                    ...view,
                    submitted: true,
                    submittedAt: outcome.result.submittedAt,
                    resultsAt: outcome.result.resultsAt ?? view.resultsAt,
                    resultsReleased: false,
                    parts: undefined,
                    result: undefined,
                    review: undefined,
                },
                anchorServer: outcome.result.submittedAt,
                anchorLocalMs: Date.now(),
            });
            return;
        }
        // Lần gửi trước đã tới máy chủ dù em không nhận được phản hồi: hỏi lại trạng thái
        const ok = await refreshOutcome(token, true);
        if (!ok) {
            setPhase({
                name: "outcome",
                token,
                view: { ...view, submitted: true, resultsReleased: false, parts: undefined },
                anchorServer: view.serverNow,
                anchorLocalMs: Date.now(),
            });
        }
    };

    // Tự kiểm tra kết quả khi tới giờ mở (nếu trang còn mở); có giới hạn số lần để không dồn lượt gọi
    const autoChecks = useRef(0);
    useEffect(() => {
        if (phase.name !== "outcome") return;
        const { view, token, anchorServer, anchorLocalMs } = phase;
        if (!view.submitted || view.resultsReleased || !view.resultsAt) return;
        if (!(info?.showScore || info?.showSolutions)) return;
        if (autoChecks.current >= MAX_AUTO_CHECKS) return;
        // Cả hai mốc cùng được đọc theo một cách nên chênh lệch đúng dù máy em khác múi giờ máy chủ
        const untilMs = new Date(view.resultsAt).getTime() - new Date(anchorServer).getTime() - (Date.now() - anchorLocalMs);
        const delay = Math.max(untilMs + AUTO_CHECK_MARGIN_MS, AUTO_CHECK_MIN_MS);
        if (!Number.isFinite(delay)) return;
        const timer = window.setTimeout(() => {
            autoChecks.current += 1;
            void refreshOutcome(token, true);
        }, delay);
        return () => window.clearTimeout(timer);
    }, [phase, info, refreshOutcome]);

    const goWelcome = () => {
        setNotice(undefined);
        setFormError(undefined);
        setPhase({ name: "welcome" });
    };

    let content;
    switch (phase.name) {
        case "loading":
            content = (
                <div className="student-center">
                    <Spin size="large" description="Đang mở bài…" />
                </div>
            );
            break;
        case "not_found":
            content = (
                <section className="student-card">
                    <h1 className="student-heading">Không tìm thấy bài này</h1>
                    <p className="student-muted">Em kiểm tra lại đường link hoặc mã QR thầy cô gửi.</p>
                </section>
            );
            break;
        case "load_error":
            content = (
                <section className="student-card">
                    <h1 className="student-heading">Chưa mở được bài</h1>
                    <p>{phase.text}</p>
                    <Button type="primary" size="large" block onClick={() => setReloadKey((k) => k + 1)}>
                        Thử lại
                    </Button>
                </section>
            );
            break;
        case "left":
            content = (
                <section className="student-card">
                    <h1 className="student-heading">Đã xóa bài làm khỏi máy này</h1>
                    <p className="student-muted">
                        Em có thể đóng trang. Muốn vào lại, em mở lại link và chọn lớp, {nameMode ? "nhập họ tên" : "số thứ tự"}.
                    </p>
                    <Button size="large" block onClick={goWelcome}>
                        {nameMode ? "Vào bằng lớp và tên khác" : "Vào bằng lớp và số khác"}
                    </Button>
                </section>
            );
            break;
        case "confirm_new": {
            const identity = { classLabel: phase.classLabel, seatNo: phase.seatNo, studentName: phase.studentName };
            content = (
                <IdentityConfirm
                    classLabel={phase.classLabel}
                    seatNo={phase.seatNo}
                    studentName={phase.studentName}
                    taken={phase.taken}
                    note={`Bấm Bắt đầu là đồng hồ ${info?.durationMinutes ?? ""} phút chạy ngay.`}
                    confirmText="Đúng, bắt đầu làm bài"
                    rejectText="Không phải, nhập lại"
                    busy={busy}
                    onConfirm={() => void beginAttempt(identity, false)}
                    onReject={goWelcome}
                    onConfirmDuplicate={() => void beginAttempt(identity, true)}
                    onRetype={goWelcome}
                />
            );
            break;
        }
        case "resume_confirm": {
            const { view, token } = phase;
            const canContinue = !view.submitted && !!view.parts;
            content = (
                <IdentityConfirm
                    classLabel={view.classLabel}
                    seatNo={view.seatNo}
                    studentName={view.studentName}
                    duplicateSeat={view.duplicateSeat}
                    note={
                        canContinue
                            ? "Máy này đang có bài làm dở. Nếu đúng là em, em làm tiếp với thời gian còn lại."
                            : view.studentName !== undefined
                                ? "Máy này đang lưu bài của lớp và tên này."
                                : "Máy này đang lưu bài của lớp và số này."
                    }
                    confirmText={canContinue ? "Đúng, làm tiếp" : "Đúng, xem tiếp"}
                    rejectText="Không phải em, thoát"
                    onConfirm={() =>
                        canContinue
                            ? setPhase({ name: "exam", view, token })
                            : setPhase({ name: "outcome", view, token, anchorServer: view.serverNow, anchorLocalMs: Date.now() })
                    }
                    onReject={() => {
                        clearSession(code);
                        goWelcome();
                    }}
                />
            );
            break;
        }
        case "exam":
            content = (
                <ExamSession
                    code={code}
                    title={info?.title ?? ""}
                    token={phase.token}
                    view={phase.view}
                    initialDraft={loadDraft(code)}
                    storageBlocked={storageBlocked}
                    onSubmitted={(outcome) => void handleSubmitted(outcome, phase.view, phase.token)}
                />
            );
            break;
        case "outcome":
            content = (
                <AttemptOutcome
                    view={phase.view}
                    showScore={!!info?.showScore}
                    showSolutions={!!info?.showSolutions}
                    refreshing={busy}
                    onRefresh={() => void refreshOutcome(phase.token)}
                    onDone={leave}
                />
            );
            break;
        case "welcome":
        default:
            content = info ? (
                <Welcome
                    info={info}
                    notice={notice}
                    classLabel={classLabel}
                    seatText={seatText}
                    nameMode={nameMode}
                    nameText={nameText}
                    error={formError}
                    onClass={(label) => {
                        setClassLabel(label);
                        setFormError(undefined);
                    }}
                    onSeat={(text) => {
                        setSeatText(text.replace(/\D/g, "").slice(0, 2));
                        setFormError(undefined);
                    }}
                    onName={(text) => {
                        setNameText(text);
                        setFormError(undefined);
                    }}
                    onNext={submitWelcome}
                />
            ) : null;
    }

    return (
        <ConfigProvider theme={{ token: { fontSize: 16 } }}>
            <div className="student-app">
                <main className="student-main">{content}</main>
            </div>
        </ConfigProvider>
    );
}

interface WelcomeProps {
    info: PublicAssignmentInfo;
    notice?: string;
    classLabel: string;
    seatText: string;
    /** Bài giao chế độ họ tên (quyết định 25/11): ô họ tên thay ô số thứ tự. */
    nameMode: boolean;
    nameText: string;
    error?: string;
    onClass: (label: string) => void;
    onSeat: (text: string) => void;
    onName: (text: string) => void;
    onNext: () => void;
}

function Welcome({ info, notice, classLabel, seatText, nameMode, nameText, error, onClass, onSeat, onName, onNext }: WelcomeProps) {
    const selected = info.classes.find((c) => c.classLabel === classLabel);
    const closed = info.status === "closed";
    return (
        <section className="student-card">
            <h1 className="student-title">{info.title}</h1>
            <ul className="student-facts">
                <li>{info.questionCount} câu</li>
                <li>Làm trong {info.durationMinutes} phút</li>
                <li>Hạn nộp: {formatServerTime(info.closesAt)}</li>
            </ul>

            {notice && <Alert type="info" showIcon message={notice} style={{ marginBottom: 16 }} />}

            {closed ? (
                <Alert type="warning" showIcon message="Bài đã đóng." description="Em không vào làm được nữa. Em hỏi thầy cô." />
            ) : (
                <>
                    <p className="student-privacy">
                        {nameMode
                            ? "Hệ thống lưu lớp, họ tên và bài làm của em để thầy cô xem điểm."
                            : "Không nhập họ tên. Hệ thống chỉ lưu lớp, số thứ tự và bài làm của em để thầy cô xem điểm."}
                    </p>

                    <div className="student-field">
                        <span id="student-class-label" className="student-field-label">Em học lớp nào?</span>
                        <div role="radiogroup" aria-labelledby="student-class-label" className="student-class-list">
                            {info.classes.map((c) => (
                                <button
                                    key={c.classLabel}
                                    type="button"
                                    role="radio"
                                    aria-checked={classLabel === c.classLabel}
                                    className={`student-class${classLabel === c.classLabel ? " is-selected" : ""}`}
                                    onClick={() => onClass(c.classLabel)}
                                >
                                    {c.classLabel}
                                </button>
                            ))}
                        </div>
                    </div>

                    {nameMode ? (
                        <div className="student-field">
                            <label htmlFor="student-name" className="student-field-label">
                                Họ và tên của em
                            </label>
                            {/* autoComplete tắt: máy dùng chung không gợi ý tên của bạn trước */}
                            <input
                                id="student-name"
                                className="student-name-input"
                                type="text"
                                autoComplete="off"
                                autoCapitalize="words"
                                spellCheck={false}
                                maxLength={NAME_MAX + 20}
                                placeholder="Ví dụ: Nguyễn Văn An"
                                value={nameText}
                                onChange={(e) => onName(e.target.value)}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") onNext();
                                }}
                            />
                        </div>
                    ) : (
                        <div className="student-field">
                            <label htmlFor="student-seat" className="student-field-label">
                                Số thứ tự của em{selected ? ` (từ 1 đến ${selected.size})` : ""}
                            </label>
                            <input
                                id="student-seat"
                                className="student-seat-input"
                                type="text"
                                inputMode="numeric"
                                pattern="[0-9]*"
                                autoComplete="off"
                                maxLength={2}
                                value={seatText}
                                onChange={(e) => onSeat(e.target.value)}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") onNext();
                                }}
                            />
                        </div>
                    )}

                    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

                    <Button type="primary" size="large" block onClick={onNext}>
                        Tiếp tục
                    </Button>
                </>
            )}
        </section>
    );
}
