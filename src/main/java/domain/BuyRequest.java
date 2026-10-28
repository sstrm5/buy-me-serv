package domain;

import lombok.Data;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
public class BuyRequest {
    public static final ZoneId TIME_ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter UPDATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private Long id;
    private Long userId;
    private String authorLogin;
    private String comment;
    private OffsetDateTime validUntil;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private boolean assigned;
    private boolean paidBack;
    private java.math.BigDecimal totalAmountDue;
    private String statusName;
    private String statusCode;
    private List<BuyRequestItem> items = List.of();
    private List<LookupValue> recipients = List.of();

    public String getValidUntilLabel() {
        return DATE_FORMAT.format(validUntil.atZoneSameInstant(TIME_ZONE));
    }

    public String getUpdatedAtLabel() {
        return UPDATE_FORMAT.format(updatedAt.atZoneSameInstant(TIME_ZONE));
    }

    public boolean isEditable() {
        return ("PENDING".equals(statusCode) || "DRAFT".equals(statusCode)) && !assigned;
    }

    public boolean isExpired() {
        return !validUntil.isAfter(OffsetDateTime.now());
    }
}
