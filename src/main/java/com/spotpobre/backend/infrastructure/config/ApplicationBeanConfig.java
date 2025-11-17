package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.application.playlist.port.in.AddSongToPlaylistUseCase;
import com.spotpobre.backend.application.playlist.port.in.CreatePlaylistUseCase;
import com.spotpobre.backend.application.playlist.port.in.GetPlaylistDetailsUseCase;
import com.spotpobre.backend.application.playlist.port.in.GetPlaylistsUseCase; // Import GetPlaylistsUseCase
import com.spotpobre.backend.application.playlist.service.AddSongToPlaylistService;
import com.spotpobre.backend.application.playlist.service.CreatePlaylistService;
import com.spotpobre.backend.application.playlist.service.GetPlaylistDetailsService;
import com.spotpobre.backend.application.playlist.service.GetPlaylistsService; // Import GetPlaylistsService
import com.spotpobre.backend.application.song.port.in.GetSongMetadataUseCase;
import com.spotpobre.backend.application.song.port.in.GetSongStreamUrlUseCase;
import com.spotpobre.backend.application.song.port.in.UploadSongUseCase;
import com.spotpobre.backend.application.song.service.GetSongMetadataService;
import com.spotpobre.backend.application.song.service.GetSongStreamUrlService;
import com.spotpobre.backend.application.song.service.UploadSongService;
import com.spotpobre.backend.application.user.port.in.AuthenticateUserUseCase;
import com.spotpobre.backend.application.user.port.in.GetUserDetailsUseCase;
import com.spotpobre.backend.application.user.port.in.GetUserProfileUseCase;
import com.spotpobre.backend.application.user.port.in.RegisterUserUseCase;
import com.spotpobre.backend.application.user.service.AuthenticationService;
import com.spotpobre.backend.application.user.service.GetUserDetailsUseCaseService;
import com.spotpobre.backend.application.user.service.GetUserProfileService;
import com.spotpobre.backend.application.user.service.RegisterUserService;
import com.spotpobre.backend.application.artist.port.in.CreateArtistUseCase;
import com.spotpobre.backend.application.artist.service.CreateArtistService;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import com.spotpobre.backend.domain.user.port.UserRepository;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    public GetPlaylistsUseCase getPlaylistsUseCase(final PlaylistRepository playlistRepository) {
        return new GetPlaylistsService(playlistRepository);
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
}
