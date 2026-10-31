import { zodResolver } from "@hookform/resolvers/zod";
import { Button, Form, Input, Typography, } from "antd";
import { Controller, useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { z } from "zod";
import { useAuth } from "../../../context/AuthContext";
import { login } from '../services/authApi';
import "./LoginForm.css";
import { useState } from "react";

const { Text } = Typography;

const loginSchema = z.object({
    email: z.string().trim().min(1, "Vui lòng nhập email.").email("Email không hợp lệ."),
    password: z.string().min(1, "Vui lòng nhập mật khẩu."),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export default function LoginForm() {
    const navigate = useNavigate();
    const { setToken } = useAuth();
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const {
        control,
        handleSubmit,
        formState: { errors, isSubmitting },
    } = useForm<LoginFormValues>({
        resolver: zodResolver(loginSchema),
        defaultValues: {
            email: "",
            password: "",
        },
    });

    const onSubmit = async (values: LoginFormValues) => {
        setErrorMessage(null); // Xóa lỗi cũ trước khi gọi API mới
        try {
            const response = await login({
                email: values.email,
                password: values.password,
            });
            // 1️⃣ Lưu token vào Context (cùng lúc đã có trong localStorage)
            if (response.accessToken) {
                setToken(response.accessToken);
            }
            // 2️⃣ Chuyển hướng: lúc này chưa có role (AuthContext tải /me sau khi có token),
            // nên về "/" và để RequireAuth chuyển admin sang trang của admin
            navigate("/");
        } catch (err) {
            setErrorMessage(err instanceof Error ? err.message : 'Đăng nhập thất bại');
        }
    };


    return (
        <form
            className="login-form"
            onSubmit={handleSubmit(onSubmit)}
            noValidate
        >
            {errorMessage && <p className="error-msg">{errorMessage}</p>}

            <Form layout="vertical" component={false}>
                <Form.Item
                    label="Email"
                    validateStatus={errors.email ? "error" : ""}
                    help={errors.email?.message}
                >
                    <Controller
                        name="email"
                        control={control}
                        render={({ field }) => (
                            <Input
                                {...field}
                                placeholder="Nhập email"
                                size="large"
                                autoComplete="email"
                                onChange={(e) => {
                                    field.onChange(e);
                                    setErrorMessage(null);
                                }}
                            />
                        )}
                    />
                </Form.Item>

                <Form.Item
                    label="Mật khẩu"
                    validateStatus={errors.password ? "error" : ""}
                    help={errors.password?.message}
                >
                    <Controller
                        name="password"
                        control={control}
                        render={({ field }) => (
                            <Input.Password
                                {...field}
                                placeholder="Nhập mật khẩu"
                                size="large"
                                autoComplete="current-password"
                                onChange={(e) => {
                                    field.onChange(e);
                                    setErrorMessage(null);
                                }}
                            />
                        )}
                    />
                </Form.Item>

                <div className="login-form-options">
                    <Link to="/auth/forgot-password">
                        Quên mật khẩu?
                    </Link>
                </div>

                <Button
                    type="primary"
                    htmlType="submit"
                    size="large"
                    block
                    loading={isSubmitting}
                    className="login-form-submit"
                >
                    Đăng nhập
                </Button>

                <div className="login-form-note">
                    <Text type="secondary">
                        Tài khoản do quản trị viên cấp.
                    </Text>
                </div>
            </Form>
        </form>
    );
}