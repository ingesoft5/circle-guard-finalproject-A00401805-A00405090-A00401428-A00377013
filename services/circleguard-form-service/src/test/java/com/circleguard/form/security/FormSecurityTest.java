package com.circleguard.form.security;

import com.circleguard.form.controller.QuestionnaireController;
import com.circleguard.form.service.QuestionnaireService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
    @MockBean QuestionnaireService questionnaires;

    private String token(String permission) {
        return Jwts.builder().setSubject("550e8400-e29b-41d4-a716-446655440000")
                .claim("permissions", List.of(permission))
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
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
}
