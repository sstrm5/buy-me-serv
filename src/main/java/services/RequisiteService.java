package services;

import dao.interfaces.IRequisiteDao;
import domain.Requisite;
import services.interfaces.IRequisiteService;

import java.util.List;

public class RequisiteService implements IRequisiteService {
    private final IRequisiteDao requisiteDao;

    public RequisiteService(IRequisiteDao requisiteDao) {
        this.requisiteDao = requisiteDao;
    }

    private static boolean isUserHasPermission(Long requestedUserId, Long userId) {
        return requestedUserId.equals(userId);
    }

    @Override
    public List<Requisite> getRequisiteList(Long userId, Boolean temporary) {
        return requisiteDao.getRequisiteList(userId, temporary);
    }

    @Override
    public boolean update(Long requestedUserId, Long userId, long id, String bank, String phone) {
        if (!isUserHasPermission(requestedUserId, userId)) {
            return false;
        }
        return requisiteDao.update(userId, id, bank, phone);
    }

    @Override
    public boolean delete(Long requestedUserId, Long userId, long id) {
        if (!isUserHasPermission(requestedUserId, userId)) {
            return false;
        }
        return requisiteDao.delete(userId, id);
    }

    @Override
    public boolean setDefault(Long requestedUserId, Long userId, long id) {
        if (!isUserHasPermission(requestedUserId, userId)) {
            return false;
        }
        return requisiteDao.setDefault(userId, id);
    }

    @Override
    public Requisite create(Long requestedUserId, Long userId, String bank, String phoneNumber, boolean temporary) {
        if (!isUserHasPermission(requestedUserId, userId)) {
            return null;
        }
        return requisiteDao.createRequisite(userId, bank, phoneNumber, temporary);
    }
}
