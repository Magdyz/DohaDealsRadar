// ============================================================================
// get_stats (moderator/admin)
// Anonymous, aggregate numbers computed from our own database - replaces the
// third-party analytics SDK. No per-user tracking involved.
// ============================================================================
import { admin, requireRole } from "../_shared/auth.ts";
import { handler, ok } from "../_shared/http.ts";

async function count(table: string, build: (q: any) => any): Promise<number> {
  const { count, error } = await build(admin().from(table).select("*", { count: "exact", head: true }));
  if (error) console.error(`stats ${table}:`, error.message);
  return count ?? 0;
}

Deno.serve(handler(async (req) => {
  await requireRole(req, ["moderator", "admin"]);
  const day = new Date(Date.now() - 86400_000).toISOString();
  const week = new Date(Date.now() - 7 * 86400_000).toISOString();
  const now = new Date().toISOString();

  const live = (q: any) => q.eq("status", "approved").eq("is_archived", false).is("deleted_at", null);

  const [liveDeals, pending, hidden, posted24h, posted7d, approved7d, rejected7d, users, newUsers7d, votes7d, reportsOpen] =
    await Promise.all([
      count("deals", (q) => live(q).or(`expires_at.is.null,expires_at.gt.${now}`)),
      count("deals", (q) => q.eq("status", "pending").is("deleted_at", null)),
      count("deals", (q) => q.eq("status", "hidden").is("deleted_at", null)),
      count("deals", (q) => q.gte("created_at", day)),
      count("deals", (q) => q.gte("created_at", week)),
      count("deals", (q) => q.eq("status", "approved").gte("created_at", week)),
      count("deals", (q) => q.eq("status", "rejected").gte("created_at", week)),
      count("users", (q) => q),
      count("users", (q) => q.gte("created_at", week)),
      count("votes", (q) => q.gte("created_at", week)),
      count("reports", (q) => q),
    ]);

  const { data: recent } = await admin().from("deals")
    .select("category, governorate")
    .gte("created_at", week)
    .limit(5000);
  const tally = (key: "category" | "governorate") => {
    const m = new Map<string, number>();
    for (const r of recent ?? []) {
      const k = (r as any)[key] ?? "unknown";
      m.set(k, (m.get(k) ?? 0) + 1);
    }
    return [...m.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8).map(([name, count]) => ({ name, count }));
  };

  return ok({
    data: {
      live_deals: liveDeals,
      pending_review: pending,
      hidden_by_reports: hidden,
      posted_24h: posted24h,
      posted_7d: posted7d,
      approved_7d: approved7d,
      rejected_7d: rejected7d,
      users_total: users,
      new_users_7d: newUsers7d,
      votes_7d: votes7d,
      open_reports: reportsOpen,
      top_categories_7d: tally("category"),
      top_governorates_7d: tally("governorate"),
      generated_at: now,
    },
  });
}));
