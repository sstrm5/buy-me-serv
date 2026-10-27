package dao;

import dao.interfaces.IRequisiteDao;
import domain.Requisite;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

public class RequisiteDao implements IRequisiteDao {
    private final JdbcTemplate jdbcTemplate;
    private final org.springframework.transaction.support.TransactionTemplate transaction;

    public RequisiteDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    private static final RowMapper<Requisite> MAPPER = (rs, rowNum) -> new Requisite(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("bank"),
            rs.getString("phone_number"),
            rs.getBoolean("is_temporary")
    );

    @Override
    public List<Requisite> getRequisiteList(Long userId, Boolean temporary) {
        String sql = """
                SELECT r.id, r.user_id, r.bank, r.phone_number, r.is_temporary
                FROM requisites r
                JOIN users u ON u.id = r.user_id
                WHERE r.user_id = ?
                """;
        String order = " ORDER BY (r.id = u.default_requisite_id) DESC NULLS LAST, r.id";
        if (temporary == null) {
            return jdbcTemplate.query(sql + order, MAPPER, userId);
        }
        return jdbcTemplate.query(sql + " AND r.is_temporary = ?" + order, MAPPER, userId, temporary);
    }

    @Override
    public boolean update(Long userId, long id, String bank, String phone) {
        return jdbcTemplate.update("""
                UPDATE requisites
                SET bank = ?, phone_number = ?
                WHERE user_id = ? AND id = ? AND NOT is_temporary
                    AND NOT EXISTS (SELECT 1 FROM purchase WHERE requisite_id = requisites.id)
                """, bank, phone, userId, id) == 1;
    }

    @Override
    public boolean delete(Long userId, long id) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            jdbcTemplate.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", userId);
            int deleted = jdbcTemplate.update("""
                DELETE FROM requisites
                WHERE user_id = ? AND id = ? AND NOT is_temporary
                    AND NOT EXISTS (SELECT 1 FROM purchase WHERE requisite_id = requisites.id)
                """, userId, id);
            if (deleted == 1) {
                jdbcTemplate.update("""
                    UPDATE users SET default_requisite_id =
                        (SELECT id FROM requisites WHERE user_id = ? AND NOT is_temporary ORDER BY id LIMIT 1)
                    WHERE id = ? AND default_requisite_id IS NULL
                    """, userId, userId);
            }
            return deleted == 1;
        }));
    }

    @Override
    public boolean setDefault(Long userId, long id) {
        return jdbcTemplate.update("""
                UPDATE users
                SET default_requisite_id = (
                    SELECT r.id FROM requisites r
                    WHERE r.id = ? AND r.user_id = users.id AND NOT r.is_temporary
                )
                WHERE id = ? AND EXISTS (
                    SELECT 1 FROM requisites r
                    WHERE r.id = ? AND r.user_id = users.id AND NOT r.is_temporary
                )
                """, id, userId, id) == 1;
    }

    @Override
    public Requisite createRequisite(Long user_id, String bank, String phoneNumber, boolean isTemporary) {
        String sql = """
                INSERT INTO requisites (user_id, bank, phone_number, is_temporary)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """;

        return transaction.execute(status -> {
            jdbcTemplate.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", user_id);
            Long id = jdbcTemplate.queryForObject(sql, Long.class, user_id, bank, phoneNumber, isTemporary);
            if (!isTemporary) {
                jdbcTemplate.update("UPDATE users SET default_requisite_id = ? WHERE id = ? AND default_requisite_id IS NULL", id, user_id);
            }
            return new Requisite(id, user_id, bank, phoneNumber, isTemporary);
        });
    }
}
