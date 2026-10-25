import { useParams } from "react-router-dom";
import { Button, Card, Space, Tag, Typography, message } from "antd";
import { ArrowLeftOutlined, EditOutlined, DeleteOutlined, FileWordOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import ExamPreview from "../components/ExamPreview";
import ExamQuestionList from "../components/ExamQuestionList";
import type { ExamPreviewData, ExamQuestionItem, ChangeQuestionRequest, QuestionReplacementCandidate } from "../types/exam.types";
import { keepQuestion } from '../services/examApi';

import "./ExamDetailPage.css";
import { useState } from "react";
import ChangeQuestionModal from "../components/ChangeQuestionModal";
import QuestionReplacementPreview from "../components/QuestionReplacementPreview";
import EditQuestionModal from "../components/EditQuestionModal";

const { Title, Text } = Typography;

export default function ExamDetailPage() {
    const navigate = useNavigate();

    // 1. Nhận examId và slotId từ URL
    const { examId, slotId } = useParams<{ examId: string; slotId: string }>();

    const [changeQuestion, setChangeQuestion] = useState<ExamQuestionItem | null>(null);
    const [generatingReplacement, setGeneratingReplacement] = useState(false);

    const [originalQuestion, setOriginalQuestion] = useState<ExamQuestionItem | null>(null);
    const [replacementQuestion, setReplacementQuestion] = useState<QuestionReplacementCandidate | null>(null);
    const [showReplacementPreview, setShowReplacementPreview] = useState(false);
    const [confirmingReplacement, setConfirmingReplacement] = useState(false);

    const [editingQuestion, setEditingQuestion] = useState<ExamQuestionItem | null>(null);
    const [savingEdit, setSavingEdit] = useState(false);

    const [questions, setQuestions] = useState<ExamQuestionItem[]>([
        {
            id: 1,
            questionText: "Nguyên tố hóa học nào có ký hiệu hóa học là 'H'?",
            optionA: "Heli",
            optionB: "Hiđrô",
            optionC: "Hafni",
            optionD: "Holmi",
            correctOption: "B",
            explanation: "Hiđrô là nguyên tố hóa học có ký hiệu là H, là nguyên tố nhẹ nhất trong bảng tuần hoàn.",
            difficulty: "easy",
        },
        {
            id: 2,
            questionText: "Dung dịch axit nào sau đây làm quỳ tím chuyển sang màu đỏ?",
            optionA: "NaCl",
            optionB: "NaOH",
            optionC: "HCl",
            optionD: "Ba(OH)₂",
            correctOption: "C",
            explanation: "HCl là axit mạnh, làm quỳ tím hóa đỏ. NaCl là muối trung tính, NaOH và Ba(OH)₂ là bazơ làm quỳ tím hóa xanh.",
            difficulty: "medium",
        },
        {
            id: 3,
            questionText: "Oxit nào sau đây là oxit lưỡng tính?",
            optionA: "SO₂",
            optionB: "Al₂O₃",
            optionC: "CaO",
            optionD: "CO₂",
            correctOption: "B",
            explanation: "Al₂O₃ (Nhôm oxit) là oxit lưỡng tính, vừa tác dụng với axit vừa tác dụng với bazơ.",
            difficulty: "hard",
        },
    ]);

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
        try {
            await keepQuestion(examId, slotId, question.id);
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
            // Gọi API ở đây (đã thực hiện trong modal → chỉ cập nhật UI)
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

            <div className="exam-detail-page-question-list">
                <Card title="Danh sách câu hỏi">
                    <ExamQuestionList
                        questions={questions}
                        onChangeQuestion={(question) => {
                            setChangeQuestion(question);
                            // Bạn có thể gán câu hỏi hiện tại cho originalQuestion để xem trước
                            setOriginalQuestion(question);
                            setReplacementQuestion(mockReplacementQuestion);
                        }}
                        onKeep={handleKeep}
                        onEdit={(question) => setEditingQuestion(question)}
                    />

                    <ChangeQuestionModal
                        open={changeQuestion !== null}
                        question={changeQuestion}
                        loading={generatingReplacement}
                        onCancel={() => setChangeQuestion(null)}
                        onGenerate={handleGenerateReplacement}
                    />

                    <QuestionReplacementPreview
                        open={showReplacementPreview}
                        originalQuestion={mockOriginalQuestion}
                        replacementQuestion={replacementQuestion}
                        examId={examId}
                        slotId={slotId}
                        onCancel={() => setShowReplacementPreview(false)}
                        onConfirm={async () => {
                            // Ví dụ: cập nhật danh sách câu hỏi sau khi thay thế thành công
                            setShowReplacementPreview(false);
                            // optional: fetch lại danh sách hoặc cập nhật state tại đây
                        }}
                        confirmLoading={confirmingReplacement}
                    />

                    <EditQuestionModal
                        open={editingQuestion !== null}
                        question={editingQuestion}
                        loading={savingEdit}
                        examId={examId!}
                        slotId={slotId!}
                        onCancel={() => setEditingQuestion(null)}
                        onSave={handleSaveEditedQuestion}
                    />
                </Card>
            </div>
        </div>
    );
}