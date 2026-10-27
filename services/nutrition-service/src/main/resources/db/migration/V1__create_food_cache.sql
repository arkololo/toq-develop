CREATE TABLE food_cache (
  barcode VARCHAR(32) PRIMARY KEY,
  name VARCHAR(250) NOT NULL,
  kcal_100g NUMERIC(12,3),
  protein_g_100g NUMERIC(12,3),
  carbohydrate_g_100g NUMERIC(12,3),
  fat_g_100g NUMERIC(12,3),
  allergens VARCHAR(2000),
  labels VARCHAR(2000),
  fetched_at TIMESTAMP WITH TIME ZONE NOT NULL
);
