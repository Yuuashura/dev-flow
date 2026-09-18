package com.saas.auth_service.service;

import com.saas.auth_service.config.JwtProperties;
import com.saas.auth_service.dto.*;
import com.saas.auth_service.entity.*;
import com.saas.auth_service.event.EmailVerificationRequestedEvent;
import com.saas.auth_service.event.EventPublisher;
import com.saas.auth_service.exception.*;
import com.saas.auth_service.repository.*;
import com.saas.auth_service.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final EventPublisher eventPublisher;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /** Kolom users.email varchar(255). @Email sendiri menerima sampai 320 karakter
     *  (batas RFC), jadi alamat yang sah secara sintaks tapi kepanjangan lolos sampai
     *  driver dan muncul sebagai 500. */
    static final int MAX_EMAIL_LENGTH = 255;

    /** BCrypt hanya memakai 72 byte pertama dan melempar IllegalArgumentException di
     *  atas itu. Dibatasi di sini supaya jadi 400 berbahasa Indonesia, bukan pesan
     *  internal dari library. Dihitung dalam byte, bukan karakter: satu emoji = 4 byte. */
    static final int MAX_PASSWORD_BYTES = 72;

    static final int MIN_PASSWORD_LENGTH = 8;

    /** Rujukan gambar hanya boleh menunjuk media yang kita simpan sendiri.
     *
     *  <p>Sebelumnya kolom ini menerima URL apa pun. Itu berarti setiap orang yang
     *  memuat halaman menembak host pilihan orang lain — pelacakan pihak ketiga
     *  tanpa persetujuan, dan isinya bisa diganti jadi apa saja setelah lolos
     *  pemeriksaan. Sekarang hanya path media internal yang diterima, jadi tidak ada
     *  jalan memutari pemeriksaan unggahan dengan menaruh URL langsung lewat API.
     */
    static final java.util.regex.Pattern MEDIA_REF = java.util.regex.Pattern.compile(
            "^/api/v1/media/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    static String validateImageRef(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!MEDIA_REF.matcher(value).matches()) {
            throw new AppException(
                    "Foto profil harus diunggah, bukan diisi tautan.",
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    /** Dipakai jalur register DAN ganti password. Tanpa helper bersama, membatasi satu
     *  jalur saja meninggalkan yang lain tetap melempar 500 dari BCrypt. */
    static void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new AppException("Password wajib diisi.", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new AppException("Password minimal " + MIN_PASSWORD_LENGTH + " karakter.",
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        }
        int bytes = password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes > MAX_PASSWORD_BYTES) {
            throw new AppException(
                    "Password terlalu panjang (maksimal " + MAX_PASSWORD_BYTES + " byte).",
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        }
    }

    /** Mengembalikan email yang sudah dinormalkan (lowercase + trim). */
    static String validateEmail(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (email.isEmpty()) {
            throw new AppException("Email wajib diisi.", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        }
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new AppException("Email maksimal " + MAX_EMAIL_LENGTH + " karakter.",
                    "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        }
        return email;
    }

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new AppException("Passwords do not match", "PASSWORD_MISMATCH", HttpStatus.BAD_REQUEST);
        }

        String email = validateEmail(request.getEmail());
        validatePassword(request.getPassword());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(savedUser)
                .token(token)
                .expiresAt(expiresAt)
                .build();

        emailVerificationTokenRepository.save(verificationToken);

        String verificationUrl = frontendUrl + "/verify-email?token=" + token;

        eventPublisher.publishEmailVerificationRequested(
                EmailVerificationRequestedEvent.builder()
                        .userId(savedUser.getId())
                        .email(savedUser.getEmail())
                        .fullName(savedUser.getFullName())
                        .verificationToken(token)
                        .verificationUrl(verificationUrl)
                        .expiresAt(expiresAt)
                        .build()
        );

        return MessageResponse.builder()
                .message("Registrasi berhasil. Silakan cek email Anda untuk verifikasi.")
                .build();
    }

    @Transactional
    public MessageResponse verifyEmail(String token) {
        Optional<EmailVerificationToken> unusedToken = emailVerificationTokenRepository.findByTokenAndUsedAtIsNull(token);

        if (unusedToken.isPresent()) {
            EmailVerificationToken verificationToken = unusedToken.get();

            if (verificationToken.getExpiresAt().isBefore(Instant.now())) {
                throw new TokenInvalidException("Link verifikasi telah kadaluarsa. Silakan minta link verifikasi baru.");
            }

            verificationToken.setUsedAt(Instant.now());
            emailVerificationTokenRepository.save(verificationToken);

            User user = verificationToken.getUser();
            user.setEmailVerified(true);
            userRepository.save(user);

            return MessageResponse.builder()
                    .message("Email berhasil diverifikasi. Silakan login.")
                    .build();
        }

        // Token sudah pernah dipakai. Bersikap idempotent: jika user terkait sudah terverifikasi, anggap sukses.
        return emailVerificationTokenRepository.findByToken(token)
                .filter(verificationToken -> verificationToken.getUser().isEmailVerified())
                .map(verificationToken -> MessageResponse.builder()
                        .message("Email Anda sudah terverifikasi sebelumnya. Silakan login.")
                        .build())
                .orElseThrow(() -> new TokenInvalidException("Link verifikasi tidak valid atau telah digunakan"));
    }

    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));

        if (user.isEmailVerified()) {
            return MessageResponse.builder()
                    .message("Email sudah terverifikasi. Silakan login.")
                    .build();
        }

        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .token(token)
                .expiresAt(expiresAt)
                .build();

        emailVerificationTokenRepository.save(verificationToken);

        String verificationUrl = frontendUrl + "/verify-email?token=" + token;

        eventPublisher.publishEmailVerificationRequested(
                EmailVerificationRequestedEvent.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .verificationToken(token)
                        .verificationUrl(verificationUrl)
                        .expiresAt(expiresAt)
                        .build()
        );

        return MessageResponse.builder()
                .message("Link verifikasi baru telah dikirim ke email Anda.")
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent, String ipAddress) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException("Akun Anda telah dinonaktifkan", "ACCOUNT_DISABLED", HttpStatus.FORBIDDEN);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getGlobalRole() != null ? user.getGlobalRole().name() : null);

        String rawRefreshToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawRefreshToken);

        RefreshSession refreshSession = RefreshSession.builder()
                .user(user)
                .tokenHash(hashedToken)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .expiresAt(Instant.now().plus(jwtProperties.getRefreshTokenExpirationMs(), ChronoUnit.MILLIS))
                .build();

        refreshSessionRepository.save(refreshSession);

        List<UserIdentity> identities = userIdentityRepository.findAllByUser(user);
        List<OAuthProvider> providers = identities.stream().map(UserIdentity::getProvider).toList();

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .globalRole(user.getGlobalRole()).identities(providers)
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .user(userDto)
                .build();
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String hashedToken = hashToken(request.getRefreshToken());

        RefreshSession session = refreshSessionRepository.findByTokenHashAndRevokedAtIsNull(hashedToken)
                .orElseThrow(() -> new TokenInvalidException("Refresh token tidak valid atau telah dicabut"));

        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setRevokedAt(Instant.now());
            refreshSessionRepository.save(session);
            throw new TokenInvalidException("Refresh token telah kadaluarsa. Silakan login kembali.");
        }

        User user = session.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException("Akun Anda telah dinonaktifkan", "ACCOUNT_DISABLED", HttpStatus.FORBIDDEN);
        }

        session.setLastUsedAt(Instant.now());
        refreshSessionRepository.save(session);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getGlobalRole() != null ? user.getGlobalRole().name() : null);

        List<UserIdentity> identities = userIdentityRepository.findAllByUser(user);
        List<OAuthProvider> providers = identities.stream().map(UserIdentity::getProvider).toList();

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .globalRole(user.getGlobalRole()).identities(providers)
                .build();

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .user(userDto)
                .build();
    }

    @Transactional
    public MessageResponse logout(RefreshTokenRequest request) {
        String hashedToken = hashToken(request.getRefreshToken());

        refreshSessionRepository.findByTokenHashAndRevokedAtIsNull(hashedToken).ifPresent(session -> {
            session.setRevokedAt(Instant.now());
            refreshSessionRepository.save(session);
        });

        return MessageResponse.builder()
                .message("Logout berhasil")
                .build();
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));

        List<UserIdentity> identities = userIdentityRepository.findAllByUser(user);
        List<OAuthProvider> providers = identities.stream().map(UserIdentity::getProvider).toList();

        return toUserDto(user, providers);
    }

    @Transactional
    public UserDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));
        user.setFullName(request.getFullName().trim());
        user.setPhoneNumber(trimToNull(request.getPhoneNumber()));
        user.setJobTitle(trimToNull(request.getJobTitle()));
        user.setCompanyName(trimToNull(request.getCompanyName()));
        user.setCity(trimToNull(request.getCity()));
        user.setBio(trimToNull(request.getBio()));
        user.setAvatarUrl(validateImageRef(request.getAvatarUrl()));
        userRepository.save(user);

        List<OAuthProvider> providers = userIdentityRepository.findAllByUser(user).stream()
                .map(UserIdentity::getProvider)
                .toList();
        return toUserDto(user, providers);
    }

    private UserDto toUserDto(User user, List<OAuthProvider> providers) {
        return UserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .phoneNumber(user.getPhoneNumber())
                .jobTitle(user.getJobTitle())
                .companyName(user.getCompanyName())
                .city(user.getCity())
                .bio(user.getBio())
                .profileComplete(user.getPhoneNumber() != null && user.getJobTitle() != null && user.getCity() != null)
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .globalRole(user.getGlobalRole())
                .identities(providers)
                .build();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    // ==================== SECURITY ====================

    @Transactional
    public MessageResponse changePassword(UUID userId, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AppException("Konfirmasi password tidak cocok", "PASSWORD_MISMATCH", HttpStatus.BAD_REQUEST);
        }
        validatePassword(request.getNewPassword());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));

        // Akun yang dibuat lewat OAuth belum punya password sama sekali. Menyuruhnya
        // mengisi "password saat ini" tidak masuk akal, jadi ditolak dengan pesan jelas.
        if (user.getPasswordHash() == null) {
            throw new AppException(
                    "Akun Anda masuk lewat Google/GitHub dan belum memiliki password.",
                    "NO_PASSWORD_SET", HttpStatus.BAD_REQUEST);
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AppException("Password saat ini salah", "INVALID_CURRENT_PASSWORD", HttpStatus.BAD_REQUEST);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new AppException("Password baru harus berbeda dari password lama",
                    "PASSWORD_UNCHANGED", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Ganti password harus mengusir perangkat lain: kalau ada yang mencuri sesi,
        // itulah gunanya. Sesi perangkat ini sendiri dipulihkan agar tidak ikut logout.
        refreshSessionRepository.revokeAllByUser(user, Instant.now());
        if (request.getCurrentRefreshToken() != null && !request.getCurrentRefreshToken().isBlank()) {
            refreshSessionRepository.findByTokenHash(hashToken(request.getCurrentRefreshToken()))
                    .filter(session -> session.getUser().getId().equals(userId))
                    .ifPresent(session -> {
                        session.setRevokedAt(null);
                        refreshSessionRepository.save(session);
                    });
        }

        return MessageResponse.builder()
                .message("Password berhasil diubah. Perangkat lain telah dikeluarkan.")
                .build();
    }

    @Transactional(readOnly = true)
    public List<SessionDto> getSessions(UUID userId, String currentRefreshToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));

        String currentHash = currentRefreshToken != null && !currentRefreshToken.isBlank()
                ? hashToken(currentRefreshToken) : null;

        return refreshSessionRepository
                .findAllByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(user, Instant.now())
                .stream()
                .map(session -> SessionDto.builder()
                        .id(session.getId())
                        .userAgent(session.getUserAgent())
                        .ipAddress(session.getIpAddress())
                        .createdAt(session.getCreatedAt())
                        .lastUsedAt(session.getLastUsedAt())
                        .expiresAt(session.getExpiresAt())
                        .current(currentHash != null && currentHash.equals(session.getTokenHash()))
                        .build())
                .toList();
    }

    @Transactional
    public MessageResponse revokeSession(UUID userId, UUID sessionId) {
        RefreshSession session = refreshSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesi tidak ditemukan"));

        // Tanpa cek ini, siapa pun yang tahu sebuah UUID sesi bisa mencabut sesi
        // milik pengguna lain.
        if (!session.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Sesi tidak ditemukan");
        }

        if (session.getRevokedAt() == null) {
            session.setRevokedAt(Instant.now());
            refreshSessionRepository.save(session);
        }

        return MessageResponse.builder().message("Sesi berhasil dicabut").build();
    }

    private String hashToken(String token) {        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
