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
import { Link } from "react-router-dom";
import { z } from "zod";
import "./RegisterForm.css";

const { Text } = Typography;

const registerSchema = z
    .object({
        fullName: z
            .string()
            .trim()
            .min(1, "Vui lòng nhập họ và tên.")
            .max(100, "Họ và tên không được vượt quá 100 ký tự."),

        email: z
            .string()
            .trim()
            .min(1, "Vui lòng nhập email.")
            .email("Email không hợp lệ."),

        password: z
            .string()
            .min(1, "Vui lòng nhập mật khẩu.")
            .min(6, "Mật khẩu phải có ít nhất 6 ký tự."),

        confirmPassword: z
            .string()
            .min(1, "Vui lòng xác nhận mật khẩu."),

        agreeTerms: z.boolean().refine((value) => value, {
            message: "Vui lòng đồng ý với điều khoản sử dụng.",
        }),
    })
    .refine((data) => data.password === data.confirmPassword, {
        message: "Mật khẩu xác nhận không khớp.",
        path: ["confirmPassword"],
    });

type RegisterFormValues = z.infer<typeof registerSchema>;

export default function RegisterForm() {
    const {
        control,
        handleSubmit,
        formState: { errors, isSubmitting },
    } = useForm<RegisterFormValues>({
        resolver: zodResolver(registerSchema),
        defaultValues: {
            fullName: "",
            email: "",
            password: "",
            confirmPassword: "",
            agreeTerms: false,
        },
    });

    const onSubmit = async (values: RegisterFormValues) => {
        console.log("Register:", values);

        // TODO:
        // Gọi API đăng ký tại đây.
    };

    return (
        <form
            className="register-form"
            onSubmit={handleSubmit(onSubmit)}
            noValidate
        >
            <Form layout="vertical" component={false}>
                <Form.Item
                    label="Họ và tên"
                    validateStatus={errors.fullName ? "error" : ""}
                    help={errors.fullName?.message}
                >
                    <Controller
                        name="fullName"
                        control={control}
                        render={({ field }) => (
                            <Input
                                {...field}
                                placeholder="Nhập họ và tên"
                                size="large"
                                autoComplete="name"
                            />
                        )}
                    />
                </Form.Item>

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
                                autoComplete="new-password"
                            />
                        )}
                    />
                </Form.Item>

                <Form.Item
                    label="Xác nhận mật khẩu"
                    validateStatus={errors.confirmPassword ? "error" : ""}
                    help={errors.confirmPassword?.message}
                >
                    <Controller
                        name="confirmPassword"
                        control={control}
                        render={({ field }) => (
                            <Input.Password
                                {...field}
                                placeholder="Nhập lại mật khẩu"
                                size="large"
                                autoComplete="new-password"
                            />
                        )}
                    />
                </Form.Item>

                <Form.Item
                    validateStatus={errors.agreeTerms ? "error" : ""}
                    help={errors.agreeTerms?.message}
                >
                    <Controller
                        name="agreeTerms"
                        control={control}
                        render={({ field }) => (
                            <Checkbox
                                checked={field.value}
                                onChange={field.onChange}
                            >
                                Tôi đồng ý với các điều khoản sử dụng.
                            </Checkbox>
                        )}
                    />
                </Form.Item>

                <Button
                    type="primary"
                    htmlType="submit"
                    size="large"
                    block
                    loading={isSubmitting}
                    className="register-form-submit"
                >
                    Đăng ký
                </Button>

                <Space
                    direction="vertical"
                    size={4}
                    className="register-form-login"
                >
                    <Text type="secondary">
                        Đã có tài khoản?
                    </Text>

                    <Link to="/auth/login">
                        Đăng nhập
                    </Link>
                </Space>
            </Form>
        </form>
    );
}