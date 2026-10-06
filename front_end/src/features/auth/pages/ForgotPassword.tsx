import { zodResolver } from "@hookform/resolvers/zod";
import {
    Alert,
    Button,
    Form,
    Input,
    Space,
    Typography,
} from "antd";
import { Controller, useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { z } from "zod";
import "./ForgotPassword.css";

const { Text } = Typography;

const forgotPasswordSchema = z.object({
    email: z
        .string()
        .trim()
        .min(1, "Vui lòng nhập email.")
        .email("Email không hợp lệ."),
});

type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>;

export default function ForgotPassword() {
    const {
        control,
        handleSubmit,
        formState: { errors, isSubmitting },
        setError,
    } = useForm<ForgotPasswordFormValues>({
        resolver: zodResolver(forgotPasswordSchema),
        defaultValues: {
            email: "",
        },
    });

    const onSubmit = async (values: ForgotPasswordFormValues) => {
        try {
            console.log("Forgot password:", values);

            // TODO:
            // Gọi API quên mật khẩu tại đây.
            //
            // Ví dụ:
            // await authService.forgotPassword(values.email);

        } catch (error) {
            setError("email", {
                type: "server",
                message: "Không thể thực hiện yêu cầu. Vui lòng thử lại.",
            });
        }
    };

    return (
        <form
            className="forgot-password"
            onSubmit={handleSubmit(onSubmit)}
            noValidate
        >
            <Form layout="vertical" component={false}>
                <Alert
                    message="Quên mật khẩu?"
                    description="Nhập email đã đăng ký. Nếu tài khoản tồn tại, hệ thống sẽ gửi hướng dẫn đặt lại mật khẩu."
                    type="info"
                    showIcon
                    className="forgot-password-info"
                />

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
                            />
                        )}
                    />
                </Form.Item>

                <Button
                    type="primary"
                    htmlType="submit"
                    size="large"
                    block
                    loading={isSubmitting}
                    className="forgot-password-submit"
                >
                    Gửi yêu cầu
                </Button>

                <Space
                    direction="vertical"
                    size={4}
                    className="forgot-password-login"
                >
                    <Text type="secondary">
                        Đã nhớ mật khẩu?
                    </Text>

                    <Link to="/auth/login">
                        Quay lại đăng nhập
                    </Link>
                </Space>
            </Form>
        </form>
    );
}