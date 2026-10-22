package org.wwz.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Account fields safe to expose to callers.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthAccountResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String userId;

    /**
     * UI-compatible alias for the normalized login name.
     */
    private String account;

    private String loginName;

    private String nickname;

    private String role;
}
