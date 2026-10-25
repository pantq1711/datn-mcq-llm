import { zodResolver } from "@hookform/resolvers/zod";
import {
    Button,
    Checkbox,
    Form,
    Input,
    Space,
    Typography,
} from "antd";
import { Controller, useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { z } from "zod";
import "./LoginForm.css";

const { Text } = Typography;

const loginSchema = z.object({
    email: z
        .string()
        .min(1, "Vui lòng nhập email.")
        .email("Email không hợp lệ."),

    password: z
        .string()
        .min(1, "Vui lòng nhập mật khẩu.")
        .min(6, "Mật khẩu phải có ít nhất 6 ký tự."),

    rememberMe: z.boolean(),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export default function LoginForm() {
    const navigate = useNavigate();
    const {
        control,
        handleSubmit,
        formState: { errors, isSubmitting },
    } = useForm<LoginFormValues>({
        resolver: zodResolver(loginSchema),
        defaultValues: {
            email: "",
            password: "",
            rememberMe: false,
        },
    });

    const onSubmit = async (values: LoginFormValues) => {
        console.log("Login:", values);
        navigate("/");
    };

    return (
        <form
            className="login-form"
            onSubmit={handleSubmit(onSubmit)}
            noValidate
        >
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
                            />
                        )}
                    />
                </Form.Item>

                <div className="login-form-options">
                    <Controller
                        name="rememberMe"
                        control={control}
                        render={({ field }) => (
                            <Checkbox
                                checked={field.value}
                                onChange={field.onChange}
                            >
                                Ghi nhớ đăng nhập
                            </Checkbox>
                        )}
                    />

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

                <Space
                    direction="vertical"
                    size={4}
                    className="login-form-register"
                >
                    <Text type="secondary">
                        Chưa có tài khoản?
                    </Text>

                    <Link to="/auth/register">
                        Đăng ký tài khoản
                    </Link>
                </Space>
            </Form>
        </form>
    );
}