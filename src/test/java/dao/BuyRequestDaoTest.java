package dao;

import domain.BuyRequestItem;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuyRequestDaoTest {
    private static final BuyRequestItem ITEM = new BuyRequestItem("Молоко", BigDecimal.ONE, 10L,
            "л", null, new BigDecimal("100"), 20L, "Не покупать", "");

    @Test
    void commitsRequestRecipientsAndItemsTogether() {
        RecordingJdbc jdbc = new RecordingJdbc();
        Long id = new BuyRequestDao(jdbc).create(1L, "", OffsetDateTime.now().plusDays(1),
                List.of(2L, 3L), List.of(ITEM, ITEM));
        assertNotNull(id);
        assertEquals(1, jdbc.state.commits);
        assertEquals(0, jdbc.state.rollbacks);
        assertEquals(7, jdbc.writes);
    }

    @Test
    void rollsBackEntireRequestIfAnItemCannotBeSaved() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.failRequirements = true;
        assertThrows(DataIntegrityViolationException.class, () -> new BuyRequestDao(jdbc).create(
                1L, "", OffsetDateTime.now().plusDays(1), List.of(2L), List.of(ITEM)));
        assertEquals(0, jdbc.state.commits);
        assertEquals(1, jdbc.state.rollbacks);
        assertTrue(jdbc.writes > 1, "Failure must roll back earlier request and recipient writes too");
    }

    @Test
    void rejectsNonFriendRecipientsInsideTransactionBeforeWriting() {
        RecordingJdbc jdbc = new RecordingJdbc();
        assertThrows(IllegalArgumentException.class, () -> new BuyRequestDao(jdbc).create(
                1L, "", OffsetDateTime.now().plusDays(1), List.of(99L), List.of(ITEM)));
        assertEquals(0, jdbc.writes);
        assertEquals(0, jdbc.state.commits);
        assertEquals(1, jdbc.state.rollbacks);
    }

    @Test
    void updatesContentsAndParentTimestampInOneTransaction() {
        RecordingJdbc jdbc = new RecordingJdbc();
        assertTrue(new BuyRequestDao(jdbc).update(42L, 1L, OffsetDateTime.now(), "Новый комментарий",
                OffsetDateTime.now().plusDays(1), List.of(2L, 3L), List.of(ITEM)));
        assertEquals(1, jdbc.state.commits);
        assertEquals(0, jdbc.state.rollbacks);
        assertTrue(jdbc.parentUpdated, "Editing items must also update the request and fire its timestamp trigger");
    }

    @Test
    void conflictingOrUnauthorisedUpdateDoesNotChangeContents() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.editable = false;
        assertFalse(new BuyRequestDao(jdbc).update(42L, 1L, OffsetDateTime.now(), "",
                OffsetDateTime.now().plusDays(1), List.of(2L), List.of(ITEM)));
        assertEquals(0, jdbc.writes);
        assertFalse(jdbc.parentUpdated);
    }

    @Test
    void assignedRequestCannotBeEditedEvenWhilePending() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.assigned = true;
        assertFalse(new BuyRequestDao(jdbc).update(42L, 1L, OffsetDateTime.now(), "",
                OffsetDateTime.now().plusDays(1), List.of(2L), List.of(ITEM)));
        assertEquals(0, jdbc.writes);
    }

    @Test
    void failedUpdateRollsBackDeletedContentsAndInsertedRows() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.failRequirements = true;
        assertThrows(DataIntegrityViolationException.class, () -> new BuyRequestDao(jdbc).update(
                42L, 1L, OffsetDateTime.now(), "", OffsetDateTime.now().plusDays(1), List.of(2L), List.of(ITEM)));
        assertEquals(0, jdbc.state.commits);
        assertEquals(1, jdbc.state.rollbacks);
        assertTrue(jdbc.writes > 2);
        assertFalse(jdbc.parentUpdated);
    }

    @Test
    void editCanKeepExistingRecipientAfterFriendshipEnds() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.friends = List.of();
        assertTrue(new BuyRequestDao(jdbc).update(42L, 1L, OffsetDateTime.now(), "",
                OffsetDateTime.now().plusDays(1), List.of(2L), List.of(ITEM)));
        assertEquals(1, jdbc.state.commits);
    }

    @Test
    void editCannotAddSomeoneWhoIsNeitherFriendNorExistingRecipient() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.friends = List.of();
        assertThrows(IllegalArgumentException.class, () -> new BuyRequestDao(jdbc).update(42L, 1L,
                OffsetDateTime.now(), "", OffsetDateTime.now().plusDays(1), List.of(3L), List.of(ITEM)));
        assertEquals(0, jdbc.writes);
        assertEquals(1, jdbc.state.rollbacks);
    }

    private static class ConnectionState {
        int commits;
        int rollbacks;
        boolean autoCommit = true;
    }

    private static DataSource dataSource(ConnectionState state) {
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (object, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(object);
                    case "equals" -> object == args[0];
                    case "toString" -> "Test connection";
                    case "getAutoCommit" -> state.autoCommit;
                    case "setAutoCommit" -> { state.autoCommit = (Boolean) args[0]; yield null; }
                    case "commit" -> { state.commits++; yield null; }
                    case "rollback" -> { state.rollbacks++; yield null; }
                    case "isReadOnly", "isClosed" -> false;
                    case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                    default -> null;
                });
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (object, method, args) -> switch (method.getName()) {
                    case "getConnection" -> connection;
                    case "hashCode" -> System.identityHashCode(object);
                    case "equals" -> object == args[0];
                    case "toString" -> "Test data source";
                    default -> null;
                });
    }

    private static class RecordingJdbc extends JdbcTemplate {
        final ConnectionState state;
        int writes;
        boolean failRequirements;
        boolean editable = true;
        boolean assigned;
        boolean parentUpdated;
        List<Long> friends = List.of(2L, 3L);

        RecordingJdbc() { this(new ConnectionState()); }

        private RecordingJdbc(ConnectionState state) {
            super(dataSource(state));
            this.state = state;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
            if (sql.contains("FROM users")) return (List<T>) List.of(1L, 2L, 3L);
            if (sql.contains("FROM buy_requests r")) return (List<T>) (editable ? List.of(42L) : List.of());
            if (sql.contains("FROM buy_requests_recipients")) return (List<T>) List.of(2L);
            return (List<T>) friends;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            if (type == Boolean.class) return type.cast(assigned);
            writes++;
            return type.cast((long) writes);
        }

        @Override
        public int update(String sql, Object... args) {
            writes++;
            if (sql.startsWith("UPDATE buy_requests SET")) parentUpdated = true;
            if (failRequirements && sql.contains("INSERT INTO item_requirements")) {
                throw new DataIntegrityViolationException("Test failure");
            }
            return 1;
        }
    }
}
