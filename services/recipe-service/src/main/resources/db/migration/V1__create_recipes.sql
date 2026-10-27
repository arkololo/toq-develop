CREATE TABLE recipe (
  id UUID PRIMARY KEY,
  owner_sub VARCHAR(255) NOT NULL,
  title VARCHAR(120) NOT NULL,
  description VARCHAR(2000),
  base_servings INTEGER NOT NULL CHECK (base_servings > 0),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX recipe_owner_title_idx ON recipe(owner_sub, title);

CREATE TABLE recipe_ingredient (
  id UUID PRIMARY KEY,
  recipe_id UUID NOT NULL REFERENCES recipe(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  quantity NUMERIC(12,3) NOT NULL CHECK (quantity > 0),
  unit VARCHAR(20) NOT NULL,
  position INTEGER NOT NULL CHECK (position > 0),
  UNIQUE(recipe_id, position)
);

CREATE TABLE recipe_step (
  id UUID PRIMARY KEY,
  recipe_id UUID NOT NULL REFERENCES recipe(id) ON DELETE CASCADE,
  instruction VARCHAR(2000) NOT NULL,
  position INTEGER NOT NULL CHECK (position > 0),
  UNIQUE(recipe_id, position)
);
