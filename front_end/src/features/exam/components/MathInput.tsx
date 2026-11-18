import {
    createContext, useCallback, useContext, useDeferredValue, useEffect, useId, useLayoutEffect, useMemo, useRef,
    useState,
    type KeyboardEvent, type ReactNode,
} from "react";
import { Form, Input } from "antd";
import type { FormItemProps } from "antd";
import type { TextAreaRef } from "antd/es/input/TextArea";

import MathText from "../../../components/common/MathText/MathText";
import {
    MATH_SNIPPETS, containsMath, hasFormulaSyntaxError, hasUnclosedMath, insertSnippet, unclosedMathRule,
} from "./mathInput.utils";

import "./MathInput.css";

/**
 * Ô nhập có công thức cho hộp Sửa câu (rà soát giao diện G9, G10, mục 7.2): dòng "Sẽ hiện là" vẽ lại công thức ngay khi
 * gõ bằng MathText (KaTeX, như thẻ câu), báo ngay khi thiếu dấu `$` đóng, và một hàng nút kí hiệu dùng chung cho cả hộp,
 * chèn vào ô đang gõ.
 */

type InsertFn = (tex: string) => void;

interface MathInputContextValue {
    register: (id: string, label: string, insert: InsertFn) => void;
    unregister: (id: string) => void;
    activate: (id: string) => void;
    insert: (tex: string) => void;
    activeLabel: string | null;
}

const MathInputContext = createContext<MathInputContextValue | null>(null);

/** Bọc phần form có các ô công thức; hàng nút kí hiệu chèn vào ô được bấm vào gần nhất (chưa bấm ô nào: ô đầu tiên). */
export function MathInputProvider({ children }: { children: ReactNode }) {
    const fields = useRef(new Map<string, { label: string; insert: InsertFn }>());
    const [active, setActive] = useState<{ id: string; label: string } | null>(null);
    const [firstLabel, setFirstLabel] = useState<string | null>(null);

    const register = useCallback((id: string, label: string, insert: InsertFn) => {
        fields.current.set(id, { label, insert });
        setFirstLabel(fields.current.values().next().value?.label ?? null);
        setActive((current) => (current?.id === id ? { id, label } : current));
    }, []);
    const unregister = useCallback((id: string) => {
        fields.current.delete(id);
        setActive((current) => (current?.id === id ? null : current));
        setFirstLabel(fields.current.values().next().value?.label ?? null);
    }, []);
    const activate = useCallback((id: string) => {
        const field = fields.current.get(id);
        if (field) setActive({ id, label: field.label });
    }, []);
    const activeId = active?.id ?? null;
    const insert = useCallback(
        (tex: string) => {
            const target = (activeId && fields.current.get(activeId)) || fields.current.values().next().value;
            target?.insert(tex);
        },
        [activeId]
    );

    const activeLabel = active?.label ?? firstLabel;
    const value = useMemo(
        () => ({ register, unregister, activate, insert, activeLabel }),
        [register, unregister, activate, insert, activeLabel]
    );
    return <MathInputContext.Provider value={value}>{children}</MathInputContext.Provider>;
}

/** Hàng nút kí hiệu. Một điểm dừng Tab, phím mũi tên trái/phải để đi giữa các nút. */
export function MathSymbolBar({ disabled = false }: { disabled?: boolean }) {
    const ctx = useContext(MathInputContext);
    const [focusIndex, setFocusIndex] = useState(0);
    const buttons = useRef<(HTMLButtonElement | null)[]>([]);
    if (!ctx) return null;

    const moveFocus = (e: KeyboardEvent<HTMLDivElement>) => {
        const last = MATH_SNIPPETS.length - 1;
        const next =
            e.key === "ArrowRight" ? (focusIndex >= last ? 0 : focusIndex + 1)
                : e.key === "ArrowLeft" ? (focusIndex <= 0 ? last : focusIndex - 1)
                    : e.key === "Home" ? 0
                        : e.key === "End" ? last
                            : -1;
        if (next < 0) return;
        e.preventDefault();
        setFocusIndex(next);
        buttons.current[next]?.focus();
    };

    return (
        <div className="math-symbol-bar">
            <div className="math-symbol-bar-caption">
                Chèn kí hiệu vào ô: <strong>{ctx.activeLabel ?? "đang gõ"}</strong>
                <span className="math-symbol-bar-tip"> · bấm vào ô khác để chèn vào ô đó; rê chuột lên nút để xem tên</span>
            </div>
            <div className="math-symbol-bar-buttons" role="toolbar" aria-label="Chèn kí hiệu toán" onKeyDown={moveFocus}>
                {MATH_SNIPPETS.map((snippet, index) => (
                    <button
                        key={snippet.tex}
                        ref={(el) => { buttons.current[index] = el; }}
                        type="button"
                        className="math-symbol-button"
                        title={snippet.title}
                        aria-label={snippet.title}
                        tabIndex={index === focusIndex ? 0 : -1}
                        disabled={disabled}
                        // Giữ con trỏ trong ô đang gõ khi bấm chuột
                        onMouseDown={(e) => e.preventDefault()}
                        onClick={() => {
                            setFocusIndex(index);
                            ctx.insert(snippet.tex);
                        }}
                    >
                        {snippet.label}
                    </button>
                ))}
            </div>
        </div>
    );
}

interface MathInputProps {
    /** Tên ô nói theo giáo viên, hiện ở hàng nút kí hiệu ("Câu dẫn", "Phương án B"...). */
    fieldLabel: string;
    value?: string;
    onChange?: (value: string) => void;
    id?: string;
    disabled?: boolean;
    placeholder?: string;
    minRows?: number;
    maxRows?: number;
}

/** Ô nhập (TextArea) nhận chèn kí hiệu từ MathSymbolBar; dùng bên trong Form.Item. */
export function MathInput({
    fieldLabel, value, onChange, id, disabled, placeholder, minRows = 1, maxRows = 6,
}: MathInputProps) {
    const ctx = useContext(MathInputContext);
    const key = useId();
    const ref = useRef<TextAreaRef>(null);
    // Đọc giá trị mới nhất trong hàm chèn mà không phải đăng kí lại mỗi lần gõ
    const latest = useRef({ value: value ?? "", onChange });
    useLayoutEffect(() => {
        latest.current = { value: value ?? "", onChange };
    }, [value, onChange]);

    useEffect(() => {
        if (!ctx) return;
        ctx.register(key, fieldLabel, (tex) => {
            const el = ref.current?.resizableTextArea?.textArea;
            const text = latest.current.value;
            const start = el?.selectionStart ?? text.length;
            const end = el?.selectionEnd ?? text.length;
            const result = insertSnippet(text, start, end, tex);
            latest.current.onChange?.(result.text);
            requestAnimationFrame(() => {
                const area = ref.current?.resizableTextArea?.textArea;
                area?.focus();
                area?.setSelectionRange(result.caret, result.caret);
            });
        });
        return () => ctx.unregister(key);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ctx?.register, ctx?.unregister, key, fieldLabel]);

    return (
        <Input.TextArea
            ref={ref}
            id={id}
            value={value}
            onChange={(e) => onChange?.(e.target.value)}
            onFocus={() => ctx?.activate(key)}
            disabled={disabled}
            placeholder={placeholder}
            autoSize={{ minRows, maxRows }}
            className="math-input-textarea"
            spellCheck={false}
        />
    );
}

/** Dòng "Sẽ hiện là": chỉ hiện khi ô có công thức. Công thức gõ sai cú pháp thì KaTeX tô đỏ chỗ sai và có lời nhắc. */
export function MathPreview({ text }: { text?: string | null }) {
    // Gõ nhanh không bị giật: vẽ lại theo giá trị trễ một nhịp
    const deferred = useDeferredValue(text ?? "");
    const syntaxError = useMemo(() => hasFormulaSyntaxError(deferred), [deferred]);
    if (!containsMath(deferred)) return null;
    return (
        <div className="math-preview">
            <div className="math-preview-line">
                <span className="math-preview-label">Sẽ hiện là:</span>{" "}
                <span className="math-preview-content"><MathText text={deferred} /></span>
            </div>
            {syntaxError && !hasUnclosedMath(deferred) && (
                <div className="math-preview-warning">
                    Có chỗ gõ công thức chưa đúng (phần chữ đỏ ở dòng trên). Thầy/cô xem lại trước khi lưu.
                </div>
            )}
        </div>
    );
}

interface MathFieldProps {
    name: FormItemProps["name"];
    label: ReactNode;
    /** Tên ô cho hàng nút kí hiệu. */
    fieldLabel: string;
    requiredMessage?: string;
    disabled?: boolean;
    placeholder?: string;
    minRows?: number;
    maxRows?: number;
    className?: string;
}

/** Form.Item + MathInput + dòng xem trước; lỗi (đỏ) nằm ngay dưới ô, trên dòng xem trước. */
export function MathField({
    name, label, fieldLabel, requiredMessage, disabled, placeholder, minRows, maxRows, className,
}: MathFieldProps) {
    const form = Form.useFormInstance();
    const value = Form.useWatch(name, form) as string | undefined;
    return (
        <Form.Item
            name={name}
            label={label}
            className={`math-field ${className ?? ""}`}
            rules={[
                ...(requiredMessage ? [{ required: true, whitespace: true, message: requiredMessage }] : []),
                unclosedMathRule,
            ]}
            extra={<MathPreview text={value} />}
        >
            <MathInput
                fieldLabel={fieldLabel}
                disabled={disabled}
                placeholder={placeholder}
                minRows={minRows}
                maxRows={maxRows}
            />
        </Form.Item>
    );
}
