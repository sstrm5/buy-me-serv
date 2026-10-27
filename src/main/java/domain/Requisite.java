package domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Requisite {
    private Long id;
    private Long user_id;
    private String bank;
    private String phone_number;
    private boolean is_temporary;
}

