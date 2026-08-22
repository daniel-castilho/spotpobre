package com.spotpobre.backend.application.artist.service;

import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.domain.artist.model.ArtistAccount;
import com.spotpobre.backend.domain.artist.model.ArtistId;
import com.spotpobre.backend.domain.artist.model.ArtistPermission;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.domain.common.ForbiddenException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtistAccessServiceTest {

    @Mock
    private ArtistAccountRepository artistAccountRepository;

    @InjectMocks
    private ArtistAccessService artistAccessService;

    private final ArtistId artistId = ArtistId.generate();
    private final UUID userId = UUID.randomUUID();

    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void attachLogAppender() {
        serviceLogger = (Logger) LoggerFactory.getLogger(ArtistAccessService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        serviceLogger.detachAppender(logAppender);
    }

    @Test
    void adminBypassesMembershipCheck() {
        assertDoesNotThrow(() -> artistAccessService.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(userId, true), artistId));

        assertEquals(1, logAppender.list.size());
        ILoggingEvent overrideEvent = logAppender.list.get(0);
        assertEquals(Level.INFO, overrideEvent.getLevel());
        assertTrue(overrideEvent.getFormattedMessage().contains("admin_override"));
        assertTrue(overrideEvent.getFormattedMessage().contains(artistId.value().toString()));
    }

    @Test
    void memberWithOwnerPermissionHasAccess() {
        when(artistAccountRepository.find(artistId, userId))
                .thenReturn(Optional.of(ArtistAccount.owner(artistId, userId, Instant.now())));
        assertDoesNotThrow(() -> artistAccessService.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(userId, false), artistId));
    }

    @Test
    void memberWithManagerPermissionHasAccess() {
        when(artistAccountRepository.find(artistId, userId))
                .thenReturn(Optional.of(ArtistAccount.manager(artistId, userId, Instant.now())));
        assertDoesNotThrow(() -> artistAccessService.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(userId, false), artistId));
    }

    @Test
    void nonMemberIsForbidden() {
        when(artistAccountRepository.find(artistId, userId)).thenReturn(Optional.empty());
        assertThrows(ForbiddenException.class, () -> artistAccessService.requireAccess(
                new RequireArtistAccessUseCase.ActorArtistRef(userId, false), artistId));

        assertEquals(1, logAppender.list.size());
        ILoggingEvent denialEvent = logAppender.list.get(0);
        assertEquals(Level.WARN, denialEvent.getLevel());
        assertTrue(denialEvent.getFormattedMessage().contains("denied"));
        assertTrue(denialEvent.getFormattedMessage().contains(artistId.value().toString()));
    }
}
