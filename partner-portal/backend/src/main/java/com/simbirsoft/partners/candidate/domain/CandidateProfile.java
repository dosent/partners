package com.simbirsoft.partners.candidate.domain;

import java.time.Instant;
import java.util.*;

public record CandidateProfile(UUID id, String code, String role, String grade, List<String> skills,
        int experienceMonths, String description, CandidateAvailability availability, String externalKey,
        Instant createdAt, Instant updatedAt, String createdBy, String updatedBy, long version) {
    public CandidateProfile {
        if (id == null) throw new IllegalArgumentException("id");
        code=text(code,40,"code"); role=text(role,200,"role");
        grade=grade==null || grade.isBlank()?null:text(grade,80,"grade");
        description=text(description,10000,"description");
        if(skills==null) throw new IllegalArgumentException("skills");
        var normalized=new LinkedHashMap<String,String>();
        for(var skill:skills) {
            if(skill==null || skill.isBlank()) continue;
            var value=text(skill,120,"skills"); normalized.putIfAbsent(value.toLowerCase(Locale.ROOT),value);
        }
        if(normalized.isEmpty() || normalized.size()>40) throw new IllegalArgumentException("skills");
        skills=List.copyOf(normalized.values());
        if(experienceMonths<0 || experienceMonths>720) throw new IllegalArgumentException("experienceMonths");
        if(availability==null) throw new IllegalArgumentException("availability");
        externalKey=normalizeExternalKey(externalKey);
        if(createdAt==null || updatedAt==null || updatedAt.isBefore(createdAt) || version<0)
            throw new IllegalArgumentException("version");
        createdBy=text(createdBy,255,"createdBy"); updatedBy=text(updatedBy,255,"updatedBy");
    }
    public static String normalizeExternalKey(String value) {
        if(value==null) return null;
        if(value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("externalKey");
        var key=value.trim();
        if(key.isEmpty()) return null;
        return text(key,200,"externalKey");
    }
    private static String text(String value,int max,String field) {
        if(value==null || value.isBlank() || value.trim().length()>max) throw new IllegalArgumentException(field);
        return value.trim();
    }
}
