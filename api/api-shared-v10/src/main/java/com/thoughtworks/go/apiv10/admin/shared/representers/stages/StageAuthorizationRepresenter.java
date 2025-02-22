/*
 * Copyright 2022 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.apiv10.admin.shared.representers.stages;

import com.thoughtworks.go.api.base.OutputWriter;
import com.thoughtworks.go.api.representers.JsonReader;
import com.thoughtworks.go.config.AdminRole;
import com.thoughtworks.go.config.AdminUser;
import com.thoughtworks.go.config.AuthConfig;
import com.thoughtworks.go.config.CaseInsensitiveString;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class StageAuthorizationRepresenter {

    public static void toJSON(OutputWriter jsonWriter, AuthConfig authConfig) {
        ArrayList<String> usersErrors = new ArrayList<>();
        authConfig.getUsers().forEach(r -> usersErrors.addAll(r.errors().getAllOn("name")));
        ArrayList<String> rolesErrors = new ArrayList<>();
        authConfig.getRoles().forEach(r -> rolesErrors.addAll(r.errors().getAllOn("name")));

        if (!usersErrors.isEmpty() || !rolesErrors.isEmpty()) {
            jsonWriter.addChild("errors", errorWriter -> {
                if (!usersErrors.isEmpty()) {
                    errorWriter.addChildList("users", usersErrors);
                }
                if (!rolesErrors.isEmpty()) {
                    errorWriter.addChildList("roles", rolesErrors);
                }
            });
        }

        jsonWriter.addChildList("roles", authConfig.getRoles().stream().map(eachItem -> eachItem.getName().toString()).collect(Collectors.toList()));
        jsonWriter.addChildList("users", authConfig.getUsers().stream().map(eachItem -> eachItem.getName().toString()).collect(Collectors.toList()));
    }

    public static AuthConfig fromJSON(JsonReader jsonReader) {
        AuthConfig authConfig = new AuthConfig();
        jsonReader.readArrayIfPresent("roles", roles -> {
            roles.forEach(role -> {
                authConfig.add(new AdminRole(new CaseInsensitiveString(role.getAsString())));
            });
        });

        jsonReader.readArrayIfPresent("users", users -> {
            users.forEach(user -> {
                authConfig.add(new AdminUser(new CaseInsensitiveString(user.getAsString())));
            });
        });

        return authConfig;
    }
}
