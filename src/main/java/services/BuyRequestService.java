package services;

import dao.interfaces.IBuyRequestDao;
import domain.BuyRequest;
import domain.BuyRequestItem;
import domain.LookupValue;
import forms.BuyRequestForm;
import services.interfaces.IBuyRequestService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BuyRequestService implements IBuyRequestService {
    public static final int MAX_ITEMS = 50;
    public static final int MAX_RECIPIENTS = 100;
    private final IBuyRequestDao buyRequestDao;
    private final Clock clock;

    public BuyRequestService(IBuyRequestDao buyRequestDao) {
        this(buyRequestDao, Clock.systemUTC());
    }

    public BuyRequestService(IBuyRequestDao buyRequestDao, Clock clock) {
        this.buyRequestDao = buyRequestDao;
        this.clock = clock;
    }

    @Override
    public List<BuyRequest> findHistory(Long userId, int limit, long offset) {
        return buyRequestDao.findHistory(userId, limit, offset);
    }

    @Override
    public List<LookupValue> getUnits() {
        return buyRequestDao.getUnits();
    }

    @Override
    public List<LookupValue> getReplacementPolicies() {
        return buyRequestDao.getReplacementPolicies();
    }

    @Override
    public Long create(Long userId, BuyRequestForm form) {
        ValidatedRequest request = validate(userId, form);
        return buyRequestDao.create(userId, request.comment(), request.validUntil(), request.recipientIds(), request.items());
    }

    @Override
    public boolean update(Long requestId, Long userId, BuyRequestForm form) {
        if (requestId == null || requestId <= 0) {
            throw new IllegalArgumentException("Некорректный номер запроса.");
        }
        OffsetDateTime expectedUpdatedAt;
        try {
            expectedUpdatedAt = OffsetDateTime.parse(form.getUpdatedAt());
        } catch (DateTimeException | NullPointerException e) {
            throw new IllegalArgumentException("Обновите страницу редактирования и попробуйте ещё раз.");
        }
        ValidatedRequest request = validate(userId, form);
        return buyRequestDao.update(requestId, userId, expectedUpdatedAt, request.comment(), request.validUntil(),
                request.recipientIds(), request.items());
    }

    private ValidatedRequest validate(Long userId, BuyRequestForm form) {
        if (userId == null) {
            throw new IllegalArgumentException("Войдите в аккаунт, чтобы отправить запрос.");
        }
        if (form.getRecipientIds().isEmpty() || form.getRecipientIds().size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException("Выберите от 1 до " + MAX_RECIPIENTS + " друзей.");
        }
        List<Long> recipientIds = form.getRecipientIds().stream()
                .map(value -> positiveId(value, "Некорректный получатель.")).distinct().toList();
        if (recipientIds.contains(userId)) {
            throw new IllegalArgumentException("Нельзя отправить запрос самому себе.");
        }
        OffsetDateTime validUntil;
        try {
            validUntil = LocalDateTime.parse(form.getValidUntil()).atZone(BuyRequest.TIME_ZONE).toOffsetDateTime();
        } catch (DateTimeException | NullPointerException e) {
            throw new IllegalArgumentException("Укажите срок действия запроса.");
        }
        if (!validUntil.isAfter(OffsetDateTime.now(clock))) {
            throw new IllegalArgumentException("Срок действия должен быть в будущем.");
        }
        if (form.getItems().isEmpty() || form.getItems().size() > MAX_ITEMS) {
            throw new IllegalArgumentException("Добавьте от 1 до " + MAX_ITEMS + " товаров.");
        }
        String comment = text(form.getComment(), 2000, "Комментарий к запросу");
        Set<Long> units = ids(getUnits());
        Set<Long> policies = ids(getReplacementPolicies());
        List<BuyRequestItem> items = new ArrayList<>();
        for (int index = 0; index < form.getItems().size(); index++) {
            BuyRequestForm.Item item = form.getItems().get(index);
            String prefix = "Товар " + (index + 1) + ": ";
            String name = text(item.getName(), 255, prefix + "название");
            if (name.isEmpty()) {
                throw new IllegalArgumentException(prefix + "укажите название.");
            }
            Long unitId = positiveId(item.getUnitId(), prefix + "выберите единицу измерения.");
            Long policyId = positiveId(item.getReplacementPolicyId(), prefix + "выберите правило замены.");
            if (!units.contains(unitId) || !policies.contains(policyId)) {
                throw new IllegalArgumentException(prefix + "неизвестная единица измерения или правило замены.");
            }
            BigDecimal quantity = number(item.getQuantity(), 9, 3, false, prefix + "количество");
            BigDecimal maxUnitPrice = number(item.getMaxUnitPrice(), 12, 2, true, prefix + "цена за единицу");
            BigDecimal maxTotalPrice = number(item.getMaxTotalPrice(), 12, 2, true, prefix + "бюджет");
            items.add(new BuyRequestItem(name, quantity, unitId, null, maxUnitPrice, maxTotalPrice,
                    policyId, null, text(item.getComment(), 1000, prefix + "комментарий")));
        }
        return new ValidatedRequest(comment, validUntil, recipientIds, items);
    }

    private record ValidatedRequest(String comment, OffsetDateTime validUntil,
                                    List<Long> recipientIds, List<BuyRequestItem> items) { }

    private static Set<Long> ids(List<LookupValue> values) {
        return values.stream().map(LookupValue::getId).collect(Collectors.toSet());
    }

    private static Long positiveId(String value, String message) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(message);
    }

    private static String text(String value, int maxLength, String label) {
        String result = value == null ? "" : value.strip();
        if (result.length() > maxLength) {
            throw new IllegalArgumentException(label + ": не больше " + maxLength + " символов.");
        }
        return result;
    }

    private static BigDecimal number(String value, int integerDigits, int scale, boolean optional, String label) {
        if (optional && (value == null || value.isBlank())) {
            return null;
        }
        try {
            String normalized = value == null ? "" : value.strip().replace(',', '.');
            if (normalized.matches("[0-9]+(\\.[0-9]{1," + scale + "})?")) {
                BigDecimal number = new BigDecimal(normalized);
                if ((optional ? number.signum() >= 0 : number.signum() > 0)
                        && number.compareTo(BigDecimal.TEN.pow(integerDigits)) < 0) {
                    return number;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(label + ": укажите " + (optional ? "неотрицательное" : "положительное")
                + " число, до " + scale + " знаков после запятой.");
    }

    @Override
    public List<BuyRequest> findForUser(Long userId, boolean incoming, int limit, long offset) {
        return buyRequestDao.findForUser(userId, incoming, limit, offset);
    }

    @Override
    public List<BuyRequest> findForUser(Long userId, boolean incoming) {
        return buyRequestDao.findForUser(userId, incoming);
    }

    @Override
    public BuyRequest findByIdForUser(Long requestId, Long userId) {
        return buyRequestDao.findByIdForUser(requestId, userId);
    }
}
