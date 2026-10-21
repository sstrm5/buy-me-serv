package exceptions;

public class LoginAlreadyInUse extends RuntimeException {
    public LoginAlreadyInUse(String message) {
        super(message);
    }
}
