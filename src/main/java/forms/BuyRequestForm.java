package forms;

import domain.BuyRequest;
import domain.BuyRequestItem;
import lombok.Data;

import java.time.ZonedDateTime;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Data
public class BuyRequestForm {
    private String comment = "";
    private String updatedAt = "";
    private String validUntil = ZonedDateTime.now(BuyRequest.TIME_ZONE).plusDays(1)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    private List<String> recipientIds = new ArrayList<>();
    private List<Item> items = new ArrayList<>(List.of(new Item()));

    public static BuyRequestForm from(BuyRequest request) {
        BuyRequestForm form = new BuyRequestForm();
        form.setComment(request.getComment());
        form.setUpdatedAt(request.getUpdatedAt().toString());
        form.setValidUntil(request.getValidUntil().atZoneSameInstant(BuyRequest.TIME_ZONE)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
        form.setRecipientIds(request.getRecipients().stream().map(recipient -> recipient.getId().toString()).toList());
        form.getItems().clear();
        for (BuyRequestItem source : request.getItems()) {
            Item item = new Item();
            item.setName(source.getName());
            item.setQuantity(decimal(source.getQuantity()));
            item.setUnitId(source.getUnitId().toString());
            item.setMaxUnitPrice(decimal(source.getMaxUnitPrice()));
            item.setMaxTotalPrice(decimal(source.getMaxTotalPrice()));
            item.setReplacementPolicyId(source.getReplacementPolicyId().toString());
            item.setComment(source.getComment());
            form.getItems().add(item);
        }
        return form;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    @Data
    public static class Item {
        private String name = "";
        private String quantity = "1";
        private String unitId = "";
        private String maxUnitPrice = "";
        private String maxTotalPrice = "";
        private String replacementPolicyId = "";
        private String comment = "";
    }
}
