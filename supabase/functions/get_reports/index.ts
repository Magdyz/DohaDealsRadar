// ============================================================================
// get_reports (moderator/admin)
// Reports with deal context and reporter info. Reporter emails are masked
// (data minimization) - moderators see who reported, not their full address.
// ============================================================================
import { admin, requireRole } from "../_shared/auth.ts";
import { handler, ok, readJson } from "../_shared/http.ts";

function maskEmail(email: string | null | undefined): string | null {
  if (!email) return null;
  const [name, domain] = email.split("@");
  return `${name.slice(0, 1)}***@${domain ?? ""}`;
}

Deno.serve(handler(async (req) => {
  await requireRole(req, ["moderator", "admin"]);
  const body = await readJson(req);
  const page = Math.max(1, Number(body.page) || 1);
  const limit = Math.max(1, Math.min(50, Number(body.limit) || 20));
  const offset = (page - 1) * limit;

  const { data: reports, error } = await admin().from("reports")
    .select("id, deal_id, device_id, reason, note, created_at, reporter_user_id, deals!reports_deal_id_fkey!inner(title, image_url, category, status, posted_by)")
    .order("created_at", { ascending: false })
    .range(offset, offset + limit - 1);
  if (error) throw error;

  const reporterIds = [...new Set((reports ?? []).map((r: any) => r.reporter_user_id).filter(Boolean))];
  const { data: users } = reporterIds.length
    ? await admin().from("users").select("id, username, email, role, approved_deals_count").in("id", reporterIds)
    : { data: [] as any[] };
  const byId = new Map((users ?? []).map((u: any) => [u.id, u]));

  const data = (reports ?? []).map((r: any) => {
    const u = byId.get(r.reporter_user_id);
    return {
      id: r.id,
      deal_id: r.deal_id,
      device_id: null,
      reason: r.reason,
      note: r.note,
      created_at: r.created_at,
      deal_title: r.deals?.title,
      deal_image: r.deals?.image_url,
      deal_category: r.deals?.category,
      deal_status: r.deals?.status,
      deal_posted_by: r.deals?.posted_by,
      reporter_username: u?.username ?? null,
      reporter_email: maskEmail(u?.email),
      reporter_role: u?.role ?? null,
      reporter_approved_deals_count: u?.approved_deals_count ?? null,
    };
  });

  return ok({ data, count: data.length, pagination: { page, limit, hasMore: data.length === limit } });
}));
