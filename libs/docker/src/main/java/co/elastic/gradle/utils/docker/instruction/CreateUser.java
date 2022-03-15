package co.elastic.gradle.utils.docker.instruction;

import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import org.gradle.api.tasks.Input;

public class CreateUser implements ContainerImageBuildInstruction {
    private final String username;
    private final String group;
    private final Integer userId;
    private final Integer groupId;

    public CreateUser(String username, String group, Integer userId, Integer groupId) {
        this.username = username;
        this.group = group;
        this.userId = userId;
        this.groupId = groupId;
    }

    @Input
    public String getUsername() {
        return username;
    }

    @Input
    public String getGroup() {
        return group;
    }

    @Input
    public Integer getUserId() {
        return userId;
    }

    @Input
    public Integer getGroupId() {
        return groupId;
    }
}
