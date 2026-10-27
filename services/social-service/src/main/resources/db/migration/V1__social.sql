CREATE TABLE publication (
  id UUID PRIMARY KEY,
  author_sub VARCHAR(100) NOT NULL,
  author_name VARCHAR(120) NOT NULL,
  recipe_id UUID NOT NULL,
  recipe_title VARCHAR(120) NOT NULL,
  caption VARCHAR(1000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX publication_recent_idx ON publication (created_at DESC, id DESC);
CREATE TABLE publication_like (
  publication_id UUID NOT NULL REFERENCES publication(id) ON DELETE CASCADE,
  user_sub VARCHAR(100) NOT NULL,
  PRIMARY KEY (publication_id, user_sub)
);
CREATE TABLE publication_favorite (
  publication_id UUID NOT NULL REFERENCES publication(id) ON DELETE CASCADE,
  user_sub VARCHAR(100) NOT NULL,
  PRIMARY KEY (publication_id, user_sub)
);
CREATE TABLE publication_comment (
  id UUID PRIMARY KEY,
  publication_id UUID NOT NULL REFERENCES publication(id) ON DELETE CASCADE,
  author_sub VARCHAR(100) NOT NULL,
  author_name VARCHAR(120) NOT NULL,
  body VARCHAR(1000) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX publication_comment_recent_idx ON publication_comment (publication_id, created_at DESC);
