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
package com.thoughtworks.go.plugin.access.configrepo.v1;


import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.access.configrepo.ConfigFileList;
import com.thoughtworks.go.plugin.access.configrepo.ConfigRepoMigrator;
import com.thoughtworks.go.plugin.access.configrepo.ExportedConfig;
import com.thoughtworks.go.plugin.access.configrepo.JsonMessageHandler;
import com.thoughtworks.go.plugin.access.configrepo.v1.messages.ParseDirectoryMessage;
import com.thoughtworks.go.plugin.access.configrepo.v1.messages.ParseDirectoryResponseMessage;
import com.thoughtworks.go.plugin.configrepo.codec.GsonCodec;
import com.thoughtworks.go.plugin.configrepo.contract.CRConfigurationProperty;
import com.thoughtworks.go.plugin.configrepo.contract.CRParseResult;
import com.thoughtworks.go.plugin.configrepo.contract.CRPipeline;
import com.thoughtworks.go.plugin.configrepo.contract.ErrorCollection;
import com.thoughtworks.go.plugin.domain.common.Image;
import com.thoughtworks.go.plugin.domain.configrepo.Capabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

public class JsonMessageHandler1_0 implements JsonMessageHandler {
    public static final int CURRENT_CONTRACT_VERSION = 11;
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonMessageHandler1_0.class);
    private final GsonCodec codec;
    private final ConfigRepoMigrator migrator;

    public JsonMessageHandler1_0(GsonCodec gsonCodec, ConfigRepoMigrator configRepoMigrator) {
        codec = gsonCodec;
        migrator = configRepoMigrator;
    }

    @Override
    public String requestMessageForParseDirectory(String destinationFolder, Collection<CRConfigurationProperty> configurations) {
        ParseDirectoryMessage requestMessage = prepareMessage_1(destinationFolder, configurations);
        return codec.getGson().toJson(requestMessage);
    }

    @Override
    public String requestMessageForParseContent(Map<String, String> content) {
        throw new UnsupportedOperationException("V1 Config Repo plugins don't support parse-content");
    }

    private ParseDirectoryMessage prepareMessage_1(String destinationFolder, Collection<CRConfigurationProperty> configurations) {
        ParseDirectoryMessage requestMessage = new ParseDirectoryMessage(destinationFolder);
        for (CRConfigurationProperty conf : configurations) {
            requestMessage.addConfiguration(conf.getKey(), conf.getValue(), conf.getEncryptedValue());
        }
        return requestMessage;
    }

    private ResponseScratch parseResponseForMigration(String responseBody) {
        return new GsonBuilder().create().fromJson(responseBody, ResponseScratch.class);
    }

    @Override
    public Capabilities getCapabilitiesFromResponse(String responseBody) {
        return null;
    }

    @Override
    public Image getImageResponseFromBody(String responseBody) {
        return null;
    }

    @Override
    public String requestMessageConfigFiles(String destinationFolder, Collection<CRConfigurationProperty> configurations) {
        throw new UnsupportedOperationException("V1 Config Repo plugins don't support config-files");
    }

    @Override
    public ConfigFileList responseMessageForConfigFiles(String responseBody) {
        throw new UnsupportedOperationException("V1 Config Repo plugins don't support config-files");
    }

    @Override
    public CRParseResult responseMessageForParseDirectory(String responseBody) {
        ErrorCollection errors = new ErrorCollection();
        try {
            ResponseScratch responseMap = parseResponseForMigration(responseBody);
            ParseDirectoryResponseMessage parseDirectoryResponseMessage;

            if (responseMap.target_version == null) {
                errors.addError("Plugin response message", "missing 'target_version' field");
                return new CRParseResult(errors);
            } else if (responseMap.target_version > CURRENT_CONTRACT_VERSION) {
                String message = String.format("'target_version' is %s but the GoCD Server supports %s",
                        responseMap.target_version, CURRENT_CONTRACT_VERSION);
                errors.addError("Plugin response message", message);
                return new CRParseResult(errors);
            } else {
                int version = responseMap.target_version;

                while (version < CURRENT_CONTRACT_VERSION) {
                    version++;
                    responseBody = migrate(responseBody, version);
                }
                // after migration, json should match contract
                parseDirectoryResponseMessage = codec.getGson().fromJson(responseBody, ParseDirectoryResponseMessage.class);
                parseDirectoryResponseMessage.validateResponse(errors);

                errors.addErrors(parseDirectoryResponseMessage.getPluginErrors());

                return new CRParseResult(parseDirectoryResponseMessage.getEnvironments(), parseDirectoryResponseMessage.getPipelines(), errors);
            }
        } catch (Exception ex) {
            StringBuilder builder = new StringBuilder();
            builder.append("Unexpected error when handling plugin response").append('\n');
            builder.append(ex);
            // "location" of error is runtime. This is what user will see in config repo errors list.
            errors.addError("runtime", builder.toString());
            LOGGER.error(builder.toString(), ex);
            return new CRParseResult(errors);
        }
    }

    @Override
    public CRParseResult responseMessageForParseContent(String responseBody) {
        throw new UnsupportedOperationException("V1 Config Repo plugins don't support parse-content");
    }

    @Override
    public String requestMessageForPipelineExport(CRPipeline pipeline) {
        throw new UnsupportedOperationException("V1 Config Repo plugins don't support pipeline export");
    }

    private String migrate(String responseBody, int targetVersion) {
        if (targetVersion > CURRENT_CONTRACT_VERSION)
            throw new RuntimeException(String.format("Migration to %s is not supported", targetVersion));

        return migrator.migrate(responseBody, targetVersion);
    }

    @Override
    public ExportedConfig responseMessageForPipelineExport(String responseBody, Map<String, String> headers) {
        throw new UnsupportedOperationException("V1 Config Repo plugins don't support pipeline export");
    }

    class ResponseScratch {
        public Integer target_version;
    }

}
