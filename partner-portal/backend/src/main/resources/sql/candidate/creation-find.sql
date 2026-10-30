SELECT candidate_id, fingerprint FROM candidate_creation WHERE actor = :actor AND idempotency_key = :key
