package com.ktb.chatapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.storage.StoragePort;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;

class ProfileImageOffloadTest {
    @Test
    void s3CapableStorageRedirectsWithoutChangingPublicProfileUrl() {
        StoragePort storage = mock(StoragePort.class);
        URI signed = URI.create("https://signed.example/profile?signature=test");
        when(storage.offloadUrl(eq("profiles/avatar.png"), any(), any(ContentDisposition.class)))
                .thenReturn(Optional.of(signed));

        var response = new ProfileImageController(storage).getProfileImage("avatar.png");

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation()).isEqualTo(signed);
    }
}
