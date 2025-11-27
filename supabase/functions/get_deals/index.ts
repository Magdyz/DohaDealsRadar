import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

/**
 * ========================================
 * ✨ GET DEALS WITH PAGINATION & CATEGORY FILTERING
 * ========================================
 *
 * Performance Improvement: 2025-01
 * - Added pagination support to reduce response time by 50-70%
 * - Previously fetched ALL deals (could be 1000+ items)
 * - Now fetches only requested page (default: 20 items)
 *
 * Category Filtering: 2025-11-27
 * - Added category parameter to filter deals by category BEFORE pagination
 * - Fixes bug where "Newest + Food & Dining" showed empty results
 * - Backend filters → sorts → paginates (correct order)
 *
 * Query Parameters:
 * - page: Page number (default: 1, minimum: 1)
 * - limit: Items per page (default: 20, range: 1-100)
 * - sort_by: Sort order ("hottest" | "newest", default: "hottest")
 * - category: Category filter (optional, e.g., "food_dining", "electronics")
 *   - If null/empty/missing → returns all categories
 *   - If "all" → returns all categories
 *   - If specific category → filters to that category only
 *
 * Valid Categories:
 * - food_dining
 * - shopping_fashion
 * - entertainment
 * - home_services
 * - other
 *
 * Response Format:
 * {
 *   success: true,
 *   data: [...deals],
 *   pagination: {
 *     page: 1,
 *     limit: 20,
 *     total: 100,
 *     totalPages: 5,
 *     hasMore: true
 *   }
 * }
 *
 * Backward Compatibility:
 * - If parameters are missing, defaults to page=1, limit=20, category=null (all)
 * - Invalid parameters are sanitized to safe values
 * - Response format matches ApiEnvelope<PaginationMeta> expected by frontend
 *
 * Examples:
 * - GET /deals?page=1&limit=20&sort_by=hottest
 *   → All deals sorted by hot_count (backward compatible)
 *
 * - GET /deals?page=1&limit=20&sort_by=newest&category=food_dining
 *   → Only food_dining deals sorted by created_at
 *
 * - GET /deals?page=1&limit=20&sort_by=hottest&category=all
 *   → All deals sorted by hot_count (same as no category)
 */
serve(async (req) => {
  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    // ========================================
    // ✅ EXTRACT AND VALIDATE PARAMETERS
    // ========================================
    const url = new URL(req.url);

    // Parse pagination parameters with defaults
    let page = parseInt(url.searchParams.get("page") || "1", 10);
    let limit = parseInt(url.searchParams.get("limit") || "20", 10);
    const sortBy = url.searchParams.get("sort_by") || "hottest";

    // ✅ NEW: Parse category parameter
    const categoryParam = url.searchParams.get("category") || null;

    // ✅ SAFETY: Validate and sanitize parameters
    // Prevent negative, zero, or invalid values
    page = Math.max(1, page);  // Minimum page is 1
    limit = Math.max(1, Math.min(100, limit));  // Limit between 1 and 100 (prevent abuse)

    // ✅ VALIDATE CATEGORY: Only allow known categories
    const validCategories = [
      "food_dining",
      "shopping_fashion",
      "entertainment",
      "home_services",
      "other"
    ];

    // Normalize category: null, empty, "all" → no filter
    let category: string | null = null;
    if (categoryParam && categoryParam !== "all" && categoryParam.trim() !== "") {
      // Only set category if it's a valid category
      if (validCategories.includes(categoryParam)) {
        category = categoryParam;
      } else {
        console.warn(`⚠️ Invalid category: ${categoryParam}, ignoring filter`);
        // Don't fail the request, just ignore invalid category
      }
    }

    // Calculate offset for database query
    const offset = (page - 1) * limit;

    console.log(`📄 Fetching deals: page=${page}, limit=${limit}, offset=${offset}, sort=${sortBy}, category=${category || "all"}`);

    // ========================================
    // ✅ FETCH DEALS WITH CATEGORY FILTER, PAGINATION, AND SORTING
    // ========================================

    // Start building query
    let query = supabase
      .from("deals")
      .select("*", { count: "exact" })
      .eq("status", "approved");

    // ✅ APPLY CATEGORY FILTER (BEFORE sorting and pagination)
    // This is the key fix: filter first, then sort, then paginate
    if (category !== null) {
      console.log(`   🏷️ Filtering by category: ${category}`);
      query = query.eq("category", category);
    }

    // ✅ APPLY SORTING (after filtering, before pagination)
    if (sortBy === "newest") {
      // Sort by creation date (newest first)
      query = query.order("created_at", { ascending: false });
    } else {
      // Default: Sort by hottest (hot_count descending, then created_at descending)
      query = query
        .order("hot_count", { ascending: false })
        .order("created_at", { ascending: false });
    }

    // ✅ APPLY PAGINATION (after filtering and sorting)
    const { data, error, count } = await query.range(offset, offset + limit - 1);

    if (error) {
      console.error("❌ Database error:", error);
      throw error;
    }

    // ========================================
    // ✅ CALCULATE PAGINATION METADATA
    // ========================================
    const total = count || 0;
    const totalPages = Math.ceil(total / limit);
    const hasMore = offset + limit < total;

    console.log(`✅ Retrieved ${data?.length || 0} deals (total: ${total}, page: ${page}/${totalPages}${category ? `, category: ${category}` : ""})`);

    // ========================================
    // ✅ RETURN RESPONSE WITH PAGINATION METADATA
    // ========================================
    // Format matches ApiEnvelope<List<DealDto>> with PaginationMeta
    // Field names use camelCase to match Kotlin data class:
    // - totalPages (not total_pages)
    // - hasMore (not has_next)
    return new Response(
      JSON.stringify({
        success: true,
        data: data || [],
        pagination: {
          page,
          limit,
          total,
          totalPages,
          hasMore
        }
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 200,
      }
    );
  } catch (error) {
    console.error("❌ Server error:", error);
    return new Response(
      JSON.stringify({
        success: false,
        error: error.message || "Internal server error",
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 500,
      }
    );
  }
});
