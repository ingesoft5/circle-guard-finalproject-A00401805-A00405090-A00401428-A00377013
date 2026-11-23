/** CG-DOC: Tests for Form Security. */
package com.circleguard.form.security;

import com.circleguard.form.controller.QuestionnaireController;
import com.circleguard.form.service.QuestionnaireService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = QuestionnaireController.class, properties =
        "jwt.secret=test-only-secret-with-at-least-32-bytes")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FormSecurityTest {
    private static final String SECRET = "test-only-secret-with-at-least-32-bytes";
    @Autowired MockMvc mvc;
    @MockitoBean QuestionnaireService questionnaires;

    private String token(String permission) {
        return token("550e8400-e29b-41d4-a716-446655440000", List.of(permission), 60000);
    }

    private String token(String subject, Object permissions, long expirationOffsetMillis) {
        var builder = Jwts.builder();
        if (subject != null) {
            builder.setSubject(subject);
        }
        return builder.claim("permissions", permissions)
                .setExpiration(new Date(System.currentTimeMillis() + expirationOffsetMillis))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void onlyAdminCanCreateQuestionnaire() throws Exception {
        mvc.perform(post("/api/v1/questionnaires")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/questionnaires")
                .header("Authorization", "Bearer " + token("ROLE_VISITOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/questionnaires")
                .header("Authorization", "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isBadRequest()); // Authorized; JSON body is absent.
        verifyNoInteractions(questionnaires);
    }

    @Test
    void rejectsInvalidTokenAndKeepsActiveQuestionnairePublic() throws Exception {
        mvc.perform(post("/api/v1/questionnaires")
                .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/questionnaires/active")).andExpect(status().isNotFound());
    }

    @Test
    void ignoresAuthorizationHeaderWithoutBearerPrefix() throws Exception {
        mvc.perform(post("/api/v1/questionnaires")
                .header("Authorization", "Basic " + token("ROLE_ADMIN")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithBlankSubject() throws Exception {
        mvc.perform(post("/api/v1/questionnaires")
                .header("Authorization", "Bearer " + token("   ", List.of("ROLE_ADMIN"), 60000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithMissingSubject() throws Exception {
        mvc.perform(post("/api/v1/questionnaires")
                .header("Authorization", "Bearer " + token(null, List.of("ROLE_ADMIN"), 60000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithPermissionsThatAreNotAList() throws Exception {
        mvc.perform(post("/api/v1/questionnaires")
                .header("Authorization", "Bearer " + token("550e8400-e29b-41d4-a716-446655440000",
                        "ROLE_ADMIN", 60000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithNonStringPermission() throws Exception {
        mvc.perform(post("/api/v1/questionnaires")
                .header("Authorization", "Bearer " + token("550e8400-e29b-41d4-a716-446655440000",
                        List.of("ROLE_ADMIN", 42), 60000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        mvc.perform(post("/api/v1/questionnaires")
                .header("Authorization", "Bearer " + token("550e8400-e29b-41d4-a716-446655440000",
                        List.of("ROLE_ADMIN"), -60000)))
                .andExpect(status().isUnauthorized());
    }
}
