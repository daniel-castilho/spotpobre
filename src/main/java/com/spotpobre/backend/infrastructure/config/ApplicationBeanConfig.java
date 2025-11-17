package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.application.artist.port.in.CreateArtistUseCase;
import com.spotpobre.backend.application.artist.port.in.SearchArtistsUseCase;
import com.spotpobre.backend.application.artist.service.CreateArtistService;
import com.spotpobre.backend.application.artist.service.SearchArtistsService;
import com.spotpobre.backend.application.like.port.in.ToggleLikeUseCase;
import com.spotpobre.backend.application.like.service.LikeStrategy;
import com.spotpobre.backend.application.like.service.LikeStrategyFactory;
import com.spotpobre.backend.application.like.service.ToggleLikeService;
import com.spotpobre.backend.application.playlist.port.in.*;
import com.spotpobre.backend.application.playlist.service.*;
import com.spotpobre.backend.application.song.port.in.*;
import com.spotpobre.backend.application.song.service.*;
import com.spotpobre.backend.application.user.port.in.AuthenticateUserUseCase;
import com.spotpobre.backend.application.user.port.in.GetUserDetailsUseCase;
import com.spotpobre.backend.application.user.port.in.GetUserProfileUseCase;
import com.spotpobre.backend.application.user.port.in.RegisterUserUseCase;
import com.spotpobre.backend.application.user.service.AuthenticationService;
import com.spotpobre.backend.application.user.service.GetUserDetailsUseCaseService;
import com.spotpobre.backend.application.user.service.GetUserProfileService;
import com.spotpobre.backend.application.user.service.RegisterUserService;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.like.port.LikeRepository;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
public class ApplicationBeanConfig {

    // Playlist Use Cases
    @Bean
    public CreatePlaylistUseCase createPlaylistUseCase(
            final UserRepository userRepository,
            final PlaylistRepository playlistRepository
    ) {
        return new CreatePlaylistService(userRepository, playlistRepository);
    }

    @Bean
    public AddSongToPlaylistUseCase addSongToPlaylistUseCase(
            final PlaylistRepository playlistRepository,
            final SongMetadataRepository songMetadataRepository
    ) {
        return new AddSongToPlaylistService(playlistRepository, songMetadataRepository);
    }

    @Bean
    public GetPlaylistDetailsUseCase getPlaylistDetailsUseCase(final PlaylistRepository playlistRepository) {
        return new GetPlaylistDetailsService(playlistRepository);
    }

    @Bean
    public GetPlaylistsByOwnerUseCase getPlaylistsByOwnerUseCase(final PlaylistRepository playlistRepository) {
        return new GetPlaylistsByOwnerService(playlistRepository);
    }

    @Bean
    public UpdatePlaylistDetailsUseCase updatePlaylistDetailsUseCase(final PlaylistRepository playlistRepository) {
        return new UpdatePlaylistDetailsService(playlistRepository);
    }

    @Bean
    public RemoveSongFromPlaylistUseCase removeSongFromPlaylistUseCase(final PlaylistRepository playlistRepository) {
        return new RemoveSongFromPlaylistService(playlistRepository);
    }

    @Bean
    public DeletePlaylistUseCase deletePlaylistUseCase(final PlaylistRepository playlistRepository) {
        return new DeletePlaylistService(playlistRepository);
    }

    // Song Use Cases
    @Bean
    public UploadSongUseCase uploadSongUseCase(
            final SongStoragePort songStoragePort,
            final SongMetadataRepository songMetadataRepository
    ) {
        return new UploadSongService(songStoragePort, songMetadataRepository);
    }

    @Bean
    public GetSongMetadataUseCase getSongMetadataUseCase(final SongMetadataRepository songMetadataRepository) {
        return new GetSongMetadataService(songMetadataRepository);
    }

    @Bean
    public GetSongStreamUrlUseCase getSongStreamUrlUseCase(
            final SongMetadataRepository songMetadataRepository,
            final SongStoragePort songStoragePort
    ) {
        return new GetSongStreamUrlService(songMetadataRepository, songStoragePort);
    }

    @Bean
    public SearchSongsUseCase searchSongsUseCase(final SongMetadataRepository songMetadataRepository) {
        return new SearchSongsService(songMetadataRepository);
    }

    // User Use Cases
    @Bean
    public GetUserDetailsUseCase getUserDetailsUseCase(final UserRepository userRepository) {
        return new GetUserDetailsUseCaseService(userRepository);
    }

    @Bean
    public RegisterUserUseCase registerUserUseCase(
            final UserRepository userRepository,
            final PasswordEncoder passwordEncoder
    ) {
        return new RegisterUserService(userRepository, passwordEncoder);
    }

    @Bean
    public AuthenticateUserUseCase authenticateUserUseCase(
            final AuthenticationManager authenticationManager,
            final UserRepository userRepository
    ) {
        return new AuthenticationService(authenticationManager, userRepository);
    }

    @Bean
    public GetUserProfileUseCase getUserProfileUseCase(final UserRepository userRepository) {
        return new GetUserProfileService(userRepository);
    }

    // Artist Use Cases
    @Bean
    public CreateArtistUseCase createArtistUseCase(final ArtistRepository artistRepository) {
        return new CreateArtistService(artistRepository);
    }

    @Bean
    public SearchArtistsUseCase searchArtistsUseCase(final ArtistRepository artistRepository) {
        return new SearchArtistsService(artistRepository);
    }

    // Like Use Cases
    @Bean
    public LikeStrategyFactory likeStrategyFactory(List<LikeStrategy> strategies) {
        return new LikeStrategyFactory(strategies);
    }

    @Bean
    public ToggleLikeUseCase toggleLikeUseCase(
            LikeRepository likeRepository,
            LikeStrategyFactory likeStrategyFactory
    ) {
        return new ToggleLikeService(likeRepository, likeStrategyFactory);
    }
}
