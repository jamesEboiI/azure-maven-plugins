/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.toolkit.lib.postgre;

import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.management.profile.AzureProfile;
import com.azure.core.util.ExpandableStringEnum;
import com.azure.resourcemanager.postgresqlflexibleserver.PostgreSqlManager;
import com.azure.resourcemanager.postgresqlflexibleserver.models.ServerVersion;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.AzureConfiguration;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzServiceSubscription;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class AzurePostgreSql extends AbstractAzService<PostgreSqlServiceSubscription, PostgreSqlManager> {

    public AzurePostgreSql() {
        super("Microsoft.DBforPostgreSQL");
    }

    @Nonnull
    public PostgreSqlServerModule servers(@Nonnull String subscriptionId) {
        final PostgreSqlServiceSubscription rm = get(subscriptionId, null);
        assert rm != null;
        return rm.getServerModule();
    }

    @Nonnull
    public List<PostgreSqlServer> servers() {
        return this.list().stream().flatMap(m -> m.servers().list().stream()).collect(Collectors.toList());
    }

    @Nullable
    @Override
    protected PostgreSqlManager loadResourceFromAzure(@Nonnull String subscriptionId, String resourceGroup) {
        final Account account = Azure.az(AzureAccount.class).account();
        final AzureConfiguration config = Azure.az().config();
        final String userAgent = config.getUserAgent();
        final HttpLogDetailLevel logLevel = Optional.ofNullable(config.getLogLevel()).map(HttpLogDetailLevel::valueOf).orElse(HttpLogDetailLevel.NONE);
        final AzureProfile azureProfile = new AzureProfile(null, subscriptionId, account.getEnvironment());
        return PostgreSqlManager.configure()
            .withHttpClient(AbstractAzServiceSubscription.getDefaultHttpClient())
            .withLogOptions(new HttpLogOptions().setLogLevel(logLevel))
            .withPolicy(AbstractAzServiceSubscription.getUserAgentPolicy(userAgent))
            .authenticate(account.getTokenCredential(subscriptionId), azureProfile);
    }

    @Nonnull
    @Override
    protected PostgreSqlServiceSubscription newResource(@Nonnull PostgreSqlManager manager) {
        return new PostgreSqlServiceSubscription(manager, this);
    }

    @Nonnull
    public List<String> listSupportedVersions() {
        return ServerVersion.values().stream().map(ExpandableStringEnum::toString).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public String getResourceTypeName() {
        return "Azure Database for PostgreSQL servers";
    }
}
