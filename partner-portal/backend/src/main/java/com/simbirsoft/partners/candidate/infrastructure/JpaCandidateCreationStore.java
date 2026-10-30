package com.simbirsoft.partners.candidate.infrastructure;
import com.simbirsoft.partners.candidate.application.CandidateCreationStore;
import com.simbirsoft.partners.shared.persistence.QueryHelper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Repository @Profile("!auth-test")
public class JpaCandidateCreationStore implements CandidateCreationStore {
    private final QueryHelper queries;
    public JpaCandidateCreationStore(QueryHelper queries) {this.queries=queries;}
    @Override @Transactional public Reservation reserve(String actor,UUID key,String fingerprint,UUID id) {
        var p=new MapSqlParameterSource().addValue("actor",actor).addValue("key",key).addValue("fingerprint",fingerprint).addValue("candidateId",id);
        boolean created=queries.update(CandidateSql.RESERVE,p)==1;
        return queries.query(CandidateSql.FIND_CREATION,p,(r,n)->new Reservation(r.getObject("candidate_id",UUID.class),r.getString("fingerprint"),created)).getFirst();
    }
}
