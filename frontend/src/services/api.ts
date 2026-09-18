import axios from 'axios';
import type { AxiosInstance } from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api/v1';

// Content-Type sengaja TIDAK diset di sini.
//
// Memaksanya ke application/json berlaku untuk SETIAP request, termasuk yang
// membawa FormData — dan axios hanya menulis sendiri header multipart beserta
// boundary-nya kalau header itu belum ada. Akibatnya unggahan gambar terkirim
// sebagai application/json, server menolaknya, dan fiturnya tidak pernah bisa
// dipakai dari browser.
//
// Tanpa default, axios memilih sendiri sesuai isi body: objek biasa jadi JSON,
// FormData jadi multipart lengkap dengan boundary.
export const api = axios.create({
  baseURL: API_BASE_URL,
});

// Interceptor to attach Access Token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Single in-flight refresh shared by every instance and every concurrent 401.
// Without this, N parallel requests fire N refreshes; with rotating refresh
// tokens the losers of that race present an already-revoked token and the user
// gets logged out mid-session.
let refreshPromise: Promise<string> | null = null;

function clearSession() {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  if (window.location.pathname !== '/') {
    window.location.href = '/';
  }
}

function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) return Promise.reject(new Error('No refresh token'));

    // Bare axios, not `api`: a 401 from the refresh endpoint itself must not
    // re-enter this interceptor.
    refreshPromise = axios
      .post(`${API_BASE_URL}/auth/refresh`, { refreshToken })
      .then((res) => {
        const newAccessToken = res.data.accessToken as string;
        localStorage.setItem('accessToken', newAccessToken);
        if (res.data.refreshToken) {
          localStorage.setItem('refreshToken', res.data.refreshToken);
        }
        return newAccessToken;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

function attachRefreshInterceptor(instance: AxiosInstance) {
  instance.interceptors.response.use(
    (response) => response,
    async (error) => {
      const originalRequest = error.config;
      if (error.response?.status !== 401 || !originalRequest || originalRequest._retry) {
        return Promise.reject(error);
      }
      originalRequest._retry = true;
      try {
        const newAccessToken = await refreshAccessToken();
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return instance(originalRequest);
      } catch {
        clearSession();
        return Promise.reject(error);
      }
    }
  );
}

attachRefreshInterceptor(api);

// ─── Time Tracking & Project Dashboard API ────────────────────────────────────

export const getProjectDashboard = (projectId: string) =>
  api.get(`/projects/${projectId}/dashboard`);

export const getProjectTimeEntries = (projectId: string, params?: { page?: number; size?: number }) =>
  api.get(`/projects/${projectId}/time-entries`, { params });

export const createTimeEntry = (projectId: string, data: {
  taskId?: string;
  entryDate: string;
  hours: number;
  description?: string;
  entryType: string;
  featureName?: string;
}) => api.post(`/projects/${projectId}/time-entries`, data);

export const updateTimeEntry = (projectId: string, entryId: string, data: {
  taskId?: string;
  entryDate: string;
  hours: number;
  description?: string;
  entryType: string;
  featureName?: string;
}) => api.patch(`/projects/${projectId}/time-entries/${entryId}`, data);

export const deleteTimeEntry = (projectId: string, entryId: string) =>
  api.delete(`/projects/${projectId}/time-entries/${entryId}`);

export const getRecentTimeEntries = (projectId: string, limit = 10) =>
  api.get(`/projects/${projectId}/time-entries/recent`, { params: { limit } });

export const getMemberTimeEntries = (projectId: string, targetUserId: string) =>
  api.get(`/projects/${projectId}/time-entries/user/${targetUserId}`);

// OAuth endpoints are served at root path /oauth2/**, NOT under /api/v1
export const oauthApi = axios.create({
  baseURL: '/oauth2',
});

// Only attach Authorization to /connect/* endpoints (GitHub repo connect requires auth)
oauthApi.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (!token) return config;

  // Only /connect/ endpoints need auth (store token per user)
  const isConnect = config.url?.includes('/connect/');
  if (isConnect) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Automatic token refresh on 401, sharing the same in-flight refresh as `api`
attachRefreshInterceptor(oauthApi);
