package com.spotpobre.backend.infrastructure.config;

import com.spotpobre.backend.application.album.port.in.CreateAlbumUseCase;
import com.spotpobre.backend.application.album.service.CreateAlbumService;
import com.spotpobre.backend.application.artist.port.in.CreateArtistUseCase;
import com.spotpobre.backend.application.artist.port.in.GrantArtistAccountUseCase;
import com.spotpobre.backend.application.artist.port.in.RequireArtistAccessUseCase;
import com.spotpobre.backend.application.artist.port.in.RevokeArtistAccountUseCase;
import com.spotpobre.backend.application.artist.port.in.SearchArtistsUseCase;
import com.spotpobre.backend.application.artist.service.ArtistAccessService;
import com.spotpobre.backend.application.artist.service.CreateArtistService;
import com.spotpobre.backend.application.artist.service.GrantArtistAccountService;
import com.spotpobre.backend.application.artist.service.RevokeArtistAccountService;
import com.spotpobre.backend.application.artist.service.SearchArtistsService;
import com.spotpobre.backend.application.like.port.in.PutLikeUseCase;
import com.spotpobre.backend.application.like.port.in.DeleteLikeUseCase;
import com.spotpobre.backend.application.like.service.LikeStrategy;
import com.spotpobre.backend.application.like.service.LikeStrategyFactory;
import com.spotpobre.backend.application.like.service.PutLikeService;
import com.spotpobre.backend.application.like.service.DeleteLikeService;
import com.spotpobre.backend.application.playlist.port.in.AddSongToPlaylistUseCase;
import com.spotpobre.backend.application.playlist.port.in.CreatePlaylistUseCase;
import com.spotpobre.backend.application.playlist.port.in.DeletePlaylistUseCase;
import com.spotpobre.backend.application.playlist.port.in.GetPlaylistDetailsUseCase;
import com.spotpobre.backend.application.playlist.port.in.GetPlaylistsByOwnerUseCase;
import com.spotpobre.backend.application.playlist.port.in.RemoveSongFromPlaylistUseCase;
import com.spotpobre.backend.application.playlist.port.in.UpdatePlaylistDetailsUseCase;
import com.spotpobre.backend.application.playlist.service.AddSongToPlaylistService;
import com.spotpobre.backend.application.playlist.service.CreatePlaylistService;
import com.spotpobre.backend.application.playlist.service.DeletePlaylistService;
import com.spotpobre.backend.application.playlist.service.GetPlaylistDetailsService;
import com.spotpobre.backend.application.playlist.service.GetPlaylistsByOwnerService;
import com.spotpobre.backend.application.playlist.service.RemoveSongFromPlaylistService;
import com.spotpobre.backend.application.playlist.service.UpdatePlaylistDetailsService;
import com.spotpobre.backend.application.song.port.in.ConfirmSongUploadUseCase;
import com.spotpobre.backend.application.song.port.in.GetSongMetadataUseCase;
import com.spotpobre.backend.application.song.port.in.GetSongStreamUrlUseCase;
import com.spotpobre.backend.application.song.port.in.InitiateSongUploadUseCase;
import com.spotpobre.backend.application.song.port.in.SearchSongsUseCase;
import com.spotpobre.backend.application.song.service.ConfirmSongUploadService;
import com.spotpobre.backend.application.song.service.GetSongMetadataService;
import com.spotpobre.backend.application.song.service.GetSongStreamUrlService;
import com.spotpobre.backend.application.song.service.InitiateSongUploadService;
import com.spotpobre.backend.application.song.service.SearchSongsService;
import com.spotpobre.backend.application.user.port.in.AuthenticateUserUseCase;
import com.spotpobre.backend.application.user.port.in.GetUserDetailsUseCase;
import com.spotpobre.backend.application.user.port.in.GetUserProfileUseCase;
import com.spotpobre.backend.application.user.port.in.RegisterUserUseCase;
import com.spotpobre.backend.application.user.service.AuthenticationService;
import com.spotpobre.backend.application.user.service.GetCurrentUserService;
import com.spotpobre.backend.application.user.service.GetUserDetailsService;
import com.spotpobre.backend.application.user.port.in.GetCurrentUserUseCase;
import com.spotpobre.backend.application.user.service.GetUserProfileService;
import com.spotpobre.backend.application.user.service.RegisterUserService;
import com.spotpobre.backend.domain.album.port.AlbumRepository;
import com.spotpobre.backend.domain.artist.port.ArtistAccountRepository;
import com.spotpobre.backend.domain.artist.port.ArtistRepository;
import com.spotpobre.backend.domain.like.port.LikeRepository;
import com.spotpobre.backend.domain.playlist.port.PlaylistRepository;
import com.spotpobre.backend.domain.song.port.SongMetadataRepository;
import com.spotpobre.backend.domain.song.port.SongStoragePort;
import com.spotpobre.backend.domain.user.port.AuthenticationPort;
import com.spotpobre.backend.domain.user.port.PasswordHasher;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
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
    public InitiateSongUploadUseCase initiateSongUploadUseCase(
            final SongStoragePort songStoragePort,
            final SongMetadataRepository songMetadataRepository,
            final AlbumRepository albumRepository,
            final RequireArtistAccessUseCase requireArtistAccessUseCase
    ) {
        return new InitiateSongUploadService(songStoragePort, songMetadataRepository, albumRepository, requireArtistAccessUseCase);
    }

    @Bean
    public ConfirmSongUploadUseCase confirmSongUploadUseCase(
            final SongStoragePort songStoragePort,
            final SongMetadataRepository songMetadataRepository,
            final AlbumRepository albumRepository,
            final RequireArtistAccessUseCase requireArtistAccessUseCase
    ) {
        return new ConfirmSongUploadService(songStoragePort, songMetadataRepository, albumRepository, requireArtistAccessUseCase);
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
        return new GetUserDetailsService(userRepository);
    }

    @Bean
    public RegisterUserUseCase registerUserUseCase(
            final UserRepository userRepository,
            final PasswordHasher passwordHasher
    ) {
        return new RegisterUserService(userRepository, passwordHasher);
    }

    @Bean
    public AuthenticateUserUseCase authenticateUserUseCase(final AuthenticationPort authenticationPort) {
        return new AuthenticationService(authenticationPort);
    }

    @Bean
    public GetUserProfileUseCase getUserProfileUseCase(final UserRepository userRepository) {
        return new GetUserProfileService(userRepository);
    }

    @Bean
    public GetCurrentUserUseCase getCurrentUserUseCase(final UserRepository userRepository) {
        return new GetCurrentUserService(userRepository);
    }

    // Artist Use Cases
    @Bean
    public CreateArtistUseCase createArtistUseCase(
            final ArtistRepository artistRepository,
            final UserRepository userRepository
    ) {
        return new CreateArtistService(artistRepository, userRepository);
    }

    @Bean
    public RequireArtistAccessUseCase requireArtistAccessUseCase(final ArtistAccountRepository artistAccountRepository) {
        return new ArtistAccessService(artistAccountRepository);
    }

    @Bean
    public GrantArtistAccountUseCase grantArtistAccountUseCase(
            final ArtistRepository artistRepository,
            final ArtistAccountRepository artistAccountRepository
    ) {
        return new GrantArtistAccountService(artistRepository, artistAccountRepository);
    }

    @Bean
    public RevokeArtistAccountUseCase revokeArtistAccountUseCase(final ArtistAccountRepository artistAccountRepository) {
        return new RevokeArtistAccountService(artistAccountRepository);
    }

    @Bean
    public SearchArtistsUseCase searchArtistsUseCase(final ArtistRepository artistRepository) {
        return new SearchArtistsService(artistRepository);
    }
    
    // Album Use Cases
    @Bean
    public CreateAlbumUseCase createAlbumUseCase(
            final ArtistRepository artistRepository,
            final AlbumRepository albumRepository,
            final RequireArtistAccessUseCase requireArtistAccessUseCase
    ) {
        return new CreateAlbumService(artistRepository, albumRepository, requireArtistAccessUseCase);
    }

    // Like Use Cases
    @Bean
    public LikeStrategyFactory likeStrategyFactory(List<LikeStrategy> strategies) {
        return new LikeStrategyFactory(strategies);
    }

    @Bean
    public PutLikeUseCase putLikeUseCase(
            LikeRepository likeRepository,
            LikeStrategyFactory likeStrategyFactory
    ) {
        return new PutLikeService(likeRepository, likeStrategyFactory);
    }

    @Bean
    public DeleteLikeUseCase deleteLikeUseCase(
            LikeRepository likeRepository,
            LikeStrategyFactory likeStrategyFactory
    ) {
        return new DeleteLikeService(likeRepository, likeStrategyFactory);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
