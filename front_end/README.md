# Frontend (React + TypeScript + Vite)

## Quy ước chữ hiển thị

Áp dụng cho mọi chữ người dùng nhìn thấy (nhãn, nút, thông báo, tooltip). Tên component, biến và comment trong code
giữ nguyên (`QcTraceModal`, `slotId`, `failureReason`...).

| Dùng | Không dùng |
|---|---|
| lần soạn | lần sinh |
| câu (câu này, thay câu này) | chỗ, slot |
| tự kiểm tra, hệ thống tự giải | QC, Solver |
| lỗi khi soạn câu | job, prompt, token, LLM, tên model |
| Biết / Hiểu / Vận dụng | Dễ / Trung bình / Khó, easy / medium / hard |

- Lỗi kỹ thuật backend trả về (ví dụ `job.error`) không hiện thẳng: hiện câu dễ hiểu, đặt nguyên văn trong tooltip
  "Chi tiết".
- Chữ do backend trả về và hiện nguyên văn (`error` của API, lý do bám tài liệu, lập luận của hệ thống) nằm ngoài
  phạm vi này; cần sửa ở backend nếu có thuật ngữ kỹ thuật.

Trước khi duyệt PR có thêm chữ hiển thị, chạy trong `front_end` (bỏ qua kết quả nằm trong comment hoặc tên biến):

```bash
grep -rnE "lần sinh|[Cc]hỗ |Solver|\bQC\b|\bjob\b|\bslot\b" src --include=*.tsx --include=*.ts \
  | grep -v "question-generation\|experiment\|teacher-review\|test-katex"
```

## Ghi chú mẫu Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```

You can also install [eslint-plugin-react-x](https://npmx.dev/package/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://npmx.dev/package/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```
