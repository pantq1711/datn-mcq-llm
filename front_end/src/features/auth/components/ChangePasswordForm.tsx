import { useState } from "react";
import { Alert, Button, Form, Input, message } from "antd";
import { useNavigate } from "react-router-dom";

import { changePassword, logout } from "../services/authApi";
import { useAuth } from "../../../context/AuthContext";

interface ChangePasswordValues {
    currentPassword: string;
    newPassword: string;
    confirmPassword: string;
}

// Backend: mật khẩu mới từ 8 ký tự, tối đa 72 byte (bcrypt); chữ có dấu chiếm nhiều byte
const MAX_PASSWORD_BYTES = 72;

/** Form đổi mật khẩu của chính người đăng nhập (POST /api/auth/password, docs/api mục 8). */
export default function ChangePasswordForm() {
    const [form] = Form.useForm<ChangePasswordValues>();
    const [submitting, setSubmitting] = useState(false);
    const navigate = useNavigate();
    const { setToken } = useAuth();

    const handleSubmit = async (values: ChangePasswordValues) => {
        setSubmitting(true);
        try {
            await changePassword(values.currentPassword, values.newPassword);
        } catch (error: any) {
            const errMsg: string = error?.response?.data?.error || "Không thể đổi mật khẩu. Vui lòng thử lại.";
            if (errMsg === "Mật khẩu hiện tại không đúng") {
                form.setFields([{ name: "currentPassword", errors: [errMsg] }]);
            } else {
                message.error(errMsg);
            }
            setSubmitting(false);
            return;
        }

        // 204: backend đã thu hồi mọi phiên, đăng xuất ngay thay vì bị đá ra khi access token hết hạn
        message.success("Đã đổi mật khẩu. Hãy đăng nhập lại bằng mật khẩu mới.");
        try {
            await logout();
        } catch {
            // Phiên đã bị thu hồi nên logout lỗi cũng không sao; token ở trình duyệt đã được xóa
        } finally {
            setToken(null);
            navigate("/auth/login");
        }
    };

    return (
        <Form<ChangePasswordValues>
            form={form}
            layout="vertical"
            requiredMark={false}
            onFinish={handleSubmit}
            autoComplete="off"
        >
            <Alert
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
                message="Đổi mật khẩu sẽ đăng xuất mọi phiên, kể cả phiên này."
            />

            <Form.Item
                label="Mật khẩu hiện tại"
                name="currentPassword"
                rules={[{ required: true, message: "Vui lòng nhập mật khẩu hiện tại" }]}
            >
                <Input.Password autoComplete="current-password" />
            </Form.Item>

            <Form.Item
                label="Mật khẩu mới"
                name="newPassword"
                dependencies={["currentPassword"]}
                rules={[
                    { required: true, message: "Vui lòng nhập mật khẩu mới" },
                    { min: 8, message: "Mật khẩu mới phải từ 8 ký tự trở lên" },
                    {
                        validator: (_, value?: string) =>
                            !value || new TextEncoder().encode(value).length <= MAX_PASSWORD_BYTES
                                ? Promise.resolve()
                                : Promise.reject(new Error("Mật khẩu mới quá dài (tối đa 72 byte; chữ có dấu tính nhiều byte)")),
                    },
                    ({ getFieldValue }) => ({
                        validator: (_, value?: string) =>
                            !value || value !== getFieldValue("currentPassword")
                                ? Promise.resolve()
                                : Promise.reject(new Error("Mật khẩu mới phải khác mật khẩu hiện tại")),
                    }),
                ]}
            >
                <Input.Password autoComplete="new-password" placeholder="Tối thiểu 8 ký tự" />
            </Form.Item>

            <Form.Item
                label="Nhập lại mật khẩu mới"
                name="confirmPassword"
                dependencies={["newPassword"]}
                rules={[
                    { required: true, message: "Vui lòng nhập lại mật khẩu mới" },
                    ({ getFieldValue }) => ({
                        validator: (_, value?: string) =>
                            !value || value === getFieldValue("newPassword")
                                ? Promise.resolve()
                                : Promise.reject(new Error("Mật khẩu nhập lại không khớp")),
                    }),
                ]}
            >
                <Input.Password autoComplete="new-password" />
            </Form.Item>

            <Button type="primary" htmlType="submit" loading={submitting}>
                Đổi mật khẩu
            </Button>
        </Form>
    );
}
