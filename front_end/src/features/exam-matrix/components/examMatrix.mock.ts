import type { ExamMatrix } from '../types/examMatrix.types';
import type { ExamMatrixTemplate } from './ExamMatrixTemplateCard';

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

export const MOCK_EXAM_MATRIX_TEMPLATES: ExamMatrixTemplate[] = [
    {
        id: 'template-001',
        name: 'Toán 8 — Kiểm tra giữa học kỳ I',
        subjectName: 'Toán',
        grade: 8,
        description: 'Mẫu tham khảo với ba mức độ nhận thức: Biết, Hiểu và Vận dụng.',
        questionCount: 15,
        questionTypeCount: 3,
        cognitiveLevelCount: 3,
        updatedAt: '2026-10-20T09:30:00+07:00',
    },
    {
        id: 'template-002',
        name: 'Toán 8 — Kiểm tra cuối học kỳ I',
        subjectName: 'Toán',
        grade: 8,
        description: 'Phân bổ câu hỏi cho các nội dung trọng tâm của học kỳ I.',
        questionCount: 20,
        questionTypeCount: 3,
        cognitiveLevelCount: 3,
        updatedAt: '2026-10-17T11:00:00+07:00',
    },
    {
        id: 'template-003',
        name: 'Vật lý 9 — Điện học',
        subjectName: 'Vật lý',
        grade: 9,
        description: 'Mẫu ma trận cho chủ đề điện học, phù hợp bài kiểm tra theo chương.',
        questionCount: 18,
        questionTypeCount: 3,
        cognitiveLevelCount: 3,
        updatedAt: '2026-10-14T13:20:00+07:00',
    },
    {
        id: 'template-004',
        name: 'Ngữ văn 8 — Đọc hiểu văn bản',
        subjectName: 'Ngữ văn',
        grade: 8,
        description: 'Mẫu tham khảo để phân bổ câu hỏi đọc hiểu theo mức độ nhận thức.',
        questionCount: 12,
        questionTypeCount: 2,
        cognitiveLevelCount: 3,
        updatedAt: '2026-10-11T08:45:00+07:00',
    },
    {
        id: 'template-005',
        name: 'Hóa học 8 — Kiến thức nền tảng',
        subjectName: 'Hóa học',
        grade: 8,
        description: 'Mẫu ma trận ngắn dùng cho bài kiểm tra thường xuyên.',
        questionCount: 10,
        questionTypeCount: 3,
        cognitiveLevelCount: 3,
        updatedAt: '2026-10-08T15:10:00+07:00',
    },
];
