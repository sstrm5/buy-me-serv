package exceptions;

public class ContactPhoneAlreadyInUse extends RuntimeException {
    public ContactPhoneAlreadyInUse(String message) {
        super(message);
    }
}
