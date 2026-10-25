import { useState, useEffect } from "react";
import { Button, Card, message, Typography, Table, Tag } from "antd";
import { ThunderboltOutlined } from "@ant-design/icons";
import { processNextJob, getGenerationJobs } from "../services/generationApi";

const { Title } = Typography;

interface ProcessedJobSummary {
    job?: { id: number; templateId?: number };
    status: "PROCESSED" | "SKIPPED" | "FAILED";
    // các trường khác tùy backend
}

export default function JobProcessorPage() {
    const [jobs, setJobs] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [processing, setProcessing] = useState(false);

    // Load danh sách job
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

    // Xử lý job tiếp theo
    const handleProcessNext = async () => {
        setProcessing(true);
        try {
            const result: ProcessedJobSummary = await processNextJob();
            message.success(
                `Job ${result.job?.id ?? "unknown"} đã được xử lý (status: ${result.status})`
            );
            // Refresh danh sách để phản ánh thay đổi
            await loadJobs();
        } catch (e) {
            console.error(e);
            message.error("Xử lý job thất bại");
        } finally {
            setProcessing(false);
        }
    };

    // (Tùy chọn) Polling tự động mỗi 10s
    // useEffect(() => {
    //     const interval = setInterval(handleProcessNext, 10_000);
    //     return () => clearInterval(interval);
    // }, []);

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
        // …các cột khác tùy nhu cầu
    ];

    return (
        <div className="job-processor-page">
            <Card>
                <Title level={2}>Quản lý job sinh câu hỏi</Title>

                <Button
                    type="primary"
                    icon={<ThunderboltOutlined />}
                    onClick={handleProcessNext}
                    loading={processing}
                    style={{ marginBottom: 16 }}
                >
                    Xử lý job tiếp theo
                </Button>

                <Table
                    columns={columns}
                    dataSource={jobs}
                    loading={loading}
                    rowKey="id"
                    pagination={{ pageSize: 10 }}
                />
            </Card>
        </div>
    );
}
