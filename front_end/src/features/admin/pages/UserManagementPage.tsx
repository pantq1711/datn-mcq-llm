import { Button, Card, Radio, Typography, message } from "antd";
import { PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { useEffect, useState } from "react";
import UserTable from "../components/UserTable";
import CreateUserModal from "../components/CreateUserModal";
import ResetPasswordModal from "../components/ResetPasswordModal";
import EditUserModal from "../components/EditUserModal";
import { getAdminUsers, updateUser } from "../services/adminUserApi";
import type { AdminRole, AdminUserItem } from "../types/adminUser.types";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../../context/AuthContext";
import { logout } from "../../auth/services/authApi";
import "./UserManagementPage.css";

const { Title } = Typography;

export default function UserManagementPage() {
    const [users, setUsers] = useState<AdminUserItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [selectedRole, setSelectedRole] = useState<string>("all");

    // Modal states
    const [createModalOpen, setCreateModalOpen] = useState(false);
    const [resetUser, setResetUser] = useState<AdminUserItem | null>(null);
    const [editUser, setEditUser] = useState<AdminUserItem | null>(null);
    const [togglingId, setTogglingId] = useState<number | null>(null);

    // UserInfo.id là chuỗi, AdminUserItem.id là số
    const { user: currentUser, setUser, setToken } = useAuth();
    const navigate = useNavigate();
    const currentUserId = currentUser ? Number(currentUser.id) : undefined;

    // Tự sửa tài khoản của mình: đồng bộ AuthContext để Header/Profile hiện thông tin mới mà không cần tải lại trang
    const syncSelf = async (updated: AdminUserItem) => {
        if (!currentUser || updated.id !== currentUserId) return;

        // Đổi email thu hồi mọi refresh token (docs/api mục 8.1): đăng xuất ngay thay vì bị đá ra sau tối đa 15 phút
        if (updated.email && updated.email !== currentUser.email) {
            message.info("Đã đổi email. Hãy đăng nhập lại bằng email mới.");
            try {
                await logout();
            } finally {
                setToken(null);
                navigate("/auth/login");
            }
            return;
        }

        setUser({ ...currentUser, name: updated.name, email: updated.email ?? currentUser.email });
    };

    const replaceUser = (updated: AdminUserItem) => {
        setUsers((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
    };

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
        setTogglingId(user.id);
        try {
            const updated = await updateUser(user.id, { enabled: nextEnabled });
            // Access token cũ còn hiệu lực tối đa 15 phút sau khi khóa (docs/api mục 8.1, H10)
            message.success(
                nextEnabled
                    ? `Đã mở khóa tài khoản "${user.name}"!`
                    : `Đã khóa tài khoản "${user.name}". Phiên bị thu hồi; người dùng có thể còn thao tác tối đa 15 phút.`
            );
            replaceUser(updated);
        } catch (error: any) {
            message.error(error.message || "Không thể thay đổi trạng thái tài khoản!");
        } finally {
            setTogglingId(null);
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

                <div className="user-management-actions">
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
                    onEdit={(user) => setEditUser(user)}
                    currentUserId={currentUserId}
                    togglingId={togglingId}
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

            {/* Modal sửa tên / email */}
            <EditUserModal
                user={editUser}
                open={!!editUser}
                isSelf={!!editUser && editUser.id === currentUserId}
                onCancel={() => setEditUser(null)}
                onSuccess={(updated) => {
                    setEditUser(null);
                    replaceUser(updated);
                    syncSelf(updated);
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
