/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.legacy.function;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.microsoft.azure.toolkit.intellij.legacy.webapp.WebAppBasePropertyView;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.AppServiceAppBase;
import com.microsoft.azure.toolkit.lib.appservice.function.AzureFunctions;
import com.microsoft.azure.toolkit.lib.appservice.function.FunctionApp;
import com.microsoft.azure.toolkit.lib.appservice.function.FunctionAppDraft;
import com.microsoft.azure.toolkit.lib.common.event.AzureEventBus;
import com.microsoft.tooling.msservices.serviceexplorer.azure.webapp.base.WebAppBasePropertyViewPresenter;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;

public class FunctionAppPropertyView extends WebAppBasePropertyView {
    private static final String ID = "com.microsoft.azure.toolkit.intellij.function.FunctionAppPropertyView";
    private final AzureEventBus.EventListener resourceDeleteListener;

    public static WebAppBasePropertyView create(@Nonnull final Project project, @Nonnull final String sid,
                                                @Nonnull final String webAppId, @Nonnull final VirtualFile virtualFile) {
        final FunctionAppPropertyView view = new FunctionAppPropertyView(project, sid, webAppId, virtualFile);
        view.onLoadWebAppProperty(sid, webAppId, null);
        return view;
    }

    protected FunctionAppPropertyView(@Nonnull Project project, @Nonnull String sid, @Nonnull String resId, @Nonnull final VirtualFile virtualFile) {
        super(project, sid, resId, null, virtualFile);
        resourceDeleteListener = new AzureEventBus.EventListener(event -> {
            final Object source = event.getSource();
            if (source instanceof FunctionApp && StringUtils.equals(((FunctionApp) source).id(), resId) &&
                ((FunctionApp) source).getFormalStatus().isDeleted()) {
                closeEditor((AppServiceAppBase<?, ?, ?>) source);
            }
        });
        AzureEventBus.on("resource.status_changed.resource", resourceDeleteListener);
    }

    @Override
    protected String getId() {
        return ID;
    }

    @Override
    public void dispose() {
        super.dispose();
        AzureEventBus.off("resource.status_changed.resource", resourceDeleteListener);
    }

    @Override
    protected WebAppBasePropertyViewPresenter createPresenter() {
        return new WebAppBasePropertyViewPresenter() {
            @Override
            protected FunctionApp getWebAppBase(String subscriptionId, String functionAppId, String name) {
                return Azure.az(AzureFunctions.class).functionApp(functionAppId);
            }

            @Override
            protected void updateAppSettings(String subscriptionId, String functionAppId, String name, Map toUpdate, Set toRemove) {
                final FunctionApp functionApp = getWebAppBase(subscriptionId, functionAppId, name);
                final FunctionAppDraft draft = (FunctionAppDraft) functionApp.update();
                draft.setAppSettings(toUpdate);
                toRemove.forEach(key -> draft.removeAppSetting((String) key));
                draft.updateIfExist();
            }
        };
    }
}