package org.dnd.session.share;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dnd.api.model.PublishSessionRequest;
import org.dnd.api.model.SessionResponse;
import org.dnd.api.model.SessionShareResponse;
import org.dnd.api.model.UpdateSessionShareRequest;
import org.dnd.exception.BadRequestException;
import org.dnd.exception.ConflictException;
import org.dnd.exception.ForbiddenException;
import org.dnd.exception.LimitReachedException;
import org.dnd.exception.NotFoundException;
import org.dnd.session.SessionEntity;
import org.dnd.session.SessionRepository;
import org.dnd.session.SessionService;
import org.dnd.user.UserEntity;
import org.dnd.user.UserMapper;
import org.dnd.user.UserRepository;
import org.dnd.user.rank.UserRankEvaluatorService;
import org.dnd.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionShareService {

  private final SessionShareRepository sessionShareRepository;
  private final SessionRepository sessionRepository;
  private final UserRepository userRepository;
  private final SessionService sessionService;
  private final SessionSnapshotFactory sessionSnapshotFactory;
  private final SessionMaterializer sessionMaterializer;
  private final SessionShareLifecycle sessionShareLifecycle;
  private final UserRankEvaluatorService userRankEvaluatorService;
  private final UserMapper userMapper;
  private final SecurityUtils securityUtils;

  @Transactional
  public SessionShareResponse publish(UUID sessionId, PublishSessionRequest request) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionEntity session = findPublishableSession(sessionId, userId);

    if (sessionShareRepository.findBySession_IdAndUnpublishedAtIsNull(sessionId).isPresent()) {
      throw new ConflictException("Session is already published");
    }

    SessionSnapshot snapshot = playableSnapshot(session);
    LocalDateTime now = LocalDateTime.now();
    SessionShareEntity share = new SessionShareEntity();
    share.setSession(session);
    share.setOwner(session.getOwner());
    share.setShareCode(generateUniqueShareCode());
    share.setDescription(request == null ? null : request.getDescription());
    share.setVersion(1);
    share.setName(session.getName());
    share.setSnapshot(snapshot);
    share.setPublishedAt(now);
    share.setUpdatedAt(now);

    SessionShareEntity saved = sessionShareRepository.save(share);
    log.info("Session {} published by user {}", sessionId, userId);
    return toResponse(saved, userId);
  }

  @Transactional
  public SessionShareResponse publishUpdate(UUID sessionId) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionEntity session = findPublishableSession(sessionId, userId);
    SessionShareEntity share = findActiveShare(sessionId);
    SessionSnapshot snapshot = playableSnapshot(session);

    share.setVersion(share.getVersion() + 1);
    share.setName(session.getName());
    share.setSnapshot(snapshot);
    share.setUpdatedAt(LocalDateTime.now());

    log.info("Session {} published as version {}", sessionId, share.getVersion());
    return toResponse(share, userId);
  }

  @Transactional
  public SessionShareResponse updateDescription(UUID sessionId, UpdateSessionShareRequest request) {
    UUID userId = securityUtils.getCurrentUserId();
    findPublishableSession(sessionId, userId);
    SessionShareEntity share = findActiveShare(sessionId);

    share.setDescription(request.getDescription());
    return toResponse(share, userId);
  }

  @Transactional
  public void unpublish(UUID sessionId) {
    UUID userId = securityUtils.getCurrentUserId();
    findPublishableSession(sessionId, userId);
    SessionShareEntity share = findActiveShare(sessionId);

    sessionShareLifecycle.unpublish(share);
    log.info("Session {} unpublished", sessionId);
  }

  @Transactional(readOnly = true)
  public List<SessionShareResponse> getPublishedSessions() {
    UUID userId = securityUtils.getCurrentUserId();
    return sessionShareRepository.findByUnpublishedAtIsNullOrderByUpdatedAtDesc().stream()
            .map(share -> toResponse(share, userId))
            .toList();
  }

  @Transactional
  public SessionResponse subscribe(String shareCode) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionShareEntity share = sessionShareRepository.findByShareCodeAndUnpublishedAtIsNull(shareCode)
            .orElseThrow(() -> new NotFoundException("Invalid share code: " + shareCode));

    if (share.getOwner().getId().equals(userId)) {
      throw new BadRequestException("You cannot subscribe to your own session");
    }
    if (sessionRepository.existsByOwner_IdAndSourceShare_Id(userId, share.getId())) {
      throw new ConflictException("You are already subscribed to this session");
    }

    UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    if (!userRankEvaluatorService.canSubscribeToSession(user)) {
      throw new LimitReachedException("Subscription limit reached");
    }

    SessionEntity session = new SessionEntity();
    session.setOwner(user);
    session.setSubscribed(true);
    session.setSourceShare(share);
    session.setName(share.getName());
    session = sessionRepository.save(session);

    install(share, session);

    log.info("User {} subscribed to session share {}", userId, share.getId());
    return sessionService.toEnrichedResponse(session, userId);
  }

  @Transactional
  public SessionResponse sync(UUID sessionId) {
    UUID userId = securityUtils.getCurrentUserId();
    SessionEntity session = sessionRepository.findByIdAndOwner_Id(sessionId, userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Session with id %s not found for user %s", sessionId, userId)
            ));

    if (!session.isSubscribed()) {
      throw new BadRequestException("Only subscribed sessions can be synced");
    }
    SessionShareEntity share = session.getSourceShare();
    if (share == null) {
      throw new ConflictException("The shared session no longer exists");
    }

    sessionMaterializer.clear(session);
    install(share, session);

    return sessionService.toEnrichedResponse(session, userId);
  }

  private void install(SessionShareEntity share, SessionEntity session) {
    sessionMaterializer.materialize(share.getSnapshot(), session);
    session.setInstalledVersion(share.getVersion());
    session.setModified(false);
    sessionRepository.flush();
  }

  private SessionSnapshot playableSnapshot(SessionEntity session) {
    SessionSnapshot snapshot = sessionSnapshotFactory.create(session);
    if (snapshot.boards().isEmpty() || snapshot.tracks().isEmpty()) {
      throw new BadRequestException("Only sessions with at least one stage and one track can be published");
    }
    return snapshot;
  }

  private SessionEntity findPublishableSession(UUID sessionId, UUID userId) {
    SessionEntity session = sessionRepository.findByIdAndOwner_Id(sessionId, userId)
            .orElseThrow(() -> new NotFoundException(
                    String.format("Session with id %s not found for user %s", sessionId, userId)
            ));
    if (session.isSubscribed()) {
      throw new ForbiddenException("Subscribed sessions cannot be published");
    }
    return session;
  }

  private SessionShareEntity findActiveShare(UUID sessionId) {
    return sessionShareRepository.findBySession_IdAndUnpublishedAtIsNull(sessionId)
            .orElseThrow(() -> new NotFoundException(String.format("Session with id %s is not published", sessionId)));
  }

  private SessionShareResponse toResponse(SessionShareEntity share, UUID userId) {
    SessionSnapshot snapshot = share.getSnapshot();
    return new SessionShareResponse()
            .id(share.getId())
            .shareCode(share.getShareCode())
            .name(share.getName())
            .description(share.getDescription())
            .version(share.getVersion())
            .publishedAt(SessionService.toOffset(share.getPublishedAt()))
            .updatedAt(SessionService.toOffset(share.getUpdatedAt()))
            .owner(userMapper.toLiteUserDto(share.getOwner()))
            .subscriberCount(Math.toIntExact(sessionRepository.countBySourceShare_Id(share.getId())))
            .boardCount(snapshot.boards().size())
            .trackCount(snapshot.tracks().size())
            .groupCount(snapshot.groups().size())
            .owned(share.getOwner().getId().equals(userId))
            .subscribed(sessionRepository.existsByOwner_IdAndSourceShare_Id(userId, share.getId()));
  }

  private String generateUniqueShareCode() {
    String shareCode;
    do {
      shareCode = UUID.randomUUID().toString();
    } while (sessionShareRepository.existsByShareCode(shareCode));
    return shareCode;
  }
}
