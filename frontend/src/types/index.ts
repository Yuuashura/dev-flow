export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED';
export type GlobalRole = 'SUPER_ADMIN' | null;

export interface User {
  id: string;
  fullName: string;
  email: string;
  avatarUrl?: string;
  phoneNumber?: string;
  jobTitle?: string;
  companyName?: string;
  city?: string;
  bio?: string;
  profileComplete?: boolean;
  status: UserStatus;
  emailVerified: boolean;
  globalRole?: GlobalRole;
  identities?: string[];
}

export type WorkspaceRole = 'OWNER' | 'ADMIN' | 'DEVELOPER' | 'CLIENT';
export type WorkspaceStatus = 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';

export interface Workspace {
  id: string;
  ownerUserId: string;
  name: string;
  slug: string;
  logoUrl?: string;
  businessType?: string;
  timezone?: string;
  status: WorkspaceStatus;
  userRole?: WorkspaceRole;
  createdAt: string;
}

export interface Invitation {
  id: string;
  workspaceId: string;
  email: string;
  role: WorkspaceRole;
  token: string;
  status: 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED';
  invitedBy: string;
  expiresAt: string;
}

export type ProjectStatus = 'PLANNING' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';

export interface Project {
  id: string;
  workspaceId: string;
  name: string;
  description?: string;
  status: ProjectStatus;
  clientVisible: boolean;
  startDate?: string;
  targetDate?: string;
  progressPercent: number;
  demoUrl?: string;
  githubRepoOwner?: string;
  githubRepoName?: string;
  githubRepoBranch?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface GithubConnectedAccount {
  connected: boolean;
  githubLogin?: string;
}

export interface GithubRepo {
  owner: string;
  name: string;
  fullName: string;
  description?: string;
  defaultBranch?: string;
  htmlUrl: string;
  privateRepo: boolean;
  updatedAt?: string;
}

export interface GithubCommit {
  sha: string;
  message: string;
  author?: string;
  authorAvatar?: string;
  committedAt?: string;
  htmlUrl?: string;
  additions?: number;
  deletions?: number;
}

export interface LinkedRepo {
  linked: boolean;
  owner?: string;
  repo?: string;
  branch?: string;
}

export interface Comment {
  id: string;
  projectId: string;
  entityType: 'PROJECT' | 'TASK' | 'REVISION';
  entityId: string;
  authorId: string;
  authorName: string;
  content: string;
  internal: boolean;
  createdAt: string;
}

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE';

export interface Task {
  id: string;
  projectId: string;
  title: string;
  description?: string;
  status: TaskStatus;
  priority: TaskPriority;
  assignedTo?: string;
  startDate?: string;
  dueDate?: string;
  estimatedHours?: number;
  completedAt?: string;
  position?: number;
  createdBy: string;
  createdAt: string;
  updatedAt?: string;
}

/** Anggota workspace (dipakai dropdown penerima tugas). Diisi service-side dengan nama. */
export interface WorkspaceMember {
  id: string;
  workspaceId: string;
  userId: string;
  fullName: string;
  email?: string;
  role: WorkspaceRole;
  status: string;
  joinedAt: string;
}

export interface Plan {
  id: string;
  name: string;
  code: string;
  description: string;
  priceAmount: number;
  billingInterval: string;
  trialDays: number;
  active: boolean;
  maxWorkspaces?: number;
  maxProjectsPerWorkspace?: number;
  maxMembersPerWorkspace?: number;
}

export interface OwnerPlan {
  id: string;
  userId: string;
  planCode: 'FREE' | 'PRO';
  active: boolean;
  planStart?: string;
  planEnd?: string;
}

export interface Subscription {
  id: string;
  workspaceId: string;
  planId: string;
  status: string;
  currentPeriodStart: string;
  currentPeriodEnd: string;
}

export type PaymentStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'FAILED';

export interface OrderRecord {
  id: string;
  externalId: string;
  planName: string;
  description: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  paymentMethod?: string;
  paymentChannel?: string;
  paidAt?: string;
  createdAt: string;
  invoiceUrl?: string;
}

export type NotificationType = 'INVITATION' | 'SYSTEM' | 'BILLING' | 'COLLABORATION';
export type NotificationStatus = 'UNREAD' | 'READ' | 'ARCHIVED';

export interface InboxNotification {
  id: string;
  type: NotificationType;
  title: string;
  content?: string;
  metadata: Record<string, string>;
  status: NotificationStatus;
  priority: 'LOW' | 'NORMAL' | 'HIGH';
  actionUrl?: string;
  createdAt: string;
  readAt?: string;
  expiresAt?: string;
}

export interface NotificationPage {
  content: InboxNotification[];
  totalElements: number;
  totalPages: number;
  number: number;
  first: boolean;
  last: boolean;
}

export interface OrderHistoryPage {
  content: OrderRecord[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

// ─── Project Member ───────────────────────────────────────────────────────────
export interface ProjectMember {
  id: string;
  projectId: string;
  userId: string;
  fullName?: string;
  email?: string;
  role: WorkspaceRole;
  avatarUrl?: string;
  status: string;
  joinedAt: string;
  updatedAt: string;
}

// ─── Time Tracking ────────────────────────────────────────────────────────────
export type TimeEntryType = 'FEATURE' | 'BUG' | 'REVIEW' | 'MEETING' | 'OTHER';

export interface TimeEntry {
  id: string;
  projectId: string;
  taskId?: string;
  taskTitle?: string;
  userId: string;
  userName: string;
  entryDate: string;
  hours: number;
  description?: string;
  entryType: TimeEntryType;
  featureName?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TimeEntryRequest {
  taskId?: string;
  entryDate: string;
  hours: number;
  description?: string;
  entryType: TimeEntryType;
  featureName?: string;
}

export interface MemberWorkload {
  userId: string;
  fullName: string;
  email?: string;
  role: WorkspaceRole;
  avatarUrl?: string;
  hoursThisWeek: number;
  totalHours: number;
  tasksAssigned: number;
  tasksDone: number;
}

export interface ProjectDashboard {
  project: Project;
  progressPercent: number;
  totalHours: number;
  hoursThisWeek: number;
  hoursToday: number;
  memberCount: number;
  taskCount: number;
  doneTaskCount: number;
  overdueTaskCount: number;
  recentEntries: TimeEntry[];
  members: MemberWorkload[];
}

export interface TimeEntryPage {
  content: TimeEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}
