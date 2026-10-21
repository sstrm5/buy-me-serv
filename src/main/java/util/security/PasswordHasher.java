package util.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHasher {
    private static final BCryptPasswordEncoder ENCODER =
            new BCryptPasswordEncoder();

    public static String hash(String password) {
        return ENCODER.encode(password);
    }

    public static boolean matches(String password, String hash) {
        return ENCODER.matches(password, hash);
    }
}