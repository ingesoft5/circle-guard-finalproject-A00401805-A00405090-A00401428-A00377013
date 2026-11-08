package com.circleguard.auth.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class IdentityClientTest {

    @Test
    void shouldMapRealIdentityToAnonymousUuidViaRestCall() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://identity-service:8083/api/v1/identities/map";
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

        UUID expectedUuid = UUID.randomUUID();
        mockServer.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.realIdentity").value("student_01"))
                .andRespond(withSuccess(
                        "{\"anonymousId\": \"" + expectedUuid + "\"}",
                        MediaType.APPLICATION_JSON
                ));

        IdentityClient identityClient = new IdentityClient(restTemplate, url);
        UUID actualUuid = identityClient.getAnonymousId("student_01");

        assertEquals(expectedUuid, actualUuid);
        mockServer.verify();
    }

    @Test
    void shouldConstructWithDefaultRestTemplate() {
        IdentityClient client = new IdentityClient("http://localhost:8083/api/v1/identities/map");
        assertNotNull(client);
    }
}
