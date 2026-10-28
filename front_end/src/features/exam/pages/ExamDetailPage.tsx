import { useParams, useNavigate } from 'react-router-dom';
import { Button, Card, Spin, Space, Tag, Typography, message } from "antd";
import { ArrowLeftOutlined, EditOutlined, DeleteOutlined, FileWordOutlined, LineChartOutlined } from "@ant-design/icons";

import ExamPreview from "../components/ExamPreview";
import ExamQuestionList from "../components/ExamQuestionList";
import type { ExamPreviewData, ExamQuestionItem, ChangeQuestionRequest, QuestionReplacementCandidate, ExamDetail } from "../types/exam.types";
import { keepQuestion, getExamDetail } from '../services/examApi';

import "./ExamDetailPage.css";
import { useEffect, useState } from 'react';
import ChangeQuestionModal from "../components/ChangeQuestionModal";
import QuestionReplacementPreview from "../components/QuestionReplacementPreview";
import EditQuestionModal from "../components/EditQuestionModal";
import { ExamProgressDetail } from '../components/ExamProgressDetail';

const { Title, Text } = Typography;

export default function ExamDetailPage() {
    const navigate = useNavigate();

    // 1. Nhận examId và slotId từ URL
    const { examId, slotId } = useParams<{ examId: string; slotId: string }>();

    const [changeQuestion, setChangeQuestion] = useState<ExamQuestionItem | null>(null);
    // const [generatingReplacement, setGeneratingReplacement] = useState(false);

    const [originalQuestion, setOriginalQuestion] = useState<ExamQuestionItem | null>(null);
    const [replacementQuestion, setReplacementQuestion] = useState<QuestionReplacementCandidate | null>(null);
    const [showReplacementPreview, setShowReplacementPreview] = useState(false);
    // const [confirmingReplacement, setConfirmingReplacement] = useState(false);

    const [editingQuestion, setEditingQuestion] = useState<ExamQuestionItem | null>(null);
    const [savingEdit, setSavingEdit] = useState(false);

    const [detail, setDetail] = useState<ExamDetail | null>(null);
    const [questions, setQuestions] = useState<ExamQuestionItem[]>([]);
    const [loading, setLoading] = useState(true);

    const [showProgress, setShowProgress] = useState(false);

    useEffect(() => {
        if (!examId) return;
        (async () => {
            try {
                const data = await getExamDetail(examId);
                setDetail(data);
            } catch (err: any) {
                message.error(err?.response?.data?.error || 'Không tải được đề');
            } finally {
                setLoading(false);
            }
        })();
    }, [examId]);

    if (loading) return <Spin tip="Đang tải đề…" />;
    if (!detail) return <p>Không có dữ liệu đề.</p>;



    const exam: ExamPreviewData = {
        title: "Đề thi giữa kỳ Hóa học",
        subject: "Hóa học",
        questionCount: questions.length,
        duration: 30,
        easyCount: 1,
        mediumCount: 1,
        hardCount: 1,
    };

    const mockOriginalQuestion: ExamQuestionItem = {
        id: 1,
        questionText:
            "Dung dịch nào sau đây làm quỳ tím chuyển sang màu đỏ?",
        optionA: "Dung dịch NaOH",
        optionB: "Dung dịch HCl",
        optionC: "Dung dịch NaCl",
        optionD: "Dung dịch KOH",
        correctOption: "B",
        explanation:
            "HCl là axit mạnh, phân li trong nước tạo ion H+, làm quỳ tím chuyển sang màu đỏ.",
        difficulty: "easy",
    };

    const mockReplacementQuestion: QuestionReplacementCandidate = {
        questionText:
            "Khi nhúng giấy quỳ tím vào dung dịch H₂SO₄ loãng, hiện tượng nào xảy ra?",
        optionA: "Quỳ tím chuyển sang màu xanh",
        optionB: "Quỳ tím không đổi màu",
        optionC: "Quỳ tím chuyển sang màu đỏ",
        optionD: "Quỳ tím mất màu hoàn toàn",
        correctOption: "C",
        explanation:
            "H₂SO₄ là axit. Dung dịch axit làm giấy quỳ tím chuyển sang màu đỏ.",
        difficulty: "easy",
    };

    const handleGenerateReplacement = async (request: ChangeQuestionRequest) => {
        console.log('request: ', request);
    };

    const handleKeep = async (question: ExamQuestionItem, index: number) => {
        if (!examId || !slotId) return;
        try {
            await keepQuestion(Number(examId), Number(slotId), Number(question.id));
            message.success('Đã giữ câu hỏi thành công');
            console.log(`Đã giữ câu hỏi số ${index + 1} – ID: ${question.id}`);
        } catch (e) {
            console.error(e);
            message.error(e instanceof Error ? e.message : 'Gặp lỗi khi giữ câu hỏi');
        }
    };

    const handleSaveEditedQuestion = async (updatedQuestion: ExamQuestionItem) => {
        setSavingEdit(true);
        try {
            setQuestions(prev =>
                prev.map(q => (q.id === updatedQuestion.id ? updatedQuestion : q))
            );
            message.success("Cập nhật câu hỏi thành công!");
            setEditingQuestion(null);
        } catch (e) {
            message.error(e instanceof Error ? e.message : "Gặp lỗi khi cập nhật câu hỏi");
        } finally {
            setSavingEdit(false);
        }
    };

    return (
        <div className="exam-detail-page">
            <div className="exam-detail-page-header">
                <div className="exam-detail-page-header-top">
                    <Button
                        type="text"
                        icon={<ArrowLeftOutlined />}
                        className="exam-detail-page-back-button"
                        onClick={() => navigate("/exams")}
                    >
                        Quay lại danh sách
                    </Button>
                </div>

                <div className="exam-detail-page-header-content">
                    <div className="exam-detail-page-title-section">
                        <Title
                            level={2}
                            className="exam-detail-page-title"
                        >
                            {exam.title}
                        </Title>

                        <Space
                            size={8}
                            wrap
                            className="exam-detail-page-meta"
                        >
                            <Tag color="success">
                                Đã phát hành
                            </Tag>

                            <Text type="secondary">
                                {exam.subject}
                            </Text>
                        </Space>
                    </div>

                    <Space
                        size={8}
                        className="exam-detail-page-actions"
                    >
                        <Button
                            icon={<LineChartOutlined />}
                            onClick={() => setShowProgress((prev) => !prev)}
                            disabled={!examId}
                        >
                            {showProgress ? "Ẩn tiến độ" : "Xem tiến độ chi tiết"}
                        </Button>

                        <Button
                            type="primary"
                            icon={<FileWordOutlined />}
                            onClick={() => console.log("Export Word")}
                        >
                            Xuất file Word
                        </Button>

                        <Button
                            icon={<EditOutlined />}
                            onClick={() => console.log("Edit exam")}
                        >
                            Chỉnh sửa
                        </Button>

                        <Button
                            danger
                            icon={<DeleteOutlined />}
                            onClick={() => console.log("Delete exam")}
                        >
                            Xóa
                        </Button>
                    </Space>
                </div>
            </div>

            <div className="exam-detail-page-overview">
                <Card title="Thông tin đề thi">
                    <ExamPreview exam={exam} />
                </Card>
            </div>

            {showProgress && examId && (
                <div className="exam-detail-page-progress">
                    <ExamProgressDetail
                        examId={examId}
                        onClose={() => setShowProgress(false)}
                    />
                </div>
            )}

            <div className="exam-detail-page-question-list">
                <Card title="Danh sách câu hỏi">
                    {/* Nhóm câu trắc nghiệm */}
                    {questions.filter(q => q.optionA && q.optionB && q.optionC && q.optionD).length > 0 && (
                        <div className="question-group">
                            <Title level={5} className="question-group-title">Câu trắc nghiệm</Title>
                            <ExamQuestionList
                                questions={questions.filter(q => q.optionA && q.optionB && q.optionC && q.optionD)}
                                onChangeQuestion={(question) => {
                                    setChangeQuestion(question);
                                    setOriginalQuestion(question);
                                    setReplacementQuestion(mockReplacementQuestion);
                                }}
                                onKeep={handleKeep}
                                onEdit={(question) => setEditingQuestion(question)}
                            />
                        </div>
                    )}

                    {/* Nhóm câu đúng/sai */}
                    {questions.filter(q => q.optionA && q.optionB && !q.optionC && !q.optionD).length > 0 && (
                        <div className="question-group">
                            <Title level={5} className="question-group-title">Câu đúng/sai</Title>
                            <ExamQuestionList
                                questions={questions.filter(q => q.optionA && q.optionB && !q.optionC && !q.optionD)}
                                onChangeQuestion={(question) => {
                                    setChangeQuestion(question);
                                    setOriginalQuestion(question);
                                    setReplacementQuestion(mockReplacementQuestion);
                                }}
                                onKeep={handleKeep}
                                onEdit={(question) => setEditingQuestion(question)}
                            />
                        </div>
                    )}

                    {/* Nhóm câu trả lời ngắn */}
                    {questions.filter(q => !q.optionA && !q.optionB && !q.optionC && !q.optionD).length > 0 && (
                        <div className="question-group">
                            <Title level={5} className="question-group-title">Câu trả lời ngắn</Title>
                            <ExamQuestionList
                                questions={questions.filter(q => !q.optionA && !q.optionB && !q.optionC && !q.optionD)}
                                onChangeQuestion={(question) => {
                                    setChangeQuestion(question);
                                    setOriginalQuestion(question);
                                    setReplacementQuestion(mockReplacementQuestion);
                                }}
                                onKeep={handleKeep}
                                onEdit={(question) => setEditingQuestion(question)}
                            />
                        </div>
                    )}

                    <ChangeQuestionModal
                        open={changeQuestion !== null}
                        question={changeQuestion}
                        loading={false}
                        onCancel={() => setChangeQuestion(null)}
                        onGenerate={handleGenerateReplacement}
                    />

                    <QuestionReplacementPreview
                        open={showReplacementPreview}
                        originalQuestion={originalQuestion || mockOriginalQuestion}
                        replacementQuestion={replacementQuestion}
                        examId={Number(examId)}
                        slotId={Number(slotId)}
                        onCancel={() => setShowReplacementPreview(false)}
                        onConfirm={async () => {
                            setShowReplacementPreview(false);
                        }}
                        confirmLoading={false}
                    />

                    <EditQuestionModal
                        open={editingQuestion !== null}
                        question={editingQuestion}
                        loading={savingEdit}
                        examId={Number(examId)}
                        slotId={Number(slotId)}
                        onCancel={() => setEditingQuestion(null)}
                        onSave={handleSaveEditedQuestion}
                    />
                </Card>
            </div>
        </div>
    );
}