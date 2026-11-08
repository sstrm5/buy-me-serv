package services;

import dao.interfaces.IBuyRequestDao;
import domain.BuyRequest;
import domain.BuyRequestItem;
import domain.LookupValue;
import forms.BuyRequestForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuyRequestServiceTest {
    private final CapturingDao dao = new CapturingDao();
    private final BuyRequestService service = new BuyRequestService(dao,
            Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC));
    private BuyRequestForm form;

    @BeforeEach
    void setUp() {
        form = new BuyRequestForm();
        form.setValidUntil("2026-11-07T16:00");
        form.setRecipientIds(List.of("2", "3", "2"));
        form.getItems().getFirst().setName("  Молоко  ");
        form.getItems().getFirst().setQuantity("1,250");
        form.getItems().getFirst().setUnitId("10");
        form.getItems().getFirst().setReplacementPolicyId("20");
    }

    @Test
    void savesAllItemsAndUniqueRecipientsUsingMoscowTimeAndExactDecimals() {
        form.setComment("  После 19:00  ");
        form.getItems().getFirst().setMaxUnitPrice("123,45");
        BuyRequestForm.Item second = new BuyRequestForm.Item();
        second.setName("Хлеб");
        second.setUnitId("10");
        second.setReplacementPolicyId("20");
        second.setMaxTotalPrice("0");
        form.getItems().add(second);

        assertEquals(42L, service.create(1L, form));
        assertEquals(List.of(2L, 3L), dao.recipients);
        assertEquals(Instant.parse("2026-11-07T13:00:00Z"), dao.validUntil.toInstant());
        assertEquals("После 19:00", dao.comment);
        assertEquals(2, dao.items.size());
        assertEquals("Молоко", dao.items.getFirst().getName());
        assertEquals(new BigDecimal("1.250"), dao.items.getFirst().getQuantity());
        assertEquals(new BigDecimal("123.45"), dao.items.getFirst().getMaxUnitPrice());
        assertNull(dao.items.getFirst().getMaxTotalPrice());
        assertEquals(BigDecimal.ZERO, dao.items.get(1).getMaxTotalPrice());
    }

    @Test
    void rejectsExpiredDeadlineIncludingCurrentMinute() {
        for (String value : List.of("2026-11-07T14:59", "2026-11-07T15:00", "invalid", "")) {
            form.setValidUntil(value);
            assertRejected();
        }
    }

    @Test
    void rejectsMissingRecipientsSelfAndMalformedIds() {
        for (List<String> recipients : List.of(List.<String>of(), List.of("1"), List.of("0"),
                List.of("-2"), List.of("abc"), List.of("99999999999999999999999"))) {
            form.setRecipientIds(recipients);
            assertRejected();
        }
        assertThrows(IllegalArgumentException.class, () -> service.create(null, form));
    }

    @Test
    void rejectsMoreThanOneHundredRecipients() {
        form.setRecipientIds(java.util.stream.IntStream.range(2, 103).mapToObj(String::valueOf).toList());
        assertRejected();
    }

    @Test
    void rejectsEmptyOrOversizedItemList() {
        BuyRequestForm.Item item = form.getItems().getFirst();
        form.setItems(List.of());
        assertRejected();
        form.setItems(new ArrayList<>(java.util.Collections.nCopies(51, item)));
        assertRejected();
    }

    @Test
    void rejectsInvalidQuantityWithoutRounding() {
        for (String value : List.of("0", "-1", "1.0001", "1000000000", "1e3", "NaN", "", "1.2.3")) {
            form.getItems().getFirst().setQuantity(value);
            assertRejected();
        }
    }

    @Test
    void rejectsNegativeOverpreciseAndOverflowingPrices() {
        for (String value : List.of("-0.01", "1.001", "1000000000000", "NaN")) {
            form.getItems().getFirst().setMaxTotalPrice(value);
            assertRejected();
        }
    }

    @Test
    void rejectsUnknownLookupIds() {
        form.getItems().getFirst().setUnitId("999");
        assertRejected();
        form.getItems().getFirst().setUnitId("10");
        form.getItems().getFirst().setReplacementPolicyId("999");
        assertRejected();
    }

    @Test
    void validatesEveryItemBeforeWritingAnything() {
        form.getItems().add(new BuyRequestForm.Item());
        assertRejected();
    }

    @Test
    void rejectsBlankNamesAndOversizedComments() {
        form.getItems().getFirst().setName("   ");
        assertRejected();
        form.getItems().getFirst().setName("а".repeat(256));
        assertRejected();
        form.getItems().getFirst().setName("Молоко");
        form.setComment("а".repeat(2001));
        assertRejected();
        form.setComment("");
        form.getItems().getFirst().setComment("а".repeat(1001));
        assertRejected();
    }

    @Test
    void updateUsesSameValidationAndPassesOriginalVersionToDao() {
        form.setUpdatedAt("2026-11-07T14:30:00.123456+03:00");
        assertTrue(service.update(42L, 1L, form));
        assertTrue(dao.updated);
        assertEquals(42L, dao.requestId);
        assertEquals(OffsetDateTime.parse(form.getUpdatedAt()), dao.expectedUpdatedAt);
        assertEquals(List.of(2L, 3L), dao.recipients);
        assertEquals("Молоко", dao.items.getFirst().getName());
    }

    @Test
    void rejectsUpdateWithoutOriginalVersionOrWithInvalidData() {
        for (String version : List.of("", "bad date")) {
            form.setUpdatedAt(version);
            assertThrows(IllegalArgumentException.class, () -> service.update(42L, 1L, form));
        }
        form.setUpdatedAt("2026-11-07T14:30:00+03:00");
        form.getItems().getFirst().setQuantity("-1");
        assertThrows(IllegalArgumentException.class, () -> service.update(42L, 1L, form));
        assertFalse(dao.updated);
        assertFalse(dao.created);
    }

    @Test
    void updatePropagatesConflictWithoutCreatingAnotherRequest() {
        form.setUpdatedAt("2026-11-07T14:30:00+03:00");
        dao.updateAllowed = false;
        assertFalse(service.update(42L, 1L, form));
        assertFalse(dao.created);
    }

    @Test
    void editFormPreservesAllFieldsAndMoscowTime() {
        BuyRequest request = new BuyRequest();
        request.setComment("У подъезда");
        request.setValidUntil(OffsetDateTime.parse("2026-11-07T17:00:00Z"));
        request.setUpdatedAt(OffsetDateTime.parse("2026-11-07T12:30:45.123456Z"));
        request.setRecipients(List.of(new LookupValue(2L, "Друг")));
        request.setItems(List.of(new BuyRequestItem("Молоко", new BigDecimal("1.250"), 10L, "л",
                null, new BigDecimal("100.00"), 20L, "Не покупать", "Любой бренд")));
        BuyRequestForm editForm = BuyRequestForm.from(request);
        assertEquals("2026-11-07T20:00", editForm.getValidUntil());
        assertEquals(request.getUpdatedAt().toString(), editForm.getUpdatedAt());
        assertEquals("07.11.2026 15:30", request.getUpdatedAtLabel());
        assertEquals(List.of("2"), editForm.getRecipientIds());
        assertEquals("1.250", editForm.getItems().getFirst().getQuantity());
        assertEquals("", editForm.getItems().getFirst().getMaxUnitPrice());
        assertEquals("100.00", editForm.getItems().getFirst().getMaxTotalPrice());
        assertEquals("Любой бренд", editForm.getItems().getFirst().getComment());
        assertEquals("У подъезда", editForm.getComment());
    }

    private void assertRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, form));
        assertFalse(dao.created, "Invalid requests must never reach persistence");
    }

    private static class CapturingDao implements IBuyRequestDao {
        boolean created;
        boolean updated;
        boolean updateAllowed = true;
        Long requestId;
        OffsetDateTime expectedUpdatedAt;
        String comment;
        OffsetDateTime validUntil;
        List<Long> recipients;
        List<BuyRequestItem> items;

        @Override
        public List<LookupValue> getUnits() { return List.of(new LookupValue(10L, "л")); }
        @Override
        public List<LookupValue> getReplacementPolicies() { return List.of(new LookupValue(20L, "Не покупать")); }
        @Override
        public Long create(Long userId, String comment, OffsetDateTime validUntil,
                           List<Long> recipientIds, List<BuyRequestItem> items) {
            this.created = true;
            this.comment = comment;
            this.validUntil = validUntil;
            this.recipients = recipientIds;
            this.items = items;
            return 42L;
        }
        @Override
        public boolean update(Long requestId, Long userId, OffsetDateTime expectedUpdatedAt, String comment,
                              OffsetDateTime validUntil, List<Long> recipientIds, List<BuyRequestItem> items) {
            this.updated = true;
            this.requestId = requestId;
            this.expectedUpdatedAt = expectedUpdatedAt;
            this.comment = comment;
            this.validUntil = validUntil;
            this.recipients = recipientIds;
            this.items = items;
            return updateAllowed;
        }
        @Override
        public List<BuyRequest> findForUser(Long userId, boolean incoming) { return List.of(); }
        public List<BuyRequest> findForUser(Long userId, boolean incoming, int limit, long offset) { return List.of(); }
        @Override
        public BuyRequest findByIdForUser(Long requestId, Long userId) { return null; }
    }
}
