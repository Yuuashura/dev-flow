package com.saas.auth_service.controller;

import com.saas.auth_service.dto.UserDto;
import com.saas.auth_service.entity.User;
import com.saas.auth_service.entity.UserStatus;
import com.saas.auth_service.repository.UserRepository;
import com.saas.auth_service.repository.UserIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    /** Batas atas ukuran halaman. Tanpa ini `?size=1000000` menarik seluruh tabel
     *  users ke memori dan memetakan tiap barisnya ke DTO. */
    private static final int MAX_PAGE_SIZE = 100;

    @GetMapping("/users")
    public ResponseEntity<Page<UserDto>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // PageRequest.of melempar IllegalArgumentException untuk nilai negatif atau
        // size 0, yang dulu keluar sebagai 500 (bahkan 404, lewat pemetaan lama).
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        Page<User> users = userRepository.findAll(PageRequest.of(safePage, safeSize));
        
        Page<UserDto> userDtos = users.map(user -> {
            var identities = userIdentityRepository.findAllByUser(user)
                    .stream()
                    .map(ui -> ui.getProvider())
                    .toList();
            
            return UserDto.builder()
                    .id(user.getId())
                    .fullName(user.getFullName())
                    .email(user.getEmail())
                    .avatarUrl(user.getAvatarUrl())
                    .status(user.getStatus())
                    .emailVerified(user.isEmailVerified())
                    .globalRole(user.getGlobalRole())
                    .identities(identities)
                    .build();
        });
        
        return ResponseEntity.ok(userDtos);
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<UserDto> updateUserStatus(
            @PathVariable UUID userId,
            @RequestParam UserStatus status) {
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pengguna tidak ditemukan."));
        
        user.setStatus(status);
        userRepository.save(user);
        
        var identities = userIdentityRepository.findAllByUser(user)
                .stream()
                .map(ui -> ui.getProvider())
                .toList();
        
        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .globalRole(user.getGlobalRole())
                .identities(identities)
                .build();
        
        return ResponseEntity.ok(userDto);
    }
}
