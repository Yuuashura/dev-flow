import { api, oauthApi } from './api';
import type { GithubCommit, GithubConnectedAccount, GithubRepo, LinkedRepo } from '../types';

let popupListeners = 0;
const messageHandlers: Record<number, (data: unknown) => void> = {};

window.addEventListener('message', (event: MessageEvent) => {
  if (event.origin !== window.location.origin) return;
  const data = event.data as { type?: string; [key: string]: unknown };
  if (data && (data.type === 'GH_CONNECT_SUCCESS' || data.type === 'GH_CONNECT_ERROR')) {
    Object.values(messageHandlers).forEach((handler) => handler(data));
  }
});

export function openGithubConnectPopup(): Promise<{ connected: boolean; githubLogin?: string }> {
  return new Promise((resolve, reject) => {
    const id = ++popupListeners;
    let checkClosed: number | undefined;

    const cleanup = () => {
      delete messageHandlers[id];
      if (checkClosed !== undefined) window.clearInterval(checkClosed);
    };

    messageHandlers[id] = (data: unknown) => {
      const msg = data as { type: string; [key: string]: unknown };
      cleanup();
      if (msg.type === 'GH_CONNECT_SUCCESS') {
        resolve({ connected: true, githubLogin: (msg.githubLogin as string) || undefined });
      } else {
        reject(new Error((msg.error as string) || 'Gagal menghubungkan GitHub.'));
      }
    };

    sessionStorage.setItem('oauth_provider', 'github');
    sessionStorage.setItem('oauth_connect', '1');

    oauthApi
      .get('/github/connect/authorize')
      .then((res) => {
        const authUrl = res.data.authUrl as string;
        const width = 520;
        const height = 700;
        const left = (window.screen.width - width) / 2;
        const top = (window.screen.height - height) / 2;
        const popup = window.open(authUrl, 'GitHub Connect', `width=${width},height=${height},top=${top},left=${left}`);
        if (!popup) {
          cleanup();
          reject(new Error('Popup diblokir. Izinkan popup untuk menghubungkan GitHub.'));
          return;
        }
        // Popup closed without posting a message would otherwise leave this
        // promise pending forever (and the interval polling forever).
        checkClosed = window.setInterval(() => {
          if (popup.closed) {
            cleanup();
            reject(new Error('Jendela GitHub ditutup sebelum proses selesai.'));
          }
        }, 600);
      })
      .catch((err) => {
        cleanup();
        reject(err);
      });
  });
}

export async function fetchGithubConnection(): Promise<GithubConnectedAccount> {
  const res = await api.get<GithubConnectedAccount>('/integrations/github/me');
  return res.data;
}

export async function fetchGithubRepos(): Promise<GithubRepo[]> {
  const res = await api.get<GithubRepo[]>('/integrations/github/repos');
  return res.data;
}

export async function fetchLinkedRepo(projectId: string): Promise<LinkedRepo> {
  const res = await api.get<LinkedRepo>(`/integrations/github/projects/${projectId}/repo`);
  return res.data;
}

export async function linkRepo(projectId: string, owner: string, repo: string, branch?: string): Promise<LinkedRepo> {
  const res = await api.post<LinkedRepo>(`/integrations/github/projects/${projectId}/repo`, { owner, repo, branch });
  return res.data;
}

export async function unlinkRepo(projectId: string): Promise<void> {
  await api.delete(`/integrations/github/projects/${projectId}/repo`);
}

export async function fetchCommits(projectId: string): Promise<GithubCommit[]> {
  const res = await api.get<GithubCommit[]>(`/integrations/github/projects/${projectId}/commits`);
  return res.data;
}