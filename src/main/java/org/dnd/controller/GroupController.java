package org.dnd.controller;

import com.giffing.bucket4j.spring.boot.starter.context.RateLimiting;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dnd.api.MusicGroupsApi;
import org.dnd.api.model.Group;
import org.dnd.api.model.GroupRequest;
import org.dnd.service.GroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/api/v1")
@Tag(name = "MusicGroups", description = "Operations related to user groups")
@RestController
@Validated
@RequiredArgsConstructor
public class GroupController implements MusicGroupsApi {
  private final GroupService groupService;

  @Override
  @RateLimiting(
          name = "default-api",
          cacheKey = "@rateLimitKeyResolver.currentUserKey()",
          ratePerMethod = true
  )
  public ResponseEntity<List<Group>> getUserGroups() {
    return ResponseEntity.ok(groupService.getUserGroups());
  }

  @Override
  @RateLimiting(
          name = "create-api",
          cacheKey = "@rateLimitKeyResolver.currentUserKey()",
          ratePerMethod = true
  )
  public ResponseEntity<Group> createGroup(GroupRequest groupRequest) {
    return ResponseEntity.ok(groupService.createGroup(groupRequest));
  }

  @Override
  @RateLimiting(
          name = "default-api",
          cacheKey = "@rateLimitKeyResolver.currentUserKey()",
          ratePerMethod = true
  )
  public ResponseEntity<Void> deleteGroup(Long groupId) {
    groupService.deleteGroup(groupId);
    return ResponseEntity.noContent().build();
  }

  @Override
  @RateLimiting(
          name = "default-api",
          cacheKey = "@rateLimitKeyResolver.currentUserKey()",
          ratePerMethod = true
  )
  public ResponseEntity<Group> updateGroup(Long groupId, GroupRequest groupRequest) {
    return ResponseEntity.ok(groupService.updateGroup(groupId, groupRequest));
  }
}

