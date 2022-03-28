/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.legacy.webapp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.microsoft.azure.toolkit.lib.appservice.AppServiceAppBase;
import com.microsoft.azure.toolkit.lib.appservice.webapp.WebAppDeploymentSlot;
import com.microsoft.azure.toolkit.lib.common.event.AzureEventBus;
import com.microsoft.tooling.msservices.serviceexplorer.azure.webapp.base.WebAppBasePropertyViewPresenter;
import com.microsoft.tooling.msservices.serviceexplorer.azure.webapp.deploymentslot.DeploymentSlotPropertyViewPresenter;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;

public class DeploymentSlotPropertyView extends WebAppBasePropertyView {
    private static final String ID = "com.microsoft.intellij.helpers.webapp.DeploymentSlotPropertyView";
    private final AzureEventBus.EventListener resourceDeleteListener;

    /**
     * Initialize the Web App Property View and return it.
     */
    public static WebAppBasePropertyView create(@Nonnull final Project project, @Nonnull final String sid,
                                                @Nonnull final String resId, @Nonnull final String slotName, @Nonnull final VirtualFile virtualFile) {
        DeploymentSlotPropertyView view = new DeploymentSlotPropertyView(project, sid, resId, slotName, virtualFile);
        view.onLoadWebAppProperty(sid, resId, slotName);
        return view;
    }

    private DeploymentSlotPropertyView(@Nonnull final Project project, @Nonnull final String sid,
                                       @Nonnull final String webAppId, @Nonnull final String slotName, @Nonnull final VirtualFile virtualFile) {
        super(project, sid, webAppId, slotName, virtualFile);
        resourceDeleteListener = new AzureEventBus.EventListener(event -> {
            final Object source = event.getSource();
            if (source instanceof WebAppDeploymentSlot &&
                StringUtils.equals(((WebAppDeploymentSlot) source).id(), resourceId) &&
                ((WebAppDeploymentSlot) source).getFormalStatus().isDeleted()) {
                closeEditor((AppServiceAppBase<?, ?, ?>) source);
            }
        });
        AzureEventBus.on("resource.status_changed.resource", resourceDeleteListener);
    }

    @Override
    protected String getId() {
        return this.ID;
    }

    @Override
    public void dispose() {
        super.dispose();
        AzureEventBus.off("resource.status_changed.resource", resourceDeleteListener);
    }

    @Override
    protected WebAppBasePropertyViewPresenter createPresenter() {
        return new DeploymentSlotPropertyViewPresenter();
    }
}