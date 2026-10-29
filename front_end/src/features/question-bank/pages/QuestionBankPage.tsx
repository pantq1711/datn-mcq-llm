import { useEffect, useState } from "react";
import { Button, Card, message, Tooltip, Typography } from "antd";
import { FileWordOutlined } from "@ant-design/icons";
import QuestionDetail from "../components/QuestionDetail";
import QuestionFilters from "../components/QuestionFilters";
import QuestionTable from "../components/QuestionTable";
import "./QuestionBankPage.css";
import type { SubjectOption, BankItemDetail, QuestionTableItem, QuestionFilterValues } from "../types/questionBank.types";
import { getCatalogSubjects, getBankItems, getBankItemDetail } from "../services/questionBankApi";

const { Title } = Typography;

const initialFilters: QuestionFilterValues = {
    keyword: "",
    subjectId: null,
    gradeLevel: null,
    questionType: null,
    topic: null,
    difficulty: null,
    status: null,
};

export default function QuestionBankPage() {
    const [filters, setFilters] =
        useState<QuestionFilterValues>(initialFilters);

    const [selectedQuestion, setSelectedQuestion] = useState<BankItemDetail | null>(null);

    const [subjectOptions, setSubjectOptions] = useState<SubjectOption[]>([]);
    const [loadingSubjects, setLoadingSubjects] = useState<boolean>(false);

    const [questions, setQuestions] = useState<QuestionTableItem[]>([]);
    const [totalQuestions, setTotalQuestions] = useState<number>(0);
    const [page, setPage] = useState<number>(1);
    const [pageSize, setPageSize] = useState<number>(10);
    const [loadingTable, setLoadingTable] = useState<boolean>(false);

    const [isDetailOpen, setIsDetailOpen] = useState<boolean>(false);
    const [loadingDetail, setLoadingDetail] = useState<boolean>(false);

    useEffect(() => {
        const fetchSubjects = async () => {
            setLoadingSubjects(true);
            try {
                const subjects = await getCatalogSubjects();
                // Map dữ liệu API về format { label, value } cho antd Select
                const options = subjects.map((s) => ({
                    label: s.name,
                    value: s.id,
                }));
                setSubjectOptions(options);
            } catch (error) {
                console.error("Lỗi khi tải danh sách môn học:", error);
                message.error("Không thể tải danh sách môn học");
            } finally {
                setLoadingSubjects(false);
            }
        };
        fetchSubjects();
    }, []);

    const fetchQuestions = async (currentPage = page, currentPageSize = pageSize, currentFilters = filters) => {
        setLoadingTable(true);
        try {
            const res = await getBankItems({
                page: currentPage,
                pageSize: currentPageSize,
                ...currentFilters,
            });
            setQuestions(res.items || []);
            setTotalQuestions(res.totalItems ?? res.total ?? 0);
        } catch (error) {
            console.error("Lỗi khi tải danh sách câu hỏi ngân hàng:", error);
            message.error("Không thể tải danh sách câu hỏi.");
        } finally {
            setLoadingTable(false);
        }
    };

    // Gọi khi lần đầu mở trang hoặc khi phân trang thay đổi
    useEffect(() => {
        fetchQuestions(page, pageSize);
    }, [page, pageSize]);


    const handleFilter = () => {
        setPage(1); // Reset về trang 1 khi lọc
        fetchQuestions(1, pageSize, filters);
    };

    const handleReset = () => {
        setFilters(initialFilters);
        setPage(1);
        fetchQuestions(1, pageSize, initialFilters);
    };

    const handleViewQuestion = async (questionItem: QuestionTableItem) => {
        setIsDetailOpen(true);
        setLoadingDetail(true);
        try {
            const detail = await getBankItemDetail(questionItem.id);
            setSelectedQuestion(detail);
        } catch (error) {
            console.error("Lỗi khi tải chi tiết câu hỏi:", error);
            message.error("Không thể tải chi tiết câu hỏi.");
            // Fallback: nếu API chi tiết lỗi, hiển thị tạm thông tin đã có từ bảng
            setSelectedQuestion({
                id: questionItem.id,
                questionText: questionItem.questionText,
                optionA: "Không có dữ liệu",
                optionB: "Không có dữ liệu",
                optionC: "Không có dữ liệu",
                optionD: "Không có dữ liệu",
                correct_option: "A",
                subject: questionItem.subject,
                difficulty: questionItem.difficulty,
                status: questionItem.status,
                createdAt: questionItem.createdAt,
            } as BankItemDetail);
        } finally {
            setLoadingDetail(false);
        }
    };

    const handleCloseDetail = () => {
        setIsDetailOpen(false);
        setSelectedQuestion(null);
    };

    const handlePageChange = (newPage: number, newPageSize: number) => {
        setPage(newPage);
        setPageSize(newPageSize);
    };

    return (
        <div className="question-bank-page">
            <div className="question-bank-page-header">
                <Title level={2} className="question-bank-page-title">
                    Ngân hàng câu hỏi
                </Title>
                {/* Chỗ giữ sẵn: backend chưa có API nhập câu vào ngân hàng (docs/api: ngân hàng chỉ đọc, origin=import để dành) */}
                <Tooltip title="Chưa hỗ trợ: cần API nhập câu vào ngân hàng ở backend.">
                    <Button icon={<FileWordOutlined />} disabled>
                        Nhập từ Word
                    </Button>
                </Tooltip>
            </div>

            <div className="question-bank-page-filters">
                <Card>
                    <QuestionFilters
                        value={filters}
                        subjects={subjectOptions}
                        loadingSubjects={loadingSubjects}
                        onChange={setFilters}
                        onFilter={handleFilter}
                        onReset={handleReset}
                    />
                </Card>
            </div>

            <div className="question-bank-page-table">
                <Card>
                    <QuestionTable
                        data={questions}
                        total={totalQuestions}
                        page={page}
                        pageSize={pageSize}
                        loading={loadingTable}
                        onPageChange={handlePageChange}
                        onView={handleViewQuestion}
                    />
                </Card>
            </div>

            <QuestionDetail
                open={isDetailOpen}
                loading={loadingDetail}
                question={selectedQuestion}
                onClose={handleCloseDetail}
            />
        </div>
    );
}