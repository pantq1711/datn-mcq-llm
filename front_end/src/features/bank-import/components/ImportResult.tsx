import { Button, Result, Typography } from "antd";

const { Paragraph } = Typography;

interface ImportResultProps {
    imported: number;
    onViewBank: () => void;
    onImportMore: () => void;
}

/** Bước 3: đã nhập xong. Câu có hình dùng để tạo đề như mọi câu (A2), nên không còn dòng nhắc riêng về hình. */
export default function ImportResult({ imported, onViewBank, onImportMore }: ImportResultProps) {
    return (
        <Result
            status="success"
            title={`Đã nhập ${imported} câu vào ngân hàng riêng của thầy/cô.`}
            subTitle={
                <div>
                    <Paragraph type="secondary">Câu riêng chỉ thầy/cô thấy; có thể gỡ khỏi ngân hàng bất cứ lúc nào.</Paragraph>
                </div>
            }
            extra={[
                <Button key="bank" type="primary" onClick={onViewBank}>
                    Xem ngân hàng câu hỏi
                </Button>,
                <Button key="more" onClick={onImportMore}>
                    Nhập file khác
                </Button>,
            ]}
        />
    );
}
