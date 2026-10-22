package org.wwz.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Account login request.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthLoginRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 64)
    private String loginName;

    @Size(max = 64)
    private String account;

    @NotBlank
    @Size(max = 128)
    private String password;
}
