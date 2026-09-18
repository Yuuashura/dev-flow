package com.saas.auth_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.saas.auth_service.repository.EmailVerificationTokenRepository;
import com.saas.auth_service.repository.OAuthStateRepository;
import com.saas.auth_service.repository.RefreshSessionRepository;

import java.time.Instant;

/**
 * Menjalankan tiga query `deleteExpired` yang sudah ada di repository.
 *
 * <p>Ketiganya sudah ditulis sejak lama — di {@code RefreshSessionRepository},
 * {@code EmailVerificationTokenRepository}, dan {@code OAuthStateRepository} — tapi
 * <b>tidak ada satu pun yang pernah memanggilnya</b>, dan auth-service tidak punya
 * {@code @EnableScheduling} sama sekali. Jadi baris kedaluwarsa tidak pernah hilang:
 * setiap login menambah satu sesi, setiap permintaan verifikasi email menambah satu
 * token, setiap percobaan OAuth menambah satu state, dan tidak ada yang menghapusnya.
 *
 * <p>Ini menyentuh hal yang lebih besar daripada ukuran tabel. {@code getSessions}
 * membaca <i>semua</i> sesi yang belum dicabut lalu membuang yang kedaluwarsa di
 * memori — jadi daftar perangkat seseorang yang login tiap hari selama setahun
 * menarik 365 baris untuk menampilkan satu. Menghapus di sumbernya memperbaiki
 * keduanya sekaligus, dan tidak menambah satu pun query baru.
 *
 * <p>Dijalankan sekali sejam, bukan sekali semenit: tidak ada yang mendesak soal
 * baris kedaluwarsa, dan DELETE per jam tidak bersaing dengan trafik login.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiredRecordCleanup {

    private final RefreshSessionRepository refreshSessionRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final OAuthStateRepository oauthStateRepository;

    @Scheduled(fixedDelayString = "${app.cleanup.interval-ms:3600000}", initialDelay = 60_000)
    @Transactional
    public void purgeExpired() {
        Instant now = Instant.now();

        // OAuth state hidup hitungan menit, jadi hampir semua baris lama di sini
        // adalah percobaan login yang ditinggalkan di tengah jalan.
        int sessions = refreshSessionRepository.deleteExpired(now);
        int emailTokens = emailVerificationTokenRepository.deleteExpired(now);
        int oauthStates = oauthStateRepository.deleteExpired(now);

        if (sessions + emailTokens + oauthStates > 0) {
            log.info("Pembersihan kedaluwarsa: {} sesi, {} token email, {} state OAuth",
                    sessions, emailTokens, oauthStates);
        }
    }
}
