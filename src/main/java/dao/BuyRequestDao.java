package dao;

import dao.interfaces.IBuyRequestDao;
import domain.BuyRequest;
import domain.BuyRequestItem;
import domain.LookupValue;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BuyRequestDao implements IBuyRequestDao {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transaction;
    private static final String SELECT_REQUEST = """
            SELECT r.id, r.user_id, u.login, r.comment, r.valid_until, r.created_at, r.updated_at, r.is_paid_back, r.total_amount_due,
                   EXISTS (SELECT 1 FROM assigned_buy_requests a WHERE a.buy_request_id = r.id) AS assigned,
                   s.code AS status_code, CASE WHEN s.code IN ('ADDED','COLLECTING') THEN s.name || ': ' ||
                   COALESCE((SELECT string_agg(u2.login, ', ' ORDER BY u2.login)
                     FROM assigned_buy_requests a2 JOIN purchase p2 ON p2.id=a2.purchase_id
                     JOIN users u2 ON u2.id=p2.shopper_id WHERE a2.buy_request_id=r.id), '')
                   ELSE s.name END AS status_name
            FROM buy_requests r
            JOIN users u ON u.id = r.user_id
            JOIN buy_request_statuses s ON s.id = r.status_id
            """;
    private static final RowMapper<BuyRequest> MAPPER = (rs, rowNum) -> {
        BuyRequest request = new BuyRequest();
        request.setId(rs.getLong("id"));
        request.setUserId(rs.getLong("user_id"));
        request.setAuthorLogin(rs.getString("login"));
        request.setComment(rs.getString("comment"));
        request.setValidUntil(rs.getObject("valid_until", OffsetDateTime.class));
        request.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        request.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        request.setAssigned(rs.getBoolean("assigned"));
        request.setPaidBack(rs.getBoolean("is_paid_back"));
        request.setTotalAmountDue(rs.getBigDecimal("total_amount_due"));
        request.setStatusName(rs.getString("status_name"));
        request.setStatusCode(rs.getString("status_code"));
        return request;
    };

    public BuyRequestDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    @Override
    public List<LookupValue> getUnits() {
        return jdbcTemplate.query("SELECT id, name FROM units ORDER BY id",
                (rs, rowNum) -> new LookupValue(rs.getLong("id"), rs.getString("name")));
    }

    @Override
    public List<LookupValue> getReplacementPolicies() {
        return jdbcTemplate.query("SELECT id, name FROM replacement_rules ORDER BY id",
                (rs, rowNum) -> new LookupValue(rs.getLong("id"), rs.getString("name")));
    }

    @Override
    public Long create(Long userId, String comment, OffsetDateTime validUntil,
                       List<Long> recipientIds, List<BuyRequestItem> items) {
        return transaction.execute(status -> {
            lockParticipants(userId, recipientIds);
            validateRecipients(userId, recipientIds, List.of());
            Long requestId = jdbcTemplate.queryForObject("""
                    INSERT INTO buy_requests (user_id, comment, valid_until, status_id)
                    VALUES (?, ?, ?, (SELECT id FROM buy_request_statuses WHERE code = 'PENDING'))
                    RETURNING id
                    """, Long.class, userId, comment, validUntil);
            insertContents(requestId, recipientIds, items);
            return requestId;
        });
    }

    @Override
    public boolean update(Long requestId, Long userId, OffsetDateTime expectedUpdatedAt, String comment,
                          OffsetDateTime validUntil, List<Long> recipientIds, List<BuyRequestItem> items) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            lockParticipants(userId, recipientIds);
            
            List<Long> editable = jdbcTemplate.queryForList("""
                    SELECT r.id FROM buy_requests r
                    JOIN buy_request_statuses s ON s.id = r.status_id
                    WHERE r.id = ? AND r.user_id = ? AND s.code IN ('PENDING', 'DRAFT') AND r.updated_at = ?
                    FOR UPDATE OF r
                    """, Long.class, requestId, userId, expectedUpdatedAt);
            if (editable.isEmpty() || Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM assigned_buy_requests WHERE buy_request_id = ?)",
                    Boolean.class, requestId))) {
                return false;
            }
            List<Long> existingRecipients = jdbcTemplate.queryForList(
                    "SELECT user_id FROM buy_requests_recipients WHERE buy_request_id = ?", Long.class, requestId);
            validateRecipients(userId, recipientIds, existingRecipients);
            jdbcTemplate.update("DELETE FROM buy_requests_recipients WHERE buy_request_id = ?", requestId);
            jdbcTemplate.update("DELETE FROM buy_request_items WHERE buy_request_id = ?", requestId);
            insertContents(requestId, recipientIds, items);
            
            jdbcTemplate.update("UPDATE buy_requests SET comment = ?, valid_until = ?, status_id = (SELECT id FROM buy_request_statuses WHERE code = 'PENDING') WHERE id = ?",
                    comment, validUntil, requestId);
            return true;
        }));
    }

    private void lockParticipants(Long userId, List<Long> recipientIds) {
        
        List<Long> participants = new ArrayList<>(recipientIds);
        participants.add(userId);
        String placeholders = String.join(",", Collections.nCopies(participants.size(), "?"));
        List<Long> locked = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE id IN (" + placeholders + ") ORDER BY id FOR KEY SHARE",
                Long.class, participants.toArray());
        if (!locked.containsAll(participants)) {
            throw new IllegalArgumentException("Один из получателей больше не существует. Обновите страницу.");
        }
    }

    private void validateRecipients(Long userId, List<Long> recipientIds, List<Long> existingRecipients) {
        List<Long> allowed = new ArrayList<>(jdbcTemplate.queryForList("""
                SELECT CASE WHEN user_id = ? THEN friend_id ELSE user_id END
                FROM friends WHERE user_id = ? OR friend_id = ?
                """, Long.class, userId, userId, userId));
        
        allowed.addAll(existingRecipients);
        if (!allowed.containsAll(recipientIds)) {
            throw new IllegalArgumentException("Новых получателей можно выбрать только из своих друзей.");
        }
    }

    private void insertContents(Long requestId, List<Long> recipientIds, List<BuyRequestItem> items) {
        for (Long recipientId : recipientIds) {
            jdbcTemplate.update("INSERT INTO buy_requests_recipients (buy_request_id, user_id) VALUES (?, ?)",
                    requestId, recipientId);
        }
        for (BuyRequestItem item : items) {
            Long itemId = jdbcTemplate.queryForObject("""
                    INSERT INTO buy_request_items (buy_request_id, item_name, requested_quantity, unit_id,
                                                  comment, buy_status_id, approval_status_id)
                    VALUES (?, ?, ?, ?, ?,
                            (SELECT id FROM buy_item_statuses WHERE code = 'PENDING'),
                            (SELECT id FROM approval_statuses WHERE code = 'NOT_REQUIRED'))
                    RETURNING id
                    """, Long.class, requestId, item.getName(), item.getQuantity(), item.getUnitId(), item.getComment());
            jdbcTemplate.update("""
                    INSERT INTO item_requirements (buy_request_item_id, max_unit_price, max_total_price,
                                                   replacement_policy_id)
                    VALUES (?, ?, ?, ?)
                    """, itemId, item.getMaxUnitPrice(), item.getMaxTotalPrice(), item.getReplacementPolicyId());
        }
    }

    @Override
    public List<BuyRequest> findForUser(Long userId, boolean incoming) {
        String condition = incoming
                ? "s.code <> 'DRAFT' AND EXISTS (SELECT 1 FROM buy_requests_recipients rr WHERE rr.buy_request_id = r.id AND rr.user_id = ?)"
                : "r.user_id = ?";
        return jdbcTemplate.query(SELECT_REQUEST + " WHERE " + condition + " ORDER BY r.created_at DESC, r.id DESC",
                MAPPER, userId);
    }

    @Override
    public List<BuyRequest> findForUser(Long userId, boolean incoming, int limit, long offset) {
        String condition = incoming
                ? "s.code <> 'DRAFT' AND EXISTS (SELECT 1 FROM buy_requests_recipients rr WHERE rr.buy_request_id = r.id AND rr.user_id = ?)"
                : "r.user_id = ?";
        return jdbcTemplate.query(SELECT_REQUEST + " WHERE " + condition
                        + " ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?",
                MAPPER, userId, limit, offset);
    }

    @Override
    public List<BuyRequest> findHistory(Long userId, int limit, long offset) {
        return jdbcTemplate.query(SELECT_REQUEST + " WHERE r.user_id = ? AND s.code = 'COMPLETED' ORDER BY r.id DESC LIMIT ? OFFSET ?", MAPPER, userId, limit, offset);
    }

    @Override
    public BuyRequest findByIdForUser(Long requestId, Long userId) {
        BuyRequest request;
        try {
            request = jdbcTemplate.queryForObject(SELECT_REQUEST + """
                    WHERE r.id = ? AND (r.user_id = ? OR (s.code <> 'DRAFT' AND EXISTS (
                        SELECT 1 FROM buy_requests_recipients rr WHERE rr.buy_request_id = r.id AND rr.user_id = ?
                    )))
                    """, MAPPER, requestId, userId, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
        request.setItems(jdbcTemplate.query("""
                SELECT i.item_name, i.requested_quantity, i.unit_id, u.name AS unit_name, i.comment,
                       ir.max_unit_price, ir.max_total_price, ir.replacement_policy_id, rr.name AS replacement_name
                FROM buy_request_items i
                JOIN units u ON u.id = i.unit_id
                JOIN item_requirements ir ON ir.buy_request_item_id = i.id
                JOIN replacement_rules rr ON rr.id = ir.replacement_policy_id
                WHERE i.buy_request_id = ? ORDER BY i.id
                """, (rs, rowNum) -> new BuyRequestItem(rs.getString("item_name"), rs.getBigDecimal("requested_quantity"),
                rs.getLong("unit_id"), rs.getString("unit_name"), rs.getBigDecimal("max_unit_price"),
                rs.getBigDecimal("max_total_price"), rs.getLong("replacement_policy_id"),
                rs.getString("replacement_name"), rs.getString("comment")), requestId));
        request.setRecipients(jdbcTemplate.query("""
                SELECT u.id, u.login FROM buy_requests_recipients rr
                JOIN users u ON u.id = rr.user_id WHERE rr.buy_request_id = ? ORDER BY u.login, u.id
                """, (rs, rowNum) -> new LookupValue(rs.getLong("id"), rs.getString("login")), requestId));
        return request;
    }
}
