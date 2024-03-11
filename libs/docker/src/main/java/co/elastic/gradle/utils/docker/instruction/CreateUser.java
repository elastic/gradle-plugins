/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
