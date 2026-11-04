import type { ExamMatrix } from '../types/examMatrix.types';

/** Dữ liệu giả lập để xem giao diện trước khi kết nối API. */
export const MOCK_EXAM_MATRICES: ExamMatrix[] = [
    {
        id: 'matrix-001',
        name: 'Ma trận kiểm tra giữa học kỳ I',
        subjectId: 1,
        gradeLevel: 8,
        updatedAt: '2026-10-20T09:30:00+07:00',
    },
    {
        id: 'matrix-002',
        name: 'Ma trận kiểm tra cuối học kỳ I',
        subjectId: 2,
        gradeLevel: 8,
        updatedAt: '2026-10-18T14:15:00+07:00',
    },
    {
        id: 'matrix-003',
        name: 'Ma trận kiểm tra chương: Điện học',
        subjectId: 3,
        gradeLevel: 9,
        updatedAt: '2026-10-15T08:00:00+07:00',
    },
    {
        id: 'matrix-004',
        name: 'Ma trận ôn tập cuối chương',
        subjectId: 3,
        gradeLevel: 9,
        updatedAt: '2026-10-12T16:45:00+07:00',
    },
    {
        id: 'matrix-005',
        name: 'Ma trận kiểm tra thường xuyên',
        subjectId: 1,
        gradeLevel: 9,
        updatedAt: '2026-10-10T10:20:00+07:00',
    },
];
