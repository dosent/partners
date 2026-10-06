package com.simbirsoft.partners.staffing.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.staffing.application.StaffingRequestStore;
import com.simbirsoft.partners.staffing.domain.StaffingRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!auth-test")
class JpaStaffingRequestStore implements StaffingRequestStore {
    private final StaffingRequestJpaRepository repository;
    private final ObjectMapper json;
    JpaStaffingRequestStore(StaffingRequestJpaRepository repository, ObjectMapper json) {
        this.repository=repository; this.json=json;
    }
    @Override @Transactional public StaffingRequest save(StaffingRequest request) {
        return repository.save(StaffingRequestRecord.from(request, json)).toDomain(json);
    }
    @Override @Transactional public StaffingRequest saveAndFlush(StaffingRequest request) {
        return repository.saveAndFlush(StaffingRequestRecord.from(request, json)).toDomain(json);
    }
    @Override @Transactional(readOnly=true) public Optional<StaffingRequest> findAny(UUID id) {
        return repository.findById(id).map(r -> r.toDomain(json));
    }
    @Override @Transactional(readOnly=true) public StaffPage findStaffPage(int page, int size) {
        var result = repository.findAll(PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new StaffPage(result.stream().map(r -> r.toDomain(json)).toList(), page, size, result.getTotalElements());
    }
    @Override @Transactional(readOnly=true) public Optional<StaffingRequest> findOwned(UUID id, UUID owner) {
        return repository.findByIdAndOwnerProfileId(id, owner).map(r -> r.toDomain(json));
    }
    @Override @Transactional(readOnly=true) public List<StaffingRequest> findOwnedPage(UUID owner, int page, int size) {
        return repository.findByOwnerProfileIdOrderByCreatedAtDesc(owner, PageRequest.of(page, size))
                .stream().map(r -> r.toDomain(json)).toList();
    }
    @Override @Transactional(readOnly=true) public Optional<StaffingRequest> findByIdempotencyKey(UUID owner, UUID key) {
        return repository.findByOwnerProfileIdAndIdempotencyKey(owner, key).map(r -> r.toDomain(json));
    }
}
