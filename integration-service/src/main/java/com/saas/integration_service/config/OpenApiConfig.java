package com.saas.integration_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spesifikasi OpenAPI service ini, disajikan Swagger UI milik api-gateway.
 *
 * <p>Dua hal di sini tidak bisa diserahkan ke nilai bawaan springdoc.
 *
 * <p><b>servers.</b> Kalau dibiarkan, springdoc menyusun daftar server dari permintaan
 * yang kebetulan diterimanya — jadi tombol "Try it out" menembak port service langsung
 * dan melewati gateway. Dokumentasinya lalu memperagakan jalur yang bukan jalur
 * sebenarnya, dan menyembunyikan kesalahan rute gateway seperti yang pernah membuat
 * unggah gambar membalas 404 padahal endpoint-nya sehat di portnya sendiri. Alamatnya
 * dipaksa ke gateway, bisa ditimpa lewat APP_PUBLIC_URL saat dideploy.
 *
 * <p><b>bearerAuth.</b> Hampir semua endpoint menuntut JWT. Tanpa skema keamanan yang
 * dideklarasikan, tombol "Authorize" tidak muncul dan setiap percobaan dari UI berakhir
 * 401 tanpa penjelasan.
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.public-url:http://localhost:8080}")
    private String publicUrl;

    @Bean
    public OpenAPI openApi() {
        final String skema = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("DevFlow — Integration Service")
                        .version("1.0")
                        .description("""
                                Integrasi GitHub dan webhook ber-HMAC.

                                Ambil token lewat POST /api/v1/auth/login, lalu tempel di
                                tombol Authorize untuk mencoba endpoint yang terlindungi."""))
                .servers(List.of(new Server().url(publicUrl).description("api-gateway")))
                .components(new Components().addSecuritySchemes(skema, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token dari /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(skema));
    }
}
