/**
 * CG-DOC: business service for the notification service: Lms Service Impl.
 */
package com.circleguard.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class LmsServiceImpl implements LmsService {

    @Value("${lms.api.url:https://lms.university.edu/api/v1}")
    private String lmsApiUrl;

    @Value("${identity.api.url:http://circleguard-identity-service:8081}")
    private String identityApiUrl;

    @Override
    @Async
    public CompletableFuture<Void> syncRemoteAttendance(String userId, String status) {
        log.info("LMS Sync: Resolving identity via Identity Vault");
        
        log.info("LMS Sync: Remote attendance requested");
        
        // In a real implementation, this would use WebClient to call the LMS API or LTI provider
        // e.g., webClient.post().uri("/attendance/remote").bodyValue(Map.of("userId", realIdentity, "status", status))...

        return CompletableFuture.completedFuture(null);
    }
}
