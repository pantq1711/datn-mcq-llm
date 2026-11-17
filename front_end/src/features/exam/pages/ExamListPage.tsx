import { Button, Card, Modal, Segmented, Typography, message } from "antd";
import { FileTextOutlined, PlusOutlined } from "@ant-design/icons";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useEffect, useRef, useState } from "react";

import ExamTable from "../components/ExamTable";
import type { ExamListItem, ExamKind } from "../types/exam.types";
import { deleteExam, getMyExams } from "../services/examApi";
import { examKindConfig } from "../../../utils/exam.utils";
import { isExamHasAssignmentsError } from "../../assignment/utils/examDeleteError";

import "./ExamListPage.css";

const { Title } = Typography;

type ExamKindFilter = "all" | "lesson" | "matrix";

export default function ExamListPage() {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();

    // 1. Đọc và chuẩn hóa giá trị 'kind' từ URL (giá trị lạ hoặc trống được coi như "all")
    const rawKind = searchParams.get("kind");
    const selectedKind: ExamKindFilter =
        rawKind === "lesson" || rawKind === "matrix" ? rawKind : "all";

    // 2. Khởi tạo các state theo yêu cầu
    const [exams, setExams] = useState<ExamListItem[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [page, setPage] = useState<number>(1);
    const [pageSize, setPageSize] = useState<number>(10);
    const [total, setTotal] = useState<number>(0);

    // Ref đếm số thứ tự request để chặn kết quả trả về trễ
    const fetchSequenceRef = useRef<number>(0);

    // 3. Tự động reset về trang 1 mỗi khi `selectedKind` thay đổi (bao gồm cả khi bấm Back/Forward)
    useEffect(() => {
        setPage(1);
    }, [selectedKind]);

    // 4. Hàm gọi API lấy danh sách đề thi
    const fetchExams = async (currentPage: number, currentPageSize: number, kind: ExamKindFilter) => {
        const currentSeq = ++fetchSequenceRef.current;
        setLoading(true);
        try {
            const params: { page: number; size: number; kind?: ExamKind } = {
                page: currentPage - 1,
                size: currentPageSize,
            };

            if (kind !== "all") {
                params.kind = kind as ExamKind;
            }

            const res = await getMyExams(params);

            // Chỉ cập nhật state nếu đây là request mới nhất
            if (currentSeq === fetchSequenceRef.current) {
                setExams(res.items);
                setTotal(res.totalItems);
            }
        } catch (error) {
            if (currentSeq === fetchSequenceRef.current) {
                message.error("Không thể tải danh sách đề thi. Vui lòng thử lại!");
            }
        } finally {
            if (currentSeq === fetchSequenceRef.current) {
                setLoading(false);
            }
        }
    };

    // 5. Sử dụng useEffect gọi API khi page, pageSize hoặc selectedKind thay đổi
    useEffect(() => {
        fetchExams(page, pageSize, selectedKind);
    }, [page, pageSize, selectedKind]);

    const handleViewExam = (exam: ExamListItem) => {
        navigate(`/exams/${exam.examId}`);
    };

    // NV-01: xóa đề (backend xóa mềm). Xóa hết đề của trang cuối thì lùi một trang, không thì tải lại trang hiện tại.
    const [deletingId, setDeletingId] = useState<number | null>(null);
    const handleDeleteExam = async (exam: ExamListItem) => {
        setDeletingId(exam.examId);
        try {
            await deleteExam(exam.examId);
            message.success(`Đã xóa đề "${exam.title}".`);
            if (exams.length === 1 && page > 1) {
                setPage(page - 1);
            } else {
                await fetchExams(page, pageSize, selectedKind);
            }
        } catch (error) {
            if (isExamHasAssignmentsError(error)) {
                // Đề còn bài giao trực tuyến: nói rõ lý do và dẫn tới các bài giao để xóa trước
                Modal.confirm({
                    title: "Chưa xóa được đề",
                    content: `Đề "${exam.title}" còn bài giao cho học sinh làm online. Thầy/cô xóa hết các bài giao của đề này trước, rồi mới xóa đề được.`,
                    okText: "Xem các bài giao",
                    cancelText: "Để sau",
                    onOk: () => navigate(`/exams/${exam.examId}/assignments`),
                });
            } else {
                message.error(error instanceof Error ? error.message : "Không thể xóa đề thi");
            }
        } finally {
            setDeletingId(null);
        }
    };

    const handlePageChange = (newPage: number, newPageSize: number) => {
        setPage(newPage);
        setPageSize(newPageSize);
    };

    const handleKindChange = (value: ExamKindFilter) => {
        // Cập nhật URL search params với replace: true để không làm rác lịch sử trình duyệt
        const newParams = new URLSearchParams(searchParams);
        if (value === "all") {
            newParams.delete("kind");
        } else {
            newParams.set("kind", value);
        }
        setSearchParams(newParams, { replace: true });
    };

    return (
        <div className="exam-list-page">
            <div className="exam-list-page-header">
                <div className="exam-list-page-header-content">
                    <div>
                        <Title level={2} className="exam-list-page-title">
                            <FileTextOutlined style={{ marginRight: 10, color: "#1677ff" }} />
                            Đề thi
                        </Title>
                    </div>

                    <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => navigate("/exams/generate")}
                    >
                        Tạo đề mới
                    </Button>
                </div>
            </div>

            <div className="exam-list-page-table">
                <Card>
                    <div className="exam-list-filter-bar">
                        <Segmented<ExamKindFilter>
                            value={selectedKind}
                            onChange={handleKindChange}
                            options={[
                                { label: "Tất cả", value: "all" },
                                { label: examKindConfig.lesson.label, value: "lesson" },
                                { label: examKindConfig.matrix.label, value: "matrix" },
                            ]}
                        />
                    </div>
                    <ExamTable
                        data={exams}
                        loading={loading}
                        total={total}
                        page={page}
                        pageSize={pageSize}
                        onView={handleViewExam}
                        onDelete={handleDeleteExam}
                        deletingId={deletingId}
                        onCreate={() => navigate("/exams/generate")}
                        onPageChange={handlePageChange}
                    />
                </Card>
            </div>
        </div>
    );
}