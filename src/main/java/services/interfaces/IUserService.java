package services.interfaces;

import dao.enums.FriendRequestStatus;
import domain.User;

import java.util.List;

public interface IUserService {
    List<User> getFriends(Long userId);

    List<User> getIncomingFriendRequests(Long userId);

    List<User> searchByLogin(String login, int limit, int offset);

    User findById(Long id);

    boolean areUsersFriends(User user1, User user2);

    boolean areUsersFriendsById(Long userId1, Long userId2);

    FriendRequestStatus getFriendRequestStatusById(Long senderId, Long recipientId);
    boolean changeFriendship(Long userId, Long otherId, String action);
}
