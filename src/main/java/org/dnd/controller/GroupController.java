package org.dnd.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.dnd.api.MusicGroupsApi;
import org.dnd.api.model.Group;
import org.dnd.api.model.GroupRequest;
import org.dnd.api.model.GroupShare;
import org.dnd.api.model.GroupShareRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/api/v1")
@Tag(name = "MusicGroups", description = "Operations related to user groups")
@RestController
@Validated
public class GroupController implements MusicGroupsApi {


    @Override
    public ResponseEntity<List<Group>> getUserGroups(Long userId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Group> createGroup(GroupRequest groupRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Void> deleteGroup(Long groupId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<Group> updateGroup(Long groupId, GroupRequest groupRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<List<GroupShare>> listGroupShares(Long groupId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<GroupShare> shareGroup(Long groupId, GroupShareRequest groupShareRequest) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
    
    @Override
    public ResponseEntity<Void> unshareGroup(Long groupId, Long userId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}

