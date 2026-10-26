// import { useEffect, useState } from "react";
// import { useNavigate } from "react-router-dom";
// import { Select, Alert, Button, Card, Input, InputNumber, Modal, Table, Typography, message, } from "antd";
// import type { ColumnsType } from "antd/es/table";
// import { DeleteOutlined, HistoryOutlined, PlusOutlined, ThunderboltOutlined, } from "@ant-design/icons";

// import LessonContentInput from "../components/LessonContentInput";
// import TextSegmentSelector from "../components/TextSegmentSelector";
// import GenerationProgress from "../components/GenerationProgress";
// import GenerationResult from "../components/GenerationResult";
// // import TemplateSelector from "../components/TemplateSelector";

// import { getCatalogSubjects, getGenerationStatus, getGenerationResult, } from "../services/generationApi";
// import type { SubjectOption, GenerationResultItem, GenerationStatus, } from "../types/generation.types";

// import "./GenerateQuestionPage.css";

// const { Title, Text } = Typography;

// interface SelectedTextSegment {
//     id: string;
//     text: string;
//     startOffset: number;
//     endOffset: number;
// }

// interface MatrixTopic {
//     id: string;
//     name: string;
// }

// type CognitiveLevel = "BIET" | "HIEU" | "VAN_DUNG";
// type MatrixCounts = Record<CognitiveLevel, Record<string, number>>;

// interface MatrixTableRow {
//     key: CognitiveLevel;
//     label: string;
// }

// const cognitiveLevels: { key: CognitiveLevel; label: string }[] = [
//     { key: "BIET", label: "Biết" },
//     { key: "HIEU", label: "Hiểu" },
//     { key: "VAN_DUNG", label: "Vận dụng" },
// ];

// const initialTopics: MatrixTopic[] = [
//     { id: "topic-1", name: "Chủ đề 1" },
//     { id: "topic-2", name: "Chủ đề 2" },
// ];

// const createEmptyMatrix = (topics: MatrixTopic[]): MatrixCounts => ({
//     BIET: Object.fromEntries(topics.map((topic) => [topic.id, 0])),
//     HIEU: Object.fromEntries(topics.map((topic) => [topic.id, 0])),
//     VAN_DUNG: Object.fromEntries(topics.map((topic) => [topic.id, 0])),
// });

// export default function GenerateQuestionPage() {
//     const navigate = useNavigate();

//     const [lessonContent, setLessonContent] = useState("");
//     const [selectedSegments, setSelectedSegments] = useState<SelectedTextSegment[]>([]);

//     const [taskId, setTaskId] = useState<string | number | null>(null);
//     const [generationStatus, setGenerationStatus] =
//         useState<GenerationStatus>("IDLE");
//     const [completedCount, setCompletedCount] = useState(0);
//     const [currentQuestion, setCurrentQuestion] = useState(0);
//     const [errorMessage, setErrorMessage] = useState<string>();
//     const [generatedQuestions, setGeneratedQuestions] = useState<GenerationResultItem[]>([]);
//     const [resultLoading, setResultLoading] = useState(false);
//     const [loading, setLoading] = useState(false);

//     const [topics, setTopics] = useState<MatrixTopic[]>(initialTopics);
//     const [matrixCounts, setMatrixCounts] = useState<MatrixCounts>(() =>
//         createEmptyMatrix(initialTopics),
//     );
//     const [topicModalOpen, setTopicModalOpen] = useState(false);
//     const [topicDrafts, setTopicDrafts] = useState<MatrixTopic[]>(initialTopics);

//     const isRunning = generationStatus === "RUNNING";

//     const [selectedSubjectId, setSelectedSubjectId] = useState<number | undefined>();
//     const [subjectOptions, setSubjectOptions] = useState<SubjectOption[]>([]);
//     const [loadingSubjects, setLoadingSubjects] = useState<boolean>(false);

//     // const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
//     // const [templateOptions, setTemplateOptions] = useState<TemplateOption[]>([]);
//     // const [loadingTemplates, setLoadingTemplates] = useState<boolean>(false);


//     useEffect(() => {
//         const fetchSubjects = async () => {
//             setLoadingSubjects(true);
//             try {
//                 const subjects = await getCatalogSubjects();
//                 const options = subjects.map((s) => ({
//                     label: s.name,
//                     value: s.id,
//                 }));
//                 setSubjectOptions(options);
//             } catch (error) {
//                 console.error("Lỗi khi tải danh sách môn học:", error);
//                 message.error("Không thể tải danh sách môn học");
//             } finally {
//                 setLoadingSubjects(false);
//             }
//         };

//         fetchSubjects();
//     }, []);

//     // useEffect(() => {
//     //     const fetchTemplates = async () => {
//     //         setLoadingTemplates(true);
//     //         try {
//     //             const templates = await getCatalogTemplates(selectedSubjectId);
//     //             const options: TemplateOption[] = templates.map((t) => ({
//     //                 id: t.id,
//     //                 label: `Mẫu #${t.id}`,
//     //                 subject: t.subjectName || "Môn học",
//     //                 version: t.version,
//     //                 sourceQuestion: t.sourceQuestion,
//     //             }));
//     //             setTemplateOptions(options);

//     //             // Nếu môn học thay đổi mà template đang chọn không thuộc môn mới thì reset
//     //             if (selectedTemplateId && !options.some(o => o.id === selectedTemplateId)) {
//     //                 setSelectedTemplateId(null);
//     //             }
//     //         } catch (error) {
//     //             console.error("Lỗi khi tải câu hỏi mẫu:", error);
//     //             message.error("Không thể tải danh sách câu hỏi mẫu.");
//     //         } finally {
//     //             setLoadingTemplates(false);
//     //         }
//     //     };

//     //     fetchTemplates();
//     // }, [selectedSubjectId]);


//     const totalByTopic = (topicId: string) =>
//         cognitiveLevels.reduce(
//             (total, level) => total + (matrixCounts[level.key][topicId] ?? 0),
//             0,
//         );

//     const totalByLevel = (level: CognitiveLevel) =>
//         topics.reduce(
//             (total, topic) => total + (matrixCounts[level][topic.id] ?? 0),
//             0,
//         );

//     const totalQuestionCount = cognitiveLevels.reduce(
//         (total, level) => total + totalByLevel(level.key),
//         0,
//     );

//     const updateMatrixCell = (
//         level: CognitiveLevel,
//         topicId: string,
//         value: number | null,
//     ) => {
//         setMatrixCounts((previous) => ({
//             ...previous,
//             [level]: {
//                 ...previous[level],
//                 [topicId]: Math.max(0, Math.min(200, value ?? 0)),
//             },
//         }));
//     };

//     const openTopicModal = () => {
//         setTopicDrafts(topics.map((topic) => ({ ...topic })));
//         setTopicModalOpen(true);
//     };

//     const addTopicDraft = () => {
//         setTopicDrafts((previous) => [
//             ...previous,
//             {
//                 id: `topic-${Date.now()}-${previous.length}`,
//                 name: `Chủ đề ${previous.length + 1}`,
//             },
//         ]);
//     };

//     const updateTopicDraft = (topicId: string, name: string) => {
//         setTopicDrafts((previous) =>
//             previous.map((topic) =>
//                 topic.id === topicId ? { ...topic, name } : topic,
//             ),
//         );
//     };

//     const removeTopicDraft = (topicId: string) => {
//         if (topicDrafts.length <= 1) {
//             message.warning("Ma trận cần có ít nhất một chủ đề.");
//             return;
//         }
//         setTopicDrafts((previous) =>
//             previous.filter((topic) => topic.id !== topicId),
//         );
//     };

//     const saveTopics = () => {
//         const normalizedTopics = topicDrafts.map((topic) => ({
//             ...topic,
//             name: topic.name.trim(),
//         }));

//         if (
//             normalizedTopics.length === 0 ||
//             normalizedTopics.some((topic) => !topic.name)
//         ) {
//             message.warning("Vui lòng nhập tên cho tất cả chủ đề.");
//             return;
//         }

//         const normalizedNames = normalizedTopics.map((topic) =>
//             topic.name.toLocaleLowerCase(),
//         );
//         if (new Set(normalizedNames).size !== normalizedNames.length) {
//             message.warning("Tên chủ đề không được trùng nhau.");
//             return;
//         }

//         setMatrixCounts((previous) => {
//             const next = {} as MatrixCounts;
//             cognitiveLevels.forEach((level) => {
//                 next[level.key] = Object.fromEntries(
//                     normalizedTopics.map((topic) => [
//                         topic.id,
//                         previous[level.key][topic.id] ?? 0,
//                     ]),
//                 );
//             });
//             return next;
//         });

//         setTopics(normalizedTopics);
//         setTopicModalOpen(false);
//     };

//     const matrixRows: MatrixTableRow[] = cognitiveLevels.map((level) => ({
//         key: level.key,
//         label: level.label,
//     }));

//     const matrixColumns: ColumnsType<MatrixTableRow> = [
//         {
//             title: "Mức độ",
//             dataIndex: "label",
//             key: "level",
//             width: 170,
//             fixed: "left",
//         },
//         ...topics.map((topic) => ({
//             title: topic.name,
//             key: topic.id,
//             align: "center" as const,
//             width: 80,
//             render: (_value: unknown, row: MatrixTableRow) => (
//                 <InputNumber
//                     min={0}
//                     max={200}
//                     precision={0}
//                     disabled={isRunning}
//                     value={matrixCounts[row.key][topic.id] ?? 0}
//                     onChange={(value) => updateMatrixCell(row.key, topic.id, value)}
//                     className="generate-question-page-matrix-number"
//                     aria-label={`${row.label} ${topic.name}`}
//                 />
//             ),
//         })),
//         {
//             title: "Tổng theo mức",
//             key: "level-total",
//             align: "center",
//             width: 130,
//             render: (_value: unknown, row: MatrixTableRow) => (
//                 <Text strong>{totalByLevel(row.key)}</Text>
//             ),
//         },
//     ];

//     const handleGeneration = async () => {
//         if (!lessonContent.trim()) {
//             message.warning("Vui lòng nhập hoặc tải lên nội dung bài học.");
//             return;
//         }

//         if (selectedSegments.length === 0) {
//             message.warning("Vui lòng chọn nội dung cần kiểm tra.");
//             return;
//         }

//         if (totalQuestionCount < 1) {
//             message.warning("Vui lòng phân bổ ít nhất một câu hỏi trong ma trận.");
//             return;
//         }

//         if (totalQuestionCount > 200) {
//             message.warning("Tổng số câu hỏi không được vượt quá 200.");
//             return;
//         }

//         setLoading(true);

//         try {
//             // setGenerationStatus("RUNNING");
//             // setCompletedCount(0);
//             // setCurrentQuestion(0);
//             // setErrorMessage(undefined);
//             // setGeneratedQuestions([]);

//             // const questionMatrix = cognitiveLevels.flatMap((level) =>
//             //     topics.map((topic) => ({
//             //         topicId: topic.id,
//             //         topicName: topic.name,
//             //         cognitiveLevel: level.key,
//             //         questionCount: matrixCounts[level.key][topic.id] ?? 0,
//             //     })),
//             // );

//             // const payload = {
//             //     lessonContent,
//             //     selectedSegments,
//             //     questionCount: totalQuestionCount,
//             //     questionMatrix,
//             // };

//             // const response = await createGenerationTask(payload);
//             // setTaskId(response.taskId);
//             // message.success("Đã khởi tạo tiến trình sinh câu hỏi!");
//             console.log("Đã khởi tạo tiến trình sinh câu hỏi!");
//         } catch (error) {
//             console.error("Lỗi tạo task:", error);
//             setGenerationStatus("FAILED");
//             setErrorMessage("Không thể khởi tạo tác vụ sinh câu hỏi. Vui lòng kiểm tra API.");
//             message.error("Không thể khởi tạo tiến trình sinh câu hỏi.");
//         } finally {
//             setLoading(false);
//         }
//     };

//     useEffect(() => {
//         if (!taskId || generationStatus !== "RUNNING") return;

//         const intervalId = setInterval(async () => {
//             try {
//                 const data = await getGenerationStatus(taskId);
//                 setGenerationStatus(data.status);
//                 setCompletedCount(data.completedCount);

//                 if (data.currentQuestion !== undefined) {
//                     setCurrentQuestion(data.currentQuestion);
//                 }
//                 if (data.errorMessage) {
//                     setErrorMessage(data.errorMessage);
//                 }

//                 if (data.status === "COMPLETED" || data.status === "FAILED") {
//                     clearInterval(intervalId);

//                     if (data.status === "COMPLETED") {
//                         message.success("Hoàn thành sinh toàn bộ câu hỏi!");
//                         setResultLoading(true);

//                         try {
//                             const resultData = await getGenerationResult(taskId);
//                             setGeneratedQuestions(resultData.questions);
//                         } catch (error) {
//                             console.error("Lỗi lấy danh sách câu hỏi kết quả:", error);
//                             message.error("Không thể tải danh sách câu hỏi kết quả.");
//                         } finally {
//                             setResultLoading(false);
//                         }
//                     } else {
//                         message.error("Quá trình sinh câu hỏi gặp sự cố.");
//                     }
//                 }
//             } catch (error) {
//                 console.error("Lỗi lấy tiến độ:", error);
//             }
//         }, 2500);

//         return () => clearInterval(intervalId);
//     }, [taskId, generationStatus]);

//     return (
//         <div className="generate-question-page">
//             <div className="generate-question-page-header">
//                 <div className="generate-question-page-header-content">
//                     <div className="generate-question-page-header-info">
//                         <Title level={2} className="generate-question-page-title">
//                             Sinh câu hỏi
//                         </Title>
//                     </div>

//                     <Button
//                         icon={<HistoryOutlined />}
//                         onClick={() => navigate("/generation-history")}
//                     >
//                         Lịch sử sinh câu hỏi
//                     </Button>
//                 </div>
//             </div>

//             <div className="generate-question-page-content">
//                 <Card
//                     title="1. Nội dung bài học"
//                     className="generate-question-page-card"
//                 >
//                     <div className="generate-question-subject-wrapper">
//                         <Text strong className="generate-question-subject-label">
//                             Môn học:
//                         </Text>
//                         <Select
//                             placeholder="Chọn môn học"
//                             allowClear
//                             loading={loadingSubjects}
//                             options={subjectOptions}
//                             value={selectedSubjectId}
//                             onChange={(value) => setSelectedSubjectId(value)}
//                             className="generate-question-subject-select"
//                             size="large"
//                             disabled={isRunning}
//                         />
//                     </div>

//                     {/* <div style={{ marginTop: 10, marginBottom: 20 }}>
//                         <TemplateSelector
//                             value={selectedTemplateId}
//                             options={templateOptions}
//                             loading={loadingTemplates}
//                             disabled={isRunning}
//                             onChange={setSelectedTemplateId}
//                         />
//                     </div> */}

//                     <LessonContentInput
//                         value={lessonContent}
//                         onChange={(content) => {
//                             setLessonContent(content);
//                             setSelectedSegments([]);
//                         }}
//                     />
//                 </Card>
//             </div>

//             <div className="generate-question-page-content">
//                 <Card
//                     title="2. Chọn nội dung cần kiểm tra"
//                     className="generate-question-page-card"
//                 >
//                     <Text
//                         type="secondary"
//                         className="generate-question-page-description"
//                     >
//                         Chọn một hoặc nhiều đoạn trong nội dung bài học làm phạm vi sinh câu hỏi.
//                     </Text>

//                     <TextSegmentSelector
//                         content={lessonContent}
//                         value={selectedSegments}
//                         onChange={setSelectedSegments}
//                         disabled={!lessonContent.trim()}
//                     />
//                 </Card>
//             </div>

//             <div className="generate-question-page-content">
//                 <Card
//                     title={
//                         <div className="generate-question-page-matrix-card-title">
//                             <span>3. Ma trận câu hỏi</span>
//                             <Button onClick={openTopicModal} disabled={isRunning}>
//                                 Tùy chỉnh
//                             </Button>
//                         </div>
//                     }
//                     className="generate-question-page-card"
//                 >
//                     <div className="generate-question-page-matrix-table">
//                         <Table<MatrixTableRow>
//                             rowKey="key"
//                             columns={matrixColumns}
//                             dataSource={matrixRows}
//                             pagination={false}
//                             bordered
//                             size="middle"
//                             scroll={{ x: "max-content" }}
//                             summary={() => (
//                                 <Table.Summary>
//                                     <Table.Summary.Row>
//                                         <Table.Summary.Cell index={0}>
//                                             <Text strong>Tổng số câu</Text>
//                                         </Table.Summary.Cell>
//                                         {topics.map((topic, index) => (
//                                             <Table.Summary.Cell
//                                                 index={index + 1}
//                                                 key={topic.id}
//                                             >
//                                                 <Text strong>{totalByTopic(topic.id)}</Text>
//                                             </Table.Summary.Cell>
//                                         ))}
//                                         <Table.Summary.Cell index={topics.length + 1}>
//                                             <Text strong>{totalQuestionCount}</Text>
//                                         </Table.Summary.Cell>
//                                     </Table.Summary.Row>
//                                 </Table.Summary>
//                             )}
//                         />
//                     </div>

//                     <div className="generate-question-page-matrix-footer">
//                         <div className="generate-question-page-matrix-summary">
//                             <Text>Tổng số câu hỏi</Text>
//                             <Text strong>{totalQuestionCount}</Text>
//                         </div>

//                         <Button
//                             type="primary"
//                             icon={<ThunderboltOutlined />}
//                             loading={loading}
//                             disabled={
//                                 !lessonContent.trim() ||
//                                 selectedSegments.length === 0 ||
//                                 totalQuestionCount < 1 ||
//                                 totalQuestionCount > 200 ||
//                                 isRunning
//                             }
//                             onClick={handleGeneration}
//                         >
//                             Sinh câu hỏi
//                         </Button>
//                     </div>

//                     <Modal
//                         title="Tùy chỉnh chủ đề"
//                         open={topicModalOpen}
//                         onCancel={() => setTopicModalOpen(false)}
//                         onOk={saveTopics}
//                         okText="Lưu thay đổi"
//                         cancelText="Hủy"
//                         destroyOnClose
//                     >
//                         <Text type="secondary">
//                             Đổi tên, thêm hoặc xóa các cột chủ đề trong ma trận.
//                         </Text>

//                         <div className="generate-question-page-topic-list">
//                             {topicDrafts.map((topic) => (
//                                 <div
//                                     className="generate-question-page-topic-row"
//                                     key={topic.id}
//                                 >
//                                     <Input
//                                         value={topic.name}
//                                         onChange={(event) =>
//                                             updateTopicDraft(topic.id, event.target.value)
//                                         }
//                                         placeholder="Tên chủ đề"
//                                         maxLength={100}
//                                     />
//                                     <Button
//                                         danger
//                                         type="text"
//                                         icon={<DeleteOutlined />}
//                                         aria-label={`Xóa ${topic.name}`}
//                                         onClick={() => removeTopicDraft(topic.id)}
//                                         disabled={topicDrafts.length <= 1}
//                                     />
//                                 </div>
//                             ))}

//                             <Button
//                                 icon={<PlusOutlined />}
//                                 onClick={addTopicDraft}
//                                 block
//                             >
//                                 Thêm chủ đề
//                             </Button>
//                         </div>
//                     </Modal>
//                 </Card>
//             </div>

//             <div className="generate-question-page-content">
//                 <Card
//                     title="4. Tiến độ sinh câu hỏi"
//                     className="generate-question-page-card"
//                 >
//                     {errorMessage && (
//                         <Alert
//                             type="error"
//                             showIcon
//                             message={errorMessage}
//                             className="generate-question-page-error"
//                         />
//                     )}

//                     <GenerationProgress
//                         status={generationStatus}
//                         completedCount={completedCount}
//                         totalCount={totalQuestionCount}
//                         currentQuestion={currentQuestion}
//                     />
//                 </Card>
//             </div>

//             <div className="generate-question-page-result">
//                 <Card
//                     title="5. Kết quả sinh câu hỏi"
//                     className="generate-question-page-card"
//                 >
//                     <GenerationResult
//                         data={generatedQuestions}
//                         loading={resultLoading}
//                     />
//                 </Card>
//             </div>
//         </div>
//     );
// }