CREATE TABLE publication_image (
  publication_id UUID PRIMARY KEY REFERENCES publication(id) ON DELETE CASCADE,
  mime_type VARCHAR(16) NOT NULL,
  data BYTEA NOT NULL
);
