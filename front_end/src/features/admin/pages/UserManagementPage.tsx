import { Button, Card, Radio, Typography, message } from "antd";
import { PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { useEffect, useState } from "react";
import UserTable from "../components/UserTable";
import CreateUserModal from "../components/CreateUserModal";
import ResetPasswordModal from "../components/ResetPasswordModal";
import { getAdminUsers, updateUser } from "../services/adminUserApi";
import type { AdminRole, AdminUserItem } from "../types/adminUser.types";
import "./UserManagementPage.css";

const { Title } = Typography;

export default function UserManagementPage() {
    const [users, setUsers] = useState<AdminUserItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [selectedRole, setSelectedRole] = useState<string>("all");

    // Modal states
    const [createModalOpen, setCreateModalOpen] = useState(false);
    const [resetUser, setResetUser] = useState<AdminUserItem | null>(null);

    const fetchUsers = async (roleFilter?: string) => {
        setLoading(true);
        try {
            const roleParam = roleFilter && roleFilter !== "all" ? (roleFilter as AdminRole) : undefined;
            const data = await getAdminUsers(roleParam);
            setUsers(data);
        } catch (error: any) {
            message.error(error.message || "Không thể tải danh sách tài khoản!");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchUsers(selectedRole);
    }, [selectedRole]);

    const handleRoleChange = (role: string) => {
        setSelectedRole(role);
    };

    const handleToggleStatus = async (user: AdminUserItem, nextEnabled: boolean) => {
        try {
            const updated = await updateUser(user.id, { enabled: nextEnabled });
            message.success(
                nextEnabled
                    ? `Đã mở khóa tài khoản "${user.name}"!`
                    : `Đã khóa tài khoản "${user.name}" và thu hồi phiên đăng nhập!`
            );
            setUsers((prev) =>
                prev.map((item) => (item.id === user.id ? { ...item, enabled: updated.enabled } : item))
            );
        } catch (error: any) {
            message.error(error.message || "Không thể thay đổi trạng thái tài khoản!");
        }
    };

    return (
        <div className="user-management-page">
            <div className="user-management-header">
                <div>
                    <Title level={2} className="user-management-title">
                        Quản lý tài khoản người dùng
                    </Title>
                </div>

                <div style={{ display: "flex", gap: 8 }}>
                    <Button
                        icon={<ReloadOutlined />}
                        onClick={() => fetchUsers(selectedRole)}
                        loading={loading}
                    >
                        Làm mới
                    </Button>
                    <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => setCreateModalOpen(true)}
                    >
                        Tạo tài khoản giáo viên
                    </Button>
                </div>
            </div>

            <Card>
                <div className="user-management-filter">
                    <Radio.Group
                        value={selectedRole}
                        onChange={(e) => handleRoleChange(e.target.value)}
                        buttonStyle="solid"
                    >
                        <Radio.Button value="all">Tất cả vai trò</Radio.Button>
                        <Radio.Button value="teacher">Giáo viên</Radio.Button>
                        <Radio.Button value="admin">Quản trị viên</Radio.Button>
                        <Radio.Button value="researcher">Nghiên cứu</Radio.Button>
                    </Radio.Group>
                </div>

                <UserTable
                    data={users}
                    loading={loading}
                    onToggleStatus={handleToggleStatus}
                    onResetPassword={(user) => setResetUser(user)}
                />
            </Card>

            {/* Modal tạo giáo viên */}
            <CreateUserModal
                open={createModalOpen}
                onCancel={() => setCreateModalOpen(false)}
                onSuccess={() => {
                    setCreateModalOpen(false);
                    fetchUsers(selectedRole);
                }}
            />

            {/* Modal đặt lại mật khẩu */}
            <ResetPasswordModal
                user={resetUser}
                open={!!resetUser}
                onCancel={() => setResetUser(null)}
                onSuccess={() => {
                    setResetUser(null);
                    fetchUsers(selectedRole);
                }}
            />
        </div>
    );
}
