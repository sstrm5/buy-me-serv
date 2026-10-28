package dao.interfaces;

import domain.BuyRequest;
import domain.BuyRequestItem;
import domain.LookupValue;

import java.time.OffsetDateTime;
import java.util.List;

public interface IBuyRequestDao {
    List<LookupValue> getUnits();
    List<LookupValue> getReplacementPolicies();
    Long create(Long userId, String comment, OffsetDateTime validUntil,
                List<Long> recipientIds, List<BuyRequestItem> items);
    boolean update(Long requestId, Long userId, OffsetDateTime expectedUpdatedAt, String comment,
                   OffsetDateTime validUntil, List<Long> recipientIds, List<BuyRequestItem> items);
    List<BuyRequest> findForUser(Long userId, boolean incoming);
    List<BuyRequest> findForUser(Long userId, boolean incoming, int limit, long offset);
    default List<BuyRequest> findHistory(Long userId, int limit, long offset) {
        return findForUser(userId, false).stream().filter(r -> "COMPLETED".equals(r.getStatusCode()))
                .skip(offset).limit(limit).toList();
    }
    BuyRequest findByIdForUser(Long requestId, Long userId);
}
