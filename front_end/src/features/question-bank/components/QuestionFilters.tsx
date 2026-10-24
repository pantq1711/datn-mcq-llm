import { Button, Col, Form, Input, Row, Select, Space } from "antd";
import { FilterOutlined, ReloadOutlined } from "@ant-design/icons";
import type { SubjectOption, QuestionFilterValues } from "../types/questionBank.types";
import "./QuestionFilters.css";

interface QuestionFiltersProps {
    value: QuestionFilterValues;
    subjects?: SubjectOption[];
    onChange: (values: QuestionFilterValues) => void;
    onFilter: () => void;
    onReset: () => void;
    loadingSubjects?: boolean
}

const difficultyOptions = [
    {
        label: "Biết",
        value: "easy",
    },
    {
        label: "Hiểu",
        value: "medium",
    },
    {
        label: "Vận dụng",
        value: "hard",
    },
];

const statusOptions = [
    {
        label: "Đã duyệt",
        value: "approved",
    },
    {
        label: "Đã chỉnh sửa",
        value: "edited",
    },
];

export default function QuestionFilters({
    value,
    subjects = [],
    onChange,
    onFilter,
    onReset,
    loadingSubjects = false,
}: QuestionFiltersProps) {
    const updateField = <K extends keyof QuestionFilterValues>(
        field: K,
        fieldValue: QuestionFilterValues[K],
    ) => {
        onChange({
            ...value,
            [field]: fieldValue,
        });
    };

    return (
        <div className="question-filters">
            <Form
                layout="vertical"
                component={false}
                onSubmitCapture={(event) => {
                    event.preventDefault();
                    onFilter();
                }}
            >
                <Row gutter={[16, 0]}>
                    <Col xs={24} lg={7}>
                        <Form.Item label="Từ khóa">
                            <Input
                                value={value.keyword}
                                onChange={(event) =>
                                    updateField(
                                        "keyword",
                                        event.target.value,
                                    )
                                }
                                placeholder="Tìm kiếm câu hỏi..."
                                allowClear
                            />
                        </Form.Item>
                    </Col>

                    <Col xs={24} sm={12} lg={5}>
                        <Form.Item label="Môn học">
                            <Select
                                placeholder="Chọn môn học"
                                allowClear
                                loading={loadingSubjects}
                                value={value.subjectId}
                                onChange={(val) => onChange({ ...value, subjectId: val })}
                                options={subjects}
                            />

                        </Form.Item>
                    </Col>

                    <Col xs={24} sm={12} lg={4}>
                        <Form.Item label="Độ khó">
                            <Select
                                value={value.difficulty ?? undefined}
                                onChange={(difficulty) =>
                                    updateField(
                                        "difficulty",
                                        difficulty ?? null,
                                    )
                                }
                                placeholder="Tất cả"
                                options={difficultyOptions}
                                allowClear
                                className="question-filters-select"
                            />
                        </Form.Item>
                    </Col>

                    <Col xs={24} sm={12} lg={4}>
                        <Form.Item label="Trạng thái">
                            <Select
                                value={value.status ?? undefined}
                                onChange={(status) =>
                                    updateField(
                                        "status",
                                        status ?? null,
                                    )
                                }
                                placeholder="Tất cả"
                                options={statusOptions}
                                allowClear
                                className="question-filters-select"
                            />
                        </Form.Item>
                    </Col>

                    <Col
                        xs={24}
                        sm={12}
                        lg={4}
                        className="question-filters-actions-col"
                    >
                        <Form.Item label=" ">
                            <Space className="question-filters-actions">
                                <Button
                                    type="primary"
                                    icon={<FilterOutlined />}
                                    onClick={onFilter}
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