import React, { useEffect, useState } from 'react';
import { X, CalendarClock, User, Send, Timer, Loader2 } from 'lucide-react';
import { api } from '../services/api';
import { apiError } from '../services/apiError';
import type { Task, Comment, WorkspaceMember, TaskPriority, TaskStatus } from '../types';

const STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'Belum mulai',
  IN_PROGRESS: 'Dikerjakan',
  IN_REVIEW: 'Review',
  DONE: 'Selesai',
};

interface Props {
  task: Task;
  projectId: string;
  members: WorkspaceMember[];
  readOnly: boolean;
  /** Jendela jadwal proyek. Server menolak tanggal Flow di luar rentang ini
   *  (ProjectService.validateTaskSchedule); tanpa diteruskan ke sini, date picker
   *  tetap mempersilakan memilihnya dan user baru tahu setelah gagal menyimpan. */
  projectStartDate?: string;
  projectTargetDate?: string;
  onClose: () => void;
  onUpdated: (task: Task) => void;
}

const PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

export const TaskDetailPanel: React.FC<Props> = ({
  task, projectId, members, readOnly, projectStartDate, projectTargetDate, onClose, onUpdated,
}) => {
  const [title, setTitle] = useState(task.title);
  const [description, setDescription] = useState(task.description ?? '');
  const [assignedTo, setAssignedTo] = useState(task.assignedTo ?? '');
  const [startDate, setStartDate] = useState(task.startDate ?? '');
  const [dueDate, setDueDate] = useState(task.dueDate ?? '');
  const [estimatedHours, setEstimatedHours] = useState(task.estimatedHours?.toString() ?? '');
  const [priority, setPriority] = useState<TaskPriority>(task.priority);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const [comments, setComments] = useState<Comment[]>([]);
  const [loadingComments, setLoadingComments] = useState(true);
  const [newComment, setNewComment] = useState('');
  const [posting, setPosting] = useState(false);

  const fetchComments = async () => {
    try {
      const res = await api.get<Comment[]>(
        `/projects/${projectId}/comments?entityType=TASK&entityId=${task.id}`
      );
      setComments(res.data);
    } catch (err) {
      console.error('Gagal memuat komentar:', err);
    } finally {
      setLoadingComments(false);
    }
  };

  useEffect(() => {
    // Komentar disinkronkan ulang saat panel berpindah Flow.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoadingComments(true);
    void fetchComments();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [task.id]);

  const handleSave = async () => {
    setSaving(true);
    setError('');
    try {
      const res = await api.patch<Task>(`/tasks/${task.id}`, {
        title,
        description,
        priority,
        assignedTo: assignedTo || null,
        startDate: startDate || null,
        dueDate: dueDate || null,
        estimatedHours: estimatedHours ? Number(estimatedHours) : null,
        clearAssignee: !assignedTo,
        clearStartDate: !startDate,
        clearDueDate: !dueDate,
        clearEstimate: !estimatedHours,
      });
      onUpdated(res.data);
    } catch (err: unknown) {
      setError(apiError(err, 'Gagal menyimpan perubahan.'));
    } finally {
      setSaving(false);
    }
  };

  const handlePostComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newComment.trim()) return;
    setPosting(true);
    try {
      await api.post(`/projects/${projectId}/comments`, {
        entityType: 'TASK',
        entityId: task.id,
        content: newComment.trim(),
        internal: false,
      });
      setNewComment('');
      fetchComments();
    } catch (err: unknown) {
      setError(apiError(err, 'Gagal mengirim komentar.'));
    } finally {
      setPosting(false);
    }
  };

  const overdue = dueDate && task.status !== 'DONE' && dateSerial(dueDate) < todaySerial();

  return (
    <div className="animate-fade-in fixed inset-0 z-50 flex items-center justify-center bg-[#17171c]/55 p-0 backdrop-blur-xs sm:p-4" onClick={onClose}>
      <aside
        className="animate-modal-in flex h-full w-full flex-col overflow-hidden bg-[#faf9f7] shadow-2xl sm:h-[min(90vh,900px)] sm:max-w-5xl sm:rounded-2xl sm:border sm:border-black/10"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="flex shrink-0 items-start justify-between gap-4 border-b border-black/[0.07] bg-[#faf9f7]/95 p-5 backdrop-blur sm:p-6">
          <div className="min-w-0">
            <span className="font-mono text-[10px] font-bold uppercase tracking-wider text-[#75758a]">
              Detail Flow
            </span>
            <h2 className="mt-1 break-words text-lg font-bold text-[#17171c]">{title}</h2>
          </div>
          <button onClick={onClose} className="press ring-focus rounded-lg p-1 text-[#75758a] transition hover:bg-white hover:text-[#17171c]">
            <X className="h-5 w-5" />
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto space-y-6 p-5 sm:p-6">
          <div>
            <label className="block text-xs font-semibold text-[#17171c]">Nama Flow</label>
            <input disabled={readOnly} value={title} onChange={(e) => setTitle(e.target.value)} required
              className="mt-1.5 w-full rounded-xl border border-black/10 bg-white px-3.5 py-2.5 font-mono text-sm font-bold text-[#17171c] outline-none focus:border-[#003c33]/50 disabled:bg-[#eeece7]/40" />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#17171c]">Detail Flow</label>
            <textarea
              rows={4}
              disabled={readOnly}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Jelaskan apa yang harus dikerjakan..."
              className="mt-1.5 w-full rounded-xl border border-black/10 bg-white px-3.5 py-2.5 text-sm text-[#17171c] outline-none focus:border-[#003c33]/50 disabled:bg-[#eeece7]/40"
            />
          </div>

          {/* Status ditampilkan, tidak diubah di sini. Perpindahan tahap hanya lewat
              papan Flow, satu-satunya tempat alasannya dikumpulkan — dan server
              sekarang mewajibkan alasan itu. Dropdown bebas di panel ini dulu
              melewati kedua aturan: urutan tahap dan alasan wajib. */}
          <div>
            <label className="block text-xs font-semibold text-[#17171c]">Status</label>
            <div className="mt-1.5 flex items-center justify-between gap-2 rounded-xl border border-black/10 bg-[#eeece7]/40 px-3.5 py-2.5">
              <span className="text-sm font-semibold text-[#17171c]">{STATUS_LABELS[task.status]}</span>
              <span className="font-mono text-[10px] uppercase tracking-wider text-[#75758a]">Ubah di papan Flow</span>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="flex items-center gap-1.5 text-xs font-semibold text-[#17171c]">
                <User className="h-3.5 w-3.5" /> Dikerjakan oleh
              </label>
              <select
                disabled={readOnly}
                value={assignedTo}
                onChange={(e) => setAssignedTo(e.target.value)}
                className="mt-1.5 w-full rounded-xl border border-black/10 bg-white px-3 py-2.5 text-sm text-[#17171c] outline-none focus:border-[#003c33]/50 disabled:bg-[#eeece7]/40"
              >
                <option value="">Belum ditugaskan</option>
                {members.map((m) => (
                  <option key={m.userId} value={m.userId}>
                    {m.fullName} · {m.role}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="flex items-center gap-1.5 text-xs font-semibold text-[#17171c]">
                <CalendarClock className="h-3.5 w-3.5" /> Tanggal mulai
              </label>
              <input
                type="date"
                disabled={readOnly}
                value={startDate}
                min={projectStartDate || undefined}
                max={dueDate || projectTargetDate || undefined}
                onChange={(e) => setStartDate(e.target.value)}
                className="mt-1.5 w-full rounded-xl border border-black/10 bg-white px-3 py-2.5 text-sm text-[#17171c] outline-none focus:border-[#003c33]/50 disabled:bg-[#eeece7]/40"
              />
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div><label className="flex items-center gap-1.5 text-xs font-semibold text-[#17171c]"><CalendarClock className="h-3.5 w-3.5" /> Target selesai</label><input type="date" min={startDate || projectStartDate || undefined} max={projectTargetDate || undefined} disabled={readOnly} value={dueDate} onChange={(e) => setDueDate(e.target.value)} className={`mt-1.5 w-full rounded-xl border bg-white px-3 py-2.5 text-sm outline-none focus:border-[#003c33]/50 disabled:bg-[#eeece7]/40 ${overdue ? 'border-rose-400 text-rose-700' : 'border-black/10 text-[#17171c]'}`} />{overdue && <p className="mt-1 text-[11px] font-medium text-rose-600">Flow melewati target.</p>}</div>
            <div><label className="flex items-center gap-1.5 text-xs font-semibold text-[#17171c]"><Timer className="h-3.5 w-3.5" /> Estimasi pengerjaan</label><div className="relative"><input type="number" min="0.01" max="10000" step="0.25" disabled={readOnly} value={estimatedHours} onChange={(e) => setEstimatedHours(e.target.value)} placeholder="16" className="mt-1.5 w-full rounded-xl border border-black/10 bg-white px-3 py-2.5 pr-12 text-sm text-[#17171c] outline-none focus:border-[#003c33]/50 disabled:bg-[#eeece7]/40" /><span className="absolute right-3 top-4 text-xs text-[#75758a]">jam</span></div></div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#17171c]">Prioritas</label>
            <div className="mt-1.5 flex flex-wrap gap-2">
              {PRIORITIES.map((p) => (
                <button
                  key={p}
                  type="button"
                  disabled={readOnly}
                  onClick={() => setPriority(p)}
                  className={`rounded-full border px-3 py-1.5 font-mono text-[10px] font-bold uppercase transition disabled:opacity-60 ${
                    priority === p
                      ? 'border-[#17171c] bg-[#17171c] text-white'
                      : 'border-black/10 bg-white text-[#75758a] hover:border-[#17171c]/40'
                  }`}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>

          {!readOnly && (
            <button
              onClick={handleSave}
              disabled={saving}
              className="press ring-focus w-full rounded-xl bg-[#17171c] py-3 text-sm font-semibold text-[#eeece7] transition hover:bg-black disabled:opacity-50"
            >
              {saving ? <span className="inline-flex items-center gap-1.5"><Loader2 className="h-3.5 w-3.5 animate-spin-fast" /> Menyimpan...</span> : 'Simpan Perubahan'}
            </button>
          )}

          {error && (
            <div className="rounded-xl border border-rose-500/20 bg-rose-50 p-3 text-xs text-rose-700">{error}</div>
          )}

          <section className="border-t border-black/[0.07] pt-6">
            <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-[#75758a]">
              Komentar ({comments.length})
            </h3>

            <div className="mt-4 space-y-3">
              {loadingComments ? (
                <p className="text-xs text-[#75758a]">Memuat komentar...</p>
              ) : comments.length === 0 ? (
                <p className="rounded-xl border border-dashed border-black/10 p-4 text-center text-xs text-[#75758a]">
                   Belum ada komentar pada Flow ini.
                </p>
              ) : (
                comments.map((c) => (
                  <div key={c.id} className="rounded-xl border border-black/[0.06] bg-white p-3.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="min-w-0 max-w-full truncate text-xs font-semibold text-[#17171c]">{c.authorName || 'Pengguna'}</span>
                      <span className="ml-auto text-[10px] text-[#75758a]">
                        {new Date(c.createdAt).toLocaleString('id-ID', {
                          day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
                        })}
                      </span>
                    </div>
                    <p className="mt-1.5 whitespace-pre-wrap text-sm text-[#3f3f46]">{c.content}</p>
                  </div>
                ))
              )}
            </div>

            <form onSubmit={handlePostComment} className="mt-4 flex items-end gap-2">
              <textarea
                rows={2}
                value={newComment}
                onChange={(e) => setNewComment(e.target.value)}
                placeholder="Tulis komentar..."
                className="flex-1 resize-none rounded-xl border border-black/10 bg-white px-3.5 py-2.5 text-sm text-[#17171c] outline-none focus:border-[#003c33]/50"
              />
              <button
                type="submit"
                disabled={posting || !newComment.trim()}
                className="press ring-focus rounded-xl bg-[#17171c] p-3 text-[#eeece7] transition hover:bg-black disabled:opacity-40"
              >
                <Send className="h-4 w-4" />
              </button>
            </form>
          </section>
        </div>
      </aside>
    </div>
  );
};

function dateSerial(value: string): number {
  const [year, month, day] = value.split('-').map(Number);
  return Date.UTC(year, month - 1, day);
}

function todaySerial(): number {
  const today = new Date();
  return Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
}

