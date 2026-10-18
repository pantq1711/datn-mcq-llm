import { useState } from "react";
import katex from "katex";
import "katex/dist/katex.min.css";
import { Card, Typography, Input, Alert } from "antd";

const { Title, Paragraph } = Typography;
const { TextArea } = Input;

export interface LatexValidationResult {
    valid: boolean;
    error?: string;
}

export function validateLatex(
    latex: string,
): LatexValidationResult {
    if (!latex.trim()) {
        return {
            valid: false,
            error: "Biểu thức LaTeX đang trống.",
        };
    }

    try {
        katex.renderToString(latex, {
            throwOnError: true,
            strict: "error",
            output: "htmlAndMathml",
        });

        return { valid: true };
    } catch (error) {
        return {
            valid: false,
            error:
                error instanceof Error
                    ? error.message
                    : "Không thể render biểu thức LaTeX.",
        };
    }
}

export default function TestKaTeX() {
    // State lưu giá trị input người dùng nhập vào
    const [inputLatex, setInputLatex] = useState(
        String.raw`\frac{-1}{2} + \frac{3}{4}`
    );

    // Kiểm tra validate cho ví dụ thứ 2 theo yêu cầu
    const result = validateLatex(
        String.raw`\frac{1}{2}`,
    );

    if (result.valid) {
        console.log("Công thức hợp lệ");
    } else {
        console.log(result.error);
    }

    // Validate và render dựa trên input người dùng
    const validation = validateLatex(inputLatex);
    let renderedHtml = "";

    if (validation.valid) {
        try {
            renderedHtml = katex.renderToString(inputLatex, {
                throwOnError: true,
                strict: "error",
                output: "htmlAndMathml",
            });
        } catch (error) {
            console.error("Lỗi LaTeX:", error);
        }
    }

    return (
        <div style={{ padding: "24px", margin: "0 auto" }}>
            <Card title="Trang Kiểm tra & Render KaTeX Tương Tác">
                <Paragraph>
                    <Title level={5}>Nhập biểu thức LaTeX của bạn:</Title>
                    <TextArea
                        rows={3}
                        value={inputLatex}
                        onChange={(e) => setInputLatex(e.target.value)}
                        placeholder="Nhập mã LaTeX tại đây (VD: \frac{a}{b})"
                        style={{ fontFamily: "monospace" }}
                    />
                </Paragraph>

                {validation.valid ? (
                    <>
                        <Paragraph>
                            <Title level={5}>Kết quả hiển thị trên giao diện:</Title>
                        </Paragraph>
                        <div
                            style={{
                                fontSize: "22px",
                                padding: "20px",
                                background: "#fafafa",
                                border: "1px solid #d9d9d9",
                                borderRadius: "6px",
                                textAlign: "center",
                                minHeight: "80px",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                            }}
                            dangerouslySetInnerHTML={{ __html: renderedHtml }}
                        />
                    </>
                ) : (
                    <Alert
                        message="Lỗi cú pháp LaTeX"
                        description={validation.error}
                        type="error"
                        showIcon
                    />
                )}
            </Card>
        </div>
    );
}