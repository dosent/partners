package com.simbirsoft.partners.finance.domain;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class CandidateHourlyRateTest {
    private CandidateHourlyRate rate(String amount,String currency) {
        return new CandidateHourlyRate(UUID.randomUUID(),new BigDecimal(amount),currency,Instant.EPOCH,"resource",0);
    }
    @Test void preservesAmountAndCurrency() {
        assertThat(rate("2500.50","RUB").amount()).isEqualByComparingTo("2500.50");
        assertThat(rate("9999999999.99","KZT").currency()).isEqualTo("KZT");
    }
    @Test void rejectsInvalidRateWithoutRounding() {
        for(String n:new String[]{"0","-1","1.001","10000000000.00"})
            assertThatIllegalArgumentException().isThrownBy(()->rate(n,"RUB"));
        assertThatIllegalArgumentException().isThrownBy(()->rate("1.00","INVALID"));
    }
}
