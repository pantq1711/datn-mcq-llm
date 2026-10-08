import { useState } from "react";

import { Button, Card, Space, Typography } from "antd";
import { HistoryOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";

import TemplateSelector from "../components/TemplateSelector";
import StrategySelector, { type GenerationStrategy } from "../components/StrategySelector";
import GenerationForm, { type GenerationFormValues } from "../components/GenerationForm";
import GenerationProgress from "../components/GenerationProgress";
import GenerationResult from "../components/GenerationResult";

import "./GenerateQuestionPage.css";

const { Title } = Typography;

export default function GenerateQuestionPage() {
    const navigate = useNavigate();

    const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(
        null,
    );
    const [strategy, setStrategy] =
        useState<GenerationStrategy | null>(null);
    const [isGenerating, setIsGenerating] = useState(false);

    const handleGeneration = (values: GenerationFormValues) => {
        console.log(values);

        setIsGenerating(true);

        setTimeout(() => {
            setIsGenerating(false);
        }, 3000);
    };

    return (
        <div className="generate-question-page">
            {/* Page Header */}
            <div className="generate-question-page-header">
                <div className="generate-question-page-header-content">
                    <div className="generate-question-page-header-info">
                        <Title
                            level={2}
                            className="generate-question-page-title"
                        >
                            Sinh câu hỏi
                        </Title>
                    </div>

                    <Button
                        icon={<HistoryOutlined />}
                        onClick={() => navigate("/generation-history")}
                    >
                        Lịch sử sinh câu hỏi
                    </Button>
                </div>
            </div>

            {/* Generation Configuration */}
            <div className="generate-question-page-configuration">
                <Card title="Cấu hình sinh câu hỏi">
                    <div className="generate-question-page-template">
                        <TemplateSelector
                            value={selectedTemplateId}
                            onChange={setSelectedTemplateId}
                        />
                    </div>

                    <div className="generate-question-page-strategy">
                        <StrategySelector
                            value={strategy}
                            onChange={setStrategy}
                        />
                    </div>

                    <div className="generate-question-page-form">
                        <GenerationForm
                            loading={isGenerating}
                            onSubmit={handleGeneration}
                        />
                    </div>
                </Card>
            </div>

            {/* Generation Progress */}
            <div className="generate-question-page-progress">
                <Card title="Tiến trình sinh câu hỏi">
                    <GenerationProgress
                        status="RUNNING"
                        completedCount={4}
                        totalCount={10}
                        currentQuestion={5}
                    />
                </Card>
            </div>

            {/* Generation Result */}
            <div className="generate-question-page-result">
                <Card
                    title={
                        <Space>
                            <span>Kết quả sinh câu hỏi</span>
                        </Space>
                    }
                >
                    <GenerationResult
                        data={[
                            {
                                id: 1,
                                questionText:
                                    "Cho hàm số f(x) = x² - 2x + 1. Giá trị nhỏ nhất là bao nhiêu?",
                                optionA: "0",
                                optionB: "1",
                                optionC: "2",
                                optionD: "-1",
                                correctOption: "A",
                                status: "SUCCESS",
                            },
                        ]}
                    />
                </Card>
            </div>
        </div>
    );
}