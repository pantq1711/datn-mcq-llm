// import { useEffect, useState } from "react";
// import { Button, Card, Input, Select, Table, Tag, Typography, message } from "antd";
// import { ReloadOutlined, SearchOutlined, CheckCircleOutlined } from "@ant-design/icons";
// import { useNavigate } from "react-router-dom";
// import type { ColumnsType } from "antd/es/table";
// import dayjs from "dayjs";
// import "./ReviewQuestionsPage.css";
// import type { ReviewQuestionListItem } from "../types/review.types";

// // Giả lập hàm gọi API lấy danh sách câu hỏi thực tế
// import { getReviewQuestions } from "../services/teacherReviewApi";

// const { Title, Text } = Typography;

// const getReviewPurposeTag = (purpose?: string) => {
//     switch (purpose) {
//         case "PRODUCTION":
//             return <Tag color="blue">Sản xuất (PRODUCTION)</Tag>;
//         case "EXPERIMENT":
//             return <Tag color="purple">Thực nghiệm (EXPERIMENT)</Tag>;
//         default:
//             return <Tag>{purpose || "N/A"}</Tag>;
//     }
// };

// export default function ReviewQuestionsPage() {
//     const navigate = useNavigate();

//     // Quản lý state dữ liệu và loading
//     const [questions, setQuestions] = useState<ReviewQuestionListItem[]>([]);
//     const [loading, setLoading] = useState<boolean>(false);

//     // State cho bộ lọc client-side
//     const [searchText, setSearchText] = useState<string>("");
//     const [selectedSubject, setSelectedSubject] = useState<string | undefined>(undefined);
//     const [selectedGrade, setSelectedGrade] = useState<string | undefined>(undefined);

//     // Hàm gọi API lấy danh sách câu hỏi
//     const fetchQuestions = async () => {
//         setLoading(true);
//         try {
//             const response = await getReviewQuestions();
//             setQuestions(response);
//         } catch (error) {
//             message.error("Không thể tải danh sách câu hỏi cần duyệt. Vui lòng thử lại sau!");
//         } finally {
//             setLoading(false);
//         }
//     };

//     // Gọi API khi mount component
//     useEffect(() => {
//         fetchQuestions();
//     }, []);

//     // Xử lý nút Đặt lại / Làm mới: Reset bộ lọc và gọi lại API nạp dữ liệu mới nhất
//     const handleResetFilters = () => {
//         setSearchText("");
//         setSelectedSubject(undefined);
//         setSelectedGrade(undefined);
//         fetchQuestions();
//     };

//     // Logic lọc phía client (Client-side filtering)
//     const filteredQuestions = questions.filter((item) => {
//         // 1. Lọc theo từ khóa trong questionText
//         const matchesSearch = searchText
//             ? item.questionText?.toLowerCase().includes(searchText.toLowerCase())
//             : true;

//         // 2. Lọc theo Môn học (subjectName)
//         const matchesSubject = selectedSubject
//             ? item.subjectName === selectedSubject
//             : true;

//         // 3. Lọc theo Khối lớp (gradeLevel)
//         const matchesGrade = selectedGrade
//             ? String(item.gradeLevel) === String(selectedGrade)
//             : true;

//         return matchesSearch && matchesSubject && matchesGrade;
//     });

//     const columns: ColumnsType<ReviewQuestionListItem> = [
//         {
//             title: "ID",
//             dataIndex: "id",
//             key: "id",
//             width: 80,
//             align: "center",
//             render: (id: number | string) => <Text strong>#{id}</Text>,
//         },
//         {
//             title: "Câu hỏi",
//             dataIndex: "questionText",
//             key: "questionText",
//             ellipsis: true,
//             render: (text: string) => (
//                 <span title={text}>
//                     {text}
//                 </span>
//             ),
//         },
//         {
//             title: "Môn học",
//             dataIndex: "subjectName",
//             key: "subjectName",
//             width: 130,
//         },
//         {
//             title: "Khối lớp / Chủ đề",
//             key: "gradeAndTopic",
//             width: 160,
//             render: (_, record: any) => (
//                 <span>
//                     {record.gradeLevel ? `Khối ${record.gradeLevel}` : ""}
//                     {record.gradeLevel && record.topic ? " - " : ""}
//                     {record.topic || ""}
//                 </span>
//             ),
//         },
//         {
//             title: "Mục đích",
//             dataIndex: "reviewPurpose",
//             key: "reviewPurpose",
//             width: 150,
//             align: "center",
//             render: (purpose) => getReviewPurposeTag(purpose),
//         },
//         {
//             title: "Ngày tạo",
//             dataIndex: "createdAt",
//             key: "createdAt",
//             width: 160,
//             render: (dateStr: string) =>
//                 dateStr ? dayjs(dateStr).format("DD/MM/YYYY HH:mm") : "N/A",
//         },
//         {
//             title: "Hành động",
//             key: "action",
//             width: 130,
//             align: "center",
//             render: (_, record) => (
//                 <Button
//                     type="link"
//                     icon={<CheckCircleOutlined />}
//                     onClick={(e) => {
//                         e.stopPropagation();
//                         navigate(`/review/${record.id}`);
//                     }}
//                 >
//                     Chấm câu hỏi
//                 </Button>
//             ),
//         },
//     ];

//     return (
//         <div className="review-questions-page">
//             <div className="review-questions-header">
//                 <div>
//                     <Title level={2} className="review-questions-title">
//                         Duyệt câu hỏi
//                     </Title>
//                 </div>
//             </div>

//             <Card className="review-filter-card">
//                 <div className="review-filter">
//                     {/* Tìm kiếm theo từ khóa trong questionText */}
//                     <Input
//                         className="review-search"
//                         placeholder="Tìm kiếm nội dung câu hỏi..."
//                         prefix={<SearchOutlined />}
//                         value={searchText}
//                         onChange={(e) => setSearchText(e.target.value)}
//                         allowClear
//                     />

//                     {/* Lọc theo Môn học */}
//                     <Select
//                         className="review-filter-select"
//                         placeholder="Chọn môn học"
//                         allowClear
//                         value={selectedSubject}
//                         onChange={(value) => setSelectedSubject(value)}
//                         options={[
//                             { label: "Toán", value: "Toán" },
//                             { label: "Vật lý", value: "Vật lý" },
//                             { label: "Hóa học", value: "Hóa học" },
//                             { label: "Sinh học", value: "Sinh học" },
//                         ]}
//                     />

//                     {/* Lọc theo Khối lớp */}
//                     <Select
//                         className="review-filter-select"
//                         placeholder="Chọn khối lớp"
//                         allowClear
//                         value={selectedGrade}
//                         onChange={(value) => setSelectedGrade(value)}
//                         options={[
//                             { label: "Khối 10", value: "10" },
//                             { label: "Khối 11", value: "11" },
//                             { label: "Khối 12", value: "12" },
//                         ]}
//                     />

//                     {/* Nút Đặt lại / Làm mới: Xóa bộ lọc và gọi lại API */}
//                     <Button
//                         icon={<ReloadOutlined />}
//                         onClick={handleResetFilters}
//                         loading={loading}
//                     >
//                         Đặt lại
//                     </Button>
//                 </div>
//             </Card>

//             <Card className="review-question-list-card">
//                 <div className="review-question-list-header">
//                     <div>
//                         <Title level={4} className="review-question-list-title">
//                             Danh sách câu hỏi
//                         </Title>
//                     </div>
//                 </div>

//                 <Table<ReviewQuestionListItem>
//                     className="review-question-table"
//                     rowKey="id"
//                     columns={columns}
//                     dataSource={filteredQuestions}
//                     loading={loading}
//                     pagination={{
//                         pageSize: 10,
//                         showSizeChanger: true,
//                         showTotal: (total) => `Tổng ${total} câu hỏi`,
//                     }}
//                     scroll={{ x: 1000 }}
//                     onRow={(record) => ({
//                         onClick: () => {
//                             navigate(`/review/${record.id}`);
//                         },
//                         style: { cursor: "pointer" },
//                     })}
//                 />
//             </Card>
//         </div>
//     );
// }