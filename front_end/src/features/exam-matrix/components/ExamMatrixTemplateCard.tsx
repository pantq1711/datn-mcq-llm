import { Card, Space, Tag, Typography } from 'antd';
import {
    CheckCircleFilled,
    FileTextOutlined,
    AppstoreOutlined,
} from '@ant-design/icons';

import './ExamMatrixTemplateCard.css';

const { Text, Paragraph } = Typography;

/** Metadata needed to render a reusable exam-matrix template. */
export interface ExamMatrixTemplate {
    id: string;
    name: string;
    subjectName: string;
    /** Example: 8 or "Lớp 8". */
    grade?: string | number;
    description?: string;
    questionCount: number;
    /** Number of distinct question types represented in the template. */
    questionTypeCount?: number;
    /** Number of cognitive levels represented in the template. */
    cognitiveLevelCount?: number;
    updatedAt?: string | null;
}

export interface ExamMatrixTemplateCardProps {
    template: ExamMatrixTemplate;
    selected: boolean;
    onSelect: (templateId: string) => void;
}

/** Selectable template card; the modal owns selection and confirmation state. */
export function ExamMatrixTemplateCard({
    template,
    selected,
    onSelect,
}: ExamMatrixTemplateCardProps) {
    const gradeLabel = template.grade == null || template.grade === ''
        ? undefined
        : `Lớp ${String(template.grade).replace(/^Lớp\s*/i, '')}`;

    return (
        <button
            type="button"
            className={`exam-matrix-template-card__selectable${selected ? ' is-selected' : ''}`}
            aria-pressed={selected}
            onClick={() => onSelect(template.id)}
        >
            <Card
                className="exam-matrix-template-card"
                bordered
                size="small"
            >
                <div className="exam-matrix-template-card__content">
                    <div className="exam-matrix-template-card__top-row">
                        <div className="exam-matrix-template-card__icon" aria-hidden="true">
                            <FileTextOutlined />
                        </div>
                        <div className="exam-matrix-template-card__selection-icon">
                            {selected ? <CheckCircleFilled /> : <AppstoreOutlined />}
                        </div>
                    </div>

                    <Text strong className="exam-matrix-template-card__name">
                        {template.name}
                    </Text>

                    <Space size={[6, 6]} wrap className="exam-matrix-template-card__tags">
                        <Tag color="blue">{template.subjectName}</Tag>
                        {gradeLabel && <Tag>{gradeLabel}</Tag>}
                    </Space>

                    {template.description ? (
                        <Paragraph
                            type="secondary"
                            className="exam-matrix-template-card__description"
                            ellipsis={{ rows: 2, expandable: false }}
                        >
                            {template.description}
                        </Paragraph>
                    ) : (
                        <Text type="secondary" className="exam-matrix-template-card__description-empty">
                            Chưa có mô tả cho mẫu này.
                        </Text>
                    )}

                    <div className="exam-matrix-template-card__stats">
                        <Text>
                            <strong>{template.questionCount}</strong> câu hỏi
                        </Text>
                        {template.questionTypeCount != null && (
                            <Text type="secondary">
                                {template.questionTypeCount} dạng câu hỏi
                            </Text>
                        )}
                        {template.cognitiveLevelCount != null && (
                            <Text type="secondary">
                                {template.cognitiveLevelCount} mức độ
                            </Text>
                        )}
                    </div>
                </div>
            </Card>
        </button>
    );
}

export default ExamMatrixTemplateCard;
