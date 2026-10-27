package services.interfaces;

import domain.Requisite;

import java.util.List;

public interface IRequisiteService {
    List<Requisite> getRequisiteList(Long userId, Boolean temporary);
    boolean update(Long requestedUserId, Long userId, long id, String bank, String phone);
    boolean delete(Long requestedUserId, Long userId, long id);
    boolean setDefault(Long requestedUserId, Long userId, long id);
    Requisite create(Long requestedUserId, Long userId, String bank, String phoneNumber, boolean temporary);
}
