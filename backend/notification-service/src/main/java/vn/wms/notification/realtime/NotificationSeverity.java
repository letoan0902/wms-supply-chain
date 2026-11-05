package vn.wms.notification.realtime;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Mức độ của thông báo, ghi chữ thường trong JSON ({@code info}, {@code success}, {@code warning}, {@code error}). */
public enum NotificationSeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR;

    @JsonValue
    public String jsonValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
