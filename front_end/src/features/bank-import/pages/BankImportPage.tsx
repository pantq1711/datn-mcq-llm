import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { Alert, Button, Popconfirm, Result, Spin, Steps, Typography } from "antd";
import { ArrowLeftOutlined, FileWordOutlined } from "@ant-design/icons";
import ImportItemCard from "../components/ImportItemCard";
import ImportResult from "../components/ImportResult";
import ImportSkippedPanel from "../components/ImportSkippedPanel";
import ImportSummary from "../components/ImportSummary";
import ImportUploadForm from "../components/ImportUploadForm";
import PendingImportsPanel from "../components/PendingImportsPanel";
import {
    confirmBankImport,
    deleteBankImport,
    getBankImport,
    listPendingImports,
    uploadBankImport,
} from "../services/bankImportApi";
import type { ImportBatch, ImportUploadOptions } from "../types/bankImport.types";
import { classifyImportError, isPendingLimitError } from "../utils/importErrors";
import type { ClassifiedImportError } from "../utils/importErrors";
import { keysToConfirm, selectAllKeys, summarizeSelection, toggleKey } from "../utils/importSelection";
import "./BankImportPage.css";

const { Title, Paragraph, Text } = Typography;

const IMPORT_PATH = "/question-bank/import";

/** Bước 1 tải lên, 2 xem trước, 3 kết quả. */
type Phase = "upload" | "loading" | "preview" | "result" | "gone" | "error";

function parseId(raw: string | undefined): number | null | undefined {
    if (raw === undefined) return undefined;
    return /^\d+$/.test(raw) ? Number(raw) : null;
}

/** Mỗi lô (hoặc bước tải lên) là một lần dựng riêng (key), nên trạng thái luôn bắt đầu sạch khi đổi lô hoặc quay về bước tải lên. */
export default function BankImportPage() {
    const routeId = parseId(useParams().id);
    return <BankImportFlow key={routeId === undefined ? "new" : String(routeId)} routeId={routeId} />;
}

function BankImportFlow({ routeId }: { routeId: number | null | undefined }) {
    const navigate = useNavigate();
    const location = useLocation();
    // Vừa tải lên xong thì bản xem trước đi kèm lần điều hướng, khỏi gọi lại máy chủ
    const carried = (location.state as { batch?: ImportBatch } | null)?.batch;
    const seed = routeId != null && carried?.id === routeId ? carried : null;

    const [phase, setPhase] = useState<Phase>(
        routeId === undefined ? "upload" : routeId === null ? "gone" : seed ? "preview" : "loading"
    );
    const [batch, setBatch] = useState<ImportBatch | null>(seed);
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [result, setResult] = useState<{ imported: number } | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);

    const [uploading, setUploading] = useState(false);
    const [uploadError, setUploadError] = useState<ClassifiedImportError | null>(null);
    const [pending, setPending] = useState<ImportBatch[]>([]);
    const [pendingError, setPendingError] = useState<string | null>(null);

    const [confirming, setConfirming] = useState(false);
    const [reloading, setReloading] = useState(false);

    const items = useMemo(() => batch?.items ?? [], [batch]);
    const hasImages = useMemo(() => items.some((item) => (item.images?.length ?? 0) > 0), [items]);
    const summary = useMemo(() => summarizeSelection(items, selected), [items, selected]);

    // Không lấy được danh sách lô chờ thì vẫn tải lên được; khối này chỉ hiện khi lấy được
    const loadPending = useCallback(() => listPendingImports().then(setPending, () => setPending([])), []);

    /** Áp một lô lấy được từ máy chủ: đã xác nhận thì sang kết quả; còn chờ thì giữ lựa chọn còn hợp lệ. */
    const applyBatch = useCallback((fresh: ImportBatch) => {
        setBatch(fresh);
        if (fresh.status === "confirmed") {
            setResult({ imported: fresh.importedCount ?? 0 });
            setPhase("result");
            return;
        }
        const valid = new Set((fresh.items ?? []).filter((item) => !item.blocking).map((item) => item.key));
        setSelected((previous) => new Set([...previous].filter((key) => valid.has(key))));
        setPhase("preview");
    }, []);

    /** Lấy lại lô từ máy chủ. 410/404 → màn hết hạn; lỗi khác → màn lỗi (lần mở đầu) hoặc thông báo (lần làm mới). */
    const load = useCallback(
        (id: number, silent: boolean): Promise<boolean> =>
            getBankImport(id).then(
                (fresh) => {
                    applyBatch(fresh);
                    return true;
                },
                (error) => {
                    const classified = classifyImportError(error, "Không mở được lần nhập này.");
                    if (classified.kind === "gone") {
                        setPhase("gone");
                    } else if (silent) {
                        setActionError(classified.message);
                    } else {
                        setLoadError(classified.message);
                        setPhase("error");
                    }
                    return false;
                }
            ),
        [applyBatch]
    );

    useEffect(() => {
        if (routeId === undefined) void loadPending();
        else if (routeId !== null && !seed) void load(routeId, false);
        // một lần cho mỗi lần dựng (key theo lô); seed và routeId không đổi trong đời của lần dựng này
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const goUpload = () => navigate(IMPORT_PATH);

    const handleUpload = async (file: File, options: ImportUploadOptions) => {
        setUploading(true);
        setUploadError(null);
        try {
            const created = await uploadBankImport(file, options);
            navigate(`${IMPORT_PATH}/${created.id}`, { state: { batch: created } });
        } catch (error) {
            setUploadError(classifyImportError(error, "Không đọc được file. Thầy/cô thử lại sau."));
            // Đã đủ số lô chờ: lấy lại danh sách để thầy/cô hủy bớt
            if (isPendingLimitError(error)) void loadPending();
        } finally {
            setUploading(false);
        }
    };

    const handleCancelPending = async (id: number) => {
        setPendingError(null);
        try {
            await deleteBankImport(id);
        } catch (error) {
            const classified = classifyImportError(error, "Không hủy được lần nhập này.");
            // 404/410: lô đã không còn; 409: đã xác nhận. Cả hai đều nên biến khỏi danh sách chờ.
            if (classified.kind !== "gone" && classified.kind !== "not_preview") {
                setPendingError(classified.message);
                return;
            }
        }
        setPending((current) => current.filter((candidate) => candidate.id !== id));
    };

    const handleConfirm = async () => {
        if (!batch) return;
        const keys = keysToConfirm(items, selected);
        if (keys.length === 0) return;
        setConfirming(true);
        setActionError(null);
        try {
            const done = await confirmBankImport(batch.id, keys);
            setResult({ imported: done.imported });
            setPhase("result");
        } catch (error) {
            const classified = classifyImportError(error, "Không nhập được các câu đã chọn.");
            if (classified.kind === "other") {
                setActionError(classified.message);
            } else {
                // 410, 404, 422, 409: lô đã đổi so với bản đang xem → lấy lại, rồi báo lý do
                const reloaded = await load(batch.id, true);
                if (reloaded || classified.kind === "blocked") setActionError(classified.message);
            }
        } finally {
            setConfirming(false);
        }
    };

    const handleReloadImages = async () => {
        if (!batch) return;
        setReloading(true);
        setActionError(null);
        await load(batch.id, true);
        setReloading(false);
    };

    const handleCancelCurrent = async () => {
        if (!batch) return;
        try {
            await deleteBankImport(batch.id);
            goUpload();
        } catch (error) {
            const classified = classifyImportError(error, "Không hủy được lần nhập này.");
            if (classified.kind === "gone") {
                goUpload();
            } else if (classified.kind === "not_preview") {
                await load(batch.id, true);
            } else {
                setActionError(classified.message);
            }
        }
    };

    const stepIndex = phase === "result" ? 2 : phase === "preview" || phase === "loading" ? 1 : 0;

    return (
        <div className="bank-import-page">
            <Button type="link" icon={<ArrowLeftOutlined />} onClick={() => navigate("/question-bank")} style={{ paddingLeft: 0 }}>
                Về ngân hàng câu hỏi
            </Button>
            <Title level={2} className="bank-import-title">
                <FileWordOutlined style={{ marginRight: 10, color: "#1677ff" }} />
                Nhập câu hỏi từ Word
            </Title>
            <Paragraph type="secondary">
                Câu nhập vào là câu riêng của thầy/cô. Hệ thống không dùng AI ở bước này; thầy/cô xem từng câu, chọn câu rồi mới nhập.
            </Paragraph>
            <Steps
                size="small"
                current={stepIndex}
                items={[{ title: "Tải file lên" }, { title: "Xem lại và chọn câu" }, { title: "Hoàn tất" }]}
                className="bank-import-steps"
            />

            {phase === "upload" && (
                <>
                    <ImportUploadForm uploading={uploading} error={uploadError} onSubmit={handleUpload} />
                    {pendingError && <Alert type="error" showIcon style={{ marginTop: 16 }} title={pendingError} />}
                    <PendingImportsPanel
                        batches={pending}
                        onOpen={(id) => navigate(`${IMPORT_PATH}/${id}`)}
                        onCancel={handleCancelPending}
                    />
                </>
            )}

            {phase === "loading" && (
                <div className="bank-import-center">
                    <Spin />
                </div>
            )}

            {phase === "error" && (
                <Alert
                    type="error"
                    showIcon
                    title={loadError}
                    action={
                        routeId ? (
                            <Button
                                size="small"
                                onClick={() => {
                                    setPhase("loading");
                                    void load(routeId, false);
                                }}
                            >
                                Thử lại
                            </Button>
                        ) : undefined
                    }
                />
            )}

            {phase === "gone" && (
                <Result
                    status="warning"
                    title="Bản xem trước đã hết hạn hoặc không còn."
                    subTitle="Lần nhập chỉ được giữ 24 giờ. Thầy/cô tải lại file để đọc lại."
                    extra={
                        <Button type="primary" onClick={goUpload}>
                            Tải file lại
                        </Button>
                    }
                />
            )}

            {phase === "result" && result && (
                <ImportResult
                    imported={result.imported}
                    onViewBank={() => navigate("/question-bank")}
                    onImportMore={goUpload}
                />
            )}

            {phase === "preview" && batch && (
                <>
                    <ImportSummary batch={batch} />
                    <ImportSkippedPanel skipped={batch.skipped} skippedItems={batch.skippedItems} />

                    <div className="bank-import-toolbar">
                        <Button onClick={() => setSelected(selectAllKeys(items))}>Chọn tất cả câu chọn được</Button>
                        <Button onClick={() => setSelected(new Set())}>Bỏ chọn tất cả</Button>
                        {hasImages && (
                            <Button loading={reloading} onClick={handleReloadImages}>
                                Tải lại hình
                            </Button>
                        )}
                        <Popconfirm
                            title="Hủy lần nhập này?"
                            description="Bản xem trước và hình của lần nhập này sẽ bị xóa."
                            okText="Hủy lần nhập này"
                            cancelText="Giữ lại"
                            onConfirm={handleCancelCurrent}
                        >
                            <Button danger>Hủy lần nhập</Button>
                        </Popconfirm>
                    </div>

                    {actionError && <Alert type="error" showIcon style={{ marginBottom: 12 }} title={actionError} />}
                    {summary.hasTf && (
                        <Alert
                            type="info"
                            showIcon
                            style={{ marginBottom: 12 }}
                            title="Câu đúng/sai nhập từ Word chỉ có một mức chung cho cả 4 ý nên thường không được tự lấy vào đề theo ma trận; câu vẫn nằm trong ngân hàng, xem và tìm được."
                        />
                    )}

                    {items.length === 0 ? (
                        <Alert type="warning" showIcon title="Không có câu nào trong bản xem trước." />
                    ) : (
                        <div className="bank-import-list" data-testid="bank-import-list">
                            {items.map((item) => (
                                <ImportItemCard
                                    key={item.key}
                                    item={item}
                                    selected={selected.has(item.key)}
                                    onToggle={(key) => setSelected((current) => toggleKey(current, key, items))}
                                />
                            ))}
                            <div
                                className="bank-import-list-spacer"
                                data-testid="bank-import-list-spacer"
                                aria-hidden="true"
                            />
                        </div>
                    )}

                    <div className="bank-import-confirm-bar">
                        <Text type="secondary">
                            Đã chọn {summary.count} / {batch.importableCount} câu chọn được
                        </Text>
                        <Button type="primary" size="large" disabled={summary.count === 0} loading={confirming} onClick={handleConfirm}>
                            {`Nhập ${summary.count} câu đã chọn`}
                        </Button>
                    </div>
                </>
            )}
        </div>
    );
}
