## Demo
multi-tenant, RLS, pgvector, spring ai, pii-safe, mcp, hybrid ssr+spa takeover for SEO

## Decisions
### backend

#### DB
Indexed 768-dimensional embeddings in PostgreSQL `pgvector` using HNSW to support low-latency similarity search
alongside frequent document re-indexing and cascading deletes

- all tenant-owned tables (`documents`, `vector_store`, `usage_logs`) enforce RLS
- connection-pool-safe RLS via AOP (`TenantRlsAspect`) and `@Transactional`
- use 768 dimensions (via Matryoshka Representation Learning, staying under HNSW's 2,000-dim cap). ~3KB per chunk estimates.

#### HNSW over IVFFlat
- spring's HNSW defaults to use cosine index. IVFFlat requires pre-existing data to compute clusters
- HNSW supports incremental inserts from empty. IVFFlat degrades on an empty growing table
- HNSW handles incremental updates smoothly, but heavy document updates/deletes can cause gradual recall drift.

### frontend
Use SSR to support SEO, then SPA takeover


## Needs improvement
document table houses the content for now,
should use a blobstore and extract the content from supported files (pdf, docx,..etc)