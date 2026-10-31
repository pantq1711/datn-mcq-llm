export type AdminRole = "teacher" | "admin" | "researcher";

export interface AdminUserItem {
    id: number;
    name: string;
    email: string | null; // tài khoản cũ có thể chưa có email
    role: AdminRole;
    enabled: boolean;
    hasPassword: boolean;
    createdAt: string;
    lastLoginAt: string | null;
}

export interface CreateTeacherPayload {
    name: string;
    email: string;
    password: string;
}

export interface UpdateUserPayload {
    name?: string;
    email?: string;
    enabled?: boolean;
}

export interface ResetPasswordPayload {
    newPassword: string;
}
