INSERT INTO candidate_creation (actor, idempotency_key, fingerprint, candidate_id)
VALUES (:actor, :key, :fingerprint, :candidateId)
ON CONFLICT (actor, idempotency_key) DO NOTHING
