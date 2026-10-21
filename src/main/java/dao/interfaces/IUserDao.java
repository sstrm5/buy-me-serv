package dao.interfaces;

import dao.enums.FriendRequestStatus;
import domain.User;

import java.util.List;

public interface IUserDao {
    User create(String login, String email, String passwordHash);

    boolean isLoginFree(String login);

    boolean isEmailFree(String email);

    boolean isContactPhoneFree(String contactPhoneFree);

    User findByLogin(String login);

    User findByEmail(String email);

    User findById(Long id);

    String getUserHashPasswordById(Long id);

    List<User> getFriends(Long userId);

    List<User> getIncomingFriendRequests(Long userId);

    List<User> searchByLogin(String login, int limit, int offset);

    boolean areUsersFriends(User user1, User user2);

    boolean areUsersFriendsById(Long userId1, Long userId2);

    FriendRequestStatus getFriendRequestStatusById(Long senderId, Long recipientId);
    boolean changeFriendship(Long userId, Long otherId, String action);
}
