package services.interfaces;

import domain.BuyRequest;
import domain.LookupValue;
import forms.BuyRequestForm;

import java.util.List;

public interface IBuyRequestService {
    List<LookupValue> getUnits();
    List<LookupValue> getReplacementPolicies();
    Long create(Long userId, BuyRequestForm form);
    boolean update(Long requestId, Long userId, BuyRequestForm form);
    List<BuyRequest> findForUser(Long userId, boolean incoming);
    List<BuyRequest> findForUser(Long userId, boolean incoming, int limit, long offset);
    default List<BuyRequest> findHistory(Long userId, int limit, long offset) {
        return findForUser(userId, false).stream().filter(r -> "COMPLETED".equals(r.getStatusCode()))
                .skip(offset).limit(limit).toList();
    }
    BuyRequest findByIdForUser(Long requestId, Long userId);
}
