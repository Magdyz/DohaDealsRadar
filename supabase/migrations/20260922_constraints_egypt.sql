-- ============================================================================
-- EgyptDealRadar - constraints for the Egypt categories, governorates and
-- trust levels (2026-09-22)
-- ============================================================================

-- Categories: Egypt set (keep in sync with DealCategory.kt and _shared/deals.ts)
alter table public.deals drop constraint if exists category_check;
alter table public.deals add constraint category_check check (category in (
  'food_dining', 'groceries', 'electronics', 'shopping_fashion',
  'telecom', 'entertainment', 'home_services', 'other'
));

-- Governorates (27) + nationwide/online
alter table public.deals drop constraint if exists governorate_check;
alter table public.deals add constraint governorate_check check (governorate is null or governorate in (
  'all_egypt', 'cairo', 'giza', 'alexandria', 'qalyubia', 'sharqia', 'dakahlia', 'gharbia',
  'monufia', 'beheira', 'kafr_el_sheikh', 'damietta', 'port_said', 'ismailia', 'suez',
  'fayoum', 'beni_suef', 'minya', 'asyut', 'sohag', 'qena', 'luxor', 'aswan',
  'red_sea', 'new_valley', 'matrouh', 'north_sinai', 'south_sinai'
));

-- Trust levels: new -> regular -> trusted (flagged kept for manual use)
alter table public.users drop constraint if exists users_trust_level_check;
alter table public.users add constraint users_trust_level_check
  check (trust_level is null or trust_level in ('new', 'regular', 'trusted', 'flagged'));
