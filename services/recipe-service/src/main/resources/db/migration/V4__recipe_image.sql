CREATE TABLE recipe_image (
  recipe_id UUID PRIMARY KEY REFERENCES recipe(id) ON DELETE CASCADE,
  mime_type VARCHAR(16) NOT NULL,
  data BYTEA NOT NULL
);
