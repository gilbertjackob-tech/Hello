const fs = require('fs');
const content = \import type { Env } from './index';

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

export async function getStatusFeed(env: Env, url: URL, auth: any) {
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
    reactionSummary: JSON.parse(s.reaction_summary || '{}')
  }));

  return json({ ok: true, feed });
}

export async function createStatus(env: Env, request: Request, auth: any) {
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
    'INSERT INTO status_archive_jobs (id, status_id, owner_id, state, created_at) VALUES (?, ?, ?, \\'pending\\', ?)'
  ).bind(jobId, id, auth.userId, now).run();

  return json({ ok: true, id });
}

export async function uploadStatusMedia(env: Env, request: Request, auth: any) {
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
    'INSERT INTO status_media (id, status_id, r2_key, media_type, expires_at) VALUES (?, \\'pending\\', ?, ?, ?)'
  ).bind(mediaId, key, file.type, expiresAt).run();

  return json({ ok: true, id: mediaId, key });
}

export async function viewStatus(env: Env, request: Request, auth: any, statusId: string) {
  const now = Date.now();
  const viewId = randomId('view');
  await env.DB.prepare(
    'INSERT INTO status_views (id, status_id, viewer_id, viewed_at) VALUES (?, ?, ?, ?)'
  ).bind(viewId, statusId, auth.userId, now).run();
  
  await env.DB.prepare(
    'UPDATE statuses SET view_count = view_count + 1 WHERE id = ?'
  ).bind(statusId).run();

  return json({ ok: true });
}

export async function reactStatus(env: Env, request: Request, auth: any, statusId: string) {
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
  const body = await readJson(request);
  const text = body.text || '';
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
  await env.DB.prepare('UPDATE statuses SET expires_at = 0 WHERE id = ? AND owner_id = ?')
    .bind(statusId, auth.userId).run();
  return json({ ok: true });
}

export async function getArchivePending(env: Env, url: URL, auth: any) {
  const res = await env.DB.prepare(
    'SELECT * FROM status_archive_jobs WHERE state = \\'pending\\' AND owner_id = ?'
  ).bind(auth.userId).all();
  
  return json({ ok: true, jobs: res.results || [] });
}

export async function ackArchive(env: Env, request: Request, auth: any) {
  const body = await readJson(request);
  const jobId = body.jobId;
  const now = Date.now();
  
  if (!jobId) return badRequest('jobId required');

  await env.DB.prepare(
    'UPDATE status_archive_jobs SET state = \\'acked\\', acked_at = ? WHERE id = ? AND owner_id = ?'
  ).bind(now, jobId, auth.userId).run();

  return json({ ok: true });
}
\
fs.writeFileSync('apps/cloudflare/chat-worker/src/status.ts', content, 'utf8');
