CREATE TABLE henkilo (
    oid VARCHAR PRIMARY KEY
);

CREATE TABLE henkilo_import (
    id INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    manifest_etag VARCHAR NOT NULL,
    row_count BIGINT NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL
);
