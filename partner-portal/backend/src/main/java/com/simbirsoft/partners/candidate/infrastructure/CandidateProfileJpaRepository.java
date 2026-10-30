package com.simbirsoft.partners.candidate.infrastructure;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
interface CandidateProfileJpaRepository extends JpaRepository<CandidateProfileRecord,UUID>,JpaSpecificationExecutor<CandidateProfileRecord> {}
