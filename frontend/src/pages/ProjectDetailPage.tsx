import React, { useEffect, useState, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Navbar } from '../components/Navbar';
import { Sidebar } from '../components/Sidebar';
import { Skeleton, SkeletonTaskBoard } from '../components/Skeleton';
import { useWorkspace } from '../context/WorkspaceContext';
import { useResolveWorkspace } from '../hooks/useResolveWorkspace';
import { api, getProjectDashboard, createTimeEntry } from '../services/api';
import { getErrMsg } from '../services/apiError';
import {
  fetchGithubConnection, fetchGithubRepos, fetchLinkedRepo, linkRepo, unlinkRepo,
  fetchCommits, openGithubConnectPopup,
} from '../services/githubApi';
import type {
  Project, Task, TaskStatus, GithubConnectedAccount, GithubRepo,
  GithubCommit, LinkedRepo, Comment, WorkspaceMember,
  ProjectDashboard, TimeEntryType,
} from '../types';
import { TaskDetailPanel } from '../components/TaskDetailPanel';
import {
  Plus, X, ArrowRight, ArrowLeft, ExternalLink, GitBranch,
  MessageSquare, LayoutDashboard, Loader2, CheckCircle2, Unplug, RefreshCw, MonitorPlay,
  User, Settings2, Timer, CalendarDays, Users, AlertTriangle,
  Play, Pause, RotateCcw, LayoutGrid,
} from 'lucide-react';

const STATUS_COLORS: Record<string, string> = {
  PLANNING: 'bg-[#edfce9] text-[#003c33] border border-[#003c33]/10',
  IN_PROGRESS: 'bg-blue-50 text-blue-700 border border-blue-700/10',
  ON_HOLD: 'bg-amber-50 text-amber-700 border border-amber-700/10',
  COMPLETED: 'bg-emerald-50 text-emerald-700 border border-emerald-700/10',
  CANCELLED: 'bg-rose-50 text-rose-600 border border-rose-600/10',
};

const ENTRY_TYPE_LABELS: Record<TimeEntryType, string> = {
  FEATURE: 'Fitur',
  BUG: 'Bug',
  REVIEW: 'Review',
  MEETING: 'Meeting',
  OTHER: 'Lainnya',
};

/** Pipeline Flow, berurutan. Dipakai untuk strip tahap, pengelompokan daftar, dan
 *  menentukan tahap berikutnya — sebelumnya urutan ini ditulis ulang di tiga tempat. */
const FLOW_STAGES: { id: TaskStatus; label: string; accent: string; dot: string }[] = [
  { id: 'TODO', label: 'Antrian', accent: 'bg-[#eeece7] text-[#3f3f46]', dot: 'bg-[#93939f]' },
  { id: 'IN_PROGRESS', label: 'Dikerjakan', accent: 'bg-blue-50 text-blue-700', dot: 'bg-blue-500' },
  { id: 'IN_REVIEW', label: 'Review', accent: 'bg-amber-50 text-amber-700', dot: 'bg-amber-500' },
  { id: 'DONE', label: 'Selesai', accent: 'bg-emerald-50 text-emerald-700', dot: 'bg-emerald-500' },
];

const nextStageOf = (status: TaskStatus): TaskStatus | null => {
  const i = FLOW_STAGES.findIndex((s) => s.id === status);
  return i >= 0 && i < FLOW_STAGES.length - 1 ? FLOW_STAGES[i + 1].id : null;
};

const PRIORITY_STYLES: Record<string, string> = {
  LOW: 'bg-slate-100 text-slate-600',
  MEDIUM: 'bg-sky-50 text-sky-700',
  HIGH: 'bg-orange-50 text-orange-700',
  URGENT: 'bg-rose-50 text-rose-700',
};

type Tab = 'overview' | 'commits' | 'demo' | 'comments';

const TABS: { id: Tab; label: string; icon: React.ReactNode }[] = [
  { id: 'overview', label: 'Ikhtisar', icon: <LayoutDashboard className="h-4 w-4" /> },
  { id: 'commits', label: 'GitBranch', icon: <GitBranch className="h-4 w-4" /> },
  { id: 'demo', label: 'Demo', icon: <MonitorPlay className="h-4 w-4" /> },
  { id: 'comments', label: 'Komentar', icon: <MessageSquare className="h-4 w-4" /> },
];

export const ProjectDetailPage: React.FC = () => {
  useResolveWorkspace();
  const { projectId } = useParams<{ projectId: string }>();
  const { currentWorkspace } = useWorkspace();
  const isClient = currentWorkspace?.userRole === 'CLIENT';
  const slug = currentWorkspace?.slug || 'default';

  const [project, setProject] = useState<Project | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const [dash, setDash] = useState<ProjectDashboard | null>(null);

  // time entry form
  const [teTaskId, setTeTaskId] = useState('');
  const [teFeature, setTeFeature] = useState('');
  const [teDate, setTeDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [teHours, setTeHours] = useState('');
  const [teType, setTeType] = useState<TimeEntryType>('FEATURE');
  const [teDesc, setTeDesc] = useState('');
  const [teSaving, setTeSaving] = useState(false);
  const [timerStartedAt, setTimerStartedAt] = useState<number | null>(null);
  const [timerElapsedMs, setTimerElapsedMs] = useState(0);

  // progress/demo editing
  // Hanya untuk ditampilkan. Server yang menghitungnya dari Flow yang selesai.
  const [progressValue, setProgressValue] = useState(0);
  const [, setSavingMeta] = useState(false);
  const [demoField, setDemoField] = useState('');

  // GitBranch
  const [ghConnection, setGhConnection] = useState<GithubConnectedAccount>({ connected: false });
  const [repos, setRepos] = useState<GithubRepo[]>([]);
  const [linkedRepo, setLinkedRepo] = useState<LinkedRepo>({ linked: false });
  const [commits, setCommits] = useState<GithubCommit[]>([]);
  const [commitsLoading, setCommitsLoading] = useState(false);
  const [repoPickerOpen, setRepoPickerOpen] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [ghMsg, setGhMsg] = useState<{ text: string; tone: 'success' | 'error' } | null>(null);

  // comments
  const [comments, setComments] = useState<Comment[]>([]);
  const [commentText, setCommentText] = useState('');
  const [commentInternal, setCommentInternal] = useState(false);
  const [sendingComment, setSendingComment] = useState(false);

  // task modal
  const [showTaskModal, setShowTaskModal] = useState(false);
  const [taskTitle, setTaskTitle] = useState('');
  const [taskDesc, setTaskDesc] = useState('');
  const [taskPriority, setTaskPriority] = useState('MEDIUM');
  const [taskAssignee, setTaskAssignee] = useState('');
  const [taskStartDate, setTaskStartDate] = useState('');
  const [taskDueDate, setTaskDueDate] = useState('');
  const [taskEstimatedHours, setTaskEstimatedHours] = useState('');

  // Anggota workspace: dipakai dropdown penerima tugas dan untuk menampilkan
  // nama pada kartu task (task hanya menyimpan UUID).
  const [members, setMembers] = useState<WorkspaceMember[]>([]);
  // Papan fullscreen: kolom kiri-ke-kanan mengikuti urutan pipeline, jadi yang
  // belum dikerjakan ada di kiri dan yang sudah selesai di kanan.
  const [boardOpen, setBoardOpen] = useState(false);
  const [openTask, setOpenTask] = useState<Task | null>(null);
  const [advanceFlow, setAdvanceFlow] = useState<{ task: Task; nextStatus: TaskStatus } | null>(null);
  const [advanceReason, setAdvanceReason] = useState('');
  const [advancing, setAdvancing] = useState(false);
  const [taskFormError, setTaskFormError] = useState('');
  // Flow yang baru pindah tahap disorot sebentar, supaya mata menemukan
  // baris mana yang berubah setelah daftar dikelompokkan ulang.
  const [flashTaskId, setFlashTaskId] = useState<string | null>(null);

  // Dipakai seksi daftar Flow dan papan fullscreen.
  const doneCount = dash?.doneTaskCount ?? tasks.filter((t) => t.status === 'DONE').length;
  const taskCount = dash?.taskCount ?? tasks.length;

  const memberName = (userId?: string) =>
    userId ? members.find((m) => m.userId === userId)?.fullName ?? 'Pengguna' : null;

  // Satu toast dipakai untuk sukses dan gagal. Sebelumnya semuanya dirender hijau
  // dengan ikon centang, jadi "Gagal membuat Flow" terlihat seperti berhasil.
  const showMsg = (msg: string) => {
    setGhMsg({ text: msg, tone: 'success' });
    setTimeout(() => setGhMsg(null), 4000);
  };

  const showErr = (msg: string) => {
    setGhMsg({ text: msg, tone: 'error' });
    setTimeout(() => setGhMsg(null), 6000);
  };

  const fetchProject = useCallback(async () => {
    const res = await api.get<Project>(`/projects/${projectId}`);
    setProject(res.data);
    setProgressValue(res.data.progressPercent ?? 0);
    setDemoField(res.data.demoUrl ?? '');
  }, [projectId]);

  const fetchTasks = useCallback(async () => {
    const res = await api.get<Task[]>(`/projects/${projectId}/tasks`);
    setTasks(res.data);
  }, [projectId]);

  const fetchDash = useCallback(async () => {
    const res = await getProjectDashboard(projectId!);
    setDash(res.data);
  }, [projectId]);

  const fetchComments = useCallback(async () => {
    const res = await api.get<Comment[]>(`/projects/${projectId}/comments`, { params: { entityType: 'PROJECT' } });
    setComments(res.data);
  }, [projectId]);

  const fetchMembers = useCallback(async () => {
    if (!currentWorkspace) return;
    try {
      if (!projectId) return;
      const res = await api.get<WorkspaceMember[]>(`/workspaces/projects/${projectId}/members`);
      setMembers(res.data);
    } catch (err) {
      console.error('Gagal memuat anggota workspace:', err);
    }
  }, [currentWorkspace, projectId]);

  useEffect(() => {
    // Data anggota berasal dari API dan hanya dimuat ulang saat workspace berubah.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void fetchMembers();
  }, [fetchMembers]);

  const loadGithubState = useCallback(async () => {
    try {
      const conn = await fetchGithubConnection();
      setGhConnection(conn);
    } catch { /* ignore */ }
    try {
      const linked = await fetchLinkedRepo(projectId!);
      setLinkedRepo(linked);
    } catch { /* ignore */ }
  }, [projectId]);

  async function loadCommits() {
    setCommitsLoading(true);
    try {
      setCommits(await fetchCommits(projectId!));
    } catch (err: unknown) {
      showErr(getErrMsg(err) || 'Gagal memuat commit.');
    } finally { setCommitsLoading(false); }
  }

  useEffect(() => {
    if (!projectId) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void Promise.all([fetchProject(), fetchTasks(), loadGithubState(), fetchDash().catch(() => {})])
      .finally(() => setLoading(false));
  }, [projectId, fetchProject, fetchTasks, loadGithubState, fetchDash]);

  useEffect(() => {
    if (activeTab === 'commits' && linkedRepo.linked && projectId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void loadCommits();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, linkedRepo.linked, projectId]);

  useEffect(() => {
    if (activeTab === 'comments') {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void fetchComments();
    }
  }, [activeTab, fetchComments]);

  useEffect(() => {
    if (timerStartedAt === null) return;
    const interval = window.setInterval(() => {
      setTimerElapsedMs(Date.now() - timerStartedAt);
    }, 1000);
    return () => window.clearInterval(interval);
  }, [timerStartedAt]);

  // ==================== PROGRESS / DEMO META ====================

  const saveMeta = async () => {
    setSavingMeta(true);
    try {
      // progressPercent tidak lagi dikirim: angkanya diturunkan server dari Flow yang
      // selesai. Mengirimnya dari sini justru menimpa nilai yang benar dengan salinan
      // basi milik komponen — itu sebabnya menyimpan URL demo mengembalikan bar ke 0.
      const res = await api.patch<Project>(`/projects/${projectId}/meta`, {
        demoUrl: demoField,
      });
      setProject(res.data);
      await fetchDash();
      showMsg('URL demo disimpan.');
    } catch (err: unknown) {
      showErr(getErrMsg(err) || 'Gagal menyimpan progres.');
    } finally { setSavingMeta(false); }
  };

  // ==================== GitBranch ====================

  const handleConnect = async () => {
    setConnecting(true);
    try {
      const r = await openGithubConnectPopup();
      setGhConnection({ connected: r.connected, githubLogin: r.githubLogin });
      setRepoPickerOpen(true);
      try { setRepos(await fetchGithubRepos()); } catch { /* ignore */ }
    } catch (err: unknown) {
      showErr((err as Error).message || 'Gagal menghubungkan GitBranch.');
    } finally { setConnecting(false); }
  };

  const handleOpenRepoPicker = async () => {
    setRepoPickerOpen(true);
    try {
      setRepos(await fetchGithubRepos());
    } catch (err: unknown) {
      showErr(getErrMsg(err) || 'Gagal memuat repos.');
    }
  };

  const handleLinkRepo = async (repo: GithubRepo) => {
    try {
      const res = await linkRepo(projectId!, repo.owner, repo.name, repo.defaultBranch);
      setLinkedRepo(res);
      setRepoPickerOpen(false);
      showMsg(`Repository ${repo.fullName} berhasil dihubungkan.`);
    } catch (err: unknown) {
      showErr(getErrMsg(err) || 'Gagal menghubungkan repository.');
    }
  };

  const handleUnlink = async () => {
    try {
      await unlinkRepo(projectId!);
      setLinkedRepo({ linked: false });
      setCommits([]);
      showMsg('Repository diputus.');
    } catch { showErr('Gagal memutus repository.'); }
  };

  // ==================== TASKS ====================

  /** Cermin dari ProjectService.validateTaskSchedule. Backend tetap jadi penentu —
   *  ini hanya supaya user tidak perlu menunggu round-trip untuk tahu tanggalnya salah. */
  const validateFlowForm = (): string => {
    if (!taskTitle.trim()) return 'Nama Flow wajib diisi.';
    if (taskTitle.trim().length > 255) return 'Nama Flow maksimal 255 karakter.';

    const hours = taskEstimatedHours ? Number(taskEstimatedHours) : null;
    if (hours !== null && (Number.isNaN(hours) || hours <= 0)) return 'Estimasi jam harus lebih dari 0.';
    if (hours !== null && hours > 10000) return 'Estimasi jam maksimal 10000 jam.';

    if (taskStartDate && taskDueDate && taskStartDate > taskDueDate) {
      return 'Tanggal mulai Flow harus sebelum atau sama dengan target selesai.';
    }

    const ps = project?.startDate;
    const pt = project?.targetDate;
    if (ps && taskStartDate && taskStartDate < ps) return `Tanggal mulai Flow tidak boleh sebelum proyek dimulai (${fmtDate(ps)}).`;
    if (ps && taskDueDate && taskDueDate < ps) return `Target selesai Flow tidak boleh sebelum proyek dimulai (${fmtDate(ps)}).`;
    if (pt && taskDueDate && taskDueDate > pt) return `Target selesai Flow tidak boleh melewati target proyek (${fmtDate(pt)}).`;
    if (pt && taskStartDate && taskStartDate > pt) return `Tanggal mulai Flow tidak boleh melewati target proyek (${fmtDate(pt)}).`;

    return '';
  };

  const handleCreateTask = async (e: React.FormEvent) => {
    e.preventDefault();
    const invalid = validateFlowForm();
    if (invalid) {
      setTaskFormError(invalid);
      return;
    }
    setTaskFormError('');
    try {
      await api.post(`/projects/${projectId}/tasks`, {
        title: taskTitle,
        description: taskDesc,
        priority: taskPriority,
        assignedTo: taskAssignee || null,
        startDate: taskStartDate || null,
        dueDate: taskDueDate || null,
        estimatedHours: taskEstimatedHours ? Number(taskEstimatedHours) : null,
      });
    } catch (err: unknown) {
      // Form sengaja tidak direset di sini: kalau gagal, isian user harus tetap ada.
      // Pesan ditaruh di dalam modal — toast di belakang backdrop mudah terlewat.
      setTaskFormError(getErrMsg(err) || 'Gagal membuat Flow.');
      return;
    }
    setShowTaskModal(false);
    setTaskFormError('');
    setTaskTitle(''); setTaskDesc(''); setTaskPriority('MEDIUM');
    setTaskAssignee(''); setTaskStartDate(''); setTaskDueDate(''); setTaskEstimatedHours('');
    await Promise.all([fetchTasks(), fetchDash()]);
  };

  const handleAdvanceFlow = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!advanceFlow || !advanceReason.trim()) return;
    setAdvancing(true);
    try {
      // Satu panggilan: server menulis komentar alasannya dalam transaksi yang sama.
      // Dulu dua panggilan terpisah — kalau POST /comments gagal, tahapnya sudah
      // berpindah tanpa jejak alasan sama sekali.
      await api.patch(`/tasks/${advanceFlow.task.id}/status`, {
        status: advanceFlow.nextStatus,
        reason: advanceReason.trim(),
      });
      await Promise.all([fetchTasks(), fetchDash()]);
      setFlashTaskId(advanceFlow.task.id);
      window.setTimeout(() => setFlashTaskId(null), 1200);
      setAdvanceFlow(null);
      setAdvanceReason('');
      showMsg('Flow berhasil dilanjutkan dan alasan tersimpan.');
    } catch (err: unknown) {
      showErr(getErrMsg(err) || 'Flow gagal dilanjutkan.');
    } finally {
      setAdvancing(false);
    }
  };

  // ==================== TIME TRACKING ====================

  const handleLogTime = async (e: React.FormEvent) => {
    e.preventDefault();
    const hours = Number(teHours);
    if (!teFeature.trim() || !hours || hours <= 0 || hours > 24) {
      showErr('Isi nama fitur dan jam (0–24).');
      return;
    }
    setTeSaving(true);
    try {
      await createTimeEntry(projectId!, {
        taskId: teTaskId || undefined,
        entryDate: teDate,
        hours,
        description: teDesc.trim() || undefined,
        entryType: teType,
        featureName: teFeature.trim(),
      });
      setTeFeature(''); setTeHours(''); setTeDesc(''); setTeTaskId('');
      setTeDate(new Date().toISOString().slice(0, 10));
      setTimerStartedAt(null); setTimerElapsedMs(0);
      await fetchDash();
      showMsg('Waktu kerja tercatat.');
    } catch (err: unknown) {
      showErr(getErrMsg(err) || 'Gagal mencatat waktu.');
    } finally { setTeSaving(false); }
  };

  // ==================== COMMENTS ====================

  const handleSendComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentText.trim()) return;
    setSendingComment(true);
    try {
      await api.post(`/projects/${projectId}/comments`, {
        entityType: 'PROJECT', content: commentText, internal: commentInternal,
      });
      setCommentText(''); setCommentInternal(false);
      fetchComments();
    } catch (err: unknown) {
      showErr(getErrMsg(err) || 'Gagal mengirim komentar.');
    } finally { setSendingComment(false); }
  };

  if (loading) {
    return (
      <div className="flex min-h-screen flex-col bg-[#eeece7] text-[#17171c]">
        <Navbar />
        <div className="flex flex-1">
          <Sidebar />
          <main className="min-w-0 flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8 pb-24 lg:pb-0">
            <div className="mb-5 flex items-center gap-2">
              <Skeleton className="h-8 w-32 rounded-full" />
            </div>
            <div className="rounded-2xl border border-[#d9d9dd] bg-white p-4 sm:p-6 shadow-xs">
              <div className="flex items-center justify-between">
                <div className="flex-1 space-y-3">
                  <Skeleton className="h-8 w-2/3" />
                  <Skeleton className="h-4 w-full" />
                  <Skeleton className="h-4 w-1/2" />
                </div>
                <Skeleton className="h-10 w-32 rounded-full" />
              </div>
              <div className="mt-6 flex gap-1.5">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-9 w-24 rounded-xl" />
                ))}
              </div>
            </div>
            <div className="mt-6">
              <Skeleton className="mb-4 h-6 w-48" />
              <SkeletonTaskBoard />
            </div>
          </main>
        </div>
      </div>
    );
  }

  if (!project) {
    return (
      <div className="flex min-h-screen flex-col bg-[#eeece7] text-[#17171c]">
        <Navbar />
        <div className="flex flex-1"><Sidebar /><main className="flex-1 p-8 pb-24 lg:pb-0">Proyek tidak ditemukan.</main></div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col bg-[#eeece7] text-[#17171c]">
      <Navbar />
        <div className="flex min-w-0 flex-1">
        <Sidebar />
        <main className="min-w-0 flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8 pb-24 lg:pb-0">
          <Link
            to={`/w/${currentWorkspace?.slug || 'default'}/projects`}
            className="press ring-focus mb-5 inline-flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-1.5 text-xs font-medium text-[#003c33] shadow-xs transition hover:bg-[#edfce9]"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Kembali ke Proyek
          </Link>

          {/* Header */}
          <div className="flex flex-col gap-5 rounded-2xl border border-[#d9d9dd] bg-white p-4 shadow-xs sm:p-6 lg:flex-row lg:items-start lg:justify-between">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-3">
                <h1 className="break-words font-mono text-xl font-bold tracking-tight text-[#17171c] sm:text-2xl">{project.name}</h1>
                <span className={`rounded-full px-3 py-1 text-xs font-semibold ${STATUS_COLORS[project.status] || ''}`}>
                  {project.status}
                </span>
              </div>
              <p className="mt-2 max-w-2xl text-sm text-[#75758a]">{project.description || 'Tidak ada deskripsi'}</p>
              {linkedRepo.linked && (
                <a href={`https://github.com/${linkedRepo.owner}/${linkedRepo.repo}`} target="_blank" rel="noreferrer"
                  className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-[#003c33] hover:underline">
                  <GitBranch className="h-3.5 w-3.5" /> {linkedRepo.owner}/{linkedRepo.repo}
                </a>
              )}
            </div>
            <div className="flex w-full flex-wrap items-center gap-2 lg:w-auto lg:justify-end">
              {!isClient && (
                <>
                  <button onClick={() => setShowTaskModal(true)}
                    className="press inline-flex flex-1 items-center justify-center gap-2 rounded-full bg-[#17171c] px-4 py-2.5 text-sm font-semibold text-[#eeece7] transition hover:bg-black sm:flex-none">
                    <Plus className="h-4 w-4" /> Tambah Flow
                  </button>
                  <Link
                    to={`/w/${slug}/projects/${projectId}/settings`}
                    title="Pengaturan proyek"
                    className="press inline-flex items-center gap-2 rounded-full border border-[#d9d9dd] bg-white px-3 py-2.5 text-sm font-semibold text-[#616161] transition hover:bg-slate-50 hover:text-[#17171c]"
                  >
                    <Settings2 className="h-4 w-4" />
                  </Link>
                </>
              )}
            </div>
          </div>

          {/* Tabs */}
          <div className="mt-6 grid grid-cols-2 gap-1 rounded-2xl border border-[#d9d9dd] bg-white p-1 shadow-xs sm:flex sm:items-center sm:overflow-x-auto sm:p-1.5">
            {TABS.map((tab) => (
              <button key={tab.id} onClick={() => setActiveTab(tab.id)}
                 className={`press tap ring-focus flex min-w-0 items-center justify-center gap-1 rounded-xl px-1.5 py-2 text-[11px] font-semibold transition sm:gap-1.5 sm:px-3.5 sm:text-xs ${
                  activeTab === tab.id ? 'bg-[#17171c] text-white shadow-sm' : 'text-slate-500 hover:bg-slate-100 hover:text-slate-700'
                }`}>
                {tab.icon}{tab.label}
              </button>
            ))}
          </div>

          {ghMsg && (
            <div
              role="status"
              aria-live="polite"
              className={`animate-toast-in mt-4 flex items-center gap-2 rounded-xl border px-4 py-3 text-sm font-medium ${
                ghMsg.tone === 'error'
                  ? 'border-rose-500/20 bg-rose-50 text-rose-700'
                  : 'border-emerald-500/20 bg-emerald-50 text-emerald-700'
              }`}
            >
              {ghMsg.tone === 'error'
                ? <AlertTriangle className="h-4 w-4 shrink-0" />
                : <CheckCircle2 className="h-4 w-4 shrink-0" />}
              {ghMsg.text}
            </div>
          )}

          <div key={activeTab} className="animate-tab-in mt-6">
            {activeTab === 'overview' && renderOverview()}
            {activeTab === 'commits' && renderCommits()}
            {activeTab === 'demo' && renderDemo()}
            {activeTab === 'comments' && renderComments()}
          </div>
        </main>
      </div>

      {/* Flow detail */}
      {openTask && (
        <TaskDetailPanel
          task={openTask}
          projectId={projectId!}
          members={members}
          readOnly={isClient}
          projectStartDate={project.startDate}
          projectTargetDate={project.targetDate}
          onClose={() => setOpenTask(null)}
          onUpdated={(updated) => {
            setOpenTask(updated);
            setTasks((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
            void Promise.all([fetchProject(), fetchDash()]);
          }}
        />
      )}

      {showTaskModal && (
        <div className="animate-fade-in fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-3 backdrop-blur-xs sm:p-4">
          <div className="animate-modal-in max-h-[calc(100dvh-1.5rem)] w-full max-w-md overflow-y-auto rounded-2xl border border-black/[0.08] bg-white p-4 shadow-xl sm:max-h-[calc(100dvh-2rem)] sm:p-6">
            <div className="flex items-center justify-between border-b border-black/[0.06] pb-4">
              <h3 className="font-mono text-lg font-bold text-[#17171c]">Tambah Flow</h3>
              <button onClick={() => { setShowTaskModal(false); setTaskFormError(''); }} className="press rounded-lg p-1 text-[#75758a] hover:bg-[#eeece7] hover:text-[#17171c]"><X className="h-5 w-5" /></button>
            </div>
            {(project.startDate || project.targetDate) && (
              <p className="mt-3 rounded-xl bg-[#edfce9] px-3 py-2 text-xs text-[#003c33]">
                Jadwal proyek: {formatDateRange(project.startDate, project.targetDate)}. Tanggal Flow harus berada di dalam rentang ini.
              </p>
            )}
            {taskFormError && (
              <p role="alert" className="animate-toast-in animate-shake mt-3 flex items-start gap-2 rounded-xl border border-rose-500/20 bg-rose-50 px-3 py-2 text-xs font-medium text-rose-700">
                <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" /> {taskFormError}
              </p>
            )}
            <form onSubmit={handleCreateTask} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-medium text-[#17171c]">Nama Flow</label>
                <input required maxLength={255} value={taskTitle} onChange={(e) => setTaskTitle(e.target.value)} placeholder="auth_service"
                  className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3.5 py-2.5 text-sm outline-none transition focus:border-[#003c33]/50 focus:bg-white" />
              </div>
              <div>
                <label className="block text-xs font-medium text-[#17171c]">Detail Flow</label>
                <textarea rows={3} value={taskDesc} onChange={(e) => setTaskDesc(e.target.value)}
                  className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3.5 py-2.5 text-sm outline-none focus:border-[#003c33]/50" />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <label className="block text-xs font-medium text-[#17171c]">Tanggal mulai</label>
                  <input type="date" value={taskStartDate}
                    min={project.startDate || undefined}
                    max={taskDueDate || project.targetDate || undefined}
                    onChange={(e) => { setTaskStartDate(e.target.value); setTaskFormError(''); }}
                    className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3 py-2.5 text-sm outline-none transition focus:border-[#003c33]/50 focus:bg-white" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#17171c]">Estimasi (jam)</label>
                  <input type="number" min="0.01" max="10000" step="0.25" value={taskEstimatedHours} onChange={(e) => { setTaskEstimatedHours(e.target.value); setTaskFormError(''); }} placeholder="16"
                    className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3 py-2.5 text-sm outline-none transition focus:border-[#003c33]/50 focus:bg-white" />
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-[#17171c]">Prioritas</label>
                <select value={taskPriority} onChange={(e) => setTaskPriority(e.target.value)}
                  className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3.5 py-2.5 text-sm outline-none focus:border-[#003c33]/50">
                  <option value="LOW">Low</option><option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option><option value="URGENT">Urgent</option>
                </select>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <label className="block text-xs font-medium text-[#17171c]">Dikerjakan oleh</label>
                  <select value={taskAssignee} onChange={(e) => setTaskAssignee(e.target.value)}
                    className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3 py-2.5 text-sm outline-none focus:border-[#003c33]/50">
                    <option value="">Belum ditugaskan</option>
                    {members.map((m) => (
                      <option key={m.userId} value={m.userId}>{m.fullName}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#17171c]">Target selesai</label>
                  <input type="date" value={taskDueDate}
                    min={taskStartDate || project.startDate || undefined}
                    max={project.targetDate || undefined}
                    onChange={(e) => { setTaskDueDate(e.target.value); setTaskFormError(''); }}
                    className="mt-1.5 w-full rounded-xl border border-black/10 bg-[#eeece7]/50 px-3 py-2.5 text-sm outline-none transition focus:border-[#003c33]/50 focus:bg-white" />
                </div>
              </div>
              <button type="submit" className="press mt-4 w-full rounded-xl bg-[#17171c] py-3 text-sm font-semibold text-[#eeece7] hover:bg-black">Buat Flow</button>
            </form>
          </div>
        </div>
      )}

      {/* Papan Flow fullscreen.
          Kolom mengikuti urutan FLOW_STAGES, jadi arah baca kiri-ke-kanan sama dengan
          arah pekerjaan bergerak: Antrian di kiri, Selesai di kanan. Definisi tahapnya
          dipakai bersama daftar di bawah, jadi keduanya tidak bisa berbeda. */}
      {boardOpen && (
        <div className="animate-fade-in fixed inset-0 z-[55] flex flex-col bg-[#eeece7]">
          <header className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-b border-[#d9d9dd] bg-white px-4 py-3 sm:px-6">
            <div className="min-w-0">
              <p className="font-mono text-[10px] font-bold uppercase tracking-wider text-[#75758a]">Papan Flow</p>
              <h2 className="truncate font-mono text-base font-bold text-[#17171c] sm:text-lg">{project!.name}</h2>
            </div>
            <div className="flex items-center gap-2">
              <span className="rounded-full bg-[#edfce9] px-3 py-1 text-xs font-bold text-[#003c33]">{doneCount}/{taskCount} selesai</span>
              <button
                type="button"
                onClick={() => setBoardOpen(false)}
                aria-label="Tutup papan"
                className="press ring-focus tap rounded-lg p-2 text-[#75758a] hover:bg-[#eeece7] hover:text-[#17171c]"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
          </header>

          {/* Gulir horizontal di layar sempit: empat kolom tidak akan pernah muat di
              320px, dan memaksanya menumpuk menghilangkan arti "kiri ke kanan". */}
          <div className="min-h-0 flex-1 overflow-x-auto overflow-y-hidden p-3 sm:p-5">
            <div className="flex h-full min-w-max gap-3 sm:gap-4">
              {FLOW_STAGES.map((stage) => {
                const stageTasks = tasks.filter((t) => t.status === stage.id);
                return (
                  <section
                    key={stage.id}
                    className="flex h-full w-[78vw] max-w-[320px] shrink-0 flex-col rounded-2xl border border-[#d9d9dd] bg-white/70 sm:w-[300px]"
                  >
                    <div className="flex shrink-0 items-center gap-2 border-b border-[#d9d9dd] px-3.5 py-3">
                      <span className={`h-2 w-2 shrink-0 rounded-full ${stage.dot}`} />
                      <h3 className="min-w-0 flex-1 truncate text-xs font-bold uppercase tracking-wide text-[#3f3f46]">{stage.label}</h3>
                      <span className="shrink-0 rounded-full bg-[#eeece7] px-2 py-0.5 font-mono text-[11px] font-bold text-[#3f3f46]">
                        {stageTasks.length}
                      </span>
                    </div>

                    <div className="stagger min-h-0 flex-1 space-y-2.5 overflow-y-auto p-2.5">
                      {stageTasks.length === 0 ? (
                        <p className="rounded-xl border border-dashed border-[#d9d9dd] px-3 py-8 text-center text-xs text-[#93939f]">
                          Tidak ada Flow di tahap ini.
                        </p>
                      ) : (
                        stageTasks.map((flow) => {
                          const next = nextStageOf(flow.status);
                          const overdue = flow.dueDate && flow.status !== 'DONE' && getDeadline(flow.dueDate).overdue;
                          return (
                            <article
                              key={flow.id}
                              className={`lift rounded-xl border border-[#d9d9dd] bg-white p-3 shadow-xs ${
                                flashTaskId === flow.id ? 'animate-flash' : ''
                              }`}
                            >
                              <button type="button" onClick={() => setOpenTask(flow)} className="ring-focus w-full text-left">
                                <div className="flex flex-wrap items-center gap-1.5">
                                  <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${PRIORITY_STYLES[flow.priority] || PRIORITY_STYLES.MEDIUM}`}>
                                    {flow.priority}
                                  </span>
                                  {overdue && (
                                    <span className="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-[10px] font-bold text-rose-700">
                                      <AlertTriangle className="h-3 w-3" /> Lewat target
                                    </span>
                                  )}
                                </div>
                                <h4 className="mt-1.5 break-words font-mono text-sm font-bold text-[#17171c]">{flow.title}</h4>
                                <p className="mt-1 line-clamp-2 text-[11px] leading-relaxed text-[#75758a]">
                                  {flow.description || 'Belum ada detail Flow.'}
                                </p>
                              </button>

                              <div className="mt-2.5 flex items-center gap-1.5 text-[11px] text-[#75758a]">
                                <User className="h-3 w-3 shrink-0 text-[#003c33]" />
                                <span className="truncate">{memberName(flow.assignedTo) || 'Belum ditentukan'}</span>
                              </div>
                              <div className="mt-1 flex flex-wrap items-center gap-1.5 text-[11px]">
                                <CalendarDays className="h-3 w-3 shrink-0 text-[#93939f]" />
                                <span className={overdue ? 'font-bold text-rose-600' : 'text-[#3f3f46]'}>
                                  {formatDateRange(flow.startDate, flow.dueDate)}
                                </span>
                                {flow.estimatedHours && (
                                  <span className="rounded-full bg-amber-50 px-1.5 py-0.5 font-bold text-amber-700">{flow.estimatedHours}j</span>
                                )}
                              </div>

                              {!isClient && next && (
                                <button
                                  type="button"
                                  onClick={() => setAdvanceFlow({ task: flow, nextStatus: next })}
                                  className="press ring-focus group mt-2.5 inline-flex w-full items-center justify-center gap-1 rounded-lg bg-[#17171c] px-3 py-2 text-[11px] font-semibold text-white hover:bg-black"
                                >
                                  Pindah ke {FLOW_STAGES.find((x) => x.id === next)?.label}
                                  <ArrowRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5" />
                                </button>
                              )}
                            </article>
                          );
                        })
                      )}
                    </div>
                  </section>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {advanceFlow && (
        <div className="animate-fade-in fixed inset-0 z-[60] flex items-center justify-center bg-[#17171c]/55 p-4 backdrop-blur-xs" onClick={() => !advancing && setAdvanceFlow(null)}>
          <div className="animate-modal-in w-full max-w-lg rounded-2xl border border-black/10 bg-[#faf9f7] p-4 sm:p-6 shadow-2xl" onClick={(event) => event.stopPropagation()}>
            <div className="flex items-start justify-between gap-4 border-b border-black/[0.07] pb-4">
              <div>
                <p className="font-mono text-[10px] font-bold uppercase tracking-wider text-[#75758a]">Konfirmasi perubahan Flow</p>
                <h3 className="mt-1 text-xl font-bold text-[#17171c]">Lanjutkan {advanceFlow.task.title}?</h3>
              </div>
              <button type="button" disabled={advancing} onClick={() => setAdvanceFlow(null)} className="press ring-focus rounded-lg p-1 text-[#75758a] hover:bg-white hover:text-[#17171c]"><X className="h-5 w-5" /></button>
            </div>
            <div className="press ring-focus mt-5 rounded-xl border border-[#d9d9dd] bg-white p-4 text-sm text-[#3f3f46]">
              Status berubah dari <strong>{advanceFlow.task.status}</strong> menjadi <strong>{advanceFlow.nextStatus}</strong>. Alasan akan disimpan sebagai komentar Flow.
            </div>
            <form onSubmit={handleAdvanceFlow} className="mt-5 space-y-4">
              <label className="block text-xs font-semibold text-[#17171c]">Alasan melanjutkan Flow
                <textarea required autoFocus rows={4} value={advanceReason} onChange={(event) => setAdvanceReason(event.target.value)} placeholder="Contoh: Implementasi sudah selesai dan siap masuk tahap review..." className="mt-1.5 w-full resize-y rounded-xl border border-black/10 bg-white px-3.5 py-3 text-sm font-normal outline-none focus:border-[#003c33]/50" />
              </label>
              <div className="flex justify-end gap-2">
                <button type="button" disabled={advancing} onClick={() => setAdvanceFlow(null)} className="press rounded-full border border-[#d9d9dd] bg-white px-4 py-2.5 text-sm font-semibold text-[#3f3f46] hover:bg-[#eeece7]">Batal</button>
                <button type="submit" disabled={advancing || !advanceReason.trim()} className="press rounded-full bg-[#003c33] px-5 py-2.5 text-sm font-bold text-white hover:bg-[#005c4e] disabled:cursor-not-allowed disabled:opacity-50">{advancing ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Menyimpan...</span> : 'Konfirmasi & Lanjutkan'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Repo picker */}
      {repoPickerOpen && (
        <div className="animate-fade-in fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
          <div className="animate-modal-in w-full max-w-lg rounded-2xl border border-black/[0.08] bg-white p-4 shadow-xl">
            <div className="flex items-center justify-between border-b border-black/[0.06] pb-3">
              <h3 className="font-mono text-lg font-bold text-[#17171c]">Pilih Repository</h3>
              <button onClick={() => setRepoPickerOpen(false)} className="text-[#75758a] hover:text-[#17171c]"><X className="h-5 w-5" /></button>
            </div>
            <div className="mt-3 max-h-[420px] overflow-y-auto space-y-1.5">
              {ghConnection.connected && (
                <div className="mb-2 flex items-center gap-2 rounded-lg bg-[#edfce9] px-3 py-2 text-xs font-semibold text-[#003c33]">
                  <GitBranch className="h-3.5 w-3.5" /> Terhubung sebagai @{ghConnection.githubLogin}
                </div>
              )}
              {repos.length === 0 ? (
                <div className="py-10 text-center text-sm text-[#75758a]">
                  <Loader2 className="mx-auto mb-2 h-6 w-6 animate-spin" />
                  {ghConnection.connected ? 'Memuat repos...' : 'Belum terhubung'}
                </div>
              ) : (
                repos.map((r) => (
                  <button key={r.fullName} onClick={() => handleLinkRepo(r)}
                    className="flex w-full items-center gap-3 rounded-xl border border-black/[0.06] px-3 py-2.5 text-left transition hover:border-[#003c33]/40 hover:bg-[#edfce9]/40">
                    <GitBranch className="h-4 w-4 shrink-0 text-slate-400" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-[#17171c]">{r.fullName}</p>
                      {r.description && <p className="truncate text-xs text-[#75758a]">{r.description}</p>}
                    </div>
                    <span className="shrink-0 font-mono text-[10px] text-slate-400">{r.defaultBranch}</span>
                  </button>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );

  // ==================== RENDER HELPERS ====================

  function renderOverview() {
    const schedule = getScheduleProgress(project!.startDate, project!.targetDate);
    const pct = schedule?.percent ?? 0;
    const deadline = project!.targetDate ? getDeadline(project!.targetDate) : null;
    const elapsedSeconds = Math.floor(timerElapsedMs / 1000);
    const timerLabel = [
      Math.floor(elapsedSeconds / 3600),
      Math.floor((elapsedSeconds % 3600) / 60),
      elapsedSeconds % 60,
    ].map((value) => String(value).padStart(2, '0')).join(':');

    const startTimer = () => {
      if (!teFeature.trim()) {
        showErr('Isi nama fitur/flow sebelum memulai timer.');
        return;
      }
      setTimerStartedAt(Date.now() - timerElapsedMs);
    };

    const pauseTimer = () => {
      if (timerStartedAt === null) return;
      const elapsed = Date.now() - timerStartedAt;
      setTimerElapsedMs(elapsed);
      setTimerStartedAt(null);
      setTeHours(Math.max(elapsed / 3_600_000, 0.01).toFixed(2));
    };

    return (
      <div className="space-y-6">
        {deadline && (
          <section className={`flex flex-col gap-4 rounded-2xl border p-5 shadow-xs sm:flex-row sm:items-center sm:justify-between ${
            deadline.urgent ? 'border-rose-200 bg-rose-50' : 'border-[#d9d9dd] bg-white'
          }`}>
            <div className="flex items-center gap-3">
              <div className={`flex h-11 w-11 items-center justify-center rounded-xl ${deadline.urgent ? 'bg-rose-100 text-rose-700' : 'bg-[#edfce9] text-[#003c33]'}`}>
                <CalendarDays className="h-5 w-5" />
              </div>
              <div>
                <p className="text-xs font-semibold text-[#75758a]">Deadline proyek</p>
                <p className="mt-0.5 font-mono text-base font-bold text-[#17171c]">
                  {new Date(`${project!.targetDate}T00:00:00`).toLocaleDateString('id-ID', { day: 'numeric', month: 'long', year: 'numeric' })}
                </p>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <span className={`rounded-full px-3 py-1.5 text-xs font-bold ${deadline.urgent ? 'bg-rose-600 text-white' : 'bg-[#17171c] text-white'}`}>
                {deadline.label}
              </span>
              {(dash?.overdueTaskCount ?? 0) > 0 && (
                <span className="inline-flex items-center gap-1 rounded-full bg-white px-3 py-1.5 text-xs font-semibold text-rose-700 ring-1 ring-rose-200">
                  <AlertTriangle className="h-3.5 w-3.5" /> {dash!.overdueTaskCount} Flow terlambat
                </span>
              )}
            </div>
          </section>
        )}

        <section className="grid overflow-hidden rounded-2xl border border-[#d9d9dd] bg-white shadow-xs sm:grid-cols-2 xl:grid-cols-4">
          <div className="border-b border-[#d9d9dd] p-5 sm:border-r xl:border-b-0">
            <div className="flex items-center justify-between text-xs font-semibold text-[#75758a]"><span>Waktu berjalan</span><CalendarDays className="h-4 w-4 text-[#003c33]" /></div>
            <p className="mt-3 font-mono text-3xl font-bold text-[#17171c]">{schedule ? `${pct}%` : '-'}</p>
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-[#eeece7]"><div className="h-full rounded-full bg-[#003c33]" style={{ width: `${pct}%` }} /></div>
            <p className="mt-2 text-xs text-[#75758a]">{schedule?.label ?? 'Atur tanggal proyek di Pengaturan'}</p>
          </div>
          <div className="border-b border-[#d9d9dd] p-5 xl:border-b-0 xl:border-r">
            <div className="flex items-center justify-between text-xs font-semibold text-[#75758a]"><span>Flow selesai</span><LayoutDashboard className="h-4 w-4 text-[#1863dc]" /></div>
            <p className="mt-3 font-mono text-3xl font-bold text-[#17171c]">{doneCount}<span className="text-base text-[#75758a]">/{taskCount}</span></p>
            {/* Angka yang sama yang dipakai kartu proyek dan dashboard. Diturunkan
                server dari Flow yang selesai — sebelumnya kolomnya tidak pernah ditulis
                siapa pun, jadi semua bar diam di 0%. */}
            <div className="mt-2.5 flex items-center gap-2">
              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-[#eeece7]">
                <div
                  className="animate-progress h-full rounded-full bg-[#003c33] transition-[width] duration-500"
                  style={{ width: `${progressValue}%` }}
                />
              </div>
              <span className="shrink-0 font-mono text-xs font-bold text-[#003c33]">{progressValue}%</span>
            </div>
            <p className={`mt-2 text-xs ${(dash?.overdueTaskCount ?? 0) > 0 ? 'font-semibold text-rose-600' : 'text-[#75758a]'}`}>{dash?.overdueTaskCount ?? 0} melewati batas waktu</p>
          </div>
          <div className="border-b border-[#d9d9dd] p-5 sm:border-r sm:border-b-0">
            <div className="flex items-center justify-between text-xs font-semibold text-[#75758a]"><span>Waktu kerja</span><Timer className="h-4 w-4 text-amber-600" /></div>
            <p className="mt-3 font-mono text-3xl font-bold text-[#17171c]">{dash?.totalHours ?? 0}<span className="text-base text-[#75758a]"> jam</span></p>
            <p className="mt-2 text-xs text-[#75758a]">{dash?.hoursThisWeek ?? 0} jam minggu ini</p>
          </div>
          <div className="p-5">
            <div className="flex items-center justify-between text-xs font-semibold text-[#75758a]"><span>Anggota proyek</span><Users className="h-4 w-4 text-violet-600" /></div>
            <p className="mt-3 font-mono text-3xl font-bold text-[#17171c]">{dash?.memberCount ?? 0}</p>
            <p className="mt-2 text-xs text-[#75758a]">Aktif dalam proyek ini</p>
          </div>
        </section>

        {!isClient && (
          <section className="rounded-2xl border border-[#d9d9dd] bg-white p-4 sm:p-6 shadow-xs">
              <div className="flex flex-col gap-3 sm:gap-4 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <div className="flex items-center gap-2"><Timer className="h-5 w-5 text-[#003c33]" /><h2 className="text-base font-bold text-[#17171c]">Catat waktu fitur / flow</h2></div>
                <p className="mt-1 text-xs text-[#75758a]">Gunakan timer atau masukkan durasi manual, lalu simpan ke aktivitas proyek.</p>
              </div>
              <div className="flex w-full flex-wrap items-center gap-2 rounded-xl bg-[#17171c] p-2 pl-3 text-white sm:w-auto sm:flex-nowrap sm:pl-4">
                <span className="min-w-0 flex-1 font-mono text-lg font-bold tabular-nums sm:min-w-24 sm:text-xl">{timerLabel}</span>
                {timerStartedAt === null ? (
                  <button type="button" onClick={startTimer} className="press ring-focus inline-flex items-center gap-1.5 rounded-lg bg-[#02ffcc] px-3 py-2 text-xs font-bold text-[#003c33] hover:bg-white"><Play className="h-3.5 w-3.5" /> Mulai</button>
                ) : (
                  <button type="button" onClick={pauseTimer} className="press ring-focus inline-flex items-center gap-1.5 rounded-lg bg-amber-300 px-3 py-2 text-xs font-bold text-amber-950 hover:bg-white"><Pause className="h-3.5 w-3.5" /> Jeda</button>
                )}
                <button type="button" title="Reset timer" onClick={() => { setTimerStartedAt(null); setTimerElapsedMs(0); setTeHours(''); }} className="press ring-focus rounded-lg p-2 text-slate-300 hover:bg-white/10 hover:text-white"><RotateCcw className="h-4 w-4" /></button>
              </div>
            </div>

            <form onSubmit={handleLogTime} className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-6">
              <label className="xl:col-span-2"><span className="text-xs font-semibold text-[#3f3f46]">Nama fitur / flow</span><input required value={teFeature} onChange={(e) => setTeFeature(e.target.value)} placeholder="Contoh: Checkout payment" className="mt-1.5 w-full rounded-xl border border-[#d9d9dd] bg-[#faf9f7] px-3 py-2.5 text-sm outline-none focus:border-[#003c33]" /></label>
              <label><span className="text-xs font-semibold text-[#3f3f46]">Flow (opsional)</span><select value={teTaskId} onChange={(e) => setTeTaskId(e.target.value)} className="mt-1.5 w-full rounded-xl border border-[#d9d9dd] bg-[#faf9f7] px-3 py-2.5 text-sm outline-none focus:border-[#003c33]"><option value="">Tanpa flow</option>{tasks.map((task) => <option key={task.id} value={task.id}>{task.title}</option>)}</select></label>
              <label><span className="text-xs font-semibold text-[#3f3f46]">Jenis</span><select value={teType} onChange={(e) => setTeType(e.target.value as TimeEntryType)} className="mt-1.5 w-full rounded-xl border border-[#d9d9dd] bg-[#faf9f7] px-3 py-2.5 text-sm outline-none focus:border-[#003c33]">{Object.entries(ENTRY_TYPE_LABELS).map(([value, text]) => <option key={value} value={value}>{text}</option>)}</select></label>
              <label><span className="text-xs font-semibold text-[#3f3f46]">Tanggal</span><input required type="date" value={teDate}
                min={project!.startDate || undefined}
                max={project!.targetDate && project!.targetDate < new Date().toISOString().slice(0, 10)
                  ? project!.targetDate
                  : new Date().toISOString().slice(0, 10)}
                onChange={(e) => setTeDate(e.target.value)} className="mt-1.5 w-full rounded-xl border border-[#d9d9dd] bg-[#faf9f7] px-3 py-2.5 text-sm outline-none focus:border-[#003c33]" /></label>
              <label><span className="text-xs font-semibold text-[#3f3f46]">Durasi (jam)</span><input required type="number" min="0.01" max="24" step="0.01" value={teHours} onChange={(e) => setTeHours(e.target.value)} placeholder="1.50" className="mt-1.5 w-full rounded-xl border border-[#d9d9dd] bg-[#faf9f7] px-3 py-2.5 text-sm outline-none focus:border-[#003c33]" /></label>
              <label className="md:col-span-2 xl:col-span-5"><span className="text-xs font-semibold text-[#3f3f46]">Catatan (opsional)</span><input value={teDesc} onChange={(e) => setTeDesc(e.target.value)} placeholder="Apa yang selesai atau sedang dikerjakan?" className="mt-1.5 w-full rounded-xl border border-[#d9d9dd] bg-[#faf9f7] px-3 py-2.5 text-sm outline-none focus:border-[#003c33]" /></label>
              <button type="submit" disabled={teSaving || timerStartedAt !== null} className="press self-end rounded-xl bg-[#003c33] px-4 py-2.5 text-sm font-bold text-white transition hover:bg-[#005c4e] disabled:cursor-not-allowed disabled:opacity-50">{teSaving ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Menyimpan...</span> : 'Catat Waktu'}</button>
            </form>
          </section>
        )}

        <div className="grid gap-6 xl:grid-cols-5">
          <section className="rounded-2xl border border-[#d9d9dd] bg-white p-4 sm:p-6 shadow-xs xl:col-span-2">
            <div className="flex items-center justify-between"><h2 className="text-base font-bold text-[#17171c]">Aktivitas waktu terakhir</h2><span className="text-xs text-[#75758a]">Hari ini: {dash?.hoursToday ?? 0} jam</span></div>
            <div className="mt-4 space-y-2">
              {!dash?.recentEntries?.length ? <p className="rounded-xl border border-dashed border-[#d9d9dd] py-8 text-center text-sm text-[#75758a]">Belum ada waktu tercatat.</p> : dash.recentEntries.slice(0, 5).map((entry) => (
                <div key={entry.id} className="flex items-start gap-3 rounded-xl bg-[#faf9f7] p-3">
                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[#003c33] text-xs font-bold text-white">{(entry.userName || '?').charAt(0).toUpperCase()}</div>
                  <div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold text-[#17171c]">{entry.featureName || ENTRY_TYPE_LABELS[entry.entryType]}</p><p className="mt-0.5 truncate text-xs text-[#75758a]">{entry.userName}{entry.taskTitle ? ` · ${entry.taskTitle}` : ''} · {new Date(`${entry.entryDate}T00:00:00`).toLocaleDateString('id-ID', { day: 'numeric', month: 'short' })}</p></div>
                  <span className="shrink-0 font-mono text-sm font-bold text-[#003c33]">{entry.hours}j</span>
                </div>
              ))}
            </div>
          </section>

          <section className="overflow-hidden rounded-2xl border border-[#d9d9dd] bg-white shadow-xs xl:col-span-3">
            <div className="flex flex-col gap-1 border-b border-[#d9d9dd] p-4 sm:p-6"><h2 className="text-base font-bold text-[#17171c]">Anggota dan beban kerja</h2><p className="mt-1 text-xs text-[#75758a]">Ringkasan kontribusi anggota proyek minggu ini.</p></div>
            <div className="overflow-x-auto"><table className="w-full min-w-[560px] text-left text-sm"><thead className="bg-[#faf9f7] text-xs text-[#75758a]"><tr><th className="px-5 py-3 font-semibold">Anggota</th><th className="px-4 py-3 font-semibold">Role</th><th className="px-4 py-3 text-right font-semibold">Flow</th><th className="px-4 py-3 text-right font-semibold">Minggu ini</th><th className="px-5 py-3 text-right font-semibold">Total</th></tr></thead><tbody className="divide-y divide-[#d9d9dd]">
              {!dash?.members?.length ? <tr><td colSpan={5} className="px-5 py-10 text-center text-[#75758a]">Belum ada anggota proyek.</td></tr> : dash.members.map((member) => <tr key={member.userId}><td className="px-5 py-3"><div className="flex items-center gap-2.5"><div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#edfce9] text-xs font-bold text-[#003c33]">{member.fullName.charAt(0).toUpperCase()}</div><div><p className="font-semibold text-[#17171c]">{member.fullName}</p>{member.email && <p className="text-xs text-[#75758a]">{member.email}</p>}</div></div></td><td className="px-4 py-3"><span className="rounded-full bg-[#eeece7] px-2 py-1 text-[10px] font-bold text-[#3f3f46]">{member.role}</span></td><td className="px-4 py-3 text-right font-mono">{member.tasksDone}/{member.tasksAssigned}</td><td className="px-4 py-3 text-right font-mono font-bold text-[#003c33]">{member.hoursThisWeek}j</td><td className="px-5 py-3 text-right font-mono text-[#75758a]">{member.totalHours}j</td></tr>)
              }</tbody></table></div>
          </section>
        </div>

        <section className="overflow-hidden rounded-2xl border border-[#d9d9dd] bg-white shadow-xs">
          <div className="flex flex-col gap-3 border-b border-[#d9d9dd] p-4 sm:flex-row sm:items-center sm:justify-between sm:p-6">
            <div className="min-w-0">
              <h2 className="font-mono text-lg font-bold text-[#17171c]">Flow Pengerjaan</h2>
              <p className="mt-1 text-xs leading-relaxed text-[#75758a]">Satu Flow mewakili satu bagian pekerjaan lengkap beserta developer dan target waktunya.</p>
            </div>
            <div className="flex shrink-0 flex-wrap items-center gap-2 self-start sm:self-auto">
              <span className="rounded-full bg-[#edfce9] px-3 py-1 text-xs font-bold text-[#003c33]">{doneCount}/{taskCount} selesai</span>
              {tasks.length > 0 && (
                <button
                  type="button"
                  onClick={() => setBoardOpen(true)}
                  className="press ring-focus tap inline-flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-1.5 text-xs font-semibold text-[#3f3f46] hover:border-[#17171c] hover:bg-[#faf9f7]"
                >
                  <LayoutGrid className="h-3.5 w-3.5" /> Papan
                </button>
              )}
            </div>
          </div>

          {/* Strip tahap: memberi tahu posisi pekerjaan dalam pipeline sebelum
              membaca satu per satu barisnya. */}
          <div className="grid grid-cols-2 gap-2 border-b border-[#d9d9dd] bg-[#faf9f7] p-3 sm:grid-cols-4 sm:gap-3 sm:p-4">
            {FLOW_STAGES.map((stage) => {
              const count = tasks.filter((t) => t.status === stage.id).length;
              const share = taskCount > 0 ? (count / taskCount) * 100 : 0;
              return (
                <div key={stage.id} className="lift min-w-0 rounded-xl border border-[#d9d9dd] bg-white p-3">
                  <div className="flex items-center gap-1.5">
                    <span className={`h-2 w-2 shrink-0 rounded-full ${stage.dot}`} />
                    <span className="truncate text-[11px] font-bold uppercase tracking-wide text-[#75758a]">{stage.label}</span>
                  </div>
                  <p className="mt-1.5 font-mono text-2xl font-bold leading-none text-[#17171c]">{count}</p>
                  <div className="mt-2 h-1 overflow-hidden rounded-full bg-[#eeece7]">
                    <div className={`animate-progress h-full rounded-full ${stage.dot}`} style={{ width: `${share}%` }} />
                  </div>
                </div>
              );
            })}
          </div>

          {tasks.length === 0 ? (
            <p className="px-6 py-12 text-center text-sm text-[#75758a]">Belum ada Flow. Tambahkan Flow pertama untuk memulai proyek.</p>
          ) : (
            FLOW_STAGES.map((stage) => {
              const stageTasks = tasks.filter((t) => t.status === stage.id);
              if (stageTasks.length === 0) return null;
              return (
                <div key={stage.id}>
                  <div className="flex items-center gap-2 border-b border-[#d9d9dd] bg-[#faf9f7]/70 px-4 py-2 sm:px-6">
                    <span className={`h-2 w-2 shrink-0 rounded-full ${stage.dot}`} />
                    <h3 className="text-xs font-bold uppercase tracking-wide text-[#3f3f46]">{stage.label}</h3>
                    <span className="text-xs text-[#75758a]">({stageTasks.length})</span>
                  </div>
                  <div className="stagger divide-y divide-[#d9d9dd]">
                    {stageTasks.map((flow) => {
                      const next = nextStageOf(flow.status);
                      const overdue = flow.dueDate && flow.status !== 'DONE' && getDeadline(flow.dueDate).overdue;
                      return (
                        <div
                          key={flow.id}
                          className={`grid gap-3 p-4 transition-colors hover:bg-[#faf9f7] sm:gap-4 sm:p-5 lg:grid-cols-[minmax(0,1.6fr)_minmax(150px,.7fr)_minmax(210px,.9fr)_auto] lg:items-center ${
                            flashTaskId === flow.id ? 'animate-flash' : ''
                          }`}
                        >
                          <button type="button" onClick={() => setOpenTask(flow)} className="min-w-0 text-left">
                            <div className="flex flex-wrap items-center gap-2">
                              <h3 className="min-w-0 truncate font-mono text-base font-bold text-[#17171c]">{flow.title}</h3>
                              <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold ${PRIORITY_STYLES[flow.priority] || PRIORITY_STYLES.MEDIUM}`}>{flow.priority}</span>
                              {overdue && (
                                <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-[10px] font-bold text-rose-700">
                                  <AlertTriangle className="h-3 w-3" /> Lewat target
                                </span>
                              )}
                            </div>
                            <p className="mt-1 line-clamp-2 text-xs text-[#75758a]">{flow.description || 'Belum ada detail Flow.'}</p>
                          </button>

                          <div className="min-w-0">
                            <p className="text-[10px] font-bold uppercase tracking-wider text-[#75758a]">Developer</p>
                            <p className="mt-1 inline-flex min-w-0 items-center gap-1.5 text-sm font-semibold text-[#17171c]">
                              <User className="h-3.5 w-3.5 shrink-0 text-[#003c33]" />
                              <span className="truncate">{memberName(flow.assignedTo) || 'Belum ditentukan'}</span>
                            </p>
                          </div>

                          <div className="min-w-0">
                            <p className="text-[10px] font-bold uppercase tracking-wider text-[#75758a]">Target pengerjaan</p>
                            <div className="mt-1 flex flex-wrap items-center gap-2 text-xs">
                              <span className={overdue ? 'font-bold text-rose-600' : 'text-[#3f3f46]'}>{formatDateRange(flow.startDate, flow.dueDate)}</span>
                              {flow.estimatedHours && <span className="rounded-full bg-amber-50 px-2 py-0.5 font-bold text-amber-700">{flow.estimatedHours} jam</span>}
                            </div>
                          </div>

                          <div className="flex w-full items-center justify-end gap-2 lg:w-auto">
                            <button type="button" onClick={() => setOpenTask(flow)} className="press flex-1 rounded-full border border-[#d9d9dd] px-3 py-1.5 text-xs font-semibold text-[#3f3f46] hover:bg-white sm:flex-none">Detail</button>
                            {!isClient && next && (
                              <button type="button" onClick={() => setAdvanceFlow({ task: flow, nextStatus: next })} className="press group inline-flex flex-1 items-center justify-center gap-1 rounded-full bg-[#17171c] px-3 py-1.5 text-xs font-semibold text-white hover:bg-black sm:flex-none">
                                Lanjut <ArrowRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5" />
                              </button>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })
          )}
        </section>
      </div>
    );
  }

  function renderCommits() {
    if (!linkedRepo.linked) {
      return (
        <section className="rounded-2xl border border-[#d9d9dd] bg-white p-5 sm:p-8 text-center shadow-xs">
          <div className="flex flex-col items-center gap-3">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-[#edfce9] text-[#003c33]"><GitBranch className="h-7 w-7" /></div>
            <h3 className="text-base font-semibold text-[#17171c]">
              {ghConnection.connected ? 'Pilih Repository' : 'Hubungkan GitBranch'}
            </h3>
            <p className="max-w-sm text-sm text-[#75758a]">
              {ghConnection.connected
                ? `Terhubung sebagai @${ghConnection.githubLogin}. Pilih repository untuk menampilkan commit-nya.`
                : 'Hubungkan akun GitBranch Anda untuk memilih repository dan melihat commit-nya di sini.'}
            </p>
            <div className="mt-2 flex items-center gap-3">
              {ghConnection.connected ? (
                <>
                  {!isClient && (
                    <button onClick={handleOpenRepoPicker}
                      className="press ring-focus inline-flex items-center gap-2 rounded-full bg-[#17171c] px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-black">
                      Pilih Repository
                    </button>
                  )}
                  <button onClick={handleConnect}
                    className="press ring-focus inline-flex items-center gap-2 rounded-full border border-[#d9d9dd] bg-white px-5 py-2.5 text-sm font-semibold text-[#17171c] transition hover:bg-slate-50">
                    Ganti Akun
                  </button>
                </>
              ) : (
                <button onClick={handleConnect} disabled={connecting}
                  className="press ring-focus inline-flex items-center gap-2 rounded-full bg-[#17171c] px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-black disabled:opacity-60">
                  {connecting ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Menghubungkan...</span> : 'Hubungkan GitBranch'}
                </button>
              )}
            </div>
          </div>
        </section>
      );
    }

    return (
      <section className="rounded-2xl border border-[#d9d9dd] bg-white p-4 sm:p-6 shadow-xs">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-sm font-semibold text-[#17171c]">
            <GitBranch className="h-4 w-4 text-[#003c33]" /> {linkedRepo.owner}/{linkedRepo.repo}
          </div>
          {!isClient && (
            <button onClick={handleUnlink}
              className="press ring-focus inline-flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 transition">
              <Unplug className="h-3.5 w-3.5" /> Putuskan
            </button>
          )}
        </div>

        <div className="mt-4 flex items-center justify-between">
          <p className="text-xs text-[#75758a]">Commit terbaru dari {linkedRepo.owner}/{linkedRepo.repo}</p>
          <div className="flex items-center gap-1.5">
            {!isClient && (
              <button onClick={handleOpenRepoPicker}
                className="press ring-focus inline-flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 transition">
                Ganti
              </button>
            )}
            <button onClick={loadCommits} disabled={commitsLoading}
              className="press ring-focus inline-flex items-center gap-1.5 rounded-full border border-[#d9d9dd] bg-white px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50 transition disabled:opacity-60">
              <RefreshCw className={`h-3.5 w-3.5 ${commitsLoading ? 'animate-spin' : ''}`} /> Muat ulang
            </button>
          </div>
        </div>

        <div className="mt-5 space-y-2">
          {commitsLoading ? (
            <div className="flex items-center justify-center gap-2 py-10 text-xs text-[#75758a]">
              <Loader2 className="h-4 w-4 animate-spin" /> Memuat commit...
            </div>
          ) : commits.length === 0 ? (
            <div className="rounded-xl border border-dashed border-black/10 py-10 text-center text-sm text-[#75758a]">
              Belum ada commit ditemukan.
            </div>
          ) : (
            commits.map((c, i) => (
              <div key={c.sha || i} className="flex items-start gap-3 rounded-xl border border-black/[0.06] bg-white p-3.5 shadow-sm">
                {c.authorAvatar ? (
                  <img src={c.authorAvatar} alt="" className="h-8 w-8 rounded-full" />
                ) : (
                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[#003c33] text-[11px] font-bold text-white">
                    {(c.author || '?').charAt(0).toUpperCase()}
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold leading-snug text-[#17171c]">{c.message}</p>
                  <div className="mt-1 flex items-center gap-2 text-[11px] text-[#75758a]">
                    <span className="font-semibold text-[#003c33]">{c.author || 'unknown'}</span>
                    <span>·</span>
                    <span>{c.committedAt ? timeAgo(c.committedAt) : ''}</span>
                  </div>
                </div>
                <span className="shrink-0 font-mono text-[10px] text-slate-400">{c.sha?.slice(0, 7)}</span>
                {c.htmlUrl && (
                  <a href={c.htmlUrl} target="_blank" rel="noreferrer" className="shrink-0 text-slate-400 hover:text-[#003c33]">
                    <ExternalLink className="h-4 w-4" />
                  </a>
                )}
              </div>
            ))
          )}
        </div>
      </section>
    );
  }

  function renderDemo() {
    const url = safeDemoUrl(project!.demoUrl);
    return (
      <section className="rounded-2xl border border-[#d9d9dd] bg-white p-4 sm:p-6 shadow-xs">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-base font-bold text-[#17171c]">Demo Aplikasi</h2>
            <p className="mt-0.5 text-xs text-[#75758a]">Pratinjau aplikasi yang sedang dikembangkan.</p>
          </div>
          {!isClient && (
            <button onClick={saveMeta} className="press ring-focus inline-flex items-center gap-1.5 rounded-full bg-[#17171c] px-4 py-2 text-xs font-semibold text-white transition hover:bg-black">
              Simpan URL
            </button>
          )}
        </div>

        {!isClient && (
          <div className="mt-4">
            <label className="block text-xs font-medium text-[#3f3f46]">URL Demo</label>
            <input type="url" value={demoField} onChange={(e) => setDemoField(e.target.value)}
              placeholder="https://my-project.vercel.app"
              className="mt-1.5 w-full rounded-xl border border-[#d9d9dd] bg-[#faf9f7] px-3.5 py-2.5 text-sm outline-none focus:border-[#003c33] focus:ring-2 focus:ring-[#003c33]/10" />
          </div>
        )}

        <div className="mt-5">
          {url ? (
            <div className="overflow-hidden rounded-xl border border-black/[0.08]">
              <div className="flex items-center justify-between bg-[#17171c] px-4 py-2">
                <span className="truncate text-xs text-slate-300">{url.replace(/^https?:\/\//, '')}</span>
                <a href={url} target="_blank" rel="noreferrer" className="shrink-0 text-xs font-semibold text-[#02ffcc] hover:underline">
                  Buka di tab baru
                </a>
              </div>
              {/* sandbox tanpa allow-same-origin: demo tidak bisa membaca localStorage
                  (tempat access/refresh token) atau menavigasi frame induk. */}
              <iframe
                src={url}
                title="Demo"
                sandbox="allow-scripts allow-forms allow-popups"
                referrerPolicy="no-referrer"
                className="h-[520px] w-full bg-white"
              />
            </div>
          ) : (
            <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-black/10 py-16">
              <MonitorPlay className="h-10 w-10 text-[#c0c0c8]" />
              <p className="text-sm text-[#75758a]">Belum ada URL demo yang ditambahkan.</p>
            </div>
          )}
        </div>
      </section>
    );
  }

  function renderComments() {
    return (
      <section className="rounded-2xl border border-[#d9d9dd] bg-white p-4 sm:p-6 shadow-xs">
        <div className="flex items-center gap-2">
          <MessageSquare className="h-4 w-4 text-[#003c33]" />
          <h2 className="text-base font-bold text-[#17171c]">Diskusi Proyek</h2>
          <span className="rounded-full bg-[#eeece7] px-2 py-0.5 text-[10px] font-bold text-[#75758a]">{comments.length}</span>
        </div>

        <div className="mt-4 space-y-3">
          {comments.length === 0 ? (
            <div className="rounded-xl border border-dashed border-black/10 py-10 text-center text-sm text-[#75758a]">
              Belum ada komentar. Mulai diskusi di bawah.
            </div>
          ) : (
            comments.map((c) => (
              <div key={c.id} className="rounded-xl border border-black/[0.06] bg-[#faf9f7] p-4">
                <div className="flex items-center gap-2">
                  <div className="flex h-7 w-7 items-center justify-center rounded-full bg-[#003c33] text-[11px] font-bold text-[#edfce9]">
                    {(c.authorName || '?').charAt(0).toUpperCase()}
                  </div>
                  <span className="text-sm font-semibold text-[#17171c]">{c.authorName}</span>
                  {c.internal && (
                    <span className="rounded-md bg-blue-100 px-1.5 py-0.5 text-[10px] font-bold uppercase text-blue-700">Internal</span>
                  )}
                  <span className="ml-auto text-[11px] text-[#75758a]">{timeAgo(c.createdAt)}</span>
                </div>
                <p className="mt-2 text-sm text-[#3f3f46]">{c.content}</p>
              </div>
            ))
          )}
        </div>

        <form onSubmit={handleSendComment} className="mt-5 rounded-xl border border-[#d9d9dd] bg-[#faf9f7] p-4">
          <textarea rows={3} value={commentText} onChange={(e) => setCommentText(e.target.value)}
            placeholder="Tulis komentar atau umpan balik..."
            className="w-full resize-y rounded-xl border border-[#d9d9dd] bg-white px-3.5 py-3 text-sm outline-none focus:border-[#003c33] focus:ring-2 focus:ring-[#003c33]/10" />
          <div className="mt-3 flex items-center justify-between">
            {!isClient && (
              <label className="flex items-center gap-2 text-xs text-slate-600">
                <input type="checkbox" checked={commentInternal} onChange={(e) => setCommentInternal(e.target.checked)} className="accent-[#003c33]" />
                Internal (hanya tim)
              </label>
            )}
            <button type="submit" disabled={sendingComment || !commentText.trim()}
              className="press ring-focus ml-auto inline-flex items-center gap-2 rounded-full bg-[#17171c] px-4 py-2 text-xs font-semibold text-white transition hover:bg-black disabled:opacity-50">
              {sendingComment ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Mengirim...</span> : 'Kirim Komentar'}
            </button>
          </div>
        </form>
      </section>
    );
  }
};

/** Hanya http(s) yang diloloskan: memblokir javascript:, data:, dan skema lain. */
function safeDemoUrl(raw?: string): string | undefined {
  if (!raw) return undefined;
  try {
    const parsed = new URL(raw);
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.href : undefined;
  } catch {
    return undefined;
  }
}

function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'Baru saja';
  if (mins < 60) return `${mins} menit lalu`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} jam lalu`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} hari lalu`;
  return new Date(dateStr).toLocaleDateString('id-ID', { day: 'numeric', month: 'short' });
}

function dateSerial(value: string): number {
  const [year, month, day] = value.split('-').map(Number);
  return Date.UTC(year, month - 1, day) / 86_400_000;
}

function todaySerial(): number {
  const today = new Date();
  return Date.UTC(today.getFullYear(), today.getMonth(), today.getDate()) / 86_400_000;
}

function getDeadline(targetDate: string): { label: string; urgent: boolean; overdue: boolean } {
  const days = dateSerial(targetDate) - todaySerial();
  if (days < 0) return { label: `Terlambat ${Math.abs(days)} hari`, urgent: true, overdue: true };
  if (days === 0) return { label: 'Deadline hari ini', urgent: true, overdue: false };
  return { label: `${days} hari lagi`, urgent: days <= 3, overdue: false };
}

function getScheduleProgress(startDate?: string, targetDate?: string): { percent: number; label: string } | null {
  if (!startDate || !targetDate) return null;
  const start = dateSerial(startDate);
  const target = dateSerial(targetDate);
  const today = todaySerial();
  if (target < start) return { percent: 0, label: 'Rentang tanggal tidak valid' };
  if (start === target) return { percent: today < start ? 0 : 100, label: today < start ? 'Belum dimulai' : 'Jadwal satu hari' };
  const percent = Math.max(0, Math.min(100, Math.round(((today - start) / (target - start)) * 100)));
  if (today < start) return { percent: 0, label: `${start - today} hari menuju mulai` };
  if (today > target) return { percent: 100, label: `Terlambat ${today - target} hari` };
  return { percent, label: `${today - start} dari ${target - start} hari berjalan` };
}

function fmtDate(value: string): string {
  return new Date(`${value}T00:00:00`).toLocaleDateString('id-ID', { day: 'numeric', month: 'short', year: 'numeric' });
}

function formatDateRange(startDate?: string, dueDate?: string): string {
  const format = (value: string) => new Date(`${value}T00:00:00`).toLocaleDateString('id-ID', { day: 'numeric', month: 'short' });
  if (startDate && dueDate) return `${format(startDate)} - ${format(dueDate)}`;
  if (dueDate) return `Target ${format(dueDate)}`;
  if (startDate) return `Mulai ${format(startDate)}`;
  return 'Belum dijadwalkan';
}
