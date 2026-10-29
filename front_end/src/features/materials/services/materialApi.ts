import axiosInstance from "../../../services/apiClient";
import type { MaterialDto, MaterialPageDto } from "../types/material.types";

export interface UploadMaterialResult {
    material: MaterialDto;
    created: boolean;
}

export async function listMaterials(page = 0, size = 50): Promise<MaterialPageDto> {
    const response = await axiosInstance.get<MaterialPageDto>("/api/materials", {
        params: { page, size },
    });
    return response.data;
}

export const uploadMaterial = async (file: File): Promise<UploadMaterialResult> => {
    const formData = new FormData();
    formData.append("file", file);

    // Đảm bảo cấu hình nhận full response từ axios để check status code
    const response = await axiosInstance.post<MaterialDto>("/api/materials", formData, {
        headers: {
            "Content-Type": "multipart/form-data",
        },
    });

    return {
        material: response.data,
        created: response.status === 201,
    };
};

export async function getMaterial(id: number | string): Promise<MaterialDto> {
    const response = await axiosInstance.get<MaterialDto>(`/api/materials/${id}`);
    return response.data;
}

export async function deleteMaterial(id: number | string): Promise<void> {
    await axiosInstance.delete(`/api/materials/${id}`);
}
