package com.simbirsoft.partners.finance.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
interface CandidateHourlyRateJpaRepository extends JpaRepository<CandidateHourlyRateRecord,UUID> {}
