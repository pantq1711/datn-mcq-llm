import { Button, Result, Typography } from "antd";

const { Paragraph } = Typography;

/** Nói rõ câu có hình chưa dùng được để tạo đề (câu có hình chưa vào được đề: ExamSlotRepository loại; 409 SLOT_FROM_BANK khi sửa). */
export const FIGURE_NOT_USABLE_NOTE = "Câu có hình đã vào ngân hàng nhưng chưa dùng để tạo đề được.";

interface ImportResultProps {
    imported: number;
    /** Lần xác nhận này có câu có hình (chỉ biết ngay sau khi xác nhận). */
    hadFigure: boolean;
    onViewBank: () => void;
    onImportMore: () => void;
}

/** Bước 3: đã nhập xong. */
export default function ImportResult({ imported, hadFigure, onViewBank, onImportMore }: ImportResultProps) {
    return (
        <Result
            status="success"
            title={`Đã nhập ${imported} câu vào ngân hàng riêng của thầy/cô.`}
            subTitle={
                <div>
                    <Paragraph type="secondary">Câu riêng chỉ thầy/cô thấy; có thể gỡ khỏi ngân hàng bất cứ lúc nào.</Paragraph>
                    {hadFigure && <Paragraph>{FIGURE_NOT_USABLE_NOTE}</Paragraph>}
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
