ALTER TABLE recipe ADD COLUMN kcal_per_serving NUMERIC(12,3) CHECK (kcal_per_serving >= 0);
ALTER TABLE recipe ADD COLUMN protein_g_per_serving NUMERIC(12,3) CHECK (protein_g_per_serving >= 0);
ALTER TABLE recipe ADD COLUMN carbohydrate_g_per_serving NUMERIC(12,3) CHECK (carbohydrate_g_per_serving >= 0);
ALTER TABLE recipe ADD COLUMN fat_g_per_serving NUMERIC(12,3) CHECK (fat_g_per_serving >= 0);
ALTER TABLE recipe ADD COLUMN source_template_id VARCHAR(12);
