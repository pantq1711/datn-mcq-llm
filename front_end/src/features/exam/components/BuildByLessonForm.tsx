import { useEffect, useMemo, useState } from "react";
import { Alert, AutoComplete, Button, Card, Col, Form, Input, InputNumber, Row, Select, Space, Spin, Tag, Typography, message } from "antd";
import { BookOutlined, BulbOutlined, ExclamationCircleOutlined, PaperClipOutlined, RocketOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import MaterialManagerModal from "../../materials/components/MaterialManagerModal";
import type { MaterialDto } from "../../materials/types/material.types";

import type { ExamSubjectOption, BuildByLessonPayload, LessonCellInput, BuildResultDto } from "../types/exam.types";
import { buildByLesson } from "../services/examApi";
import type { BankTopic } from "../../question-bank/types/questionBank.types";
import { getBankTopics } from "../../question-bank/services/questionBankApi";
import { mergeBankTopics, sortBankTopics } from "../../../utils/bankTopic.utils";
import "./BuildByLessonForm.css";

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

export interface BuildByLessonFormProps {
    subjects: ExamSubjectOption[];
    loadingSubjects?: boolean;
    onSuccess?: (result: BuildResultDto) => void;
}

interface FormValues {
    subjectId: number;
    gradeLevel?: number;
    lesson: string;
    content?: string;
    title?: string;
    extraInstruction?: string;
    mcqEasy: number;
    mcqMedium: number;
    mcqHard: number;
    tfCount: number;
    saEasy: number;
    saMedium: number;
    saHard: number;
}

const GRADE_OPTIONS = [
    { label: "Lớp 6", value: 6 },
    { label: "Lớp 7", value: 7 },
    { label: "Lớp 8", value: 8 },
    { label: "Lớp 9", value: 9 },
];

export default function BuildByLessonForm({
    subjects,
    loadingSubjects = false,
    onSuccess,
}: BuildByLessonFormProps) {
    const navigate = useNavigate();
    const [form] = Form.useForm<FormValues>();

    const [submitting, setSubmitting] = useState(false);
    const [bankTopics, setBankTopics] = useState<BankTopic[]>([]);
    const [loadingTopics, setLoadingTopics] = useState(false);
    const [topicsError, setTopicsError] = useState(false);

    // Quản lý tài liệu bài học đính kèm
    const [selectedMaterials, setSelectedMaterials] = useState<MaterialDto[]>([]);
    const [isMaterialModalOpen, setIsMaterialModalOpen] = useState(false);

    // Watch values to dynamically calculate totals & filter topics
    const watchedSubjectId = Form.useWatch("subjectId", form);
    const watchedGradeLevel = Form.useWatch("gradeLevel", form);
    const watchedLesson = Form.useWatch("lesson", form);
    const watchedContent = Form.useWatch("content", form);
    const watchedExtraInstruction = Form.useWatch("extraInstruction", form);

    const mcqEasy = Form.useWatch("mcqEasy", form) || 0;
    const mcqMedium = Form.useWatch("mcqMedium", form) || 0;
    const mcqHard = Form.useWatch("mcqHard", form) || 0;
    const tfCount = Form.useWatch("tfCount", form) || 0;
    const saEasy = Form.useWatch("saEasy", form) || 0;
    const saMedium = Form.useWatch("saMedium", form) || 0;
    const saHard = Form.useWatch("saHard", form) || 0;

    const totalMcq = mcqEasy + mcqMedium + mcqHard;
    const totalTf = tfCount;
    const totalSa = saEasy + saMedium + saHard;
    const totalQuestions = totalMcq + totalTf + totalSa;

    // Fetch topics from bank when subjectId changes to suggest lessons
    useEffect(() => {
        if (!watchedSubjectId) {
            setBankTopics([]);
            setTopicsError(false);
            return;
        }

        let isMounted = true;
        setBankTopics([]);
        setTopicsError(false);
        setLoadingTopics(true);

        getBankTopics(watchedSubjectId)
            .then((topics) => {
                if (isMounted) {
                    setBankTopics(topics);
                }
            })
            .catch((err) => {
                console.warn("Không thể tải gợi ý chủ đề:", err);
                if (isMounted) {
                    setTopicsError(true);
                }
            })
            .finally(() => {
                if (isMounted) setLoadingTopics(false);
            });

        return () => {
            isMounted = false;
        };
    }, [watchedSubjectId]);

    // Gộp các chủ đề gốc để phục vụ việc tra cứu khi chọn và hiển thị
    const mergedTopics = useMemo(() => {
        return mergeBankTopics(bankTopics);
    }, [bankTopics]);

    // Dựng Map tra cứu chủ đề nhanh chóng với key là topic.trim().toLowerCase()
    const topicMap = useMemo(() => {
        const map = new Map<string, (typeof mergedTopics)[number]>();
        mergedTopics.forEach((t) => {
            map.set(t.topic.trim().toLowerCase(), t);
        });
        return map;
    }, [mergedTopics]);

    // Sắp xếp và tạo danh sách option cho AutoComplete
    const topicOptions = useMemo(() => {
        return sortBankTopics(mergedTopics, watchedGradeLevel).map((t) => {
            const matchesGrade = watchedGradeLevel ? t.grades.includes(watchedGradeLevel) : false;

            return {
                value: t.topic,
                label: (
                    <div className="build-lesson-topic-option">
                        <div className="build-lesson-topic-main">
                            <span className="build-lesson-topic-text">{t.topic}</span>
                            {t.grades && t.grades.length > 0 && (
                                <div className="build-lesson-topic-sub">
                                    <span>Khối: </span>
                                    {t.grades.map((g, idx) => {
                                        const isCurrent = g === watchedGradeLevel;
                                        return (
                                            <span
                                                key={g}
                                                style={{
                                                    fontWeight: isCurrent ? 700 : 400,
                                                    color: isCurrent ? "#1677ff" : "inherit",
                                                }}
                                            >
                                                {idx > 0 ? ", " : ""}
                                                {g}
                                                {isCurrent ? " (Đúng khối)" : ""}
                                            </span>
                                        );
                                    })}
                                </div>
                            )}
                        </div>
                        <div className="build-lesson-topic-right">
                            {matchesGrade && (
                                <Tag color="success" className="build-lesson-topic-tag" style={{ marginRight: 4 }}>
                                    Đúng khối
                                </Tag>
                            )}
                            <Tag color="blue" className="build-lesson-topic-tag">
                                {t.count} câu trong ngân hàng
                            </Tag>
                        </div>
                    </div>
                ),
            };
        });
    }, [mergedTopics, watchedGradeLevel]);

    // Nội dung hiển thị bên trong dropdown AutoComplete theo các trạng thái
    const topicNotFound = useMemo(() => {
        if (!watchedSubjectId) {
            return <Text type="secondary">Chọn môn học để xem gợi ý bài</Text>;
        }
        if (loadingTopics) {
            return (
                <Space size={8}>
                    <Spin size="small" />
                    <Text type="secondary">Đang tải gợi ý…</Text>
                </Space>
            );
        }
        if (topicsError) {
            return <Text type="secondary">Không tải được gợi ý. Bạn vẫn có thể tự nhập tên bài.</Text>;
        }
        if (mergedTopics.length === 0) {
            return <Text type="secondary">Ngân hàng chưa có bài nào của môn này</Text>;
        }
        return null;
    }, [watchedSubjectId, loadingTopics, topicsError, mergedTopics.length]);

    // Thông báo trạng thái ngân hàng theo 4 mức ưu tiên (bankHint)
    const bankHint = useMemo(() => {
        // Mức 1: Có tài liệu hoặc extraInstruction khác rỗng -> Thắng tất cả
        const hasMaterials = selectedMaterials && selectedMaterials.length > 0;
        const hasExtra = watchedExtraInstruction && watchedExtraInstruction.trim() !== "";
        if (hasMaterials || hasExtra) {
            return (
                <Text type="warning" style={{ display: "block", marginTop: 4 }}>
                    <ExclamationCircleOutlined style={{ marginRight: 4 }} />
                    Khi có tài liệu hoặc yêu cầu thêm, hệ thống bỏ qua ngân hàng và sinh mới toàn bộ.
                </Text>
            );
        }

        // Mức 2: Tên bài trống, chưa chọn môn, đang tải hoặc tải lỗi gợi ý -> Không hiện gì
        const isLessonEmpty = !watchedLesson || watchedLesson.trim() === "";
        if (!watchedSubjectId || isLessonEmpty || loadingTopics || topicsError) {
            return null;
        }

        // Mức 3 & 4: Tra cứu theo tên bài nhập vào
        const matchedTopic = topicMap.get(watchedLesson.trim().toLowerCase());
        if (matchedTopic) {
            return (
                <div style={{ marginTop: 4 }}>
                    <Text type="success">
                        Ngân hàng có {matchedTopic.count} câu thuộc bài này. Hệ thống sẽ dùng lại câu phù hợp trước khi sinh mới.
                    </Text>
                    {watchedContent && watchedContent.trim() !== "" && (
                        <div>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                                Phạm vi cụ thể: {watchedContent.trim()}
                            </Text>
                        </div>
                    )}
                </div>
            );
        }

        // Mức 4: Không khớp chủ đề nào trong ngân hàng
        return (
            <Text type="secondary" style={{ display: "block", marginTop: 4 }}>
                Chưa có câu ngân hàng cho bài này, mọi câu sẽ được sinh mới.
            </Text>
        );
    }, [
        watchedSubjectId,
        watchedLesson,
        watchedContent,
        watchedExtraInstruction,
        selectedMaterials,
        loadingTopics,
        topicsError,
        topicMap,
    ]);

    // Xử lý tự điền khối lớp khi giáo viên chọn một chủ đề gợi ý (tra cứu qua topicMap)
    const handleTopicSelect = (value: string) => {
        const picked = topicMap.get(value.trim().toLowerCase());
        if (
            !form.getFieldValue("gradeLevel") &&
            picked &&
            picked.grades.length === 1 &&
            !picked.hasUnknownGrade
        ) {
            form.setFieldsValue({ gradeLevel: picked.grades[0] });
        }
    };

    const handleSubmit = async (values: FormValues) => {
        if (totalQuestions <= 0) {
            message.error("Vui lòng nhập ít nhất 1 câu hỏi cho đề thi.");
            return;
        }

        if (totalQuestions > 50) {
            message.error(`Một lần sinh theo bài tối đa 50 câu (hiện đang chọn ${totalQuestions} câu).`);
            return;
        }

        const cells: LessonCellInput[] = [];

        // Phần I: MCQ
        if (values.mcqEasy > 0) {
            cells.push({ questionType: "mcq", difficulty: "easy", questionCount: values.mcqEasy });
        }
        if (values.mcqMedium > 0) {
            cells.push({ questionType: "mcq", difficulty: "medium", questionCount: values.mcqMedium });
        }
        if (values.mcqHard > 0) {
            cells.push({ questionType: "mcq", difficulty: "hard", questionCount: values.mcqHard });
        }

        // Phần II: Đúng/Sai (TF)
        if (values.tfCount > 0) {
            cells.push({
                questionType: "tf",
                statementDifficulties: ["easy", "easy", "medium", "hard"],
                questionCount: values.tfCount,
            });
        }

        // Phần III: Trả lời ngắn (SA)
        if (values.saEasy > 0) {
            cells.push({ questionType: "sa", difficulty: "easy", questionCount: values.saEasy });
        }
        if (values.saMedium > 0) {
            cells.push({ questionType: "sa", difficulty: "medium", questionCount: values.saMedium });
        }
        if (values.saHard > 0) {
            cells.push({ questionType: "sa", difficulty: "hard", questionCount: values.saHard });
        }

        const payload: BuildByLessonPayload = {
            subjectId: values.subjectId,
            gradeLevel: values.gradeLevel,
            lesson: values.lesson.trim(),
            content: values.content?.trim() || undefined,
            title: values.title?.trim() || undefined,
            cells,
            materialIds: selectedMaterials.length > 0 ? selectedMaterials.map((m) => m.id) : undefined,
            extraInstruction: values.extraInstruction?.trim() || undefined,
        };

        setSubmitting(true);
        try {
            const result = await buildByLesson(payload);
            message.success(
                `Khởi tạo đề thi thành công! Tổng ${result.slots} câu (${result.fromBank} từ ngân hàng, ${result.jobsQueued} đang sinh).`
            );

            if (onSuccess) {
                onSuccess(result);
            } else {
                navigate(`/exams/${result.examId}`);
            }
        } catch (err: any) {
            const errMsg =
                err?.response?.data?.error ||
                err?.response?.data?.message ||
                err?.message ||
                "Có lỗi xảy ra khi tạo đề thi.";
            message.error(errMsg);
        } finally {
            setSubmitting(false);
        }
    };

    // Preset cấu hình nhanh
    const applyPreset = (preset: "15p" | "45p") => {
        if (preset === "15p") {
            form.setFieldsValue({
                mcqEasy: 5,
                mcqMedium: 3,
                mcqHard: 2,
                tfCount: 0,
                saEasy: 0,
                saMedium: 0,
                saHard: 0,
            });
        } else {
            form.setFieldsValue({
                mcqEasy: 12,
                mcqMedium: 4,
                mcqHard: 0,
                tfCount: 2,
                saEasy: 1,
                saMedium: 2,
                saHard: 1,
            });
        }
    };

    return (
        <Card className="build-lesson-card" bordered={false}>
            <div className="build-lesson-intro">
                <div className="build-lesson-intro-icon">
                    <BookOutlined />
                </div>
                <div>
                    <Title level={4} className="build-lesson-intro-title">
                        Tạo đề thi theo bài học
                    </Title>
                    <Paragraph type="secondary" className="build-lesson-intro-desc">
                        Thích hợp cho kiểm tra thường xuyên (15 phút), kiểm tra định kỳ 1 tiết hoặc ôn tập từng bài học
                        SGK. Hệ thống ưu tiên chọn câu hỏi sẵn có từ ngân hàng và tự động sinh mới các câu còn thiếu qua AI.
                    </Paragraph>
                </div>
            </div>

            <Form
                form={form}
                layout="vertical"
                onFinish={handleSubmit}
                initialValues={{
                    gradeLevel: 8,
                    mcqEasy: 4,
                    mcqMedium: 2,
                    mcqHard: 0,
                    tfCount: 0,
                    saEasy: 0,
                    saMedium: 0,
                    saHard: 0,
                }}
            >
                {/* Phần 1: Thông tin bài học */}
                <Card size="small" className="build-lesson-section" title="1. Thông tin bài học & phạm vi">
                    <Row gutter={16}>
                        <Col xs={24} md={12}>
                            <Form.Item
                                name="subjectId"
                                label="Môn học"
                                rules={[{ required: true, message: "Vui lòng chọn môn học" }]}
                            >
                                <Select
                                    placeholder="Chọn môn học"
                                    options={subjects}
                                    loading={loadingSubjects}
                                    showSearch
                                    filterOption={(input, option) =>
                                        (option?.label ?? "").toLowerCase().includes(input.toLowerCase())
                                    }
                                />
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={12}>
                            <Form.Item name="gradeLevel" label="Khối lớp">
                                <Select placeholder="Chọn khối lớp" options={GRADE_OPTIONS} />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Row gutter={16}>
                        <Col xs={24} md={14}>
                            <Form.Item
                                name="lesson"
                                label={
                                    <Space>
                                        <span>Tên bài học</span>
                                        <Text type="secondary" style={{ fontSize: 12 }}>
                                            (Hệ thống tự động gợi ý chủ đề trong ngân hàng)
                                        </Text>
                                    </Space>
                                }
                                rules={[
                                    { required: true, message: "Vui lòng nhập tên bài học" },
                                    { max: 255, message: "Tên bài học tối đa 255 ký tự" },
                                ]}
                                extra={bankHint}
                            >
                                <AutoComplete
                                    options={topicOptions}
                                    onSelect={handleTopicSelect}
                                    notFoundContent={topicNotFound}
                                    placeholder="Ví dụ: Bài 3. Hằng đẳng thức đáng nhớ..."
                                    filterOption={(inputValue, option) =>
                                        String(option?.value ?? "")
                                            .toLowerCase()
                                            .includes(inputValue.toLowerCase())
                                    }
                                />
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={10}>
                            <Form.Item
                                name="content"
                                label="Phạm vi / Mục cụ thể trong bài (tùy chọn)"
                                tooltip="Ví dụ: Bình phương của một tổng. Bỏ trống nếu kiểm tra toàn bài."
                            >
                                <Input placeholder="Ví dụ: Bình phương của một tổng..." maxLength={255} />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Row gutter={16}>
                        <Col xs={24}>
                            <Form.Item
                                name="title"
                                label="Tên đề thi hiển thị (tùy chọn)"
                                tooltip="Nếu bỏ trống, hệ thống sẽ tự động đặt tên theo tên bài học."
                            >
                                <Input placeholder="Mặc định lấy theo tên bài học" maxLength={255} />
                            </Form.Item>
                        </Col>
                    </Row>
                </Card>

                {/* Phần 2: Cấu trúc số lượng câu hỏi */}
                <Card
                    size="small"
                    className="build-lesson-section"
                    title={
                        <div className="build-lesson-matrix-header">
                            <span>2. Cấu trúc câu hỏi theo dạng & mức độ</span>
                            <Space size={8}>
                                <Text type="secondary" style={{ fontSize: 13 }}>
                                    Mẫu nhanh:
                                </Text>
                                <Button size="small" onClick={() => applyPreset("15p")}>
                                    10 câu trắc nghiệm (15p)
                                </Button>
                                <Button size="small" onClick={() => applyPreset("45p")}>
                                    Đề tổng hợp (16 câu)
                                </Button>
                            </Space>
                        </div>
                    }
                >
                    <div className="build-lesson-matrix-table">
                        <div className="build-lesson-matrix-row heading-row">
                            <div className="col-type">Dạng câu hỏi</div>
                            <div className="col-level">Biết (Dễ)</div>
                            <div className="col-level">Hiểu (Vừa)</div>
                            <div className="col-level">Vận dụng (Khó)</div>
                            <div className="col-subtotal">Số câu</div>
                        </div>

                        {/* MCQ */}
                        <div className="build-lesson-matrix-row">
                            <div className="col-type">
                                <div>
                                    <Text strong>Phần I. Trắc nghiệm 4 lựa chọn</Text>
                                    <div className="col-type-desc">Chọn 1 đáp án đúng trong 4 lựa chọn A, B, C, D</div>
                                </div>
                            </div>
                            <div className="col-level">
                                <Form.Item name="mcqEasy" noStyle>
                                    <InputNumber min={0} max={50} className="level-input" />
                                </Form.Item>
                            </div>
                            <div className="col-level">
                                <Form.Item name="mcqMedium" noStyle>
                                    <InputNumber min={0} max={50} className="level-input" />
                                </Form.Item>
                            </div>
                            <div className="col-level">
                                <Form.Item name="mcqHard" noStyle>
                                    <InputNumber min={0} max={50} className="level-input" />
                                </Form.Item>
                            </div>
                            <div className="col-subtotal">
                                <Tag color="blue">{totalMcq} câu</Tag>
                            </div>
                        </div>

                        {/* TF */}
                        <div className="build-lesson-matrix-row">
                            <div className="col-type">
                                <div>
                                    <Text strong>Phần II. Đúng / Sai</Text>
                                    <div className="col-type-desc">Mỗi câu gồm 4 ý a, b, c, d (Dễ, Dễ, Vừa, Khó)</div>
                                </div>
                            </div>
                            <div className="col-level span-3">
                                <Space align="center">
                                    <Text type="secondary">Số câu hỏi:</Text>
                                    <Form.Item name="tfCount" noStyle>
                                        <InputNumber min={0} max={20} className="level-input" />
                                    </Form.Item>
                                    <Text type="secondary" style={{ fontSize: 12 }}>
                                        (Mỗi câu gồm 4 ý hỏi)
                                    </Text>
                                </Space>
                            </div>
                            <div className="col-subtotal">
                                <Tag color="cyan">{totalTf} câu</Tag>
                            </div>
                        </div>

                        {/* SA */}
                        <div className="build-lesson-matrix-row">
                            <div className="col-type">
                                <div>
                                    <Text strong>Phần III. Trả lời ngắn</Text>
                                    <div className="col-type-desc">Học sinh tự điền câu trả lời ngắn gọn / con số</div>
                                </div>
                            </div>
                            <div className="col-level">
                                <Form.Item name="saEasy" noStyle>
                                    <InputNumber min={0} max={50} className="level-input" />
                                </Form.Item>
                            </div>
                            <div className="col-level">
                                <Form.Item name="saMedium" noStyle>
                                    <InputNumber min={0} max={50} className="level-input" />
                                </Form.Item>
                            </div>
                            <div className="col-level">
                                <Form.Item name="saHard" noStyle>
                                    <InputNumber min={0} max={50} className="level-input" />
                                </Form.Item>
                            </div>
                            <div className="col-subtotal">
                                <Tag color="purple">{totalSa} câu</Tag>
                            </div>
                        </div>
                    </div>

                    <div className="build-lesson-summary-bar">
                        <Space size={16} wrap>
                            <div>
                                <Text strong>Tổng số câu hỏi: </Text>
                                <Tag
                                    color={totalQuestions === 0 ? "default" : totalQuestions > 50 ? "error" : "success"}
                                    style={{ fontSize: 14, padding: "2px 10px" }}
                                >
                                    {totalQuestions} / 50 câu
                                </Tag>
                            </div>
                            {totalQuestions > 50 && (
                                <Text type="danger">
                                    Vượt quá giới hạn tối đa 50 câu mỗi lần sinh đề.
                                </Text>
                            )}
                            {totalQuestions === 0 && (
                                <Text type="secondary">
                                    Cần chọn ít nhất 1 câu hỏi.
                                </Text>
                            )}
                        </Space>
                    </div>
                </Card>

                {/* Phần 3: Tài liệu bài học đính kèm */}
                <Card
                    size="small"
                    className="build-lesson-section"
                    title={
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                            <span>3. Tài liệu bài học đính kèm (tùy chọn)</span>
                            <Button
                                size="small"
                                icon={<PaperClipOutlined />}
                                onClick={() => setIsMaterialModalOpen(true)}
                            >
                                {selectedMaterials.length > 0 ? "Thay đổi tài liệu" : "Chọn tài liệu"}
                            </Button>
                        </div>
                    }
                >
                    <Paragraph type="secondary" style={{ fontSize: 13, marginBottom: 12 }}>
                        Đính kèm file Word (.docx), PDF hoặc TXT bài giảng để mô hình AI sinh câu hỏi bám sát văn bản tài liệu
                        (tối đa 5 tài liệu).
                    </Paragraph>

                    {selectedMaterials.length > 0 ? (
                        <Space size={[8, 8]} wrap>
                            {selectedMaterials.map((m) => (
                                <Tag
                                    key={m.id}
                                    closable
                                    onClose={() =>
                                        setSelectedMaterials((prev) => prev.filter((item) => item.id !== m.id))
                                    }
                                    color="blue"
                                    style={{ padding: "4px 10px", fontSize: 13 }}
                                >
                                    📄 {m.fileName} ({m.charCount.toLocaleString()} ký tự)
                                </Tag>
                            ))}
                        </Space>
                    ) : (
                        <Text type="secondary" style={{ fontStyle: "italic", fontSize: 13 }}>
                            Chưa đính kèm tài liệu nào.
                        </Text>
                    )}
                </Card>

                {/* Phần 4: Yêu cầu bổ sung cho AI */}
                <Card size="small" className="build-lesson-section" title="4. Yêu cầu thêm cho AI (tùy chọn)">
                    <Form.Item
                        name="extraInstruction"
                        label="Chỉ dẫn thêm cho câu hỏi sinh bằng AI"
                        tooltip="Yêu cầu cụ thể về bối cảnh, tình huống thực tế hoặc phong cách diễn đạt cho AI."
                    >
                        <TextArea
                            rows={3}
                            maxLength={1000}
                            showCount
                            placeholder="Ví dụ: Ưu tiên các bài toán gắn với thực tế đời sống; câu hỏi ngắn gọn, tránh diễn đạt gây hiểu lầm..."
                        />
                    </Form.Item>
                    <Alert
                        type="info"
                        showIcon
                        icon={<BulbOutlined />}
                        message="Lưu ý về nguồn câu hỏi"
                        description="Hệ thống sẽ đối soát với Ngân hàng câu hỏi theo Môn, Bài học và Mức độ. Nếu có tài liệu hoặc yêu cầu thêm cho AI, hệ thống sẽ ưu tiên sinh câu hỏi mới bám sát các nội dung này."
                    />
                </Card>

                <div className="build-lesson-footer">
                    <Button
                        type="primary"
                        size="large"
                        htmlType="submit"
                        icon={<RocketOutlined />}
                        loading={submitting}
                        disabled={totalQuestions <= 0 || totalQuestions > 50}
                        className="build-lesson-submit-btn"
                    >
                        Tạo và Duyệt đề thi
                    </Button>
                </div>
            </Form>

            {/* Modal quản lý & chọn tài liệu */}
            <MaterialManagerModal
                open={isMaterialModalOpen}
                selectedIds={selectedMaterials.map((m) => m.id)}
                onClose={() => setIsMaterialModalOpen(false)}
                onSelect={(newSelected) => setSelectedMaterials(newSelected)}
                onRemoved={(removedId) =>
                    setSelectedMaterials((prev) => prev.filter((m) => m.id !== removedId))
                }
                maxSelect={5}
                title="Chọn tài liệu bài học đính kèm"
            />
        </Card>
    );
}