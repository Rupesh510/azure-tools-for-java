/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.ide.springcloud;

import com.microsoft.azure.toolkit.ide.common.IExplorerContributor;
import com.microsoft.azure.toolkit.ide.common.component.AzureResourceLabelView;
import com.microsoft.azure.toolkit.ide.common.component.AzureServiceLabelView;
import com.microsoft.azure.toolkit.ide.common.component.Node;
import com.microsoft.azure.toolkit.lib.common.action.AzureActionManager;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.messager.IAzureMessager;
import com.microsoft.azure.toolkit.lib.springcloud.AzureSpringCloud;
import com.microsoft.azure.toolkit.lib.springcloud.SpringCloudCluster;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.microsoft.azure.toolkit.lib.Azure.az;

public class SpringCloudExplorerContributor implements IExplorerContributor {

    private static final String NAME = "Spring Cloud";
    private static final String ICON = "/icons/springcloud.svg";

    @Override
    public Node<?> getModuleNode() {
        final AzureActionManager am = AzureActionManager.getInstance();
        final IAzureMessager messager = AzureMessager.getDefaultMessager();

        final AzureSpringCloud service = az(AzureSpringCloud.class);
        final Function<AzureSpringCloud, List<SpringCloudCluster>> clusters = asc -> asc.list().stream().flatMap(m -> m.clusters().list().stream())
            .collect(Collectors.toList());
        return new Node<>(service).view(new AzureServiceLabelView<>(service, "Spring Cloud", ICON))
            .actions(SpringCloudActionsContributor.SERVICE_ACTIONS)
            .addChildren(clusters, (cluster, ascNode) -> new Node<>(cluster)
                .view(new AzureResourceLabelView<>(cluster))
                .actions(SpringCloudActionsContributor.CLUSTER_ACTIONS)
                .addChildren(c -> c.apps().list(), (app, clusterNode) -> new Node<>(app)
                    .view(new AzureResourceLabelView<>(app))
                    .actions(SpringCloudActionsContributor.APP_ACTIONS)));
    }
}
