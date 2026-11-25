-- ============================================================================
-- DocGuard X-Ray: Idempotent Seed Data
-- Location: backend/src/main/resources/db/migration/V1_1__seed.sql
-- ============================================================================

BEGIN;

-- 1. Seed Tenants (deterministic UUIDs for local dev and tests)
INSERT INTO tenants (id, slug, name, monthly_tokens, active)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'acme-fintech', 'Acme FinTech Corp',    250000, TRUE),
    ('22222222-2222-2222-2222-222222222222', 'global-hr',    'Global HR People Ops', 250000, TRUE)
ON CONFLICT (slug) DO NOTHING;

-- 2. Seed API Keys (SHA-256 hex digests for demo keys)
--   demo-fintech-key -> encode(digest('demo-fintech-key', 'sha256'), 'hex')
--   demo-hr-key      -> encode(digest('demo-hr-key', 'sha256'), 'hex')
INSERT INTO api_keys (id, tenant_id, key_hash, label, active)
VALUES
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        '11111111-1111-1111-1111-111111111111',
        encode(digest('demo-fintech-key', 'sha256'), 'hex'),
        'Acme FinTech Demo Key',
        TRUE
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2',
        '22222222-2222-2222-2222-222222222222',
        encode(digest('demo-hr-key', 'sha256'), 'hex'),
        'Global HR Demo Key',
        TRUE
    )
ON CONFLICT (key_hash) DO NOTHING;

-- 3. Seed Public KB Articles per Tenant (with SET LOCAL app.current_tenant_id for RLS WITH CHECK)
SET LOCAL app.current_tenant_id = '11111111-1111-1111-1111-111111111111';

INSERT INTO documents (id, tenant_id, slug, title, content, content_hash, is_public)
VALUES (
    'cccccccc-cccc-cccc-cccc-ccccccccccc1',
    '11111111-1111-1111-1111-111111111111',
    'refund-policy',
    'Chargeback & Refund SLA Policy',
    E'# Chargeback & Refund SLA Policy\n\nEligible card reversals are reviewed within **5 business days**. Never paste raw cardholder account numbers, emails, or phone numbers into prompts; DocGuard redacts PII automatically prior to model inference.',
    encode(digest(
        E'# Chargeback & Refund SLA Policy\n\nEligible card reversals are reviewed within **5 business days**. Never paste raw cardholder account numbers, emails, or phone numbers into prompts; DocGuard redacts PII automatically prior to model inference.',
        'sha256'
    ), 'hex'),
    TRUE
)
ON CONFLICT (tenant_id, slug) DO NOTHING;

SET LOCAL app.current_tenant_id = '22222222-2222-2222-2222-222222222222';

INSERT INTO documents (id, tenant_id, slug, title, content, content_hash, is_public)
VALUES (
    'cccccccc-cccc-cccc-cccc-ccccccccccc2',
    '22222222-2222-2222-2222-222222222222',
    'remote-onboarding-guide',
    'Global Remote Onboarding Checklist',
    E'# Global Remote Onboarding Checklist\n\nNew hires complete security orientation, laptop provisioning, and payroll tax setup in Week 1. Employee IDs and personal contact details are masked before AI knowledge queries.',
    encode(digest(
        E'# Global Remote Onboarding Checklist\n\nNew hires complete security orientation, laptop provisioning, and payroll tax setup in Week 1. Employee IDs and personal contact details are masked before AI knowledge queries.',
        'sha256'
    ), 'hex'),
    TRUE
)
ON CONFLICT (tenant_id, slug) DO NOTHING;

COMMIT;