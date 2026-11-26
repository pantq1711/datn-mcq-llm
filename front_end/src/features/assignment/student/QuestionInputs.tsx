import { Button } from "antd";

import MathText from "../../../components/common/MathText/MathText";
import type { PublicQuestion } from "../types/assignment.types";
import { looksLikeThousandsDot, sanitizeSaInput, SA_MAX_LENGTH } from "../utils/answers";

const OPTION_KEYS = ["A", "B", "C", "D"];

interface McqProps {
    question: PublicQuestion;
    value: string | undefined;
    onChange: (choice: string | undefined) => void;
}

/** Trắc nghiệm: bốn hàng, chạm cả hàng để chọn; chạm lại hàng đã chọn để bỏ chọn. */
export function McqInput({ question, value, onChange }: McqProps) {
    const labelId = `q-${question.slotId}-label`;
    return (
        <div role="radiogroup" aria-labelledby={labelId} className="student-options">
            {OPTION_KEYS.filter((k) => question.options?.[k] !== undefined).map((k) => {
                const selected = value === k;
                return (
                    <button
                        key={k}
                        type="button"
                        role="radio"
                        aria-checked={selected}
                        className={`student-option${selected ? " is-selected" : ""}`}
                        onClick={() => onChange(selected ? undefined : k)}
                    >
                        <span className="student-option-key" aria-hidden="true">{k}</span>
                        <span className="student-option-text student-math"><MathText text={question.options?.[k]} images={question.images} /></span>
                        <span className="student-visually-hidden">{`Phương án ${k}`}</span>
                    </button>
                );
            })}
        </div>
    );
}

interface TfProps {
    question: PublicQuestion;
    value: Record<string, "T" | "F"> | undefined;
    onChange: (key: string, answer: "T" | "F" | undefined) => void;
}

/** Đúng/Sai: mỗi ý hai nút Đúng / Sai; chạm lại nút đang chọn để bỏ chọn. */
export function TfInput({ question, value, onChange }: TfProps) {
    return (
        <div className="student-statements">
            {(question.statements ?? []).map((s) => {
                const current = value?.[s.key];
                return (
                    <div key={s.key} className="student-statement" role="group" aria-label={`Ý ${s.key}`}>
                        <div className="student-statement-text student-math">
                            <strong>{s.key})</strong> <MathText text={s.text} images={question.images} />
                        </div>
                        <div className="student-tf-buttons">
                            <button
                                type="button"
                                aria-pressed={current === "T"}
                                aria-label={`Ý ${s.key}: Đúng`}
                                className={`student-tf-button${current === "T" ? " is-selected" : ""}`}
                                onClick={() => onChange(s.key, current === "T" ? undefined : "T")}
                            >
                                Đúng
                            </button>
                            <button
                                type="button"
                                aria-pressed={current === "F"}
                                aria-label={`Ý ${s.key}: Sai`}
                                className={`student-tf-button${current === "F" ? " is-selected is-false" : ""}`}
                                onClick={() => onChange(s.key, current === "F" ? undefined : "F")}
                            >
                                Sai
                            </button>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}

interface SaProps {
    question: PublicQuestion;
    value: string | undefined;
    onChange: (text: string) => void;
}

/**
 * Trả lời ngắn: ô chỉ nhận chữ số , . - (tối đa 10 ký tự). Có nút "−" và "," bên cạnh vì bàn phím số của iOS không có
 * dấu trừ. Báo ngay khi gõ dạng 1.234 (backend hiểu dấu chấm trước đúng ba chữ số là ngăn hàng nghìn, chấm sai).
 */
export function SaInput({ question, value = "", onChange }: SaProps) {
    const inputId = `q-${question.slotId}-input`;
    // Dấu trừ chỉ hợp lệ ở đầu số: nút bật/tắt dấu trừ đầu, không chèn vào giữa
    const toggleMinus = () => onChange(sanitizeSaInput(value.startsWith("-") ? value.slice(1) : `-${value}`));
    const appendComma = () => onChange(sanitizeSaInput(`${value},`));
    return (
        <div className="student-sa">
            <div className="student-sa-row">
                <input
                    id={inputId}
                    className="student-sa-input"
                    type="text"
                    inputMode="decimal"
                    autoComplete="off"
                    autoCapitalize="off"
                    spellCheck={false}
                    maxLength={SA_MAX_LENGTH}
                    aria-label={`Đáp số câu ${question.number}`}
                    placeholder="Đáp số"
                    value={value}
                    onChange={(e) => onChange(sanitizeSaInput(e.target.value))}
                />
                <Button size="large" aria-label="Đổi dấu trừ" onClick={toggleMinus}>−</Button>
                <Button size="large" aria-label="Thêm dấu phẩy" onClick={appendComma}>,</Button>
            </div>
            {looksLikeThousandsDot(value) && (
                <p className="student-sa-warning" role="alert">
                    Viết liền, không dùng dấu chấm ngăn hàng nghìn. Số thập phân thì dùng dấu phẩy, ví dụ 1,234.
                </p>
            )}
        </div>
    );
}
