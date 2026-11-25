-- ============================================================================
-- DocGuard X-Ray: Initial PostgreSQL 18 + pgvector + RLS Schema
-- Location: backend/src/main/resources/db/migration/V1__init.sql
-- ============================================================================

-- 1. Extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

-- 2. Tenants Table
CREATE TABLE tenants (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    monthly_tokens  BIGINT       NOT NULL DEFAULT 100000,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 3. API Keys Table (SHA-256 hashed keys for REST + /mcp auth)
CREATE TABLE api_keys (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    key_hash        VARCHAR(128) NOT NULL UNIQUE,
    label           VARCHAR(120) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_tenant_id ON api_keys(tenant_id);

-- 4. Documents Table (Supports /api/v1/documents & public /kb/[tenant]/[slug])
CREATE TABLE documents (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    version         INT          NOT NULL DEFAULT 1,
    slug            VARCHAR(160) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    content         TEXT         NOT NULL,
    content_hash    VARCHAR(64)  NOT NULL,
    is_public       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_documents_tenant_slug UNIQUE (tenant_id, slug)
);

CREATE INDEX idx_documents_tenant_id ON documents(tenant_id);
CREATE INDEX idx_documents_public_lookup ON documents(tenant_id, slug) WHERE is_public = TRUE;

-- 5. Usage Logs Table (Audits REST, SSE, and MCP calls)
CREATE TABLE usage_logs (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    source          VARCHAR(32)  NOT NULL DEFAULT 'WEB', -- WEB, MCP, API
    tokens_used     INT          NOT NULL DEFAULT 0,
    pii_redacted    INT          NOT NULL DEFAULT 0,
    trace_id        VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usage_logs_tenant_created ON usage_logs(tenant_id, created_at DESC);

-- 6. Spring AI PgVectorStore Table (768-dim gemini-embedding-001)
-- Note: Spring AI PgVectorStore typically reads metadata->>'tenant_id' and metadata->>'document_id'.
-- We also keep a top-level document_id FK with ON DELETE CASCADE for clean parent deletion.
CREATE TABLE vector_store (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    content         TEXT         NOT NULL,
    metadata        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    embedding       vector(768)  NOT NULL,
    document_id     UUID         REFERENCES documents(id) ON DELETE CASCADE
);

CREATE INDEX idx_vector_store_embedding_hnsw
    ON vector_store USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_vector_store_document_id
    ON vector_store(document_id);

CREATE INDEX idx_vector_store_meta_tenant
    ON vector_store ((metadata->>'tenant_id'));

-- 7. BEFORE INSERT Trigger: Auto-populate tenant_id from metadata if omitted,
--    and normalize metadata->>'tenant_id' consistently for RLS checks.
CREATE OR REPLACE FUNCTION fn_vector_store_before_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_current_tenant TEXT;
    v_meta_tenant    TEXT;
    v_doc_tenant     UUID;
BEGIN
    v_current_tenant := NULLIF(current_setting('app.current_tenant_id', TRUE), '');
    v_meta_tenant    := NULLIF(NEW.metadata->>'tenant_id', '');

    IF NEW.document_id IS NULL AND NULLIF(NEW.metadata->>'document_id', '') IS NOT NULL THEN
        NEW.document_id := (NEW.metadata->>'document_id')::uuid;
    END IF;

    -- If a document_id is supplied, verify/inherit its owning tenant_id
    IF NEW.document_id IS NOT NULL THEN
        SELECT d.tenant_id INTO v_doc_tenant
        FROM documents d
        WHERE d.id = NEW.document_id;

        IF v_doc_tenant IS NOT NULL THEN
            IF v_meta_tenant IS NULL THEN
                NEW.metadata := jsonb_set(COALESCE(NEW.metadata, '{}'::jsonb), '{tenant_id}', to_jsonb(v_doc_tenant::text), TRUE);
                v_meta_tenant := v_doc_tenant::text;
            ELSIF v_meta_tenant <> v_doc_tenant::text THEN
                RAISE EXCEPTION 'Vector metadata tenant_id (%) does not match document owner tenant_id (%)',
                    v_meta_tenant, v_doc_tenant
                    USING ERRCODE = '42501';
            END IF;
        END IF;
    END IF;

    -- If session tenant is set and metadata lacks tenant_id, stamp it automatically
    IF v_meta_tenant IS NULL AND v_current_tenant IS NOT NULL THEN
        NEW.metadata := jsonb_set(COALESCE(NEW.metadata, '{}'::jsonb), '{tenant_id}', to_jsonb(v_current_tenant), TRUE);
        v_meta_tenant := v_current_tenant;
    END IF;

    -- Enforce that a tenant_id exists
    IF v_meta_tenant IS NULL THEN
        RAISE EXCEPTION 'Missing tenant_id in vector_store metadata and app.current_tenant_id session setting'
            USING ERRCODE = '22023';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_vector_store_before_insert
    BEFORE INSERT ON vector_store
    FOR EACH ROW
    EXECUTE FUNCTION fn_vector_store_before_insert();

-- 8. Row-Level Security (RLS) with NULLIF-safe session lookup
--    NULLIF(..., '') prevents empty string '' from failing UUID/text comparisons
--    and ensures missing context never leaks rows.
ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents FORCE ROW LEVEL SECURITY;

ALTER TABLE usage_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_logs FORCE ROW LEVEL SECURITY;

ALTER TABLE vector_store ENABLE ROW LEVEL SECURITY;
ALTER TABLE vector_store FORCE ROW LEVEL SECURITY;

-- api_keys is not RLS since request will receive the tenant_id details

-- Documents policies:
-- A) Tenant isolation for authenticated session context
CREATE POLICY tenant_isolation_documents
    ON documents
    FOR ALL
    USING (
        NULLIF(current_setting('app.current_tenant_id', TRUE), '') IS NOT NULL
        AND tenant_id = NULLIF(current_setting('app.current_tenant_id', TRUE), '')::uuid
    )
    WITH CHECK (
        NULLIF(current_setting('app.current_tenant_id', TRUE), '') IS NOT NULL
        AND tenant_id = NULLIF(current_setting('app.current_tenant_id', TRUE), '')::uuid
    );

-- B) Public KB read policy for /api/v1/public/kb/{tenantSlug}/{docSlug}
CREATE POLICY public_kb_read
    ON documents
    FOR SELECT
    USING (is_public = TRUE);

-- Usage Logs policy
CREATE POLICY tenant_isolation_usage_logs
    ON usage_logs
    FOR ALL
    USING (
        NULLIF(current_setting('app.current_tenant_id', TRUE), '') IS NOT NULL
        AND tenant_id = NULLIF(current_setting('app.current_tenant_id', TRUE), '')::uuid
    )
    WITH CHECK (
        NULLIF(current_setting('app.current_tenant_id', TRUE), '') IS NOT NULL
        AND tenant_id = NULLIF(current_setting('app.current_tenant_id', TRUE), '')::uuid
    );

-- Vector Store policy (tenant isolated via metadata->>'tenant_id')
CREATE POLICY tenant_isolation_vector_store
    ON vector_store
    FOR ALL
    USING (
        NULLIF(current_setting('app.current_tenant_id', TRUE), '') IS NOT NULL
        AND metadata->>'tenant_id' = NULLIF(current_setting('app.current_tenant_id', TRUE), '')
    )
    WITH CHECK (
        NULLIF(current_setting('app.current_tenant_id', TRUE), '') IS NOT NULL
        AND metadata->>'tenant_id' = NULLIF(current_setting('app.current_tenant_id', TRUE), '')
    );
