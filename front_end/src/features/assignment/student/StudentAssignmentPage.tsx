import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { Alert, Button, ConfigProvider, Spin, message } from "antd";

import { getCurrentAttempt, getPublicAssignment, startAttempt } from "../services/publicAttemptApi";
import type { AttemptView, PublicAssignmentInfo } from "../types/assignment.types";
import { clearDraft, clearSession, loadDraft, loadSession, saveSession } from "../utils/attemptStorage";
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
    | { name: "confirm_new"; classLabel: string; seatNo: number }
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

    // Chọn lớp và nhập số thứ tự (màn chào)
    const [classLabel, setClassLabel] = useState<string>("");
    const [seatText, setSeatText] = useState("");
    const [formError, setFormError] = useState<string | undefined>();

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
                    setNotice("Bài làm cũ trên máy này không còn dùng được. Em chọn lớp và nhập số thứ tự để vào lại.");
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
        const seatNo = Number(seatText);
        if (!selectedClass) {
            setFormError("Em chọn lớp của em.");
            return;
        }
        if (!seatText || !Number.isInteger(seatNo) || seatNo < 1 || seatNo > selectedClass.size) {
            setFormError(`Số thứ tự của lớp ${selectedClass.classLabel} phải từ 1 đến ${selectedClass.size}.`);
            return;
        }
        setFormError(undefined);
        setNotice(undefined);
        setPhase({ name: "confirm_new", classLabel: selectedClass.classLabel, seatNo });
    };

    const beginAttempt = async (label: string, seatNo: number) => {
        setBusy(true);
        try {
            const view = await startAttempt(code, label, seatNo);
            if (!view.attemptToken) throw new Error("Máy chủ không trả mã lượt làm.");
            const saved = saveSession(code, { token: view.attemptToken, classLabel: view.classLabel, seatNo: view.seatNo });
            setStorageBlocked(!saved);
            setPhase({ name: "exam", view, token: view.attemptToken });
        } catch (error) {
            const status = statusOf(error);
            if (status === 409 && (error as ErrorLike).response?.data?.code === "ASSIGNMENT_CLOSED") {
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
        setPhase({ name: "left" });
    }, [code]);

    const refreshOutcome = useCallback(
        async (token: string, quiet = false): Promise<boolean> => {
            setBusy(true);
            try {
                const view = await getCurrentAttempt(token);
                setPhase({ name: "outcome", view, token, anchorServer: view.serverNow, anchorLocalMs: Date.now() });
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
                    <p className="student-muted">Em có thể đóng trang. Muốn vào lại, em mở lại link và chọn lớp, số thứ tự.</p>
                    <Button size="large" block onClick={goWelcome}>
                        Vào bằng lớp và số khác
                    </Button>
                </section>
            );
            break;
        case "confirm_new":
            content = (
                <IdentityConfirm
                    classLabel={phase.classLabel}
                    seatNo={phase.seatNo}
                    note={`Bấm Bắt đầu là đồng hồ ${info?.durationMinutes ?? ""} phút chạy ngay.`}
                    confirmText="Đúng, bắt đầu làm bài"
                    rejectText="Không phải, nhập lại"
                    busy={busy}
                    onConfirm={() => void beginAttempt(phase.classLabel, phase.seatNo)}
                    onReject={goWelcome}
                />
            );
            break;
        case "resume_confirm": {
            const { view, token } = phase;
            const canContinue = !view.submitted && !!view.parts;
            content = (
                <IdentityConfirm
                    classLabel={view.classLabel}
                    seatNo={view.seatNo}
                    duplicateSeat={view.duplicateSeat}
                    note={
                        canContinue
                            ? "Máy này đang có bài làm dở. Nếu đúng là em, em làm tiếp với thời gian còn lại."
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
                    error={formError}
                    onClass={(label) => {
                        setClassLabel(label);
                        setFormError(undefined);
                    }}
                    onSeat={(text) => {
                        setSeatText(text.replace(/\D/g, "").slice(0, 2));
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
    error?: string;
    onClass: (label: string) => void;
    onSeat: (text: string) => void;
    onNext: () => void;
}

function Welcome({ info, notice, classLabel, seatText, error, onClass, onSeat, onNext }: WelcomeProps) {
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
                        Không nhập họ tên. Hệ thống chỉ lưu lớp, số thứ tự và bài làm của em để thầy cô xem điểm.
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

                    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

                    <Button type="primary" size="large" block onClick={onNext}>
                        Tiếp tục
                    </Button>
                </>
            )}
        </section>
    );
}
