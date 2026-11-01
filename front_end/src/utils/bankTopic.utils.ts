import type { BankTopic } from "../features/question-bank/types/questionBank.types";

export interface MergedBankTopic {
    topic: string;          // tên hiển thị và giá trị điền vào ô "Tên bài học"
    count: number;          // tổng số câu của mọi khối
    grades: number[];       // các khối có câu, tăng dần, không trùng
    hasUnknownGrade: boolean; // có dòng gradeLevel = null
}

export function mergeBankTopics(topics: BankTopic[]): MergedBankTopic[] {
    if (!topics || !Array.isArray(topics)) return [];

    const map = new Map<
        string,
        {
            trimmedTopic: string;
            count: number;
            gradesSet: Set<number>;
            hasUnknownGrade: boolean;
            // Lưu lại tần suất/số lượng câu của từng cách viết (đã trim) để chọn cách viết phổ biến nhất
            spellingCounts: Map<string, number>;
        }
    >();

    for (const item of topics) {
        if (!item || !item.topic) continue;
        const trimmed = item.topic.trim();
        if (!trimmed) continue;

        const key = trimmed.toLowerCase();

        let entry = map.get(key);
        if (!entry) {
            entry = {
                trimmedTopic: trimmed,
                count: 0,
                gradesSet: new Set<number>(),
                hasUnknownGrade: false,
                spellingCounts: new Map<string, number>(),
            };
            map.set(key, entry);
        }

        const itemCnt = item.count ?? 0;
        entry.count += itemCnt;

        // Thống kê số lượng theo từng cách viết (để lấy cách viết có tổng count lớn nhất)
        const currentSpellingCount = entry.spellingCounts.get(trimmed) ?? 0;
        entry.spellingCounts.set(trimmed, currentSpellingCount + itemCnt);

        // Xử lý khối lớp (gradeLevel)
        if (item.gradeLevel !== null && item.gradeLevel !== undefined) {
            entry.gradesSet.add(Number(item.gradeLevel));
        } else {
            entry.hasUnknownGrade = true;
        }
    }

    const result: MergedBankTopic[] = [];

    for (const entry of map.values()) {
        // Tìm cách viết (spelling) có count lớn nhất
        let bestSpelling = entry.trimmedTopic;
        let maxSpellingCount = -1;

        for (const [spelling, cnt] of entry.spellingCounts.entries()) {
            if (cnt > maxSpellingCount) {
                maxSpellingCount = cnt;
                bestSpelling = spelling;
            }
        }

        // Sắp xếp các khối lớp tăng dần và không trùng lặp
        const grades = Array.from(entry.gradesSet).sort((a, b) => a - b);

        result.push({
            topic: bestSpelling,
            count: entry.count,
            grades,
            hasUnknownGrade: entry.hasUnknownGrade,
        });
    }

    return result;
}

export function sortBankTopics(list: MergedBankTopic[], gradeLevel?: number | null): MergedBankTopic[] {
    if (!list || !Array.isArray(list)) return [];

    // Tạo bản sao để tránh làm thay đổi mảng gốc
    return [...list].sort((a, b) => {
        // 1. Nếu đã chọn khối (gradeLevel hợp lệ)
        if (gradeLevel !== null && gradeLevel !== undefined) {
            const aHasGrade = a.grades.includes(gradeLevel);
            const bHasGrade = b.grades.includes(gradeLevel);

            if (aHasGrade && !bHasGrade) return -1;
            if (!aHasGrade && bHasGrade) return 1;
        }

        // 2. Ưu tiên nhiều câu lên trước (giảm dần theo count)
        if (b.count !== a.count) {
            return b.count - a.count;
        }

        // 3. Số câu bằng nhau thì xếp theo tên tiếng Việt (tăng dần)
        return a.topic.localeCompare(b.topic, "vi");
    });
}