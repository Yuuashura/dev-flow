package com.saas.auth_service.dto;

import com.saas.auth_service.entity.GlobalRole;
import com.saas.auth_service.entity.OAuthProvider;
import com.saas.auth_service.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private UUID id;
    private String fullName;
    private String email;
    private String avatarUrl;
    private String phoneNumber;
    private String jobTitle;
    private String companyName;
    private String city;
    private String bio;
    private boolean profileComplete;
    private UserStatus status;
    private boolean emailVerified;
    private GlobalRole globalRole;
    private List<OAuthProvider> identities;
}
