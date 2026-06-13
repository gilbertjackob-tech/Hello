import type { Env } from './index';

export function json(body: any, init: ResponseInit = {}): Response {
  const headers = new Headers(init.headers);
  headers.set('content-type', 'application/json; charset=utf-8');
  return new Response(JSON.stringify(body), { ...init, headers });
}

export function badRequest(message: string): Response {
  return json({ ok: false, error: message }, { status: 400 });
}

export function randomId(prefix: string): string {
  return prefix + '_' + crypto.randomUUID().replace(/-/g, '');
}

export async function readJson(request: Request): Promise<any> {
  try {
    return await request.json();
  } catch {
    return {};
  }
}

async function ensureStatusSchema(env: Env): Promise<void> {
  await env.DB.prepare(`
    CREATE TABLE IF NOT EXISTS statuses (
      id TEXT PRIMARY KEY,
      owner_id TEXT NOT NULL,
      type TEXT NOT NULL DEFAULT 'text',
      text TEXT,
      prompt_id TEXT,
      chain_id TEXT,
      audience TEXT NOT NULL DEFAULT 'household',
      expires_at INTEGER NOT NULL,
      archived_at INTEGER,
      archive_state TEXT NOT NULL DEFAULT 'pending',
      reply_count INTEGER NOT NULL DEFAULT 0,
      reaction_summary TEXT NOT NULL DEFAULT '{}',
      view_count INTEGER NOT NULL DEFAULT 0,
      created_at INTEGER NOT NULL
    )
  `).run();
  await env.DB.prepare(`
    CREATE TABLE IF NOT EXISTS status_views (
      id TEXT PRIMARY KEY,
      status_id TEXT NOT NULL,
      viewer_id TEXT NOT NULL,
      viewed_at INTEGER NOT NULL,
      completed_at INTEGER
    )
  `).run();
  await env.DB.prepare(`
    CREATE TABLE IF NOT EXISTS status_archive_jobs (
      id TEXT PRIMARY KEY,
      status_id TEXT NOT NULL,
      owner_id TEXT NOT NULL,
      state TEXT NOT NULL DEFAULT 'pending',
      created_at INTEGER NOT NULL,
      acked_at INTEGER
    )
  `).run();
  await env.DB.prepare(`
    CREATE TABLE IF NOT EXISTS status_media (
      id TEXT PRIMARY KEY,
      status_id TEXT NOT NULL,
      r2_key TEXT NOT NULL,
      media_type TEXT,
      expires_at INTEGER NOT NULL,
      deleted_at INTEGER
    )
  `).run();
  await env.DB.prepare(`
    CREATE TABLE IF NOT EXISTS status_reactions (
      id TEXT PRIMARY KEY,
      status_id TEXT NOT NULL,
      reactor_id TEXT NOT NULL,
      emoji TEXT NOT NULL,
      reacted_at INTEGER NOT NULL,
      deleted_at INTEGER
    )
  `).run();
  await env.DB.prepare(`
    CREATE TABLE IF NOT EXISTS status_replies (
      id TEXT PRIMARY KEY,
      status_id TEXT NOT NULL,
      sender_id TEXT NOT NULL,
      text TEXT NOT NULL,
      sent_at INTEGER NOT NULL,
      deleted_at INTEGER
    )
  `).run();
  await ensureColumn(env, 'statuses', 'attachment_url', 'TEXT').catch(() => undefined);
  await ensureColumn(env, 'statuses', 'attachment_type', 'TEXT').catch(() => undefined);
  await ensureColumn(env, 'statuses', 'background_color', 'TEXT').catch(() => undefined);
  await ensureColumn(env, 'statuses', 'duration', 'INTEGER').catch(() => undefined);
  await ensureColumn(env, 'statuses', 'backup_state', 'TEXT').catch(() => undefined);
  await ensureColumn(env, 'statuses', 'backup_started_at', 'INTEGER').catch(() => undefined);
  await ensureColumn(env, 'statuses', 'backup_completed_at', 'INTEGER').catch(() => undefined);
  await ensureColumn(env, 'statuses', 'backup_error', 'TEXT').catch(() => undefined);
  await ensureColumn(env, 'statuses', 'drive_file_id', 'TEXT').catch(() => undefined);
  await env.DB.prepare('CREATE INDEX IF NOT EXISTS idx_statuses_expires_at ON statuses(expires_at)').run().catch(() => undefined);
  await env.DB.prepare('CREATE INDEX IF NOT EXISTS idx_status_views_status_id ON status_views(status_id)').run().catch(() => undefined);
  await env.DB.prepare('CREATE UNIQUE INDEX IF NOT EXISTS idx_status_views_unique ON status_views(status_id, viewer_id)').run().catch(() => undefined);
  await env.DB.prepare('CREATE INDEX IF NOT EXISTS idx_status_media_status_id ON status_media(status_id)').run().catch(() => undefined);
  await env.DB.prepare('CREATE INDEX IF NOT EXISTS idx_status_reactions_status_id ON status_reactions(status_id)').run().catch(() => undefined);
  await env.DB.prepare('CREATE INDEX IF NOT EXISTS idx_status_replies_status_id ON status_replies(status_id)').run().catch(() => undefined);
}

async function ensureColumn(env: Env, table: string, column: string, definition: string): Promise<void> {
  const info = await env.DB.prepare(`PRAGMA table_info(${table})`).all<any>();
  const exists = (info.results || []).some((row: any) => row.name === column);
  if (!exists) {
    await env.DB.prepare(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`).run();
  }
}

async function userById(env: Env, userId: string): Promise<any> {
  return env.DB.prepare(`
    SELECT u.id,
      COALESCE(p.display_name, u.display_name, u.name, u.id) AS name,
      COALESCE(p.avatar_url, u.avatar_url, u.avatar) AS avatar
    FROM users u
    LEFT JOIN user_profiles p ON p.user_id = u.id
    WHERE u.id = ?
  `).bind(userId).first<any>().catch(() => null);
}

function legacyStatus(row: any, views: any[], user: any | undefined): any {
  return {
    id: row.id,
    userId: row.owner_id || row.userId,
    text: row.text || '',
    attachmentUrl: row.attachment_url || '',
    attachmentType: row.attachment_type || '',
    backgroundColor: row.background_color || '#0b141a',
    duration: Number(row.duration || 5000),
    timestamp: Number(row.created_at || row.timestamp || 0),
    userName: user?.name || row.owner_id || row.userId || 'Hello user',
    userAvatar: user?.avatar || null,
    views,
  };
}

export async function getLegacyStatuses(env: Env, url: URL) {
  await ensureStatusSchema(env);
  const cutoff = Date.now() - 24 * 60 * 60 * 1000;
  const res = await env.DB.prepare(
    'SELECT * FROM statuses WHERE created_at >= ? AND expires_at > ? ORDER BY created_at ASC'
  ).bind(cutoff, Date.now()).all();
  const rows = res.results || [];
  const viewsRes = await env.DB.prepare(
    'SELECT status_id AS statusId, viewer_id AS userId, viewed_at AS timestamp FROM status_views'
  ).all().catch(() => ({ results: [] as any[] }));
  const users = new Map<string, any>();
  await Promise.all(rows.map(async (row: any) => {
    const userId = String(row.owner_id || row.userId || '');
    if (userId && !users.has(userId)) users.set(userId, await userById(env, userId));
  }));
  const views = viewsRes.results || [];
  return json(rows.map((row: any) => {
    const statusViews = views
      .filter((view: any) => view.statusId === row.id)
      .map((view: any) => ({ userId: view.userId, timestamp: Number(view.timestamp || 0) }));
    return legacyStatus(row, statusViews, users.get(String(row.owner_id || row.userId || '')));
  }));
}

export async function createLegacyStatus(env: Env, request: Request) {
  await ensureStatusSchema(env);
  const body = await readJson(request);
  const userId = String(body.userId || '');
  if (!userId) return badRequest('userId is required');
  const now = Date.now();
  const id = randomId('status');
  const expiresAt = now + 24 * 60 * 60 * 1000;
  const attachmentUrl = body.attachmentUrl ? String(body.attachmentUrl) : '';
  const attachmentType = body.attachmentType ? String(body.attachmentType) : '';
  const type = attachmentType.startsWith('video') ? 'video' : attachmentType.startsWith('image') ? 'photo' : 'text';
  await env.DB.prepare(`
    INSERT INTO statuses (
      id, owner_id, type, text, audience, expires_at, created_at,
      attachment_url, attachment_type, background_color, duration
    ) VALUES (?, ?, ?, ?, 'household', ?, ?, ?, ?, ?, ?)
  `).bind(
    id,
    userId,
    type,
    String(body.text || ''),
    expiresAt,
    now,
    attachmentUrl,
    attachmentType,
    String(body.backgroundColor || '#0b141a'),
    Number(body.duration || 5000),
  ).run();
  await env.DB.prepare(
    'INSERT INTO status_archive_jobs (id, status_id, owner_id, state, created_at) VALUES (?, ?, ?, \'pending\', ?)'
  ).bind(randomId('job'), id, userId, now).run().catch(() => undefined);
  const user = await userById(env, userId);
  return json(legacyStatus({ id, owner_id: userId, text: body.text || '', attachment_url: attachmentUrl, attachment_type: attachmentType, background_color: body.backgroundColor || '#0b141a', duration: body.duration || 5000, created_at: now }, [], user), { status: 201 });
}

export async function viewLegacyStatus(env: Env, request: Request, statusId: string) {
  await ensureStatusSchema(env);
  const body = await readJson(request);
  const userId = String(body.userId || '');
  if (!userId) return badRequest('userId is required');
  const existing = await env.DB.prepare(
    'SELECT id FROM status_views WHERE status_id = ? AND viewer_id = ? LIMIT 1'
  ).bind(statusId, userId).first<any>().catch(() => null);
  if (!existing) {
    await env.DB.prepare(
      'INSERT INTO status_views (id, status_id, viewer_id, viewed_at) VALUES (?, ?, ?, ?)'
    ).bind(randomId('view'), statusId, userId, Date.now()).run();
    await env.DB.prepare('UPDATE statuses SET view_count = view_count + 1 WHERE id = ?').bind(statusId).run().catch(() => undefined);
  }
  return json({ success: true });
}

export async function getStatusFeed(env: Env, url: URL, auth: any) {
  await ensureStatusSchema(env);
  const now = Date.now();
  const res = await env.DB.prepare(
    'SELECT * FROM statuses WHERE expires_at > ? ORDER BY created_at DESC LIMIT 100'
  ).bind(now).all();
  
  const statuses = res.results || [];
  
  const statusIds = statuses.map((s: any) => s.id);
  let mediaByStatus: Record<string, any[]> = {};
  if (statusIds.length > 0) {
    const placeholders = statusIds.map(() => '?').join(',');
    const mediaRes = await env.DB.prepare(
      'SELECT * FROM status_media WHERE status_id IN (' + placeholders + ') AND (deleted_at IS NULL OR deleted_at = 0)'
    ).bind(...statusIds).all();
    for (const m of (mediaRes.results || [])) {
      if (!mediaByStatus[m.status_id as string]) mediaByStatus[m.status_id as string] = [];
      mediaByStatus[m.status_id as string].push(m);
    }
  }

  const feed = statuses.map((s: any) => ({
    ...s,
    media: mediaByStatus[s.id] || [],
    reactionSummary: JSON.parse((s.reaction_summary as string) || '{}')
  }));

  return json({ ok: true, feed });
}

export async function createStatus(env: Env, request: Request, auth: any) {
  await ensureStatusSchema(env);
  const body = await readJson(request);
  const now = Date.now();
  const id = randomId('status');
  const expiresAt = now + 24 * 60 * 60 * 1000;
  const type = body.type || 'text';
  const text = body.text || '';
  const audience = body.audience || 'household';
  const promptId = body.promptId || null;
  const chainId = body.chainId || null;

  await env.DB.prepare(
    'INSERT INTO statuses (id, owner_id, type, text, prompt_id, chain_id, audience, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
  ).bind(id, auth.userId, type, text, promptId, chainId, audience, expiresAt, now).run();

  if (body.mediaIds && Array.isArray(body.mediaIds)) {
    for (const mediaId of body.mediaIds) {
      await env.DB.prepare(
        'UPDATE status_media SET status_id = ? WHERE id = ?'
      ).bind(id, mediaId).run();
    }
  }

  const jobId = randomId('job');
  await env.DB.prepare(
    'INSERT INTO status_archive_jobs (id, status_id, owner_id, state, created_at) VALUES (?, ?, ?, \'pending\', ?)'
  ).bind(jobId, id, auth.userId, now).run();

  return json({ ok: true, id });
}

export async function uploadStatusMedia(env: Env, request: Request, auth: any) {
  await ensureStatusSchema(env);
  const form = await request.formData();
  const fileEntry = form.get('file');
  if (!fileEntry || typeof fileEntry === 'string') return badRequest('file is required');
  const file = fileEntry as File;
  
  const mediaId = randomId('media');
  const key = 'status_media/' + auth.userId + '/' + mediaId + '/' + file.name;
  
  await env.TEMP_FILES.put(key, file.stream(), {
    httpMetadata: { contentType: file.type || 'application/octet-stream' },
  });

  const now = Date.now();
  const expiresAt = now + 24 * 60 * 60 * 1000;

  await env.DB.prepare(
    'INSERT INTO status_media (id, status_id, r2_key, media_type, expires_at) VALUES (?, \'pending\', ?, ?, ?)'
  ).bind(mediaId, key, file.type, expiresAt).run();

  return json({ ok: true, id: mediaId, key });
}

export async function viewStatus(env: Env, request: Request, auth: any, statusId: string) {
  await ensureStatusSchema(env);
  const now = Date.now();
  const viewId = randomId('view');
  const existing = await env.DB.prepare(
    'SELECT id FROM status_views WHERE status_id = ? AND viewer_id = ? LIMIT 1'
  ).bind(statusId, auth.userId).first<any>().catch(() => null);
  if (!existing) {
    await env.DB.prepare(
      'INSERT INTO status_views (id, status_id, viewer_id, viewed_at) VALUES (?, ?, ?, ?)'
    ).bind(viewId, statusId, auth.userId, now).run();
    await env.DB.prepare(
      'UPDATE statuses SET view_count = view_count + 1 WHERE id = ?'
    ).bind(statusId).run();
  }

  return json({ ok: true });
}

export async function reactStatus(env: Env, request: Request, auth: any, statusId: string) {
  await ensureStatusSchema(env);
  const body = await readJson(request);
  const emoji = body.emoji || '👍';
  const now = Date.now();
  const reactId = randomId('react');

  await env.DB.prepare(
    'INSERT INTO status_reactions (id, status_id, reactor_id, emoji, reacted_at) VALUES (?, ?, ?, ?, ?)'
  ).bind(reactId, statusId, auth.userId, emoji, now).run();

  return json({ ok: true });
}

export async function replyStatus(env: Env, request: Request, auth: any, statusId: string) {
  await ensureStatusSchema(env);
  const body = await readJson(request);
  const text = String(body.text || '').trim();
  if (!text) return badRequest('text is required');
  const now = Date.now();
  const replyId = randomId('reply');

  await env.DB.prepare(
    'INSERT INTO status_replies (id, status_id, sender_id, text, sent_at) VALUES (?, ?, ?, ?, ?)'
  ).bind(replyId, statusId, auth.userId, text, now).run();

  await env.DB.prepare(
    'UPDATE statuses SET reply_count = reply_count + 1 WHERE id = ?'
  ).bind(statusId).run();

  return json({ ok: true });
}

export async function deleteStatus(env: Env, request: Request, auth: any, statusId: string) {
  await ensureStatusSchema(env);
  await env.DB.prepare('UPDATE statuses SET expires_at = 0 WHERE id = ? AND owner_id = ?')
    .bind(statusId, auth.userId).run();
  return json({ ok: true });
}

export async function getArchivePending(env: Env, url: URL, auth: any) {
  await ensureStatusSchema(env);
  const res = await env.DB.prepare(
    'SELECT * FROM status_archive_jobs WHERE state = \'pending\' AND owner_id = ?'
  ).bind(auth.userId).all();
  
  return json({ ok: true, jobs: res.results || [] });
}

export async function ackArchive(env: Env, request: Request, auth: any) {
  await ensureStatusSchema(env);
  const body = await readJson(request);
  const jobId = body.jobId;
  const now = Date.now();
  
  if (!jobId) return badRequest('jobId required');

  await env.DB.prepare(
    'UPDATE status_archive_jobs SET state = \'acked\', acked_at = ? WHERE id = ? AND owner_id = ?'
  ).bind(now, jobId, auth.userId).run();

  return json({ ok: true });
}

export async function getStory(env: Env, url: URL, auth: any, statusId: string) {
  await ensureStatusSchema(env);
  const row = await env.DB.prepare(
    'SELECT * FROM statuses WHERE id = ? AND (expires_at > ? OR owner_id = ?) LIMIT 1'
  ).bind(statusId, Date.now(), auth.userId).first<any>();
  if (!row) return json({ ok: false, error: 'not_found' }, { status: 404 });
  const user = await userById(env, String(row.owner_id || ''));
  const viewsRes = await env.DB.prepare(
    'SELECT status_id AS statusId, viewer_id AS userId, viewed_at AS timestamp FROM status_views WHERE status_id = ?'
  ).bind(statusId).all().catch(() => ({ results: [] as any[] }));
  return json({ ok: true, story: legacyStatus(row, viewsRes.results || [], user) });
}

export async function getStoryAnalytics(env: Env, url: URL, auth: any, statusId: string) {
  await ensureStatusSchema(env);
  const owner = await env.DB.prepare('SELECT owner_id FROM statuses WHERE id = ? LIMIT 1')
    .bind(statusId).first<any>();
  if (!owner) return json({ ok: false, error: 'not_found' }, { status: 404 });
  if (owner.owner_id !== auth.userId) return json({ ok: false, error: 'forbidden' }, { status: 403 });
  const [views, reactions, comments] = await Promise.all([
    env.DB.prepare('SELECT * FROM status_views WHERE status_id = ? ORDER BY viewed_at DESC').bind(statusId).all(),
    env.DB.prepare('SELECT * FROM status_reactions WHERE status_id = ? AND (deleted_at IS NULL OR deleted_at = 0) ORDER BY reacted_at DESC').bind(statusId).all(),
    env.DB.prepare('SELECT * FROM status_replies WHERE status_id = ? AND (deleted_at IS NULL OR deleted_at = 0) ORDER BY sent_at DESC').bind(statusId).all(),
  ]);
  return json({ ok: true, views: views.results || [], reactions: reactions.results || [], comments: comments.results || [] });
}

export async function deleteStoryReaction(env: Env, request: Request, auth: any, reactionId: string) {
  await ensureStatusSchema(env);
  await env.DB.prepare('UPDATE status_reactions SET deleted_at = ? WHERE id = ? AND reactor_id = ?')
    .bind(Date.now(), reactionId, auth.userId).run();
  return json({ ok: true });
}

export async function deleteStoryComment(env: Env, request: Request, auth: any, commentId: string) {
  await ensureStatusSchema(env);
  await env.DB.prepare('UPDATE status_replies SET deleted_at = ? WHERE id = ? AND sender_id = ?')
    .bind(Date.now(), commentId, auth.userId).run();
  return json({ ok: true });
}

export async function getStoryMedia(env: Env, key: string) {
  await ensureStatusSchema(env);
  const object = await env.TEMP_FILES.get(decodeURIComponent(key));
  if (!object) return new Response('not_found', { status: 404 });
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set('cache-control', 'private, max-age=300');
  return new Response(object.body, { headers });
}

export async function updateStoryBackup(env: Env, request: Request, auth: any, statusId: string, state: 'started' | 'complete' | 'failed') {
  await ensureStatusSchema(env);
  const body = await readJson(request);
  const now = Date.now();
  if (state === 'started') {
    await env.DB.prepare('UPDATE statuses SET backup_state = ?, backup_started_at = ? WHERE id = ? AND owner_id = ?')
      .bind('started', now, statusId, auth.userId).run();
  } else if (state === 'complete') {
    await env.DB.prepare('UPDATE statuses SET backup_state = ?, backup_completed_at = ?, drive_file_id = ? WHERE id = ? AND owner_id = ?')
      .bind('complete', now, body.driveFileId || null, statusId, auth.userId).run();
  } else {
    await env.DB.prepare('UPDATE statuses SET backup_state = ?, backup_error = ? WHERE id = ? AND owner_id = ?')
      .bind('failed', String(body.error || 'backup_failed'), statusId, auth.userId).run();
  }
  return json({ ok: true });
}
