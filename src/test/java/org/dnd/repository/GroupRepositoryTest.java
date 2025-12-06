package org.dnd.repository;

import jakarta.transaction.Transactional;
import org.dnd.model.GroupEntity;
import org.dnd.model.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class GroupRepositoryTest extends DatabaseBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;


    @Test
    @Transactional
    void ownerSeesOwnGroups() {
        UserEntity owner = new UserEntity();
        owner.setName("owner");
        owner.setPassword("pw");
        owner = userRepository.save(owner);

        GroupEntity group1 = new GroupEntity();
        group1.setListName("Group 1");
        group1.setOwner(owner);
        groupRepository.save(group1);

        GroupEntity group2 = new GroupEntity();
        group2.setListName("Group 2");
        group2.setOwner(owner);
        groupRepository.save(group2);

        List<GroupEntity> groups = groupRepository.findByOwner_Id(owner.getId());

        assertThat(groups).hasSize(2);
        assertThat(groupRepository.existsByIdAndOwner_Id(group1.getId(), owner.getId())).isTrue();
    }
}
