package domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class User {
    private static final String DEFAULT_USER_AVATAR_URL = "/images/default-avatar.svg";

    private Long id;
    private String login;
    private String email;
    private String contactPhoneNumber;
    private String userAvatarUrl;

    public String getUserAvatarUrl() {
        if (userAvatarUrl == null) {
            return DEFAULT_USER_AVATAR_URL;
        }
        return userAvatarUrl;
    }
}
