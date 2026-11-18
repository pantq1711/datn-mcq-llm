import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Spin, Table, Tag, Tooltip, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { ClockCircleOutlined, QuestionCircleOutlined, ReloadOutlined } from "@ant-design/icons";

import { getItemStats, recomputeItemStats } from "../services/assignmentApi";
import { ASSIGNMENT_OPEN, STATS_NOT_READY } from "../types/assignment.types";
import type { AssignmentStatus, ItemStat, ItemStats } from "../types/assignment.types";
import {
    flagView,
    formatPercent,
    formatRatio,
    itemLabel,
    mcqRows,
    msUntilReady,
    questionTypeLabel,
    saSummary,
    tfRows,
} from "../utils/itemStats";
import type { FlagTone } from "../utils/itemStats";
import { formatServerTime } from "../utils/timeFormat";
import { getApiErrorMessage, showApiError } from "../../../utils/apiError";

const { Text } = Typography;

// setTimeout không nhận quá 2^31 - 1 ms (khoảng 24 ngày)
const MAX_TIMER_MS = 2_000_000_000;

const FLAG_COLOR: Record<FlagTone, string | undefined> = { neutral: undefined, warn: "orange", danger: "red" };

type View =
    | { kind: "loading" }
    | { kind: "open" }
    | { kind: "notReady"; readyAt: string }
    | { kind: "error"; message: string }
    | { kind: "ready"; stats: ItemStats };

/** Lỗi của B1/B2 → trạng thái hiển thị: 409 ASSIGNMENT_OPEN, 409 STATS_NOT_READY (errors[0] là lúc có thống kê), còn lại là lỗi. */
function viewFromError(error: unknown): View {
    const response = (error as { response?: { status?: number; data?: { code?: string; errors?: unknown } } })?.response;
    if (response?.status === 409 && response.data?.code === ASSIGNMENT_OPEN) return { kind: "open" };
    if (response?.status === 409 && response.data?.code === STATS_NOT_READY) {
        const first = Array.isArray(response.data.errors) ? response.data.errors[0] : undefined;
        return { kind: "notReady", readyAt: typeof first === "string" ? first : "" };
    }
    return { kind: "error", message: getApiErrorMessage(error, "Chưa tải được thống kê câu.") };
}

interface Props {
    examId: string;
    assignmentId: string;
    status: AssignmentStatus;
}

/** Tab "Phân tích câu" của trang chi tiết bài giao (docs/api mục 11.3, thiết kế mục 5.5 và 14). */
export default function ItemStatsTab({ examId, assignmentId, status }: Props) {
    const [view, setView] = useState<View>({ kind: "loading" });
    const [reloadTick, setReloadTick] = useState(0);
    const [recomputing, setRecomputing] = useState(false);

    // Bài còn mở thì không gọi API: chắc chắn 409
    useEffect(() => {
        if (status === "open") return;
        let cancelled = false;
        getItemStats(examId, assignmentId)
            .then((stats) => {
                if (!cancelled) setView({ kind: "ready", stats });
            })
            .catch((error) => {
                if (!cancelled) setView(viewFromError(error));
            });
        return () => {
            cancelled = true;
        };
    }, [examId, assignmentId, status, reloadTick]);

    // Còn ân hạn: tự tải lại khi tới giờ
    useEffect(() => {
        if (view.kind !== "notReady") return;
        const timer = window.setTimeout(
            () => setReloadTick((t) => t + 1),
            Math.min(msUntilReady(view.readyAt, Date.now()), MAX_TIMER_MS)
        );
        return () => window.clearTimeout(timer);
    }, [view]);

    const retry = () => {
        setView({ kind: "loading" });
        setReloadTick((t) => t + 1);
    };

    const recompute = async () => {
        setRecomputing(true);
        try {
            const stats = await recomputeItemStats(examId, assignmentId);
            setView({ kind: "ready", stats });
            message.success("Đã tính lại thống kê.");
        } catch (error) {
            const next = viewFromError(error);
            if (next.kind === "error") showApiError(error, "Chưa tính lại được thống kê.");
            else setView(next);
        } finally {
            setRecomputing(false);
        }
    };

    if (status === "open" || view.kind === "open") {
        return (
            <WaitingNote
                title="Thống kê có sau khi đóng bài"
                detail="Thầy/cô đóng bài rồi chờ thêm 2 phút để học sinh còn nộp kịp, hệ thống sẽ tính p và D của từng câu."
            />
        );
    }
    if (view.kind === "notReady") {
        const when = formatServerTime(view.readyAt);
        return (
            <WaitingNote
                title={when ? `Có sau ${when}` : "Chưa có thống kê"}
                detail="Học sinh còn được nộp bài trong 2 phút sau khi đóng. Trang sẽ tự tải lại khi tới giờ."
            />
        );
    }
    if (view.kind === "error") {
        return (
            <Alert
                type="error"
                showIcon
                title={view.message}
                action={
                    <Button size="small" onClick={retry}>
                        Thử lại
                    </Button>
                }
            />
        );
    }
    if (view.kind === "loading") {
        return (
            <div className="item-stat-state">
                <Spin />
                <Text type="secondary">Đang tải thống kê câu…</Text>
            </div>
        );
    }

    return <StatsView stats={view.stats} recomputing={recomputing} onRecompute={recompute} />;
}

function WaitingNote({ title, detail }: { title: string; detail: string }) {
    return (
        <div className="item-stat-state">
            <ClockCircleOutlined className="item-stat-state-icon" />
            <div className="item-stat-state-title">{title}</div>
            <Text type="secondary">{detail}</Text>
        </div>
    );
}

function StatsView({ stats, recomputing, onRecompute }: { stats: ItemStats; recomputing: boolean; onRecompute: () => void }) {
    const columns: ColumnsType<ItemStat> = useMemo(
        () => [
            {
                title: "Câu",
                key: "label",
                width: 170,
                render: (_, item) => (
                    <div>
                        <div>{itemLabel(item.part, item.number)}</div>
                        <Text type="secondary">{questionTypeLabel(item.questionType)}</Text>
                        {item.slotChanged && (
                            <div>
                                <Tag color="blue" className="item-stat-changed-tag">
                                    Đã đổi sau khi giao
                                </Tag>
                            </div>
                        )}
                    </div>
                ),
            },
            {
                title: (
                    <Tooltip title="p (độ khó): tỉ lệ học sinh làm đúng câu. Càng gần 1 câu càng dễ.">
                        <span>
                            p <QuestionCircleOutlined />
                        </span>
                    </Tooltip>
                ),
                key: "p",
                width: 72,
                render: (_, item) => formatRatio(item.p),
            },
            {
                title: (
                    <Tooltip title="D (độ phân biệt): tỉ lệ đúng của nhóm điểm cao trừ nhóm điểm thấp. Dưới 0,2 là phân biệt kém.">
                        <span>
                            D <QuestionCircleOutlined />
                        </span>
                    </Tooltip>
                ),
                key: "d",
                width: 72,
                render: (_, item) =>
                    item.d !== null && item.d < 0 ? <Text type="danger">{formatRatio(item.d)}</Text> : formatRatio(item.d),
            },
            {
                title: "Cờ",
                key: "flags",
                render: (_, item) =>
                    item.flags.length === 0 ? (
                        <Text type="secondary">Không có cờ</Text>
                    ) : (
                        <div className="item-stat-flags">
                            {item.flags.map((code) => {
                                const flag = flagView(code, item.questionType);
                                return (
                                    <Tag key={code} color={FLAG_COLOR[flag.tone]}>
                                        {flag.label}
                                    </Tag>
                                );
                            })}
                        </div>
                    ),
            },
        ],
        []
    );

    const sampleNote =
        stats.sampleNote?.trim() ||
        `Mới có ${stats.n} lượt, cần ít nhất ${stats.minSampleSize} lượt thì D và các cờ mới đáng tin.`;

    return (
        <div className="item-stat">
            <div className="item-stat-summary">
                <div>
                    <div className="item-stat-n">
                        <strong>{stats.n} lượt làm hợp lệ</strong>
                        {stats.groupSize > 0 && <Text type="secondary"> · mỗi nhóm điểm cao/thấp {stats.groupSize} lượt</Text>}
                    </div>
                    <Text type="secondary" className="item-stat-excluded">
                        {`Đã loại ${stats.excluded.duplicate} lượt trùng STT và ${stats.excluded.notSubmitted} lượt chưa nộp. `}
                        Lượt trùng lớp và số thứ tự bị loại vì không biết lượt nào là của học sinh đó; lượt chưa nộp thì chưa có bài làm
                        hoàn chỉnh.
                    </Text>
                    {stats.computedAt && (
                        <div>
                            <Text type="secondary">Tính lúc {formatServerTime(stats.computedAt)}</Text>
                        </div>
                    )}
                </div>
                <Button icon={<ReloadOutlined />} loading={recomputing} onClick={onRecompute}>
                    Tính lại
                </Button>
            </div>

            {!stats.sampleSufficient && <Alert type="warning" showIcon className="item-stat-sample" title={sampleNote} />}

            <ul className="item-stat-help">
                <li>
                    <strong>p</strong> là tỉ lệ học sinh làm đúng câu. p càng gần 1 thì câu càng dễ, càng gần 0 thì câu càng khó. Câu đúng/sai
                    tính theo từng ý.
                </li>
                <li>
                    <strong>D</strong> cho biết câu có phân biệt được học sinh khá và học sinh yếu hay không: lấy tỉ lệ làm đúng của nhóm 27%
                    điểm cao nhất trừ tỉ lệ làm đúng của nhóm 27% điểm thấp nhất. D dưới 0,2 là phân biệt kém; D âm nghĩa là nhóm yếu làm
                    đúng nhiều hơn nhóm khá, thầy/cô nên xem lại đáp án.
                </li>
                <li>Cờ chỉ là gợi ý để thầy/cô xem lại câu, không phải kết luận câu hay dở: với một lớp, số liệu dao động khá nhiều.</li>
            </ul>

            <Table<ItemStat>
                rowKey={rowKey}
                columns={columns}
                dataSource={stats.items}
                pagination={false}
                scroll={{ x: 560 }}
                expandable={{
                    expandRowByClick: true,
                    expandedRowRender: (item) => <ItemDetail item={item} stats={stats} />,
                }}
                locale={{ emptyText: "Chưa có lượt nào được tính nên chưa có số liệu." }}
            />
        </div>
    );
}

function rowKey(item: ItemStat): string {
    return item.slotId !== null && item.slotId !== undefined ? String(item.slotId) : `${item.part}-${item.number}`;
}

function ItemDetail({ item, stats }: { item: ItemStat; stats: ItemStats }) {
    return (
        <div className="item-stat-detail" data-testid={`item-detail-${rowKey(item)}`}>
            {item.slotChanged && (
                <div className="item-stat-changed-note">
                    Câu này đã được đổi sau khi giao bài. Số liệu dưới đây là của câu học sinh đã làm lúc giao bài, không phải câu đang ở đề
                    bây giờ.
                </div>
            )}
            {item.questionType === "mcq" && <McqDetail item={item} stats={stats} />}
            {item.questionType === "tf" && <TfDetail item={item} />}
            {item.questionType === "sa" && <SaDetail item={item} />}
        </div>
    );
}

function McqDetail({ item, stats }: { item: ItemStat; stats: ItemStats }) {
    const rows = mcqRows(item);
    return (
        <table className="item-stat-detail-table">
            <thead>
                <tr>
                    <th>Phương án</th>
                    <th>Số lượt chọn</th>
                    <th>Tỉ lệ</th>
                    <th>Nhóm điểm cao ({stats.groupSize})</th>
                    <th>Nhóm điểm thấp ({stats.groupSize})</th>
                </tr>
            </thead>
            <tbody>
                {rows.map((r) => (
                    <tr key={r.key} className={r.correct ? "item-stat-correct" : undefined}>
                        <td>
                            {r.key === "blank" ? (
                                "Bỏ trống"
                            ) : (
                                <>
                                    {r.key}
                                    {r.correct && (
                                        <Tag color="green" className="item-stat-correct-tag">
                                            Đáp án đúng
                                        </Tag>
                                    )}
                                </>
                            )}
                        </td>
                        <td>{r.all}</td>
                        <td>{stats.n > 0 ? formatPercent(r.all / stats.n) : "—"}</td>
                        <td>{r.high}</td>
                        <td>{r.low}</td>
                    </tr>
                ))}
            </tbody>
        </table>
    );
}

function TfDetail({ item }: { item: ItemStat }) {
    const rows = tfRows(item);
    return (
        <table className="item-stat-detail-table">
            <thead>
                <tr>
                    <th className="item-stat-nowrap">Ý</th>
                    <th>Nhãn đúng</th>
                    <th>Chọn Đúng</th>
                    <th>Chọn Sai</th>
                    <th>Bỏ trống</th>
                    <th>Làm đúng</th>
                    <th>p</th>
                    <th>D</th>
                </tr>
            </thead>
            <tbody>
                {rows.map((r) => (
                    <tr key={r.key}>
                        {/* Chữ 16px: "Ý b" không được xuống dòng thành "Ý" / "b" */}
                        <td className="item-stat-nowrap">{`Ý ${r.key}`}</td>
                        <td>{r.label === null ? "—" : r.label ? "Đúng" : "Sai"}</td>
                        <td>{r.t}</td>
                        <td>{r.f}</td>
                        <td>{r.blank}</td>
                        <td>{r.correct}</td>
                        <td>{formatRatio(r.p)}</td>
                        <td>{formatRatio(r.d)}</td>
                    </tr>
                ))}
            </tbody>
        </table>
    );
}

function SaDetail({ item }: { item: ItemStat }) {
    const sa = saSummary(item);
    return (
        <div>
            <p>{`Đáp số đúng: ${item.answerKey ?? "—"}`}</p>
            <p>{`Làm đúng: ${sa.correct} lượt`}</p>
            <p>{`Bỏ trống: ${sa.blank} lượt`}</p>
            {sa.topWrong.length === 0 ? (
                <Text type="secondary">Chưa có đáp số sai nào được ghi nhận.</Text>
            ) : (
                <div>
                    <div>Đáp số sai hay gặp:</div>
                    <ul className="item-stat-wrong-list">
                        {sa.topWrong.map((w) => (
                            <li key={w.value}>
                                <span className="item-stat-wrong-value">{w.value}</span>
                                {` — ${w.count} lượt`}
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    );
}
