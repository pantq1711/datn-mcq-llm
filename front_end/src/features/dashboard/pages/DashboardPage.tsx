import { Col, Row, Typography } from "antd";
import { ClockCircleOutlined, CheckCircleOutlined, EditOutlined, CloseCircleOutlined } from "@ant-design/icons";
import GenerationSummary from "../components/GenerationSummary";
import RecentQuestions from "../components/RecentQuestions";
import StatisticsCard from "../components/StatisticsCard";
import "./DashboardPage.css";

const { Title } = Typography;

export default function DashboardPage() {
    return (
        <div className="dashboard-page">
            <div className="dashboard-page-header">
                <Title level={2} className="dashboard-page-title">
                    Tổng quan
                </Title>
            </div>

            <Row gutter={[16, 16]} className="dashboard-statistics">
                <Col xs={24} sm={12} xl={6}>
                    <StatisticsCard
                        title="Chờ duyệt"
                        value={24}
                        prefix={<ClockCircleOutlined />}
                    />
                </Col>

                <Col xs={24} sm={12} xl={6}>
                    <StatisticsCard
                        title="Đã duyệt"
                        value={156}
                        prefix={<CheckCircleOutlined />}
                    />
                </Col>

                <Col xs={24} sm={12} xl={6}>
                    <StatisticsCard
                        title="Đã chỉnh sửa"
                        value={31}
                        prefix={<EditOutlined />}
                    />
                </Col>

                <Col xs={24} sm={12} xl={6}>
                    <StatisticsCard
                        title="Từ chối"
                        value={18}
                        prefix={<CloseCircleOutlined />}
                    />
                </Col>
            </Row>

            <Row gutter={[16, 16]} className="dashboard-content">
                <Col xs={24} xl={10}>
                    <GenerationSummary />
                </Col>

                <Col xs={24} xl={14}>
                    <RecentQuestions />
                </Col>
            </Row>
        </div>
    );
}
