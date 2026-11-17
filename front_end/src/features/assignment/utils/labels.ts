import type { AssignmentClass, AttemptState } from "../types/assignment.types";

const ATTEMPT_STATE_LABEL: Record<AttemptState, string> = {
    in_progress: "Đang làm",
    submitted: "Đã nộp",
    expired: "Hết giờ, chưa nộp",
};

export function attemptStateLabel(state: AttemptState): string {
    return ATTEMPT_STATE_LABEL[state] ?? state;
}

/** "8A1 (35), 8A2 (36)" */
export function classSummary(classes: AssignmentClass[]): string {
    return classes.map((c) => `${c.classLabel} (${c.size})`).join(", ");
}
