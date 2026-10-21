package dao;

import dao.enums.FriendRequestStatus;
import dao.interfaces.IUserDao;
import domain.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

public class UserDao implements IUserDao {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<User> userRowMapper = (rs, rowNum) -> {
        return new User(rs.getLong("id"), rs.getString("login"), rs.getString("email"), rs.getString("contact_phone_number"), rs.getString("avatar_url"));
    };

    public UserDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public User create(String login, String email, String passwordHash) {
        String sql = """
                INSERT INTO users (login, email, hash_password, contact_phone_number, avatar_url)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """;

        String contactPhoneNumber = null;
        String userAvatarUrl = null;
        Long id = jdbcTemplate.queryForObject(sql, Long.class, login, email, passwordHash, contactPhoneNumber, userAvatarUrl);

        return new User(id, login, email, contactPhoneNumber, userAvatarUrl);
    }


    @Override
    public boolean isLoginFree(String login) {
        return Boolean.FALSE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE login = ?)", Boolean.class, login));
    }

    @Override
    public boolean isEmailFree(String email) {
        return Boolean.FALSE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE email = ?)", Boolean.class, email));
    }

    @Override
    public boolean isContactPhoneFree(String contactPhoneNumber) {
        return Boolean.FALSE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE right(contact_phone_number, 10) = right(?, 10))", Boolean.class, contactPhoneNumber));
    }

    @Override
    public User findByLogin(String login) {
        String sql = """
                SELECT id, login, email, contact_phone_number, hash_password, avatar_url
                FROM users
                WHERE login = ?;
                """;

        try {
            return jdbcTemplate.queryForObject(sql, userRowMapper, login);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public User findByEmail(String email) {
        String sql = """
                SELECT id, login, email, contact_phone_number, hash_password, avatar_url
                FROM users
                WHERE email = ?;
                """;

        try {
            return jdbcTemplate.queryForObject(sql, userRowMapper, email);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public User findById(Long id) {
        String sql = """
                SELECT id, login, email, hash_password, contact_phone_number, avatar_url 
                FROM users
                WHERE id = ?;
                """;
        try {
            return jdbcTemplate.queryForObject(sql, userRowMapper, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public String getUserHashPasswordById(Long id) {
        String sql = """
                SELECT hash_password
                FROM users
                WHERE id = ?
                """;
        try {
            return jdbcTemplate.queryForObject(sql, String.class, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<User> getIncomingFriendRequests(Long userId) {
        return jdbcTemplate.query("""
                SELECT u.id, u.login, u.email, u.contact_phone_number, u.avatar_url
                FROM users u
                WHERE (SELECT fr.status FROM friend_requests fr
                       WHERE fr.sender_id = u.id AND fr.recipient_id = ?
                       ORDER BY fr.created_at DESC, fr.id DESC LIMIT 1) = 'PENDING'
                  AND NOT EXISTS (SELECT 1 FROM friends f
                      WHERE (f.user_id = ? AND f.friend_id = u.id)
                         OR (f.friend_id = ? AND f.user_id = u.id))
                ORDER BY lower(u.login), u.id
                """, userRowMapper, userId, userId, userId);
    }

    @Override
    public List<User> getFriends(Long userId) {
        String sql = """
                SELECT u.id, u.login, u.email, u.contact_phone_number, u.avatar_url
                FROM friends f
                JOIN users u ON u.id = CASE
                    WHEN f.user_id = ? THEN f.friend_id
                    ELSE f.user_id
                END
                WHERE f.user_id = ? OR f.friend_id = ?;
                """;

        try {
            return jdbcTemplate.query(sql, userRowMapper, userId, userId, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<User> searchByLogin(String login, int limit, int offset) {
        String sql = """
                SELECT id, login, email, contact_phone_number, avatar_url
                FROM users
                WHERE strpos(lower(login), lower(?)) > 0
                ORDER BY lower(login), id
                LIMIT ?
                OFFSET ?
                """;

        try {
            return jdbcTemplate.query(sql, userRowMapper, login, limit, offset);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public boolean areUsersFriends(User user1, User user2) {
        return areUsersFriendsById(user1.getId(), user2.getId());
    }

    @Override
    public boolean areUsersFriendsById(Long userId1, Long userId2) {
        if (userId1.equals(userId2)) {
            return false;
        }
        if (userId1 > userId2) {
            Long tmp = userId1;
            userId1 = userId2;
            userId2 = tmp;
        }
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM friends
                    WHERE user_id = ? AND friend_id = ?
                );
                """;
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, userId1, userId2));
    }

    @Override
    public FriendRequestStatus getFriendRequestStatusById(Long senderId, Long recipientId) {
        if (senderId.equals(recipientId)) {
            return null;
        }
        String sql = """
                SELECT status
                FROM friend_requests
                WHERE sender_id = ? AND recipient_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 1;
                """;
        String status;

        try {
            status = jdbcTemplate.queryForObject(sql, String.class, senderId, recipientId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }

        try {
            return FriendRequestStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    @Override
    public boolean changeFriendship(Long userId, Long otherId, String action) {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbcTemplate.getDataSource()));
        return Boolean.TRUE.equals(transaction.execute(transactionStatus -> {
            
            List<Long> users = jdbcTemplate.queryForList(
                    "SELECT id FROM users WHERE id IN (?, ?) ORDER BY id FOR UPDATE",
                    Long.class, userId, otherId);
            if (users.size() != 2) {
                return false;
            }
            long first = Math.min(userId, otherId);
            long second = Math.max(userId, otherId);
            switch (action) {
                case "friend-send":
                    return jdbcTemplate.update("""
                            INSERT INTO friend_requests (sender_id, recipient_id)
                            SELECT ?, ? WHERE NOT EXISTS (
                                SELECT 1 FROM friends WHERE user_id = ? AND friend_id = ?
                            )
                            ON CONFLICT DO NOTHING
                            """, userId, otherId, first, second) == 1;
                case "friend-remove":
                    jdbcTemplate.update("""
                            DELETE FROM friends WHERE user_id = ? AND friend_id = ?
                            """, first, second);
                    return true;
                case "friend-accept":
                    int accepted = jdbcTemplate.update("""
                            UPDATE friend_requests SET status = 'ACCEPTED', resolved_at = CURRENT_TIMESTAMP
                            WHERE sender_id = ? AND recipient_id = ? AND status = 'PENDING'
                            """, otherId, userId);
                    if (accepted == 1) {
                        jdbcTemplate.update("""
                                INSERT INTO friends (user_id, friend_id) VALUES (?, ?)
                                ON CONFLICT DO NOTHING
                                """, first, second);
                    }
                    return accepted == 1;
                case "friend-reject":
                    return jdbcTemplate.update("""
                            UPDATE friend_requests SET status = 'REJECTED', resolved_at = CURRENT_TIMESTAMP
                            WHERE sender_id = ? AND recipient_id = ? AND status = 'PENDING'
                            """, otherId, userId) == 1;
                case "friend-cancel":
                    return jdbcTemplate.update("""
                            UPDATE friend_requests SET status = 'CANCELLED', resolved_at = CURRENT_TIMESTAMP
                            WHERE sender_id = ? AND recipient_id = ? AND status = 'PENDING'
                            """, userId, otherId) == 1;
                default:
                    throw new IllegalArgumentException("Неизвестное действие с друзьями.");
            }
        }));
    }
}
