import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Alert, Button, Checkbox, DatePicker, Form, Input, InputNumber, Modal, Space, Switch, Typography } from "antd";
import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";

import { createAssignment } from "../services/assignmentApi";
import type { Assignment } from "../types/assignment.types";
import { MAX_CLASSES, MAX_CLASS_SIZE, toClassPayload, validateClassRows, type RawClassRow } from "../utils/classInput";
import { getApiErrorMessage } from "../../../utils/apiError";
import AssignmentLinkCard from "./AssignmentLinkCard";

const { Text } = Typography;

const MAX_DAYS_AHEAD = 30;

interface FormValues {
    durationMinutes: number;
    closesAt: Dayjs;
    showScore: boolean;
    showSolutions: boolean;
    classes: RawClassRow[];
    consent: boolean;
}

interface AssignExamModalProps {
    open: boolean;
    examId: number | string;
    onClose: () => void;
    /** Gọi sau khi giao xong (để trang cha tải lại danh sách bài giao). */
    onCreated?: (assignment: Assignment) => void;
}

function backendList(error: unknown): string[] {
    const list = (error as { response?: { data?: { errors?: unknown } } })?.response?.data?.errors;
    return Array.isArray(list) ? list.filter((x): x is string => typeof x === "string") : [];
}

/** Hộp "Giao bài online": lớp + sĩ số, thời gian làm, hạn nộp, hiện điểm / đáp án sau khi đóng, xác nhận của giáo viên. */
export default function AssignExamModal({ open, examId, onClose, onCreated }: AssignExamModalProps) {
    const navigate = useNavigate();
    const [form] = Form.useForm<FormValues>();
    const [submitting, setSubmitting] = useState(false);
    const [errorText, setErrorText] = useState<string | undefined>();
    const [errorList, setErrorList] = useState<string[]>([]);
    const [created, setCreated] = useState<Assignment | null>(null);

    const handleClose = () => {
        setErrorText(undefined);
        setErrorList([]);
        setCreated(null);
        onClose();
    };

    const handleFinish = async (values: FormValues) => {
        setErrorText(undefined);
        setErrorList([]);
        const classError = validateClassRows(values.classes);
        if (classError) {
            setErrorText(classError);
            return;
        }
        const closesAt = values.closesAt;
        const earliest = dayjs().add(values.durationMinutes, "minute");
        if (closesAt.isBefore(earliest)) {
            setErrorText(`Hạn nộp phải sau lúc giao ít nhất ${values.durationMinutes} phút (bằng thời gian làm bài).`);
            return;
        }
        if (closesAt.isAfter(dayjs().add(MAX_DAYS_AHEAD, "day"))) {
            setErrorText(`Hạn nộp không quá ${MAX_DAYS_AHEAD} ngày kể từ hôm nay.`);
            return;
        }
        setSubmitting(true);
        try {
            const assignment = await createAssignment(examId, {
                durationMinutes: values.durationMinutes,
                closesAt: closesAt.toISOString(),
                showScore: values.showScore,
                showSolutions: values.showSolutions,
                consentConfirmed: true,
                classes: toClassPayload(values.classes),
            });
            setCreated(assignment);
            onCreated?.(assignment);
        } catch (error) {
            setErrorText(getApiErrorMessage(error, "Chưa giao được bài. Thầy/cô thử lại."));
            setErrorList(backendList(error));
        } finally {
            setSubmitting(false);
        }
    };

    if (created) {
        return (
            <Modal
                open={open}
                title="Đã giao bài"
                onCancel={handleClose}
                destroyOnHidden
                footer={[
                    <Button key="close" onClick={handleClose}>
                        Đóng
                    </Button>,
                    <Button
                        key="open"
                        type="primary"
                        onClick={() => {
                            handleClose();
                            navigate(`/exams/${examId}/assignments/${created.assignmentId}`);
                        }}
                    >
                        Xem bài giao
                    </Button>,
                ]}
            >
                <p>Thầy/cô gửi link hoặc chiếu mã QR cho học sinh. Học sinh chọn lớp và nhập số thứ tự của mình.</p>
                <AssignmentLinkCard publicCode={created.publicCode} fileName={created.title} />
            </Modal>
        );
    }

    return (
        <Modal
            open={open}
            title="Giao bài online"
            onCancel={handleClose}
            destroyOnHidden
            width={620}
            okText="Giao bài"
            cancelText="Hủy"
            confirmLoading={submitting}
            onOk={() => form.submit()}
        >
            <Form<FormValues>
                form={form}
                layout="vertical"
                requiredMark={false}
                onFinish={(values) => void handleFinish(values)}
                initialValues={{
                    durationMinutes: 45,
                    closesAt: dayjs().add(1, "day").minute(0).second(0),
                    showScore: true,
                    showSolutions: false,
                    classes: [{ classLabel: "", size: undefined }],
                    consent: false,
                }}
            >
                <Text strong>Các lớp làm bài (tối đa {MAX_CLASSES} lớp)</Text>
                <Form.List name="classes">
                    {(fields, { add, remove }) => (
                        <div style={{ margin: "8px 0 16px" }}>
                            {fields.map((field) => (
                                <Space key={field.key} align="baseline" style={{ display: "flex", marginBottom: 8 }}>
                                    <Form.Item {...field} name={[field.name, "classLabel"]} noStyle>
                                        <Input placeholder="Tên lớp, ví dụ 8A1" aria-label="Tên lớp" maxLength={14} style={{ width: 180 }} />
                                    </Form.Item>
                                    <Form.Item {...field} name={[field.name, "size"]} noStyle>
                                        <InputNumber
                                            placeholder="Sĩ số"
                                            aria-label="Sĩ số"
                                            min={1}
                                            max={MAX_CLASS_SIZE}
                                            precision={0}
                                            style={{ width: 110 }}
                                        />
                                    </Form.Item>
                                    <Button
                                        type="text"
                                        danger
                                        icon={<DeleteOutlined />}
                                        aria-label="Bỏ lớp này"
                                        disabled={fields.length <= 1}
                                        onClick={() => remove(field.name)}
                                    />
                                </Space>
                            ))}
                            <Button
                                icon={<PlusOutlined />}
                                disabled={fields.length >= MAX_CLASSES}
                                onClick={() => add({ classLabel: "", size: undefined })}
                            >
                                Thêm lớp
                            </Button>
                            <div className="assignment-hint">
                                Học sinh chọn lớp trong danh sách này và nhập số thứ tự từ 1 đến sĩ số của lớp.
                            </div>
                        </div>
                    )}
                </Form.List>

                <Space size={16} wrap align="start">
                    <Form.Item
                        label="Thời gian làm bài (phút)"
                        name="durationMinutes"
                        rules={[{ required: true, message: "Nhập thời gian làm bài." }]}
                    >
                        <InputNumber min={5} max={180} precision={0} style={{ width: 160 }} />
                    </Form.Item>
                    <Form.Item
                        label="Hạn nộp"
                        name="closesAt"
                        rules={[{ required: true, message: "Chọn hạn nộp." }]}
                    >
                        <DatePicker showTime={{ format: "HH:mm" }} format="DD/MM/YYYY HH:mm" allowClear={false} needConfirm={false} />
                    </Form.Item>
                </Space>

                <Form.Item name="showScore" valuePropName="checked" style={{ marginBottom: 8 }}>
                    <Switchable label="Hiện điểm cho học sinh sau khi đóng bài" />
                </Form.Item>
                <Form.Item name="showSolutions" valuePropName="checked" style={{ marginBottom: 16 }}>
                    <Switchable label="Hiện đáp án và lời giải sau khi đóng bài" hint="Mặc định tắt. Bật khi đề không dùng lại cho lớp khác." />
                </Form.Item>

                <Form.Item
                    name="consent"
                    valuePropName="checked"
                    rules={[
                        {
                            validator: (_, value) =>
                                value ? Promise.resolve() : Promise.reject(new Error("Thầy/cô cần tích xác nhận này để giao bài.")),
                        },
                    ]}
                >
                    <Checkbox>
                        Tôi xác nhận việc cho học sinh làm bài trên hệ thống này đã được nhà trường cho phép, phụ huynh đã được thông báo
                        theo quy định của trường, và tôi sẽ không yêu cầu học sinh nhập họ tên.
                    </Checkbox>
                </Form.Item>

                {errorText && (
                    <Alert
                        type="error"
                        showIcon
                        message={errorText}
                        description={
                            errorList.length > 0 ? (
                                <ul style={{ paddingLeft: 20, margin: 0 }}>
                                    {errorList.map((line) => (
                                        <li key={line}>{line}</li>
                                    ))}
                                </ul>
                            ) : undefined
                        }
                    />
                )}
            </Form>
        </Modal>
    );
}

/** Công tắc kèm chữ bên cạnh, dùng được trong Form.Item (nhận checked / onChange). */
function Switchable({
    label,
    hint,
    checked,
    onChange,
}: {
    label: string;
    hint?: string;
    checked?: boolean;
    onChange?: (checked: boolean) => void;
}) {
    return (
        <div>
            <Space>
                <Switch checked={checked} onChange={onChange} aria-label={label} />
                <span>{label}</span>
            </Space>
            {hint && <div className="assignment-hint">{hint}</div>}
        </div>
    );
}
