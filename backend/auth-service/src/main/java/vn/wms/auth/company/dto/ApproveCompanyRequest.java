package vn.wms.auth.company.dto;

public record ApproveCompanyRequest(
        String plan,       // "TRIAL" hoặc "PRO", mặc định "TRIAL"
        Integer trialDays, // Mặc định 30 ngày nếu là TRIAL
        String cycle,      // "MONTHLY" hoặc "YEARLY" nếu là PRO
        String note
) {
}
