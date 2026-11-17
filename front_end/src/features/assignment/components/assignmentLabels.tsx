import { Tag } from "antd";

import type { AssignmentStatus } from "../types/assignment.types";

export function AssignmentStatusTag({ status }: { status: AssignmentStatus }) {
    return status === "open" ? <Tag color="green">Đang mở</Tag> : <Tag>Đã đóng</Tag>;
}
