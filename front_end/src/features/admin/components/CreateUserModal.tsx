import { Modal, Form, Input, message } from "antd";
import { useState } from "react";
import { createTeacher } from "../services/adminUserApi";
import type { CreateTeacherPayload } from "../types/adminUser.types";

interface CreateUserModalProps {
    open: boolean;
    onCancel: () => void;
    onSuccess: () => void;
}

export default function CreateUserModal({ open, onCancel, onSuccess }: CreateUserModalProps) {
    const [form] = Form.useForm<CreateTeacherPayload>();
    const [loading, setLoading] = useState(false);

    const handleSubmit = async () => {
        try {
            const values = await form.validateFields();
            setLoading(true);
            await createTeacher({
                name: values.name.trim(),
                email: values.email.trim().toLowerCase(),
                password: values.password,
            });
            message.success("Tạo tài khoản giáo viên thành công!");
            form.resetFields();
            onSuccess();
        } catch (error: any) {
            if (error.errorFields) {
                return; // Lỗi validation của form
            }
            message.error(error.message || "Tạo tài khoản thất bại!");
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            title="Tạo tài khoản giáo viên mới"
            open={open}
            onCancel={() => {
                form.resetFields();
                onCancel();
            }}
            onOk={handleSubmit}
            confirmLoading={loading}
            okText="Tạo tài khoản"
            cancelText="Hủy"
            destroyOnClose
        >
            <Form form={form} layout="vertical" preserve={false}>
                <Form.Item
                    label="Họ và tên"
                    name="name"
                    rules={[
                        { required: true, message: "Vui lòng nhập họ và tên" },
                        { max: 255, message: "Họ và tên tối đa 255 ký tự" },
                    ]}
                >
                    <Input placeholder="Ví dụ: Nguyễn Văn A" />
                </Form.Item>

                <Form.Item
                    label="Email"
                    name="email"
                    rules={[
                        { required: true, message: "Vui lòng nhập email" },
                        { type: "email", message: "Email không đúng định dạng" },
                    ]}
                >
                    <Input placeholder="Ví dụ: gv.toan@truong.edu.vn" />
                </Form.Item>

                <Form.Item
                    label="Mật khẩu khởi tạo"
                    name="password"
                    rules={[
                        { required: true, message: "Vui lòng nhập mật khẩu" },
                        { min: 8, message: "Mật khẩu phải từ 8 ký tự trở lên" },
                        { max: 72, message: "Mật khẩu tối đa 72 ký tự" },
                    ]}
                >
                    <Input.Password placeholder="Tối thiểu 8 ký tự" />
                </Form.Item>
            </Form>
        </Modal>
    );
}
