/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.vm.creation;

import com.intellij.openapi.project.Project;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.action.Action;
import com.microsoft.azure.toolkit.lib.common.action.ActionView;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperationBundle;
import com.microsoft.azure.toolkit.lib.common.task.AzureTask;
import com.microsoft.azure.toolkit.lib.common.task.AzureTaskManager;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemetry;
import com.microsoft.azure.toolkit.lib.compute.AzureCompute;
import com.microsoft.azure.toolkit.lib.compute.virtualmachine.VirtualMachineDraft;
import com.microsoft.azure.toolkit.lib.compute.virtualmachine.task.CreateVirtualMachineTask;

import java.util.Optional;
import java.util.function.Consumer;

public class CreateVirtualMachineAction {

    public static final String REOPEN_CREATION_DIALOG = "Reopen Creation Dialog";

    public static void createVirtualMachine(Project project) {
        AzureTaskManager.getInstance().runLater(() -> openDialog(project, null));
    }

    private static void openDialog(final Project project, final VirtualMachineDraft draft) {
        final VMCreationDialog dialog = new VMCreationDialog(project);
        dialog.setOkActionListener((config) -> {
            dialog.close();
            doCreateVirtualMachine(project, config);
        });
        AzureTaskManager.getInstance().runOnPooledThread(() -> {
            final VirtualMachineDraft defaultData = Optional.ofNullable(draft).orElseGet(() -> {
                final Account account = Azure.az(AzureAccount.class).account();
                final Subscription subs = account.getSelectedSubscriptions().get(0);
                final String name = VirtualMachineDraft.generateDefaultName();
                final VirtualMachineDraft vmDraft = Azure.az(AzureCompute.class).virtualMachines(subs.getId()).create(name, "<none>");
                return vmDraft.withDefaultConfig();
            });
            AzureTaskManager.getInstance().runLater(() -> dialog.setValue(defaultData), AzureTask.Modality.ANY);
        });
        dialog.show();
    }

    private static void doCreateVirtualMachine(final Project project, final VirtualMachineDraft draft) {
        final AzureTaskManager tm = AzureTaskManager.getInstance();
        tm.runInBackground(AzureOperationBundle.title("vm.create_vm.vm", draft.getName()), () -> {
            AzureTelemetry.getActionContext().setProperty("subscriptionId", draft.getSubscriptionId());
            try {
                new CreateVirtualMachineTask(draft).execute();
            } catch (final Exception e) {
                final Consumer<Object> act = t -> tm.runLater("open dialog", () -> openDialog(project, draft));
                final Action<?> action = new Action<>(act, new ActionView.Builder(REOPEN_CREATION_DIALOG));
                AzureMessager.getMessager().error(e, null, action);
            }
        });
    }
}