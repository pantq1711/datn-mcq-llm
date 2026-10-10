import {
    Button,
    Col,
    Form,
    Input,
    Row,
    Select,
    Space,
} from "antd";
import {
    FilterOutlined,
    ReloadOutlined,
} from "@ant-design/icons";
import "./QuestionFilters.css";

export type QuestionDifficulty = "EASY" | "MEDIUM" | "HARD";

export type QuestionStatus = "APPROVED" | "EDITED";

export interface QuestionFilterValues {
    keyword: string;
    subjectId: number | null;
    difficulty: QuestionDifficulty | null;
    status: QuestionStatus | null;
}

interface SubjectOption {
    label: string;
    value: number;
}

interface QuestionFiltersProps {
    value: QuestionFilterValues;
    subjects?: SubjectOption[];
    onChange: (values: QuestionFilterValues) => void;
    onFilter: () => void;
    onReset: () => void;
}

const difficultyOptions = [
    {
        label: "Dễ",
        value: "EASY",
    },
    {
        label: "Trung bình",
        value: "MEDIUM",
    },
    {
        label: "Khó",
        value: "HARD",
    },
];

const statusOptions = [
    {
        label: "Đã duyệt",
        value: "APPROVED",
    },
    {
        label: "Đã chỉnh sửa",
        value: "EDITED",
    },
];

export default function QuestionFilters({
    value,
    subjects = [],
    onChange,
    onFilter,
    onReset,
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
                                value={value.subjectId ?? undefined}
                                onChange={(subjectId) =>
                                    updateField(
                                        "subjectId",
                                        subjectId ?? null,
                                    )
                                }
                                placeholder="Tất cả môn học"
                                options={subjects}
                                allowClear
                                className="question-filters-select"
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