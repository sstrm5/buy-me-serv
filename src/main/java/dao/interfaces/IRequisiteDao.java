package dao.interfaces;

import domain.Requisite;

import java.util.List;

public interface IRequisiteDao {
    List<Requisite> getRequisiteList(Long userId, Boolean temporary);
    boolean update(Long userId, long id, String bank, String phone);
    boolean delete(Long userId, long id);
    boolean setDefault(Long userId, long id);
    Requisite createRequisite(
            Long user_id,
            String bank,
            String phoneNumber,
            boolean isTemporary
    );
}
