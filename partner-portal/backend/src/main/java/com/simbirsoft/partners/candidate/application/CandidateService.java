package com.simbirsoft.partners.candidate.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simbirsoft.partners.access.application.*;
import com.simbirsoft.partners.candidate.domain.*;
import com.simbirsoft.partners.document.application.*;
import com.simbirsoft.partners.finance.application.CandidateRates;
import com.simbirsoft.partners.finance.domain.CandidateHourlyRate;
import com.simbirsoft.partners.shared.api.FieldValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Profile("!auth-test")
public class CandidateService {
    private final CandidateProfileStore profiles;
    private final CandidateCreationStore creations;
    private final CandidateRates rates;
    private final CandidateCv cvs;
    private final DocumentValidationService documents;
    private final StaffRoleLookup roles;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    public CandidateService(CandidateProfileStore profiles,CandidateCreationStore creations,CandidateRates rates,
            CandidateCv cvs,DocumentValidationService documents,StaffRoleLookup roles,ObjectMapper json,PlatformTransactionManager manager) {
        this.profiles=profiles;this.creations=creations;this.rates=rates;this.cvs=cvs;this.documents=documents;
        this.roles=roles;this.json=json;this.transaction=new TransactionTemplate(manager);
    }
    private void authorize(StaffPrincipal actor) {
        if(actor==null || actor.username()==null || actor.username().isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if(!roles.hasPermission(actor.username(),"candidate.manage")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    @Transactional(readOnly=true)
    public CandidatePage page(StaffPrincipal actor,String q,CandidateAvailability availability,int page,int size) {
        authorize(actor);
        if(page<0 || size<1 || size>100 || q!=null && q.length()>200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var found=profiles.page(q==null?"":q.trim(),availability,page,size);
        var values=rates.findAll(found.items().stream().map(CandidateProfile::id).collect(Collectors.toSet()));
        return new CandidatePage(found.items().stream().map(p->new CandidateDetails(p,values.get(p.id()),null)).toList(),page,size,found.total());
    }
    @Transactional(readOnly=true)
    public CandidateDetails get(StaffPrincipal actor,UUID id) { authorize(actor);return details(required(id)); }
    public CandidateDetails create(StaffPrincipal actor,UUID key,CandidateCommand command,String name,String type,byte[] bytes) {
        authorize(actor);if(key==null || command==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        UUID id=UUID.randomUUID();Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        CandidateProfile p=normalized(id,"C-"+id.toString().replace("-",""),command,now,now,actor.username(),actor.username(),0);
        CandidateHourlyRate rate=validatedRate(id,command,actor.username(),now);
        if(bytes==null || bytes.length==0) throw new FieldValidationException("VALIDATION_ERROR","cv","CV is required");
        if(bytes.length>20*1024*1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        String fingerprint=fingerprint(p,rate,name,type,bytes);
        return atomic(()->{
            var reservation=creations.reserve(actor.username(),key,fingerprint,id);
            if(!reservation.fingerprint().equals(fingerprint)) throw new ResponseStatusException(HttpStatus.CONFLICT);
            if(!reservation.created()) return details(required(reservation.candidateId()));
            PreparedDocument file;
            try { file=documents.prepare(name,type,bytes); }
            catch(ResponseStatusException rejected) {
                int status=rejected.getStatusCode().value();
                // This branch only runs for a newly reserved operation: rollback proves no candidate was created.
                if(status==400 || status==413 || status==422)
                    throw new FieldValidationException(HttpStatus.valueOf(status),"CANDIDATE_CREATE_REJECTED","cv","CV was not accepted");
                throw rejected;
            }
            var saved=profiles.saveAndFlush(p);
            var savedRate=rates.set(id,rate.amount(),rate.currency(),actor.username(),now);
            var cv=cvs.store(id,file,actor.username(),now);
            return new CandidateDetails(saved,savedRate,cv);
        });
    }
    public CandidateDetails update(StaffPrincipal actor,UUID id,long version,CandidateCommand command) {
        authorize(actor);
        return atomic(()->{
            var current=expected(id,version);Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            var changed=normalized(id,current.code(),command,current.createdAt(),now,current.createdBy(),actor.username(),version);
            var rate=validatedRate(id,command,actor.username(),now);
            var saved=profiles.saveAndFlush(changed);
            var savedRate=rates.set(id,rate.amount(),rate.currency(),actor.username(),now);
            return new CandidateDetails(saved,savedRate,cvs.current(id).orElseThrow(()->new IllegalStateException("Candidate CV is missing")));
        });
    }
    public CandidateDetails replaceCv(StaffPrincipal actor,UUID id,long version,String name,String type,byte[] bytes) {
        authorize(actor);
        return atomic(()->{
            var current=expected(id,version);
            var file=documents.prepare(name,type,bytes);Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            var changed=new CandidateProfile(id,current.code(),current.role(),current.grade(),current.skills(),current.experienceMonths(),
                current.description(),current.availability(),current.externalKey(),current.createdAt(),now,current.createdBy(),actor.username(),version);
            var saved=profiles.saveAndFlush(changed);
            var cv=cvs.store(id,file,actor.username(),now);
            return new CandidateDetails(saved,rates.findAll(Set.of(id)).get(id),cv);
        });
    }
    @Transactional(readOnly=true)
    public CandidateCv.Download downloadCv(StaffPrincipal actor,UUID id) {authorize(actor);required(id);return cvs.download(id);}
    private CandidateProfile required(UUID id) {return profiles.find(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    private CandidateProfile expected(UUID id,long version) {
        if(version<0) throw new FieldValidationException("VALIDATION_ERROR","expectedVersion","Version is required");
        var p=required(id);if(p.version()!=version) throw new ResponseStatusException(HttpStatus.CONFLICT);return p;
    }
    private CandidateDetails details(CandidateProfile p) {
        return new CandidateDetails(p,rates.findAll(Set.of(p.id())).get(p.id()),cvs.current(p.id()).orElseThrow(()->new IllegalStateException("Candidate CV is missing")));
    }
    private CandidateProfile normalized(UUID id,String code,CandidateCommand c,Instant created,Instant changed,String creator,String actor,long version) {
        if(c==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        try {return new CandidateProfile(id,code,c.role(),c.grade(),c.skills(),c.experienceMonths(),c.description(),c.availability(),c.externalKey(),created,changed,creator,actor,version);}
        catch(IllegalArgumentException e) {throw new FieldValidationException("VALIDATION_ERROR",e.getMessage(),"Invalid value");}
    }
    private CandidateHourlyRate validatedRate(UUID id,CandidateCommand c,String actor,Instant now) {
        try {return new CandidateHourlyRate(id,c.amount(),c.currency(),now,actor,0);}
        catch(IllegalArgumentException e) {throw new FieldValidationException("VALIDATION_ERROR",e.getMessage(),"Invalid value");}
    }
    private String fingerprint(CandidateProfile p,CandidateHourlyRate r,String name,String type,byte[] bytes) {
        try {
            var c=new CandidateCommand(p.role(),p.grade(),p.skills(),p.experienceMonths(),p.description(),p.availability(),p.externalKey(),r.amount(),r.currency());
            var digest=MessageDigest.getInstance("SHA-256");
            digest.update(json.writeValueAsBytes(c));
            digest.update(json.writeValueAsBytes(List.of(name==null?"":name,type==null?"":type)));
            digest.update(bytes);return HexFormat.of().formatHex(digest.digest());
        } catch(Exception e) {throw new IllegalStateException("Cannot fingerprint candidate creation",e);}
    }
    private <T> T atomic(Supplier<T> work) {
        try {return transaction.execute(s->work.get());}
        catch(DataIntegrityViolationException e) {
            String detail=e.getMostSpecificCause().getMessage();
            if(detail!=null && detail.contains("uq_candidate_profile_external_key"))
                throw new FieldValidationException(HttpStatus.CONFLICT,"EXTERNAL_KEY_CONFLICT","externalKey","External key is already linked");
            throw e;
        }
    }
}
