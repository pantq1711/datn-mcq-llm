import { Button, Result } from "antd";
import { useNavigate } from "react-router-dom";

export const NotFoundPage = () => {
    const navigate = useNavigate();

    return (
        <div
            style={{
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                minHeight: "100vh",
                width: "100%",
            }}
        >
            <Result
                status="404"
                title="404"
                subTitle="Trang bạn tìm không tồn tại hoặc đã bị di chuyển."
                extra={
                    <Button type="primary" onClick={() => navigate("/")}>
                        Về trang chủ
                    </Button>
                }
            />
        </div>
    );
};

export default NotFoundPage;