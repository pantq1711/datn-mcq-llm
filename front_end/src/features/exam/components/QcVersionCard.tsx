import type { FC, ReactNode } from "react";
import { Space, Tag, Typography } from "antd";
import {
    CheckCircleOutlined,
    ClockCircleOutlined,
    ExclamationCircleOutlined,
    FileSearchOutlined,
} from "@ant-design/icons";

import type { QcVersion } from "../types/qcTrace.types";
import { describeReviewReason } from "../../../utils/exam.utils";
import {
    describeAnswerCheck,
    describeGrounding,
    describeJudgeAnswer,
    describeQuestionStatus,
    formatLabels,
    formatQcTime,
    tagColor,
    type QcTone,
} from "../../../utils/qcTrace.utils";

const { Text, Paragraph } = Typography;

interface Props {
    version: QcVersion;
    isLast: boolean;
}

function markerIcon(tone: QcTone) {
    if (tone === "success") return <CheckCircleOutlined />;
    if (tone === "error" || tone === "warning") return <ExclamationCircleOutlined />;
    return <FileSearchOutlined />;
}

/** Một dòng QC: tên bước, tag kết luận, chi tiết. */
const QcRow: FC<{ name: string; tone: QcTone; label: string; children?: ReactNode }> = ({ name, tone, label, children }) => (
    <div className="qc-trace-modal__qc-row">
        <Text type="secondary" className="qc-trace-modal__qc-name">{name}</Text>
        <div className="qc-trace-modal__qc-body">
            <Tag color={tagColor(tone)}>{label}</Tag>
            {children}
        </div>
    </div>
);

const NoData: FC<{ name: string }> = ({ name }) => (
    <div className="qc-trace-modal__qc-row">
        <Text type="secondary" className="qc-trace-modal__qc-name">{name}</Text>
        <Text type="secondary" className="qc-trace-modal__no-details">Không có dữ liệu</Text>
    </div>
);

const List: FC<{ items: string[]; type?: "danger" | "warning" | "secondary" }> = ({ items, type }) =>
    items.length === 0 ? null : (
        <ul className="qc-trace-modal__qc-list">
            {items.map((item, index) => (
                <li key={index}><Text type={type}>{item}</Text></li>
            ))}
        </ul>
    );

function describeAnswer(version: QcVersion): string | null {
    const answer = version.answer;
    if (!answer) return null;
    if (answer.correctOption) return `Đáp án ${answer.correctOption}`;
    if (answer.saAnswer) return `Đáp số ${answer.saAnswer}`;
    if (answer.labels) return formatLabels(answer.labels);
    return null;
}

/** Thẻ một phiên bản câu trong một lần sinh: thông tin phiên bản và 4 bước QC (docs/api mục 7.7). */
export const QcVersionCard: FC<Props> = ({ version, isLast }) => {
    const status = describeQuestionStatus(version.status);
    const qc = version.qc;
    const answer = describeAnswer(version);

    return (
        <div className={`qc-trace-modal__step qc-trace-modal__step--${status.tone}`}>
            <div className="qc-trace-modal__step-rail">
                <div className="qc-trace-modal__step-marker">{markerIcon(status.tone)}</div>
                {!isLast && <div className="qc-trace-modal__step-line" />}
            </div>

            <div className="qc-trace-modal__step-card">
                <div className="qc-trace-modal__step-header">
                    <Space size={6} wrap className="qc-trace-modal__step-heading">
                        <Text strong className="qc-trace-modal__step-action">
                            {version.kind === "teacher_edit"
                                ? `GV sửa${version.editedBy?.name ? ` · ${version.editedBy.name}` : ""}`
                                : "Bản LLM"}
                        </Text>
                        <Tag color={tagColor(status.tone)}>{status.label}</Tag>
                        {version.current && <Tag color="purple">Đang dùng</Tag>}
                        <Text type="secondary">#{version.questionId}</Text>
                    </Space>

                    <div className="qc-trace-modal__step-time">
                        <ClockCircleOutlined />
                        <Text type="secondary">{formatQcTime(version.createdAt)}</Text>
                    </div>
                </div>

                {(answer || (version.changedFields && version.changedFields.length > 0)) && (
                    <div className="qc-trace-modal__step-details">
                        {answer && <div>{answer}</div>}
                        {version.changedFields && version.changedFields.length > 0 && (
                            <div>
                                Trường đã sửa: {version.changedFields.join(", ")}
                                {version.editDistance != null && ` (khoảng cách sửa ${version.editDistance})`}
                            </div>
                        )}
                    </div>
                )}

                <div className="qc-trace-modal__qc">
                    {qc?.format ? (
                        <QcRow
                            name="Định dạng"
                            tone={qc.format.passed ? "success" : "error"}
                            label={qc.format.passed ? "Đạt" : "Không đạt"}
                        >
                            <List items={qc.format.errors} type="danger" />
                            <List items={qc.format.warnings} type="warning" />
                            <List items={qc.format.autoRepairs.map((r) => `Tự sửa: ${r}`)} type="secondary" />
                        </QcRow>
                    ) : (
                        <NoData name="Định dạng" />
                    )}

                    {qc?.dedup ? (
                        <QcRow
                            name="Trùng lặp"
                            tone={qc.dedup.passed ? "success" : "error"}
                            label={qc.dedup.passed ? "Không trùng" : "Trùng"}
                        >
                            {qc.dedup.similarity != null && (
                                <Text type="secondary">
                                    Độ giống {qc.dedup.similarity.toFixed(2)}
                                    {qc.dedup.threshold != null && ` / ngưỡng ${qc.dedup.threshold.toFixed(2)}`}
                                    {qc.dedup.comparedTo &&
                                        ` · so với câu #${qc.dedup.comparedTo.questionId}${qc.dedup.comparedTo.inThisExam ? " (trong đề này)" : ""
                                        }${qc.dedup.comparedTo.inBank ? " (trong ngân hàng)" : ""}`}
                                </Text>
                            )}
                        </QcRow>
                    ) : (
                        <NoData name="Trùng lặp" />
                    )}

                    {qc?.answerCheck ? (
                        (() => {
                            const check = describeAnswerCheck(qc.answerCheck);
                            const judge = qc.answerCheck.state === "inconsistent" ? describeJudgeAnswer(qc.answerCheck) : null;
                            return (
                                <QcRow name="Solver" tone={check.tone} label={check.label}>
                                    {judge && <Text type="danger">Solver chọn: {judge}</Text>}
                                    {qc.answerCheck.solverReasoning && (
                                        <Paragraph
                                            type="secondary"
                                            className="qc-trace-modal__qc-reasoning"
                                            ellipsis={{ rows: 2, expandable: true, symbol: "Xem lập luận" }}
                                        >
                                            {qc.answerCheck.solverReasoning}
                                        </Paragraph>
                                    )}
                                </QcRow>
                            );
                        })()
                    ) : (
                        <NoData name="Solver" />
                    )}

                    {/* Chỉ câu sinh từ đề có tài liệu mới có bước này (docs/api mục 7.7a) */}
                    {qc?.grounding && (() => {
                        const grounding = describeGrounding(qc.grounding.state);
                        return (
                            <QcRow name="Bám tài liệu" tone={grounding.tone} label={grounding.label}>
                                {qc.grounding.reason && <Text type="secondary">{qc.grounding.reason}</Text>}
                                {qc.grounding.evidenceQuote && (
                                    <Text italic className="qc-trace-modal__qc-quote">
                                        “{qc.grounding.evidenceQuote}”
                                        {qc.grounding.evidenceFound === false && (
                                            <Text type="warning"> (không tìm thấy trong tài liệu)</Text>
                                        )}
                                    </Text>
                                )}
                                {qc.grounding.error && <Text type="danger">{qc.grounding.error}</Text>}
                            </QcRow>
                        );
                    })()}
                </div>

                {version.reviewReasons && version.reviewReasons.length > 0 && (
                    <Space size={[4, 4]} wrap className="qc-trace-modal__qc-reasons">
                        <Text type="secondary">Cần xem kỹ:</Text>
                        {version.reviewReasons.map((reason) => (
                            <Tag key={reason} color="orange">{describeReviewReason(reason)}</Tag>
                        ))}
                    </Space>
                )}
            </div>
        </div>
    );
};

export default QcVersionCard;
