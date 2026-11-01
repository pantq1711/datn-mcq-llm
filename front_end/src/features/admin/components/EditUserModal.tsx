import { Alert, Modal, Form, Input, message } from "antd";
import { useState } from "react";
import { updateUser } from "../services/adminUserApi";
import type { AdminUserItem, UpdateUserPayload } from "../types/adminUser.types";

interface EditUserModalProps {
    user: AdminUserItem | null;
    open: boolean;
    isSelf: boolean;
    onCancel: () => void;
    onSuccess: (updated: AdminUserItem) => void;
}

interface EditUserFormValues {
    name: string;
    email?: string;
}

export default function EditUserModal({ user, open, isSelf, onCancel, onSuccess }: EditUserModalProps) {
    const [form] = Form.useForm<EditUserFormValues>();
    const [loading, setLoading] = useState(false);
    const watchedEmail = Form.useWatch("email", form);

    const originalEmail = user?.email ?? "";
    const emailChanged =
        (watchedEmail?.trim().toLowerCase() ?? "") !== "" &&
        watchedEmail?.trim().toLowerCase() !== originalEmail;

    const handleSubmit = async () => {
        if (!user) return;
        try {
            const values = await form.validateFields();

            // Chỉ gửi trường đã đổi; body rỗng bị backend trả 400 (docs/api mục 8.1)
            const payload: UpdateUserPayload = {};
            const name = values.name.trim();
            const email = values.email?.trim().toLowerCase() ?? "";
            if (name !== user.name) payload.name = name;
            if (email !== "" && email !== originalEmail) payload.email = email;

            if (Object.keys(payload).length === 0) {
                message.info("Chưa có thay đổi nào");
                onCancel();
                return;
            }

            setLoading(true);
            const updated = await updateUser(user.id, payload);
            message.success(`Đã cập nhật tài khoản "${updated.name}"!`);
            onSuccess(updated);
        } catch (error: any) {
            if (error.errorFields) {
                return; // Lỗi validation của form
            }
            message.error(error.message || "Cập nhật tài khoản thất bại!");
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            title={`Sửa tài khoản: ${user?.name || ""}`}
            open={open}
            onCancel={onCancel}
            onOk={handleSubmit}
            confirmLoading={loading}
            okText="Lưu"
            cancelText="Hủy"
            destroyOnClose
        >
            {user && (
                <Form
                    form={form}
                    layout="vertical"
                    preserve={false}
                    initialValues={{ name: user.name, email: user.email ?? "" }}
                >
                    {!user.email && (
                        <Alert
                            type="info"
                            showIcon
                            style={{ marginBottom: 16 }}
                            message="Tài khoản chưa có email. Thêm email để có thể đặt mật khẩu cho tài khoản này."
                        />
                    )}

                    <Form.Item
                        label="Họ và tên"
                        name="name"
                        rules={[
                            { required: true, whitespace: true, message: "Vui lòng nhập họ và tên" },
                            { max: 255, message: "Họ và tên tối đa 255 ký tự" },
                        ]}
                    >
                        <Input placeholder="Ví dụ: Nguyễn Văn A" />
                    </Form.Item>

                    <Form.Item
                        label="Email"
                        name="email"
                        rules={[
                            // Tài khoản đã có email thì không được để trống (backend không xóa email)
                            { required: !!user.email, whitespace: true, message: "Vui lòng nhập email" },
                            { type: "email", message: "Email không đúng định dạng" },
                        ]}
                    >
                        <Input placeholder="Ví dụ: gv.toan@truong.edu.vn" />
                    </Form.Item>

                    {emailChanged && (
                        <Alert
                            type="warning"
                            showIcon
                            message={
                                "Đổi email sẽ thu hồi mọi phiên đăng nhập; người dùng phải đăng nhập lại bằng email mới." +
                                (isSelf ? " Bạn sẽ được đăng xuất ngay sau khi lưu." : "")
                            }
                        />
                    )}
                </Form>
            )}
        </Modal>
    );
}
