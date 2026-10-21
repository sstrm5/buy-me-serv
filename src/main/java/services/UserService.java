package services;

import dao.enums.FriendRequestStatus;
import dao.interfaces.IUserDao;
import domain.User;
import services.interfaces.IUserService;

import java.util.List;

public class UserService implements IUserService {
    private final IUserDao userDao;

    public UserService(IUserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public List<User> getIncomingFriendRequests(Long userId) {
        return userDao.getIncomingFriendRequests(userId);
    }

    @Override
    public List<User> getFriends(Long userId) {
        return userDao.getFriends(userId);
    }

    @Override
    public List<User> searchByLogin(String login, int limit, int offset) {
        if (login == null || login.isBlank()) {
            return List.of();
        }
        return userDao.searchByLogin(login.strip(), limit, offset);
    }

    @Override
    public User findById(Long id) {
        return userDao.findById(id);
    }

    @Override
    public boolean areUsersFriends(User user1, User user2) {
        return userDao.areUsersFriends(user1, user2);
    }

    @Override
    public boolean areUsersFriendsById(Long userId1, Long userId2) {
        return userDao.areUsersFriendsById(userId1, userId2);
    }

    @Override
    public FriendRequestStatus getFriendRequestStatusById(Long senderId, Long recipientId) {
        return userDao.getFriendRequestStatusById(senderId, recipientId);
    }


    @Override
    public boolean changeFriendship(Long userId, Long otherId, String action) {
        if (userId == null || otherId == null || userId.equals(otherId)) {
            throw new IllegalArgumentException("Нельзя выполнить это действие с самим собой.");
        }
        return userDao.changeFriendship(userId, otherId, action);
    }
}
