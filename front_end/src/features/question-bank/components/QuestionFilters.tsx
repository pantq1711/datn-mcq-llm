import { useEffect, useMemo, useState } from "react";
import { AutoComplete, Button, Col, Form, Input, Row, Select, Space, Spin, Tag, Typography, message } from "antd";
import { DownOutlined, FilterOutlined, ReloadOutlined, RightOutlined } from "@ant-design/icons";
import type { SubjectOption, QuestionFilterValues, BankTopic, BankUsage, QuestionType, QuestionDifficulty } from "../types/questionBank.types";
import { getBankTopics } from "../services/questionBankApi";
import { mergeBankTopics, sortBankTopics } from "../../../utils/bankTopic.utils";
import { QUESTION_TYPE_LABELS } from "../../../utils/exam.utils";

import "./QuestionFilters.css";

const { Text } = Typography;

interface QuestionFiltersProps {
    value: QuestionFilterValues;
    subjects?: SubjectOption[];
    onChange: (values: QuestionFilterValues) => void;
    /** Lọc; `next` là bộ lọc vừa đổi (lọc ngay khi đổi ô chọn), bỏ trống thì dùng `value` hiện tại. */
    onFilter: (next?: QuestionFilterValues) => void;
    onReset: () => void;
    loadingSubjects?: boolean;
}

/** Số mức đã chọn trong 4 ý a–d của câu đúng/sai. */
function countLevels(levels: QuestionFilterValues["levels"]): number {
    return (levels ?? []).filter((l) => l !== null && l !== undefined).length;
}

const gradeOptions = [
    { label: "Lớp 6", value: 6 },
    { label: "Lớp 7", value: 7 },
    { label: "Lớp 8", value: 8 },
    { label: "Lớp 9", value: 9 },
];

const questionTypeOptions: { label: string; value: QuestionType }[] = (["mcq", "tf", "sa"] as const).map((value) => ({
    label: QUESTION_TYPE_LABELS[value],
    value,
}));

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
                                    <Text type="secondary" style={{ fontSize: 14 }}>
                                        Lớp {t.grades.join(", ")}
                                    </Text>
                                </div>
                            )}
                        </div>
                        <div className="question-filter-topic-right">
                            <Tag color="blue" style={{ fontSize: 14, marginLeft: 4 }}>
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

    // Hệ thống chỉ có đúng 1 môn: ẩn ô Môn (như form Ma trận, Tạo đề theo bài; rà soát mục 18, H4). Khác hai form kia:
    // không tự chọn môn, vì bỏ trống ở bộ lọc là "mọi môn" (cùng kết quả) và không làm hiện "Xóa điều kiện lọc".
    const onlySubject = subjects.length === 1 ? subjects[0] : null;
    const hideSubject = onlySubject !== null && (value.subjectId == null || value.subjectId === onlySubject.value);

    // Khung "Lọc thêm": Mức dùng, Nội dung ô ma trận, Mức từng ý; thu gọn sẵn. Số trên nút cho biết đang có bao nhiêu
    // điều kiện nằm trong khung, để khi khung đóng giáo viên vẫn biết có điều kiện bị ẩn
    const [moreOpen, setMoreOpen] = useState(false);
    const moreCount = (value.usage ? 1 : 0) + (value.content ? 1 : 0) + (countLevels(value.levels) > 0 ? 1 : 0);
    const moreLabel = moreCount > 0
        ? `Lọc thêm (${moreCount})`
        : value.questionType === "tf" ? "Lọc thêm (có mức từng ý)" : "Lọc thêm";

    const nextWith = <K extends keyof QuestionFilterValues>(
        field: K,
        fieldValue: QuestionFilterValues[K]
    ): QuestionFilterValues => {
        const nextValue: QuestionFilterValues = {
            ...value,
            [field]: fieldValue,
        };

        // Nếu đổi Dạng câu sang loại khác "tf", tự động đặt levels: null
        if (field === "questionType" && fieldValue !== "tf") {
            nextValue.levels = null;
        }
        return nextValue;
    };

    /** Đổi giá trị, chưa lọc (ô gõ chữ: lọc khi nhấn Enter hoặc bấm Lọc). */
    const updateField = <K extends keyof QuestionFilterValues>(field: K, fieldValue: QuestionFilterValues[K]) => {
        onChange(nextWith(field, fieldValue));
    };

    /** Đổi giá trị và lọc ngay (ô chọn; rà soát H1 phần phụ). */
    const applyField = <K extends keyof QuestionFilterValues>(field: K, fieldValue: QuestionFilterValues[K]) => {
        const nextValue = nextWith(field, fieldValue);
        onChange(nextValue);
        onFilter(nextValue);
    };

    const handleLevelChange = (index: number, diff: QuestionDifficulty | null) => {
        const currentLevels = [...(value.levels || [null, null, null, null])];
        currentLevels[index] = diff;
        // Chỉ tự lọc khi đủ 4 ý hoặc bỏ trống cả 4; đang chọn dở thì chờ, không nhắc (nhắc khi bấm "Lọc")
        const filled = countLevels(currentLevels);
        if (filled === 0 || filled === 4) {
            applyField("levels", filled === 0 ? null : currentLevels);
        } else {
            updateField("levels", currentLevels);
        }
    };

    const handleFilterClick = () => {
        // Kiểm tra logic mức từng ý (a–d) khi dạng câu là "tf"
        if (value.questionType === "tf") {
            const filledCount = countLevels(value.levels);
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
                {/* Hàng 1: Từ khóa, (Môn học), Lớp, Dạng câu hỏi */}
                <Row gutter={[16, 0]}>
                    <Col xs={24} sm={12} lg={8}>
                        <Form.Item label="Từ khóa" htmlFor="qb-filter-keyword">
                            <Input
                                id="qb-filter-keyword"
                                value={value.keyword}
                                onChange={(event) =>
                                    updateField("keyword", event.target.value)
                                }
                                // Nhấn Enter trong ô từ khóa = bấm "Lọc" (rà soát giao diện H1)
                                onPressEnter={handleFilterClick}
                                placeholder="Tìm kiếm nội dung câu hỏi..."
                                allowClear
                            />
                        </Form.Item>
                    </Col>

                    {!hideSubject && (
                        <Col xs={24} sm={12} lg={6}>
                            <Form.Item label="Môn học" htmlFor="qb-filter-subject">
                                <Select
                                    id="qb-filter-subject"
                                    placeholder="Tất cả môn học"
                                    allowClear
                                    showSearch
                                    loading={loadingSubjects}
                                    value={value.subjectId}
                                    onChange={(val) => applyField("subjectId", val ?? null)}
                                    options={subjects}
                                    filterOption={(input, option) =>
                                        (option?.label ?? "").toLowerCase().includes(input.toLowerCase())
                                    }
                                />
                            </Form.Item>
                        </Col>
                    )}

                    {/* Ô Môn ẩn thì Lớp, Dạng câu giãn ra lấp chỗ */}
                    <Col xs={24} sm={12} lg={hideSubject ? 8 : 5}>
                        <Form.Item label="Lớp" htmlFor="qb-filter-grade">
                            <Select
                                id="qb-filter-grade"
                                placeholder="Tất cả lớp"
                                allowClear
                                value={value.gradeLevel}
                                onChange={(val) => applyField("gradeLevel", val ?? null)}
                                options={gradeOptions}
                            />
                        </Form.Item>
                    </Col>

                    <Col xs={24} sm={12} lg={hideSubject ? 8 : 5}>
                        <Form.Item label="Dạng câu hỏi" htmlFor="qb-filter-type">
                            <Select
                                id="qb-filter-type"
                                placeholder="Tất cả dạng"
                                allowClear
                                value={value.questionType}
                                onChange={(val) => applyField("questionType", val ?? null)}
                                options={questionTypeOptions}
                            />
                        </Form.Item>
                    </Col>
                </Row>

                {/* Hàng 2: Bài học/Chủ đề, Mức độ, cụm nút Lọc thêm / Lọc / Đặt lại */}
                <Row gutter={[16, 0]}>
                    <Col xs={24} sm={12} lg={8}>
                        <Form.Item
                            label="Bài học / Chủ đề"
                            htmlFor="qb-filter-topic"
                            tooltip="Lọc theo tên bài chính xác (không phân biệt hoa thường). Hãy chọn từ gợi ý."
                        >
                            <AutoComplete
                                id="qb-filter-topic"
                                value={value.topic || ""}
                                // Đang gõ: chỉ đổi giá trị; chọn một gợi ý hoặc bấm xóa thì lọc ngay
                                onChange={(val) => updateField("topic", val || null)}
                                onSelect={(val: string) => applyField("topic", val || null)}
                                onClear={() => applyField("topic", null)}
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
                        <Form.Item label="Mức độ" htmlFor="qb-filter-difficulty">
                            <Select
                                id="qb-filter-difficulty"
                                value={value.difficulty ?? undefined}
                                onChange={(difficulty) =>
                                    applyField("difficulty", difficulty ?? null)
                                }
                                placeholder="Tất cả mức độ"
                                options={difficultyOptions}
                                allowClear
                                className="question-filters-select"
                            />
                        </Form.Item>
                    </Col>

                    <Col xs={24} sm={24} lg={10} className="question-filters-actions-col">
                        <Form.Item label=" ">
                            <Space className="question-filters-actions" wrap>
                                <Button
                                    type="text"
                                    className="question-filters-more-toggle"
                                    icon={moreOpen ? <DownOutlined /> : <RightOutlined />}
                                    aria-expanded={moreOpen}
                                    aria-controls="qb-filter-more"
                                    onClick={() => setMoreOpen((open) => !open)}
                                >
                                    {moreLabel}
                                </Button>

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

                {/* Khung "Lọc thêm" (thu gọn sẵn): Mức dùng, Nội dung ô ma trận, Mức từng ý (chỉ dạng Đúng/Sai) */}
                {moreOpen && (
                    <div id="qb-filter-more" className="question-filters-more">
                        <Row gutter={[16, 0]}>
                            <Col xs={24} sm={12} lg={6}>
                                <Form.Item label="Mức dùng" htmlFor="qb-filter-usage">
                                    <Select
                                        id="qb-filter-usage"
                                        value={value.usage ?? undefined}
                                        onChange={(val) => applyField("usage", val ?? null)}
                                        placeholder="Tất cả trạng thái dùng"
                                        options={usageOptions}
                                        allowClear
                                        className="question-filters-select"
                                    />
                                </Form.Item>
                            </Col>

                            <Col xs={24} sm={12} lg={8}>
                                <Form.Item
                                    label="Nội dung ô ma trận"
                                    htmlFor="qb-filter-content"
                                    tooltip="Gõ đúng nội dung của ô trong ma trận (không phân biệt chữ hoa, chữ thường)."
                                >
                                    <Input
                                        id="qb-filter-content"
                                        value={value.content || ""}
                                        onChange={(event) =>
                                            updateField("content", event.target.value || null)
                                        }
                                        onPressEnter={handleFilterClick}
                                        placeholder="Nhập nội dung ô ma trận..."
                                        allowClear
                                    />
                                </Form.Item>
                            </Col>

                            {value.questionType === "tf" && (
                                <Col xs={24} sm={24} lg={10}>
                                    <Form.Item label="Mức từng ý (a–d)">
                                        <Row gutter={6}>
                                            {[0, 1, 2, 3].map((idx) => {
                                                const name = `Ý ${String.fromCharCode(97 + idx)}`;
                                                return (
                                                    <Col span={6} key={idx}>
                                                        <Select
                                                            size="middle"
                                                            aria-label={name}
                                                            placeholder={name}
                                                            value={value.levels?.[idx] ?? undefined}
                                                            onChange={(diff) => handleLevelChange(idx, diff ?? null)}
                                                            options={difficultyOptions}
                                                            allowClear
                                                            style={{ width: "100%" }}
                                                        />
                                                    </Col>
                                                );
                                            })}
                                        </Row>
                                    </Form.Item>
                                </Col>
                            )}
                        </Row>
                    </div>
                )}
            </Form>
        </div>
    );
}