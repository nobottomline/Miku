-- Miku Database Schema v1
-- Professional manga aggregator backend

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS manga (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL,
    url VARCHAR(2048) NOT NULL,
    title VARCHAR(512) NOT NULL,
    artist VARCHAR(512),
    author VARCHAR(512),
    description TEXT,
    genres TEXT NOT NULL DEFAULT '',
    status INTEGER NOT NULL DEFAULT 0,
    thumbnail_url VARCHAR(2048),
    initialized BOOLEAN NOT NULL DEFAULT false,
    last_updated TIMESTAMP WITH TIME ZONE,
    UNIQUE(source_id, url)
);

CREATE TABLE IF NOT EXISTS chapters (
    id BIGSERIAL PRIMARY KEY,
    manga_id BIGINT NOT NULL REFERENCES manga(id) ON DELETE CASCADE,
    source_id BIGINT NOT NULL,
    url VARCHAR(2048) NOT NULL,
    name VARCHAR(512) NOT NULL,
    chapter_number REAL NOT NULL DEFAULT -1,
    scanlator VARCHAR(256),
    date_upload TIMESTAMP WITH TIME ZONE,
    date_fetch TIMESTAMP WITH TIME ZONE,
    UNIQUE(manga_id, url)
);

CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    UNIQUE(user_id, name)
);

CREATE TABLE IF NOT EXISTS library (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    manga_id BIGINT NOT NULL REFERENCES manga(id) ON DELETE CASCADE,
    category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, manga_id)
);

CREATE TABLE IF NOT EXISTS read_status (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chapter_id BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    manga_id BIGINT NOT NULL REFERENCES manga(id) ON DELETE CASCADE,
    last_page_read INTEGER NOT NULL DEFAULT 0,
    read_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, chapter_id)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_manga_source_id ON manga(source_id);
CREATE INDEX IF NOT EXISTS idx_manga_title ON manga(title);
CREATE INDEX IF NOT EXISTS idx_chapters_manga_id ON chapters(manga_id);
CREATE INDEX IF NOT EXISTS idx_library_user_id ON library(user_id);
CREATE INDEX IF NOT EXISTS idx_categories_user_id ON categories(user_id);
CREATE INDEX IF NOT EXISTS idx_read_status_user_manga ON read_status(user_id, manga_id);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
