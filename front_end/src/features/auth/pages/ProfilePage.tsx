import { Avatar, Card, Descriptions, Divider, Spin, Tag, Typography } from "antd";
import { MailOutlined, IdcardOutlined, UserOutlined } from "@ant-design/icons";
import { useAuth } from "../../../context/AuthContext";
import { describeRole } from "../../../utils/auth.utils";
import ChangePasswordForm from "../components/ChangePasswordForm";
import "./ProfilePage.css";

const { Title, Text } = Typography;

export default function ProfilePage() {
    const { user } = useAuth();

    if (!user) {
        return (
            <div className="profile-loading">
                <Spin size="large" />
                <Text type="secondary" className="profile-loading-text">
                    Đang tải thông tin...
                </Text>
            </div>
        );
    }

    const roleInfo = describeRole(user.role);

    return (
        <div className="profile-page">
            <Card className="profile-card" bordered={false}>
                {/* Header với avatar và tên */}
                <div className="profile-header">
                    <Avatar
                        size={80}
                        icon={<UserOutlined />}
                        className="profile-avatar"
                    />
                    <div className="profile-header-info">
                        <Title level={3} className="profile-name">
                            {user.name}
                        </Title>
                        <Tag color={roleInfo.color} className="profile-role-tag">
                            {roleInfo.label}
                        </Tag>
                    </div>
                </div>

                <Divider className="profile-divider" />

                {/* Thông tin chi tiết */}
                <Descriptions
                    column={1}
                    colon={false}
                    className="profile-descriptions"
                    labelStyle={{ fontWeight: 500, color: "#595959", minWidth: 120 }}
                    contentStyle={{ color: "#262626" }}
                >
                    <Descriptions.Item
                        label={
                            <span className="profile-label">
                                <IdcardOutlined className="profile-label-icon" />
                                Mã người dùng
                            </span>
                        }
                    >
                        <Text copyable={{ text: user.id }} className="profile-id">
                            {user.id}
                        </Text>
                    </Descriptions.Item>

                    <Descriptions.Item
                        label={
                            <span className="profile-label">
                                <UserOutlined className="profile-label-icon" />
                                Họ và tên
                            </span>
                        }
                    >
                        {user.name}
                    </Descriptions.Item>

                    <Descriptions.Item
                        label={
                            <span className="profile-label">
                                <MailOutlined className="profile-label-icon" />
                                Email
                            </span>
                        }
                    >
                        <a href={`mailto:${user.email}`}>{user.email}</a>
                    </Descriptions.Item>
                </Descriptions>
            </Card>

            <Card className="profile-card profile-password-card" bordered={false} title="Đổi mật khẩu">
                <ChangePasswordForm />
            </Card>
        </div>
    );
}
