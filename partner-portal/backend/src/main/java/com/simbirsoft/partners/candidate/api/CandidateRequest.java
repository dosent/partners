package com.simbirsoft.partners.candidate.api;
import com.simbirsoft.partners.candidate.application.CandidateCommand;
import com.simbirsoft.partners.candidate.domain.CandidateAvailability;
import com.simbirsoft.partners.shared.api.FieldValidationException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
@Schema(description="Профиль кандидата и ручная часовая ставка")
public record CandidateRequest(@NotBlank @Size(max=200) String role,@Size(max=80) String grade,
        @NotEmpty @Size(max=40) List<@NotBlank @Size(max=120) String> skills,
        @NotNull @Min(0) @Max(720) Integer experienceMonths,@NotBlank @Size(max=10000) String description,
        @NotNull CandidateAvailability availability,@Size(max=200) String externalKey,
        @NotNull @Valid HourlyRate hourlyRate,@PositiveOrZero Long expectedVersion) {
    public CandidateCommand command() {
        return new CandidateCommand(role,grade,skills,experienceMonths,description,availability,externalKey,new BigDecimal(hourlyRate.amount()),hourlyRate.currency());
    }
    public long requiredVersion() {
        if(expectedVersion==null) throw new FieldValidationException("VALIDATION_ERROR","expectedVersion","Version is required");
        return expectedVersion;
    }
    @Schema(description="Сумма кандидата за час, отдельно от ставки клиента")
    public record HourlyRate(@Schema(type="string",example="2500.50") @NotBlank @Pattern(regexp="[0-9]{1,10}(\\.[0-9]{1,2})?") String amount,
        @NotBlank @Size(min=3,max=3) String currency) {}
}
