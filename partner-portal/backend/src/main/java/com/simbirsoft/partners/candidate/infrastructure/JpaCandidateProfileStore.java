package com.simbirsoft.partners.candidate.infrastructure;
import com.simbirsoft.partners.candidate.application.CandidateProfileStore;
import com.simbirsoft.partners.candidate.domain.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
@Repository @Profile("!auth-test")
public class JpaCandidateProfileStore implements CandidateProfileStore {
    private final CandidateProfileJpaRepository rows;
    public JpaCandidateProfileStore(CandidateProfileJpaRepository rows) { this.rows=rows; }
    @Override @Transactional(readOnly=true) public Optional<CandidateProfile> find(UUID id) {return rows.findById(id).map(CandidateProfileRecord::value);}
    @Override @Transactional public CandidateProfile saveAndFlush(CandidateProfile p) {
        var row=rows.findById(p.id()).orElseGet(CandidateProfileRecord::new);
        if(row.version!=null && row.version!=p.version()) throw new ObjectOptimisticLockingFailureException(CandidateProfile.class,p.id());
        row.copy(p);return rows.saveAndFlush(row).value();
    }
    @Override @Transactional(readOnly=true) public Page page(String q,CandidateAvailability availability,int page,int size) {
        Specification<CandidateProfileRecord> spec=(root,query,cb)-> {
            var filter=availability==null?cb.conjunction():cb.equal(root.get("availability"),availability);
            if(q==null || q.isBlank()) return filter;
            String needle="%"+q.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
            var combined=cb.or(cb.like(cb.lower(root.get("code")),needle,'\\'),
                cb.like(cb.lower(root.get("role")),needle,'\\'),cb.like(cb.lower(root.get("description")),needle,'\\'),
                cb.like(cb.lower(root.get("externalKey")),needle,'\\'),
                cb.like(cb.lower(((org.hibernate.query.criteria.JpaExpression<?>)root.get("skills")).cast(String.class)),needle,'\\'));
            return cb.and(filter,combined);
        };
        var result=rows.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Order.desc("updatedAt"),Sort.Order.desc("id"))));
        return new Page(result.stream().map(CandidateProfileRecord::value).toList(),page,size,result.getTotalElements());
    }
}
