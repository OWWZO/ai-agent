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
 * Public account registration request.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthRegisterRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Size(max = 64)
    private String loginName;

    @NotBlank
    @Size(max = 128)
    private String password;

    @NotBlank
    @Size(max = 128)
    private String nickname;
}
