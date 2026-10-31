import { useEffect, useMemo, useState } from "react";
import { AutoComplete, Button, Col, Form, Input, Row, Select, Space, Spin, Tag, Typography, message } from "antd";
import { FilterOutlined, ReloadOutlined } from "@ant-design/icons";
import type { SubjectOption, QuestionFilterValues, BankTopic, BankUsage, QuestionType, QuestionDifficulty } from "../types/questionBank.types";
import { getBankTopics } from "../services/questionBankApi";
import { mergeBankTopics, sortBankTopics } from "../../../utils/bankTopic.utils";

import "./QuestionFilters.css";

const { Text } = Typography;

interface QuestionFiltersProps {
    value: QuestionFilterValues;
    subjects?: SubjectOption[];
    onChange: (values: QuestionFilterValues) => void;
    onFilter: () => void;
    onReset: () => void;
    loadingSubjects?: boolean;
}

const gradeOptions = [
    { label: "Khối 6", value: 6 },
    { label: "Khối 7", value: 7 },
    { label: "Khối 8", value: 8 },
    { label: "Khối 9", value: 9 },
];

const questionTypeOptions: { label: string; value: QuestionType }[] = [
    { label: "Trắc nghiệm 4 lựa chọn", value: "mcq" },
    { label: "Đúng / Sai", value: "tf" },
    { label: "Trả lời ngắn", value: "sa" },
];

const difficultyOptions: { label: string; value: QuestionDifficulty }[] = [
    { label: "Biết", value: "easy" },
    { label: "Hiểu", value: "medium" },
    { label: "Vận dụng", value: "hard" },
];

const usageOptions: { label: string; value: BankUsage }[] = [
    { label: "Đã dùng trong đề", value: "used" },
    { label: "Chưa dùng", value: "unused" },
];

export default function QuestionFilters({
    value,
    subjects = [],
    onChange,
    onFilter,
    onReset,
    loadingSubjects = false,
}: QuestionFiltersProps) {
    const [bankTopics, setBankTopics] = useState<BankTopic[]>([]);
    const [loadingTopics, setLoadingTopics] = useState(false);
    const [topicsError, setTopicsError] = useState(false);

    // Tự động tải danh sách chủ đề theo môn học đã chọn
    useEffect(() => {
        let isMounted = true;
        setBankTopics([]);
        setTopicsError(false);
        setLoadingTopics(true);

        getBankTopics(value.subjectId ?? undefined)
            .then((topics) => {
                if (isMounted) {
                    setBankTopics(topics);
                }
            })
            .catch((err) => {
                console.warn("Không thể tải danh sách chủ đề ngân hàng:", err);
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
    }, [value.subjectId]);

    // Lọc bankTopics theo khối lớp trước khi gộp (nếu đã chọn khối)
    const filteredBankTopics = useMemo(() => {
        if (!value.gradeLevel) {
            return bankTopics;
        }
        return bankTopics.filter((t) => t.gradeLevel === value.gradeLevel);
    }, [bankTopics, value.gradeLevel]);

    // Gộp các chủ đề sau khi đã lọc
    const mergedTopics = useMemo(() => {
        return mergeBankTopics(filteredBankTopics);
    }, [filteredBankTopics]);

    // Sắp xếp theo số lượng câu giảm dần và tạo danh sách option cho AutoComplete
    const topicOptions = useMemo(() => {
        return sortBankTopics(mergedTopics, value.gradeLevel ?? undefined).map((t) => {
            return {
                value: t.topic,
                label: (
                    <div className="question-filter-topic-option">
                        <div className="question-filter-topic-main">
                            <span className="question-filter-topic-text">{t.topic}</span>
                            {!value.gradeLevel && t.grades && t.grades.length > 0 && (
                                <div className="question-filter-topic-sub">
                                    <Text type="secondary" style={{ fontSize: 11 }}>
                                        Khối {t.grades.join(", ")}
                                    </Text>
                                </div>
                            )}
                        </div>
                        <div className="question-filter-topic-right">
                            <Tag color="blue" style={{ fontSize: 11, marginLeft: 4 }}>
                                {t.count} câu
                            </Tag>
                        </div>
                    </div>
                ),
            };
        });
    }, [mergedTopics, value.gradeLevel]);

    const topicNotFound = useMemo(() => {
        if (loadingTopics) {
            return (
                <Space size={8}>
                    <Spin size="small" />
                    <Text type="secondary">Đang tải chủ đề…</Text>
                </Space>
            );
        }
        if (topicsError) {
            return <Text type="secondary">Không tải được chủ đề</Text>;
        }
        if (mergedTopics.length === 0) {
            return <Text type="secondary">Chưa có chủ đề nào</Text>;
        }
        return null;
    }, [loadingTopics, topicsError, mergedTopics.length]);

    const updateField = <K extends keyof QuestionFilterValues>(
        field: K,
        fieldValue: QuestionFilterValues[K]
    ) => {
        const nextValue: QuestionFilterValues = {
            ...value,
            [field]: fieldValue,
        };

        // Nếu đổi Dạng câu sang loại khác "tf", tự động đặt levels: null
        if (field === "questionType" && fieldValue !== "tf") {
            nextValue.levels = null;
        }

        onChange(nextValue);
    };

    const handleLevelChange = (index: number, diff: QuestionDifficulty | null) => {
        const currentLevels = [...(value.levels || [null, null, null, null])];
        currentLevels[index] = diff;
        updateField("levels", currentLevels);
    };

    const handleFilterClick = () => {
        // Kiểm tra logic mức từng ý (a–d) khi dạng câu là "tf"
        if (value.questionType === "tf" && value.levels && Array.isArray(value.levels)) {
            const filledCount = value.levels.filter((l) => l !== null && l !== undefined).length;
            if (filledCount > 0 && filledCount < 4) {
                message.warning("Chọn đủ mức cho 4 ý a–d hoặc bỏ trống cả 4");
                return;
            }
        }
        onFilter();
    };

    return (
        <div className="question-filters">
            <Form
                layout="vertical"
                component={false}
                onSubmitCapture={(event) => {
                    event.preventDefault();
                    handleFilterClick();
                }}
            >
                {/* Hàng 1: Từ khóa, Môn học, Khối lớp, Dạng câu hỏi */}
                <Row gutter={[16, 0]}>
                    <Col xs={24} sm={12} lg={8}>
                        <Form.Item label="Từ khóa">
                            <Input
                                value={value.keyword}
                                onChange={(event) =>
                                    updateField("keyword", event.target.value)
                                }
                                placeholder="Tìm kiếm nội dung câu hỏi..."
                                allowClear
                            />
                        </Form.Item>
                    </Col>

                    <Col xs={24} sm={12} lg={6}>
                        <Form.Item label="Môn học">
                            <Select
                                placeholder="Tất cả môn học"
                                allowClear
                                showSearch
                                loading={loadingSubjects}
                                value={value.subjectId}
                                onChange={(val) => updateField("subjectId", val ?? null)}
                                options={subjects}
                                filterOption={(input, option) =>
                                    (option?.label ?? "").toLowerCase().includes(input.toLowerCase())
                                }
                            />
                        </Form.Item>
                    </Col>

                    <Col xs={24} sm={12} lg={5}>
                        <Form.Item label="Khối lớp">
                            <Select
                                placeholder="Tất cả khối"
                                allowClear
                                value={value.gradeLevel}
                                onChange={(val) => updateField("gradeLevel", val ?? null)}
                                options={gradeOptions}
                            />
                        </Form.Item>
                    </Col>

                    <Col xs={24} sm={12} lg={5}>
                        <Form.Item label="Dạng câu hỏi">
                            <Select
                                placeholder="Tất cả dạng"
                                allowClear
                                value={value.questionType}
                                onChange={(val) => updateField("questionType", val ?? null)}
                                options={questionTypeOptions}
                            />
                        </Form.Item>
                    </Col>
                </Row>

                {/* Hàng 2: Bài học/Chủ đề, Độ khó, Mức dùng */}
                <Row gutter={[16, 0]}>
                    <Col xs={24} sm={12} lg={8}>
                        <Form.Item
                            label="Bài học / Chủ đề"
                            tooltip="Lọc theo tên bài chính xác (không phân biệt hoa thường). Hãy chọn từ gợi ý."
                        >
                            <AutoComplete
                                value={value.topic || ""}
                                onChange={(val) => updateField("topic", val || null)}
                                options={topicOptions}
                                notFoundContent={topicNotFound}
                                placeholder="Chọn hoặc nhập bài học..."
                                allowClear
                                filterOption={(inputValue, option) =>
                                    String(option?.value ?? "")
                                        .toLowerCase()
                                        .includes(inputValue.toLowerCase())
                                }
                            />
                        </Form.Item>
                    </Col>

                    <Col xs={24} sm={12} lg={6}>
                        <Form.Item label="Độ khó">
                            <Select
                                value={value.difficulty ?? undefined}
                                onChange={(difficulty) =>
                                    updateField("difficulty", difficulty ?? null)
                                }
                                placeholder="Tất cả độ khó"
                                options={difficultyOptions}
                                allowClear
                                className="question-filters-select"
                            />
                        </Form.Item>
                    </Col>

                    <Col xs={24} sm={12} lg={10}>
                        <Form.Item label="Mức dùng">
                            <Select
                                value={value.usage ?? undefined}
                                onChange={(val) => updateField("usage", val ?? null)}
                                placeholder="Tất cả trạng thái dùng"
                                options={usageOptions}
                                allowClear
                                className="question-filters-select"
                            />
                        </Form.Item>
                    </Col>
                </Row>

                {/* Hàng 3: Nội dung ô ma trận, Mức từng ý (nếu tf), Cụm nút Lọc/Đặt lại */}
                <Row gutter={[16, 0]}>
                    <Col xs={24} sm={12} lg={8}>
                        <Form.Item
                            label="Nội dung ô ma trận"
                            tooltip="So khớp đúng nguyên văn nội dung ô (không phân biệt hoa thường)"
                        >
                            <Input
                                value={value.content || ""}
                                onChange={(event) =>
                                    updateField("content", event.target.value || null)
                                }
                                placeholder="Nhập nội dung ô ma trận..."
                                allowClear
                            />
                        </Form.Item>
                    </Col>

                    {value.questionType === "tf" ? (
                        <Col xs={24} sm={12} lg={10}>
                            <Form.Item label="Mức từng ý (a–d)">
                                <Row gutter={6}>
                                    {[0, 1, 2, 3].map((idx) => (
                                        <Col span={6} key={idx}>
                                            <Select
                                                size="middle"
                                                placeholder={`Ý ${String.fromCharCode(97 + idx)}`}
                                                value={value.levels?.[idx] ?? undefined}
                                                onChange={(diff) => handleLevelChange(idx, diff ?? null)}
                                                options={difficultyOptions}
                                                allowClear
                                                style={{ width: "100%" }}
                                            />
                                        </Col>
                                    ))}
                                </Row>
                            </Form.Item>
                        </Col>
                    ) : (
                        <Col xs={24} sm={12} lg={10}>
                            {/* Ô trống giữ bố cục khi không phải dạng Đúng/Sai */}
                        </Col>
                    )}

                    <Col xs={24} sm={12} lg={6} className="question-filters-actions-col">
                        <Form.Item label=" ">
                            <Space className="question-filters-actions">
                                <Button
                                    type="primary"
                                    icon={<FilterOutlined />}
                                    onClick={handleFilterClick}
                                >
                                    Lọc
                                </Button>

                                <Button
                                    icon={<ReloadOutlined />}
                                    onClick={onReset}
                                >
                                    Đặt lại
                                </Button>
                            </Space>
                        </Form.Item>
                    </Col>
                </Row>
            </Form>
        </div>
    );
}