package domain;

import lombok.Value;

import java.math.BigDecimal;

@Value
public class BuyRequestItem {
    String name;
    BigDecimal quantity;
    Long unitId;
    String unitName;
    BigDecimal maxUnitPrice;
    BigDecimal maxTotalPrice;
    Long replacementPolicyId;
    String replacementPolicyName;
    String comment;
}
