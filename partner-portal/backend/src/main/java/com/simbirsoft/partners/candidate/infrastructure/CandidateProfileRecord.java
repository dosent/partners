package com.simbirsoft.partners.candidate.infrastructure;
import com.simbirsoft.partners.candidate.domain.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="candidate_profile")
class CandidateProfileRecord {
    @Id UUID id;
    @Column(nullable=false,length=40,unique=true,updatable=false) String code;
    @Column(nullable=false,length=200) String role;
    @Column(length=80) String grade;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") List<String> skills;
    @Column(name="experience_months",nullable=false) int experienceMonths;
    @Column(nullable=false,length=10000) String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) CandidateAvailability availability;
    @Column(name="external_key",length=200,unique=true) String externalKey;
    @Column(name="created_at",nullable=false,updatable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    @Column(name="created_by",nullable=false,updatable=false) String createdBy;
    @Column(name="updated_by",nullable=false) String updatedBy;
    @Version Long version;
    void copy(CandidateProfile p) {
        id=p.id();code=p.code();role=p.role();grade=p.grade();skills=p.skills();experienceMonths=p.experienceMonths();
        description=p.description();availability=p.availability();externalKey=p.externalKey();createdAt=p.createdAt();
        updatedAt=p.updatedAt();createdBy=p.createdBy();updatedBy=p.updatedBy();
    }
    CandidateProfile value() { return new CandidateProfile(id,code,role,grade,skills,experienceMonths,description,
        availability,externalKey,createdAt,updatedAt,createdBy,updatedBy,version==null?0:version); }
}
