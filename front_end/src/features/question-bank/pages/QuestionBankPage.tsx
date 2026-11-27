import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, message, Typography } from "antd";
import { FileWordOutlined, ReadOutlined } from "@ant-design/icons";
import QuestionDetail from "../components/QuestionDetail";
import QuestionFilters from "../components/QuestionFilters";
import QuestionTable from "../components/QuestionTable";
import "./QuestionBankPage.css";
import type { SubjectOption, BankItemDetail, BankSort, QuestionTableItem, QuestionFilterValues } from "../types/questionBank.types";
import { getCatalogSubjects, getBankItems, getBankItemDetail, removeBankItem } from "../services/questionBankApi";
import { getApiErrorMessage } from "../../../utils/apiError";

const { Title, Paragraph } = Typography;

const initialFilters: QuestionFilterValues = {
    keyword: "",
    subjectId: null,
    gradeLevel: null,
    questionType: null,
    topic: null,
    difficulty: null,
    usage: null,
    levels: null,
    content: null
};

const IMPORT_PATH = "/question-bank/import";

export default function QuestionBankPage() {
    const navigate = useNavigate();
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

    const [sort, setSort] = useState<BankSort>("newest");

    useEffect(() => {
        const fetchSubjects = async () => {
            setLoadingSubjects(true);
            try {
                const subjects = await getCatalogSubjects();
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

    // Bộ đếm lượt tải: lọc ngay khi đổi ô chọn có thể gửi nhiều lượt liền nhau; lượt cũ về muộn thì bỏ, không đè lượt mới
    const fetchSequenceRef = useRef(0);

    const fetchQuestions = async (currentPage = page, currentPageSize = pageSize, currentFilters = filters, currentSort = sort) => {
        const fetchId = ++fetchSequenceRef.current;
        setLoadingTable(true);
        try {
            const res = await getBankItems({
                page: currentPage,
                pageSize: currentPageSize,
                ...currentFilters,
                sort: currentSort,
            });
            if (fetchId !== fetchSequenceRef.current) return;
            setQuestions(res.items || []);
            setTotalQuestions(res.totalItems ?? res.total ?? 0);
        } catch (error) {
            if (fetchId !== fetchSequenceRef.current) return;
            console.error("Lỗi khi tải danh sách câu hỏi ngân hàng:", error);
            message.error("Không thể tải danh sách câu hỏi.");
        } finally {
            if (fetchId === fetchSequenceRef.current) setLoadingTable(false);
        }
    };

    // Gọi khi lần đầu mở trang hoặc khi phân trang thay đổi
    useEffect(() => {
        fetchQuestions(page, pageSize, filters, sort);
    }, [page, pageSize]);


    // `next`: bộ lọc vừa đổi khi lọc ngay lúc đổi ô chọn (state `filters` lúc đó chưa kịp cập nhật)
    const handleFilter = (next: QuestionFilterValues = filters) => {
        setPage(1); // Reset về trang 1 khi lọc
        fetchQuestions(1, pageSize, next, sort);
    };

    const handleViewQuestion = async (questionItem: QuestionTableItem) => {
        setIsDetailOpen(true);
        setLoadingDetail(true);
        try {
            const detail = await getBankItemDetail(questionItem.id);
            setSelectedQuestion(detail);
        } catch (error: any) {
            console.error("Lỗi khi tải chi tiết câu hỏi:", error);
            message.error(error?.response?.data?.error || "Không thể tải chi tiết câu hỏi.");
            setIsDetailOpen(false);
            setSelectedQuestion(null);
        } finally {
            setLoadingDetail(false);
        }
    };

    const handleCloseDetail = () => {
        setIsDetailOpen(false);
        setSelectedQuestion(null);
    };

    // NV-21: gỡ câu khỏi ngân hàng rồi đóng ngăn chi tiết và tải lại danh sách (câu đã gỡ không còn trong danh sách)
    const handleRemoveQuestion = async (question: BankItemDetail) => {
        try {
            await removeBankItem(question.id);
            message.success("Đã gỡ câu khỏi ngân hàng. Các đề đã có câu này vẫn giữ nguyên.");
            handleCloseDetail();
            await fetchQuestions(page, pageSize, filters, sort);
        } catch (error: unknown) {
            message.error(getApiErrorMessage(error, "Không gỡ được câu khỏi ngân hàng."));
        }
    };

    const handlePageChange = (newPage: number, newPageSize: number) => {
        setPage(newPage);
        setPageSize(newPageSize);
        fetchQuestions(newPage, newPageSize, filters, sort);
    };

    // Khi đổi lựa chọn sắp xếp: áp dụng ngay lập tức
    const handleSortChange = (value: BankSort) => {
        setSort(value);
        setPage(1);
        fetchQuestions(1, pageSize, filters, value);
    };

    // Khi bấm nút "Đặt lại" trong bộ lọc
    const handleReset = () => {
        setFilters(initialFilters);
        setSort("newest"); // Đưa sắp xếp về newest
        setPage(1);
        fetchQuestions(1, pageSize, initialFilters, "newest");
    };

    // Có điều kiện lọc nào khác mặc định: bảng rỗng thì gợi ý bỏ bớt điều kiện thay vì chỉ ghi "Trống"
    const hasFilters = Object.values(filters).some((v) =>
        Array.isArray(v) ? v.some((item) => item != null) : v != null && v !== ""
    );

    return (
        <div className="question-bank-page">
            <div className="question-bank-page-header">
                <div>
                    <Title level={2} className="question-bank-page-title">
                        <ReadOutlined style={{ marginRight: 10, color: "#1677ff" }} />
                        Ngân hàng câu hỏi
                    </Title>
                    {/* NV-30: cột "Phạm vi" cho biết từng câu là Chung hay Riêng */}
                    <Paragraph type="secondary" className="question-bank-page-note">
                        Gồm câu dùng chung và câu riêng của thầy/cô (cột "Phạm vi"). Câu riêng chỉ thầy/cô thấy.
                    </Paragraph>
                </div>
                {/* href để mở được bằng chuột phải/tab mới; bấm thường thì chuyển trang trong ứng dụng */}
                <Button
                    icon={<FileWordOutlined />}
                    href={IMPORT_PATH}
                    onClick={(event) => {
                        event.preventDefault();
                        navigate(IMPORT_PATH);
                    }}
                >
                    Nhập từ Word
                </Button>
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
                <QuestionTable
                    data={questions}
                    total={totalQuestions}
                    page={page}
                    pageSize={pageSize}
                    sort={sort}
                    loading={loadingTable}
                    onSortChange={handleSortChange}
                    onPageChange={handlePageChange}
                    onView={handleViewQuestion}
                    hasFilters={hasFilters}
                    onClearFilters={handleReset}
                />
            </div>

            <QuestionDetail
                open={isDetailOpen}
                loading={loadingDetail}
                question={selectedQuestion}
                onClose={handleCloseDetail}
                onRemove={handleRemoveQuestion}
            />
        </div>
    );
}