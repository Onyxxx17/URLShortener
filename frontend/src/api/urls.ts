import api from './axios';

export interface URLItem {
  id: number;
  shortUrl: string;
  shortCode: string;
  originalUrl: string;
  createdAt: string;
  expiresAt: string | null;
  clickCount: number;
  createdBy: string;
}

export interface CreateUrlPayload {
  originalUrl: string;
  expiresInDays?: number;
}

export const getMyUrls = async (): Promise<URLItem[]> => {
  const response = await api.get('/urls/my-urls');
  return response.data;
};

export const createUrl = async (payload: CreateUrlPayload): Promise<URLItem> => {
  const response = await api.post('/create', payload);
  return response.data;
};

export const deleteUrl = async (shortCode: string): Promise<void> => {
  await api.delete(`/urls/${shortCode}`);
};

export const getQRCode = async (shortCode: string): Promise<Blob> => {
  const response = await api.get(`/${shortCode}/qr`, { responseType: 'blob' });
  return response.data;
};
