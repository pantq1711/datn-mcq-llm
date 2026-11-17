import { Table, Tag, Switch, Button, Space, Tooltip, Popconfirm, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EditOutlined, KeyOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import type { AdminRole, AdminUserItem } from "../types/adminUser.types";

const { Text } = Typography;

interface UserTableProps {
    data: AdminUserItem[];
    loading: boolean;
    onToggleStatus: (user: AdminUserItem, nextEnabled: boolean) => void;
    onResetPassword: (user: AdminUserItem) => void;
    onEdit: (user: AdminUserItem) => void;
    currentUserId?: number;
    togglingId?: number | null;
}

const roleConfig: Record<AdminRole, { label: string; color: string }> = {
    teacher: { label: "Giáo viên", color: "blue" },
    admin: { label: "Quản trị viên", color: "red" },
    researcher: { label: "Nghiên cứu", color: "purple" },
};

export default function UserTable({
    data,
    loading,
    onToggleStatus,
    onResetPassword,
    onEdit,
    currentUserId,
    togglingId,
}: UserTableProps) {
    // Bỏ cột ID để bảng vừa khung, cột "Trạng thái" không bị đẩy ra ngoài tầm nhìn (rà soát giao diện L1)
    const columns: ColumnsType<AdminUserItem> = [
        {
            title: "Họ và tên",
            dataIndex: "name",
            key: "name",
            width: 170,
            render: (name: string) => <Text strong>{name}</Text>,
        },
        {
            title: "Email",
            dataIndex: "email",
            key: "email",
            width: 200,
            render: (email: string | null) => email || <Text type="secondary">(Chưa có)</Text>,
        },
        {
            title: "Vai trò",
            dataIndex: "role",
            key: "role",
            width: 120,
            align: "center",
            render: (role: AdminRole) => {
                const config = roleConfig[role] || { label: role, color: "default" };
                return <Tag color={config.color}>{config.label}</Tag>;
            },
        },
        {
            title: "Mật khẩu",
            dataIndex: "hasPassword",
            key: "hasPassword",
            width: 120,
            align: "center",
            render: (hasPassword: boolean) => (
                <Tag color={hasPassword ? "success" : "warning"}>
                    {hasPassword ? "Đã thiết lập" : "Chưa đặt"}
                </Tag>
            ),
        },
        {
            title: "Lần đăng nhập cuối",
            dataIndex: "lastLoginAt",
            key: "lastLoginAt",
            width: 150,
            align: "center",
            render: (lastLoginAt: string | null) =>
                lastLoginAt ? dayjs(lastLoginAt).format("DD/MM/YYYY HH:mm") : <Text type="secondary">—</Text>,
        },
        {
            title: "Trạng thái",
            dataIndex: "enabled",
            key: "enabled",
            width: 115,
            align: "center",
            render: (enabled: boolean, record) => {
                // Backend chặn tự khóa (409); tắt sẵn công tắc ở dòng của chính mình
                const isSelf = record.id === currentUserId;
                return (
                    <Tooltip title={isSelf ? "Không thể tự khóa tài khoản của mình" : undefined}>
                        <span>
                            <Popconfirm
                                title={enabled ? "Khóa tài khoản này?" : "Mở khóa tài khoản này?"}
                                description={
                                    enabled
                                        ? "Người dùng không làm mới được phiên và sẽ bị đăng xuất trong tối đa 15 phút."
                                        : "Mở khóa cho phép người dùng đăng nhập lại."
                                }
                                onConfirm={() => onToggleStatus(record, !enabled)}
                                okText="Đồng ý"
                                cancelText="Hủy"
                                disabled={isSelf}
                            >
                                <Switch
                                    checked={enabled}
                                    checkedChildren="Bật"
                                    unCheckedChildren="Khóa"
                                    disabled={isSelf}
                                    loading={togglingId === record.id}
                                />
                            </Popconfirm>
                        </span>
                    </Tooltip>
                );
            },
        },
        {
            title: "Thao tác",
            key: "actions",
            width: 270,
            align: "center",
            fixed: "right",
            render: (_, record) => (
                // Nút có chữ thay cho biểu tượng (rà soát giao diện L2)
                <Space size={4}>
                    <Button
                        icon={<EditOutlined />}
                        onClick={() => onEdit(record)}
                    >
                        Sửa
                    </Button>
                    {/* Chưa có email thì backend trả 400 khi đặt mật khẩu: phải thêm email trước */}
                    <Tooltip
                        title={
                            record.email
                                ? undefined
                                : "Chưa có email: hãy Sửa tài khoản để thêm email trước"
                        }
                    >
                        <Button
                            icon={<KeyOutlined />}
                            disabled={!record.email}
                            onClick={() => onResetPassword(record)}
                        >
                            Đặt lại mật khẩu
                        </Button>
                    </Tooltip>
                </Space>
            ),
        },
    ];

    return (
        <Table<AdminUserItem>
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={loading}
            scroll={{ x: 1145 }}
            pagination={{
                pageSize: 10,
                showSizeChanger: true,
                pageSizeOptions: ["10", "20", "50"],
                showTotal: (total, range) => `${range[0]}-${range[1]} / ${total} tài khoản`,
            }}
        />
    );
}
