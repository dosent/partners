package com.simbirsoft.partners.lead.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadJpaRepository extends JpaRepository<LeadRecord, UUID> {
    @Query("select l from LeadRecord l where (:status is null or l.status=:status) "
        + "and (:query='' or lower(l.companyName) like lower(concat('%',:query,'%')) "
        + "or lower(l.number) like lower(concat('%',:query,'%')) "
        + "or lower(l.requestSummary) like lower(concat('%',:query,'%')) "
        + "or lower(coalesce(l.domainOrTaxId,'')) like lower(concat('%',:query,'%'))) "
        + "order by l.updatedAt desc,l.id desc")
    List<LeadRecord> findStaffPage(@Param("status") String status, @Param("query") String query, Pageable pageable);
    @Query("select count(l) from LeadRecord l where (:status is null or l.status=:status) "
        + "and (:query='' or lower(l.companyName) like lower(concat('%',:query,'%')) "
        + "or lower(l.number) like lower(concat('%',:query,'%')) "
        + "or lower(l.requestSummary) like lower(concat('%',:query,'%')) "
        + "or lower(coalesce(l.domainOrTaxId,'')) like lower(concat('%',:query,'%')))")
    long countStaffMatching(@Param("status") String status, @Param("query") String query);
    @Query("select l from LeadRecord l where l.ownerProfileId=:owner and l.status in :statuses "
        + "and (lower(l.companyName) like lower(concat('%',:query,'%')) "
        + "or lower(l.requestSummary) like lower(concat('%',:query,'%')) or lower(coalesce(l.domainOrTaxId,'')) like lower(concat('%',:query,'%'))) "
        + "and l.updatedAt>=:from and l.updatedAt<=:to "
        + "and (:hasCursor=false or l.updatedAt<:before or (l.updatedAt=:before and l.id<:beforeId)) "
        + "order by l.updatedAt desc,l.id desc")
    List<LeadRecord> findPage(@Param("owner") UUID owner,@Param("statuses") java.util.Collection<String> statuses,@Param("query") String query,
        @Param("from") Instant from,@Param("to") Instant to,@Param("before") Instant before,@Param("beforeId") UUID beforeId,
        @Param("hasCursor") boolean hasCursor,Pageable pageable);
    @Query("select count(l) from LeadRecord l where l.ownerProfileId=:owner and l.status in :statuses "
        + "and (lower(l.companyName) like lower(concat('%',:query,'%')) "
        + "or lower(l.requestSummary) like lower(concat('%',:query,'%')) or lower(coalesce(l.domainOrTaxId,'')) like lower(concat('%',:query,'%'))) "
        + "and l.updatedAt>=:from and l.updatedAt<=:to")
    long countMatching(@Param("owner") UUID owner,@Param("statuses") java.util.Collection<String> statuses,@Param("query") String query,
        @Param("from") Instant from,@Param("to") Instant to);
    Optional<LeadRecord> findByIdAndOwnerProfileId(UUID id,UUID ownerProfileId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LeadRecord l where l.id=:id")
    Optional<LeadRecord> findForUpdateById(@Param("id") UUID id);
}
