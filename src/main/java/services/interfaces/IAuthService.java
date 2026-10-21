package services.interfaces;

import domain.User;

public interface IAuthService {
    void register(String login, String email, String password);

    User authenticate(String login, String password);
}
