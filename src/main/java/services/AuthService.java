package services;

import dao.interfaces.IUserDao;
import domain.User;
import exceptions.EmailAlreadyInUse;
import exceptions.LoginAlreadyInUse;
import services.interfaces.IAuthService;
import util.security.PasswordHasher;
import org.springframework.dao.DuplicateKeyException;

import java.util.regex.Pattern;

public class AuthService implements IAuthService {
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern CONTACT_PHONE_PATTERN =
            Pattern.compile("^(?:\\+7|7|8)\\d{10}$");
    private final IUserDao userDao;

    public AuthService(IUserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public void register(String login, String email, String password) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Введите логин");
        }
        login = login.strip();
        if (login.length() < 2 || login.length() > 50) {
            throw new IllegalArgumentException("Некорректная длина логина");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Введите почту");
        }
        email = email.strip();
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Некорректный формат почты");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Введите пароль");
        }
        if (password.length() < 8 || password.length() > 50) {
            throw new IllegalArgumentException("Некорректная длина пароля");
        }

        if (!userDao.isLoginFree(login)) {
            throw new LoginAlreadyInUse("Такой логин уже используется");
        }
        if (!userDao.isEmailFree(email)) {
            throw new EmailAlreadyInUse("Такая почта уже используется");
        }

        String passwordHash = PasswordHasher.hash(password);

        try {
            userDao.create(
                    login,
                    email,
                    passwordHash
            );
        } catch (DuplicateKeyException e) {
            if (!userDao.isLoginFree(login)) {
                throw new LoginAlreadyInUse("Такой логин уже используется");
            }
            if (!userDao.isEmailFree(email)) {
                throw new EmailAlreadyInUse("Такая почта уже используется");
            }
            throw e;
        }


    }

    @Override
    public User authenticate(String loginOrEmail, String password) {
        User user;
        if (loginOrEmail.contains("@")) {
            user = userDao.findByEmail(loginOrEmail);
        } else {
            user = userDao.findByLogin(loginOrEmail);
        }

        if (user == null) {
            throw new IllegalArgumentException("Неправильный логин или пароль");
        }

        String passwordHash = userDao.getUserHashPasswordById(user.getId());

        if (!PasswordHasher.matches(password, passwordHash)) {
            throw new IllegalArgumentException("Неправильный логин или пароль");
        }

        return user;
    }
}
