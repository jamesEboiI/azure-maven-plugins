/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.postgre;

import com.azure.core.util.paging.ContinuablePage;
import com.azure.resourcemanager.postgresqlflexibleserver.PostgreSqlManager;
import com.azure.resourcemanager.postgresqlflexibleserver.models.Server;
import com.azure.resourcemanager.postgresqlflexibleserver.models.Servers;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

public class PostgreSqlServerModule extends AbstractAzResourceModule<PostgreSqlServer, PostgreSqlServiceSubscription, Server> {

    public static final String NAME = "flexibleServers";

    public PostgreSqlServerModule(@Nonnull PostgreSqlServiceSubscription parent) {
        super(NAME, parent);
    }

    @Nonnull
    @Override
    protected Iterator<? extends ContinuablePage<String, Server>> loadResourcePagesFromAzure() {
        return Optional.ofNullable(getClient())
            .map(c -> c.list().iterableByPage(getPageSize()).iterator())
            .orElse(Collections.emptyIterator());
    }

    @Nullable
    @Override
    @AzureOperation(name = "azure/resource.load_resource.resource|type", params = {"name", "this.getResourceTypeName()"})
    protected Server loadResourceFromAzure(@Nonnull String name, @Nullable String resourceGroup) {
        assert StringUtils.isNoneBlank(resourceGroup) : "resource group can not be empty";
        return Optional.ofNullable(this.getClient()).map(c -> c.getByResourceGroup(resourceGroup, name)).orElse(null);
    }

    @Override
    @AzureOperation(name = "azure/postgre.delete_server.server", params = {"nameFromResourceId(id)"})
    protected void deleteResourceFromAzure(@Nonnull String resourceId) {
        Optional.ofNullable(this.getClient()).ifPresent(c -> c.deleteById(resourceId));
    }

    @Nonnull
    @Override
    protected PostgreSqlServerDraft newDraftForCreate(@Nonnull String name, @Nullable String resourceGroupName) {
        assert resourceGroupName != null : "'Resource group' is required.";
        return new PostgreSqlServerDraft(name, resourceGroupName, this);
    }

    @Nonnull
    @Override
    protected PostgreSqlServerDraft newDraftForUpdate(@Nonnull PostgreSqlServer origin) {
        return new PostgreSqlServerDraft(origin);
    }

    @Nullable
    @Override
    public Servers getClient() {
        return Optional.ofNullable(this.parent.getRemote()).map(PostgreSqlManager::servers).orElse(null);
    }

    @Nonnull
    @Override
    protected PostgreSqlServer newResource(@Nonnull Server r) {
        return new PostgreSqlServer(r, this);
    }

    @Nonnull
    @Override
    protected PostgreSqlServer newResource(@Nonnull String name, @Nullable String resourceGroupName) {
        return new PostgreSqlServer(name, Objects.requireNonNull(resourceGroupName), this);
    }

    @Nonnull
    @Override
    public String getResourceTypeName() {
        return "PostgreSQL flexible server";
    }
}
