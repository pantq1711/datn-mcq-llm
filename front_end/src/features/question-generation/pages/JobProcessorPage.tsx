import { useState, useEffect } from "react";
import { Button, Card, message, Typography, Table, Tag } from "antd";
import { ThunderboltOutlined } from "@ant-design/icons";
import { processNextJob, getGenerationJobs } from "../services/generationApi";
import "./JobProcessorPage.css";

const { Title } = Typography;

export default function JobProcessorPage() {
    const [jobs, setJobs] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [processing, setProcessing] = useState(false);

    const loadJobs = async () => {
        setLoading(true);
        try {
            const data = await getGenerationJobs();
            setJobs(data);
        } catch (e) {
            message.error("Không tải được danh sách job");
        } finally {
            setLoading(false);
        }
    };

    const handleProcessNext = async () => {
        setProcessing(true);
        try {
            const result: any = await processNextJob();
            message.success(
                `Job ${result.job?.id ?? "unknown"} đã được xử lý (status: ${result.status})`
            );
            await loadJobs();
        } catch (e) {
            console.error(e);
            message.error("Xử lý job thất bại");
        } finally {
            setProcessing(false);
        }
    };

    useEffect(() => {
        loadJobs();
    }, []);

    const columns = [
        { title: "ID", dataIndex: "id", key: "id", width: 60 },
        {
            title: "Nguồn",
            dataIndex: "sourceName",
            key: "sourceName",
            render: (text: string, rec: any) => (
                <>
                    {text} <Tag>{rec.sourceType}</Tag>
                </>
            ),
        },
        { title: "Môn học", dataIndex: "subject", key: "subject" },
        {
            title: "Trạng thái", dataIndex: "status", key: "status", render: (s: string) => (
                <Tag color={s === "COMPLETED" ? "success" : s === "RUNNING" ? "processing" : "error"}>
                    {s}
                </Tag>
            )
        },
    ];

    return (
        <div className="job-processor-page">
            <div className="job-processor-page-header">
                <div className="job-processor-page-header-content">
                    <div>
                        <Title level={2} className="job-processor-page-title">
                            Quản lý job sinh câu hỏi
                        </Title>
                    </div>

                    <Button
                        type="primary"
                        icon={<ThunderboltOutlined />}
                        onClick={handleProcessNext}
                        loading={processing}
                    >
                        Xử lý job tiếp theo
                    </Button>
                </div>
            </div>

            <div className="job-processor-page-table">
                <Card>
                    <Table
                        columns={columns}
                        dataSource={jobs}
                        loading={loading}
                        rowKey="id"
                        pagination={{ pageSize: 10 }}
                        scroll={{ x: "max-content" }}
                    />
                </Card>
            </div>
        </div>
    );
}