package com.simbirsoft.partners.candidate.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CandidateProfileTest {
    private CandidateProfile profile(int months, CandidateAvailability availability, List<String> skills, String key) {
        var now=Instant.parse("2026-10-30T10:00:00Z");
        return new CandidateProfile(UUID.randomUUID(), "C-001", " Java ", null, skills, months,
                "Backend experience", availability, key, now, now, "resource", "resource", 0);
    }
    @Test void normalizesSkillsAndExternalKeyWithZeroExperience() {
        var p=profile(0,CandidateAvailability.AVAILABLE,List.of(" Java ","java","", "Spring")," JIRA-001 ");
        assertThat(p.skills()).containsExactly("Java","Spring");
        assertThat(p.experienceMonths()).isZero();
        assertThat(p.externalKey()).isEqualTo("JIRA-001");
        assertThat(p.role()).isEqualTo("Java");
        assertThat(profile(720,CandidateAvailability.UNAVAILABLE,List.of("Java")," ").externalKey()).isNull();
    }
    @Test void rejectsMissingOrOutOfRangeExperienceAndAvailability() {
        assertThatIllegalArgumentException().isThrownBy(()->profile(-1,CandidateAvailability.AVAILABLE,List.of("Java"),null));
        assertThatIllegalArgumentException().isThrownBy(()->profile(721,CandidateAvailability.AVAILABLE,List.of("Java"),null));
        assertThatIllegalArgumentException().isThrownBy(()->profile(1,null,List.of("Java"),null));
        assertThatIllegalArgumentException().isThrownBy(()->profile(1,CandidateAvailability.AVAILABLE,List.of(),null));
    }
    @Test void externalKeyIsOpaqueCaseSensitiveAndBounded() {
        assertThat(CandidateProfile.normalizeExternalKey("aBc/123")).isEqualTo("aBc/123");
        assertThat(CandidateProfile.normalizeExternalKey("x".repeat(200))).hasSize(200);
        assertThatIllegalArgumentException().isThrownBy(()->CandidateProfile.normalizeExternalKey("x".repeat(201)));
        assertThatIllegalArgumentException().isThrownBy(()->CandidateProfile.normalizeExternalKey("bad\nkey"));
    }
}
