package vn.wms.auth.subscription.dto;

import lombok.Builder;
import lombok.Getter;
import vn.wms.auth.subscription.entity.Plan;

import java.math.BigDecimal;

@Getter
@Builder
public class PlanResponse {
    private String code;
    private String name;
    private BigDecimal priceMonthly;
    private BigDecimal priceYearly;
    private Integer maxUsers;
    private Integer maxWarehouses;
    private Integer maxSkus;
    private Integer maxAiRequests;
    private Boolean apiAccess;
    private Integer trialDays;

    public static PlanResponse from(Plan plan) {
        return PlanResponse.builder()
                .code(plan.getCode())
                .name(plan.getName())
                .priceMonthly(plan.getPriceMonthly())
                .priceYearly(plan.getPriceYearly())
                .maxUsers(plan.getMaxUsers())
                .maxWarehouses(plan.getMaxWarehouses())
                .maxSkus(plan.getMaxSkus())
                .maxAiRequests(plan.getMaxAiRequests())
                .apiAccess(plan.getApiAccess())
                .trialDays(plan.getTrialDays())
                .build();
    }
}
