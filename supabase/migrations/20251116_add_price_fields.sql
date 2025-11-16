-- ========================================
-- Add price fields to deals table
-- ========================================
-- This migration adds optional price fields to support
-- displaying original and discounted prices for deals

-- Add original_price column (nullable, supports decimals like QR 1,995.99)
ALTER TABLE deals
ADD COLUMN IF NOT EXISTS original_price NUMERIC(10,2);

-- Add discounted_price column (nullable, supports decimals)
ALTER TABLE deals
ADD COLUMN IF NOT EXISTS discounted_price NUMERIC(10,2);

-- Add comments for documentation
COMMENT ON COLUMN deals.original_price IS 'Original price before discount (e.g., 2745.00 for QR 2,745)';
COMMENT ON COLUMN deals.discounted_price IS 'Discounted price (e.g., 1995.00 for QR 1,995)';

-- Add check constraint to ensure discounted price is less than original price when both exist
ALTER TABLE deals
ADD CONSTRAINT check_discounted_less_than_original
CHECK (
  (discounted_price IS NULL) OR
  (original_price IS NULL) OR
  (discounted_price < original_price)
);

-- Create index for price queries (optional, for future filtering/sorting by price)
CREATE INDEX IF NOT EXISTS idx_deals_prices ON deals(original_price, discounted_price);
