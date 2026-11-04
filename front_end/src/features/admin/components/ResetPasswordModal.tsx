import { Modal, Form, Input, Typography, message } from "antd";
import { useState } from "react";
import { resetUserPassword } from "../services/adminUserApi";
import type { AdminUserItem, ResetPasswordPayload } from "../types/adminUser.types";

interface ResetPasswordModalProps {
    user: AdminUserItem | null;
    open: boolean;
    onCancel: () => void;
    onSuccess: () => void;
}

export default function ResetPasswordModal({
    user,
    open,
    onCancel,
    onSuccess,
}: ResetPasswordModalProps) {
    const [form] = Form.useForm<ResetPasswordPayload>();
    const [loading, setLoading] = useState(false);

    const handleSubmit = async () => {
        if (!user) return;
        try {
            const values = await form.validateFields();
            setLoading(true);
            await resetUserPassword(user.id, {
                newPassword: values.newPassword,
            });
            message.success(`Đặt lại mật khẩu cho tài khoản "${user.name}" thành công!`);
            form.resetFields();
            onSuccess();
        } catch (error: any) {
            if (error.errorFields) {
                return;
            }
            message.error(error.message || "Đặt lại mật khẩu thất bại!");
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            title={`Đặt lại mật khẩu: ${user?.name || ""}`}
            open={open}
            onCancel={() => {
                form.resetFields();
                onCancel();
            }}
            onOk={handleSubmit}
            confirmLoading={loading}
            okText="Xác nhận"
            cancelText="Hủy"
            destroyOnClose
        >
            <Typography.Paragraph type="secondary">
                Lưu ý: Đặt lại mật khẩu sẽ thu hồi tất cả các phiên đăng nhập hiện tại của người dùng này.
            </Typography.Paragraph>
            <Form form={form} layout="vertical" preserve={false}>
                <Form.Item
                    label="Mật khẩu mới"
                    name="newPassword"
                    rules={[
                        { required: true, message: "Vui lòng nhập mật khẩu mới" },
                        { min: 8, message: "Mật khẩu phải từ 8 ký tự trở lên" },
                        { max: 72, message: "Mật khẩu tối đa 72 ký tự" },
                    ]}
                >
                    <Input.Password placeholder="Tối thiểu 8 ký tự" autoFocus />
                </Form.Item>
            </Form>
        </Modal>
    );
}
