/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.appservice.manager;

import com.azure.core.annotation.BodyParam;
import com.azure.core.annotation.Delete;
import com.azure.core.annotation.Get;
import com.azure.core.annotation.Headers;
import com.azure.core.annotation.Host;
import com.azure.core.annotation.HostParam;
import com.azure.core.annotation.PathParam;
import com.azure.core.annotation.Post;
import com.azure.core.annotation.Put;
import com.azure.core.annotation.ServiceInterface;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpPipelineBuilder;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.RestProxy;
import com.azure.core.http.rest.StreamResponse;
import com.azure.core.management.serializer.SerializerFactory;
import com.azure.core.util.FluxUtil;
import com.azure.resourcemanager.appservice.models.KuduAuthenticationPolicy;
import com.azure.resourcemanager.appservice.models.WebAppBase;
import com.azure.resourcemanager.resources.fluentcore.policy.AuthenticationPolicy;
import com.azure.resourcemanager.resources.fluentcore.policy.AuxiliaryAuthenticationPolicy;
import com.azure.resourcemanager.resources.fluentcore.policy.ProviderRegistrationPolicy;
import com.microsoft.azure.toolkit.lib.appservice.model.AppServiceFile;
import com.microsoft.azure.toolkit.lib.appservice.model.CommandOutput;
import com.microsoft.azure.toolkit.lib.appservice.model.ProcessInfo;
import com.microsoft.azure.toolkit.lib.appservice.model.TunnelStatus;
import com.microsoft.azure.toolkit.lib.appservice.service.IAppService;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.utils.JsonUtils;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.apache.commons.codec.binary.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class AppServiceKuduManager {
    private final String host;
    private final KuduService kuduService;
    private final IAppService appService;

    private AppServiceKuduManager(String host, KuduService kuduService, IAppService appService) {
        this.host = host;
        this.appService = appService;
        this.kuduService = kuduService;
    }

    public static AppServiceKuduManager getClient(@Nonnull WebAppBase webAppBase, @Nonnull IAppService appService) {
        // refers : https://github.com/Azure/azure-sdk-for-java/blob/master/sdk/resourcemanager/azure-resourcemanager-appservice/src/main/java/
        // com/azure/resourcemanager/appservice/implementation/KuduClient.java
        if (webAppBase.defaultHostname() == null) {
            throw new AzureToolkitRuntimeException("Cannot initialize kudu client before web app is created");
        }
        String host = webAppBase.defaultHostname().toLowerCase(Locale.ROOT)
                .replace("http://", "")
                .replace("https://", "");
        String[] parts = host.split("\\.", 2);
        host = parts[0] + ".scm." + parts[1];
        host = "https://" + host;
        List<HttpPipelinePolicy> policies = new ArrayList<>();
        for (int i = 0, count = webAppBase.manager().httpPipeline().getPolicyCount(); i < count; ++i) {
            HttpPipelinePolicy policy = webAppBase.manager().httpPipeline().getPolicy(i);
            if (!(policy instanceof AuthenticationPolicy)
                    && !(policy instanceof ProviderRegistrationPolicy)
                    && !(policy instanceof AuxiliaryAuthenticationPolicy)) {
                policies.add(policy);
            }
        }
        policies.add(new KuduAuthenticationPolicy(webAppBase));
        HttpPipeline httpPipeline = new HttpPipelineBuilder()
                .policies(policies.toArray(new HttpPipelinePolicy[0]))
                .httpClient(webAppBase.manager().httpPipeline().getHttpClient())
                .build();

        final KuduService kuduService = RestProxy.create(KuduService.class, httpPipeline,
                SerializerFactory.createDefaultManagementSerializerAdapter());
        return new AppServiceKuduManager(host, kuduService, appService);
    }

    public Mono<byte[]> getFileContent(final String path) {
        final Flux<ByteBuffer> byteBufferFlux = this.kuduService.getFileContent(host, path).flatMapMany(StreamResponse::getValue);
        return FluxUtil.collectBytesInByteBufferStream(byteBufferFlux);
    }

    public List<? extends AppServiceFile> getFilesInDirectory(String dir) {
        // this file is generated by kudu itself, should not be visible to user.
        final Mono<Response<List<AppServiceFile>>> filesInDirectory = this.kuduService.getFilesInDirectory(host, dir);
        return getValueFromResponseMono(filesInDirectory, Collections.emptyList()).stream()
                .filter(file -> !"text/xml".equals(file.getMime()) || !file.getName().contains("LogFiles-kudu-trace_pending.xml"))
                .map(file -> file.withApp(appService).withPath(Paths.get(dir, file.getName()).toString()))
                .collect(Collectors.toList());
    }

    public AppServiceFile getFileByPath(String path) {
        final File file = new File(path);
        final List<? extends AppServiceFile> result = getFilesInDirectory(file.getParent());
        return result.stream()
                .filter(appServiceFile -> StringUtils.equals(file.getName(), appServiceFile.getName()))
                .findFirst()
                .orElse(null);
    }

    public void uploadFileToPath(String content, String path) {
        this.kuduService.saveFile(host, path, content).block();
    }

    public void createDirectory(String path) {
        this.kuduService.createDirectory(host, path).block();
    }

    public void deleteFile(String path) {
        this.kuduService.deleteFile(host, path).block();
    }

    public List<ProcessInfo> listProcess() {
        final Mono<Response<List<ProcessInfo>>> responseMono = this.kuduService.listProcess(host);
        return getValueFromResponseMono(responseMono, Collections.emptyList());
    }

    public CommandOutput execute(final String command, final String dir) {
        final CommandRequest commandRequest = CommandRequest.builder().command(command).dir(dir).build();
        final Mono<Response<CommandOutput>> responseMono = kuduService.execute(host, JsonUtils.toJson(commandRequest));
        return getValueFromResponseMono(responseMono);
    }

    public TunnelStatus getAppServiceTunnelStatus() {
        final Mono<Response<TunnelStatus>> responseMono = this.kuduService.getAppServiceTunnelStatus(host);
        return getValueFromResponseMono(responseMono);
    }

    private <T> T getValueFromResponseMono(Mono<Response<T>> response) {
        return getValueFromResponseMono(response, null);
    }

    private <T> T getValueFromResponseMono(Mono<Response<T>> response, T defaultValue) {
        return Optional.ofNullable(response.block()).map(Response::getValue).orElse(defaultValue);
    }

    @Host("{$host}")
    @ServiceInterface(name = "KuduService")
    private interface KuduService {
        @Headers({
                "Content-Type: application/json; charset=utf-8",
                "x-ms-logging-context: com.microsoft.azure.management.appservice.WebApps getFile"
        })
        @Get("api/vfs/{path}")
        Mono<StreamResponse> getFileContent(@HostParam("$host") String host, @PathParam("path") String path);

        @Headers({
                "Content-Type: application/json; charset=utf-8",
                "x-ms-logging-context: com.microsoft.azure.management.appservice.WebApps getFilesInDirectory"
        })
        @Get("api/vfs/{path}/")
        Mono<Response<List<AppServiceFile>>> getFilesInDirectory(@HostParam("$host") String host, @PathParam("path") String path);

        @Headers({
                "Content-Type: application/octet-stream; charset=utf-8",
                "x-ms-logging-context: com.microsoft.azure.management.appservice.WebApps saveFile",
                "If-Match: *"
        })
        @Put("api/vfs/{path}")
        Mono<Void> saveFile(@HostParam("$host") String host, @PathParam("path") String path, @BodyParam("application/octet-stream") String content);

        @Headers({
                "Content-Type: application/json; charset=utf-8",
                "x-ms-logging-context: com.microsoft.azure.management.appservice.WebApps createDirectory"
        })
        @Put("api/vfs/{path}/")
        Mono<Void> createDirectory(@HostParam("$host") String host, @PathParam("path") String path);

        @Headers({
                "Content-Type: application/json; charset=utf-8",
                "x-ms-logging-context: com.microsoft.azure.management.appservice.WebApps deleteFile",
                "If-Match: *"
        })
        @Delete("api/vfs/{path}")
        Mono<Void> deleteFile(@HostParam("$host") String host, @PathParam("path") String path);

        @Headers({
                "x-ms-logging-context: com.microsoft.azure.management.appservice.WebApps listProcesses",
                "x-ms-body-logging: false"
        })
        @Get("api/processes")
        Mono<Response<List<ProcessInfo>>> listProcess(@HostParam("$host") String host);

        @Headers({
                "Content-Type: application/json; charset=utf-8",
                "x-ms-logging-context: com.microsoft.azure.management.appservice.WebApps command",
                "x-ms-body-logging: false"
        })
        @Post("api/command")
        Mono<Response<CommandOutput>> execute(@HostParam("$host") String host, @BodyParam("json") String command);

        @Headers({
                "Content-Type: application/json; charset=utf-8",
                "x-ms-logging-context: com.microsoft.azure.management.appservice.WebApps AppServiceTunnelStatus",
                "x-ms-body-logging: false"
        })
        @Get("AppServiceTunnel/Tunnel.ashx?GetStatus&GetStatusAPIVer=2")
        Mono<Response<TunnelStatus>> getAppServiceTunnelStatus(@HostParam("$host") String host);
    }

    @Data
    @SuperBuilder(toBuilder = true)
    public static class CommandRequest {
        private String command;
        private String dir;
    }
}