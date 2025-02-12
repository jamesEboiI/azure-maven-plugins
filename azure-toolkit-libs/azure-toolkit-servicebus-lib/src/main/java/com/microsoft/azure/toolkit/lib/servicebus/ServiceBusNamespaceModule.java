/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.servicebus;

import com.azure.core.util.paging.ContinuablePage;
import com.azure.resourcemanager.servicebus.ServiceBusManager;
import com.azure.resourcemanager.servicebus.models.ServiceBusNamespaces;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

public class ServiceBusNamespaceModule extends AbstractAzResourceModule<ServiceBusNamespace, ServiceBusNamespaceSubscription, com.azure.resourcemanager.servicebus.models.ServiceBusNamespace> {

    public static final String NAME = "namespaces";
    public ServiceBusNamespaceModule(ServiceBusNamespaceSubscription parent) {
        super(NAME, parent);
    }

    @Nonnull
    @Override
    protected Iterator<? extends ContinuablePage<String, com.azure.resourcemanager.servicebus.models.ServiceBusNamespace>> loadResourcePagesFromAzure() {
        return Optional.ofNullable(this.getClient()).map(c -> c.list().iterableByPage(getPageSize()).iterator()).orElse(Collections.emptyIterator());
    }

    @Nullable
    @Override
    @AzureOperation(name = "azure/resource.load_resource.resource|type", params = {"name", "this.getResourceTypeName()"})
    protected com.azure.resourcemanager.servicebus.models.ServiceBusNamespace loadResourceFromAzure(@Nonnull String name, @Nullable String resourceGroup) {
        assert StringUtils.isNoneBlank(resourceGroup) : "resource group can not be empty";
        return Optional.ofNullable(this.getClient()).map(serviceBusNamespaces -> serviceBusNamespaces.getByResourceGroup(resourceGroup, name)).orElse(null);
    }

    @Override
    @AzureOperation(name = "azure/servicebus.delete_service_bus_namespace.servicebus", params = {"nameFromResourceId(resourceId)"})
    protected void deleteResourceFromAzure(@Nonnull String resourceId) {
        Optional.ofNullable(this.getClient()).ifPresent(serviceBusNamespaces -> serviceBusNamespaces.deleteById(resourceId));
    }

    @Nonnull
    @Override
    protected ServiceBusNamespace newResource(@Nonnull com.azure.resourcemanager.servicebus.models.ServiceBusNamespace remote) {
        return new ServiceBusNamespace(remote, this);
    }

    @Nonnull
    @Override
    protected ServiceBusNamespace newResource(@Nonnull String name, @Nullable String resourceGroupName) {
        return new ServiceBusNamespace(name, Objects.requireNonNull(resourceGroupName), this);
    }

    @Nullable
    @Override
    protected ServiceBusNamespaces getClient() {
        return Optional.ofNullable(this.parent.getRemote()).map(ServiceBusManager::namespaces).orElse(null);
    }

    @Nonnull
    @Override
    public String getResourceTypeName() {
        return "Service Bus Namespace";
    }
}
