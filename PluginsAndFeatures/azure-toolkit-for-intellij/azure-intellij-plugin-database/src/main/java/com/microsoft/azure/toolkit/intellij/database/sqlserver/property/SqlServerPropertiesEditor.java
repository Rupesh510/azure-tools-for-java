/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.database.sqlserver.property;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.microsoft.azure.toolkit.intellij.common.AzureHideableTitledSeparator;
import com.microsoft.azure.toolkit.intellij.common.properties.AzResourcePropertiesEditor;
import com.microsoft.azure.toolkit.intellij.database.component.DatabaseComboBox;
import com.microsoft.azure.toolkit.intellij.database.ui.ConnectionSecurityPanel;
import com.microsoft.azure.toolkit.intellij.database.ui.ConnectionStringsOutputPanel;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.task.AzureTask;
import com.microsoft.azure.toolkit.lib.common.task.AzureTaskManager;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemetry;
import com.microsoft.azure.toolkit.lib.sqlserver.MicrosoftSqlDatabase;
import com.microsoft.azure.toolkit.lib.sqlserver.MicrosoftSqlServer;
import com.microsoft.azure.toolkit.lib.sqlserver.MicrosoftSqlServerDraft;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;

public class SqlServerPropertiesEditor extends AzResourcePropertiesEditor<MicrosoftSqlServer> {

    public static final String ID = "com.microsoft.azure.toolkit.intellij.sqlserver.property.SqlServerPropertiesEditor";
    private final Project project;
    @Nonnull
    private final MicrosoftSqlServer origin;
    @Nonnull
    private final MicrosoftSqlServerDraft server;

    private AzureHideableTitledSeparator overviewSeparator;
    private SqlServerPropertyOverviewPanel overview;
    private AzureHideableTitledSeparator connectionSecuritySeparator;
    private ConnectionSecurityPanel connectionSecurity;
    private AzureHideableTitledSeparator connectionStringsSeparator;
    private ConnectionStringsOutputPanel connectionStringsJDBC;
    private ConnectionStringsOutputPanel connectionStringsSpring;
    private JPanel rootPanel;
    private JPanel contextPanel;
    private JScrollPane scrollPane;
    private SqlServerPropertyActionPanel propertyActionPanel;
    private DatabaseComboBox<MicrosoftSqlDatabase> databaseComboBox;
    private JLabel databaseLabel;
    public static final String SQL_SERVER_OUTPUT_TEXT_PATTERN_SPRING =
        "spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver" + System.lineSeparator() +
            "spring.datasource.url=jdbc:sqlserver://%s:1433;encrypt=true;trustServerCertificate=false;loginTimeout=30;database=%s;" + System.lineSeparator() +
            "spring.datasource.username=%s" + System.lineSeparator() + "spring.datasource.password={your_password}";

    public static final String SQL_SERVER_OUTPUT_TEXT_PATTERN_JDBC =
        "String url =\"jdbc:sqlserver://%s:1433;encrypt=true;trustServerCertificate=false;loginTimeout=30;database=%s;\";" + System.lineSeparator() +
            "myDbConn = DriverManager.getConnection(url, \"%s\", {your_password});";

    @Override
    protected void rerender() {
        AzureTaskManager.getInstance().runLater(() -> this.setData(this.server));
    }

    SqlServerPropertiesEditor(@Nonnull Project project, @Nonnull MicrosoftSqlServer server, @Nonnull final VirtualFile virtualFile) {
        super(virtualFile, server, project);
        this.project = project;
        this.origin = server;
        this.server = (MicrosoftSqlServerDraft) server.update();

        overviewSeparator.addContentComponent(overview);
        connectionSecuritySeparator.addContentComponent(connectionSecurity);
        connectionStringsSeparator.addContentComponent(databaseLabel);
        connectionStringsSeparator.addContentComponent(databaseComboBox);
        connectionStringsSeparator.addContentComponent(connectionStringsJDBC);
        connectionStringsSeparator.addContentComponent(connectionStringsSpring);
        connectionStringsJDBC.getTitleLabel().setText("JDBC");
        connectionStringsJDBC.getOutputTextArea().setText(getConnectionString(SQL_SERVER_OUTPUT_TEXT_PATTERN_JDBC, null, null, null));
        connectionStringsSpring.getTitleLabel().setText("Spring");
        connectionStringsSpring.getOutputTextArea().setText(getConnectionString(SQL_SERVER_OUTPUT_TEXT_PATTERN_SPRING, null, null, null));
        this.rerender();
        this.initListeners();
    }

    private String getConnectionString(final String pattern, final String hostname, final String database, final String username) {
        final String newHostname = StringUtils.isNotBlank(hostname) ? hostname : "{your_hostname}";
        final String newDatabase = StringUtils.isNotBlank(database) ? database : "{your_database}";
        final String newUsername = StringUtils.isNotBlank(username) ? username : "{your_username}";
        return String.format(pattern, newHostname, newDatabase, newUsername);
    }

    private void setData(MicrosoftSqlServer server) {
        this.overview.setFormData(server);
        this.databaseComboBox.setServer(server);
        this.refreshButtons();
        if (StringUtils.equalsIgnoreCase("READY", server.getStatus())) {
            connectionSecuritySeparator.expand();
            connectionSecuritySeparator.setEnabled(true);
            connectionStringsSeparator.expand();
            connectionStringsSeparator.setEnabled(true);
            AzureTaskManager.getInstance().runOnPooledThread(() -> {
                final boolean originalAllowAccessToAzureServices = server.isAzureServiceAccessAllowed();
                final boolean originalAllowAccessToLocal = server.isLocalMachineAccessAllowed();
                AzureTaskManager.getInstance().runLater(() -> {
                    connectionSecurity.getAllowAccessFromAzureServicesCheckBox().setSelected(originalAllowAccessToAzureServices);
                    connectionSecurity.getAllowAccessFromLocalMachineCheckBox().setSelected(originalAllowAccessToLocal);
                });
            });
        } else {
            connectionSecuritySeparator.collapse();
            connectionSecuritySeparator.setEnabled(false);
            connectionStringsSeparator.collapse();
            connectionStringsSeparator.setEnabled(false);
        }
    }

    private void initListeners() {
        // update to trigger save/discard buttons
        connectionSecurity.getAllowAccessFromAzureServicesCheckBox().addItemListener(this::onCheckBoxChanged);
        connectionSecurity.getAllowAccessFromLocalMachineCheckBox().addItemListener(this::onCheckBoxChanged);
        // actions of copy buttons
        connectionStringsJDBC.getCopyButton().addActionListener(this::onJDBCCopyButtonClicked);
        connectionStringsSpring.getCopyButton().addActionListener(this::onSpringCopyButtonClicked);
        // save/discard buttons
        propertyActionPanel.getSaveButton().addActionListener(this::apply);
        propertyActionPanel.getDiscardButton().addActionListener(this::reset);
        // database combobox changed
        databaseComboBox.addItemListener(this::onDatabaseComboBoxChanged);
    }

    private void onCheckBoxChanged(ItemEvent itemEvent) {
        this.refreshButtons();
    }

    private void onJDBCCopyButtonClicked(ActionEvent e) {
        try {
            copyToSystemClipboard(SqlServerPropertiesEditor.this.connectionStringsJDBC.getOutputTextArea().getText());
        } catch (final Exception exception) {
            final String error = "copy JDBC connection strings";
            final String action = "try again later.";
            throw new AzureToolkitRuntimeException(error, action);
        }
    }

    private void copyToSystemClipboard(String text) {
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }

    private void onSpringCopyButtonClicked(ActionEvent e) {
        try {
            copyToSystemClipboard(this.connectionStringsSpring.getOutputTextArea().getText());
        } catch (final Exception exception) {
            final String error = "copy Spring connection strings";
            final String action = "try again later.";
            throw new AzureToolkitRuntimeException(error, action);
        }
    }

    private void apply(ActionEvent e) {
        this.propertyActionPanel.getSaveButton().setEnabled(false);
        this.propertyActionPanel.getDiscardButton().setEnabled(false);
        final Runnable runnable = () -> {
            final String subscriptionId = server.getSubscriptionId();
            final boolean allowAccessToAzureServices = connectionSecurity.getAllowAccessFromAzureServicesCheckBox().getModel().isSelected();
            final boolean allowAccessToLocal = connectionSecurity.getAllowAccessFromLocalMachineCheckBox().getModel().isSelected();
            this.server.setAzureServiceAccessAllowed(allowAccessToAzureServices);
            this.server.setLocalMachineAccessAllowed(allowAccessToLocal);
            final AzureTelemetry.Context context = AzureTelemetry.getActionContext();
            context.setProperty("subscriptionId", subscriptionId);
            context.setProperty("allowAccessToLocal", String.valueOf(allowAccessToLocal));
            context.setProperty("allowAccessToAzureServices", String.valueOf(allowAccessToAzureServices));
            this.server.commit();
            this.refreshButtons();
        };
        AzureTaskManager.getInstance().runInBackground(new AzureTask<>(this.project, "Saving updates", false, runnable));
    }

    private void refreshButtons() {
        final boolean modified = this.isModified();
        this.propertyActionPanel.getSaveButton().setEnabled(modified);
        this.propertyActionPanel.getDiscardButton().setEnabled(modified);
    }

    private void reset(ActionEvent e) {
        this.server.reset();
        this.rerender();
    }

    private void onDatabaseComboBoxChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() instanceof MicrosoftSqlDatabase) {
            final MicrosoftSqlDatabase database = (MicrosoftSqlDatabase) e.getItem();
            final String username = server.getAdminName() + "@" + server.name();
            connectionStringsJDBC.getOutputTextArea().setText(getConnectionString(SQL_SERVER_OUTPUT_TEXT_PATTERN_JDBC,
                server.getFullyQualifiedDomainName(), database.getName(), username));
            connectionStringsSpring.getOutputTextArea().setText(getConnectionString(SQL_SERVER_OUTPUT_TEXT_PATTERN_SPRING,
                server.getFullyQualifiedDomainName(), database.getName(), username));
        }
    }

    @Override
    public boolean isModified() {
        return this.origin.isAzureServiceAccessAllowed() != connectionSecurity.getAllowAccessFromAzureServicesCheckBox().getModel().isSelected() ||
            this.origin.isLocalMachineAccessAllowed() != connectionSecurity.getAllowAccessFromLocalMachineCheckBox().getModel().isSelected();
    }

    @Override
    public @Nonnull
    JComponent getComponent() {
        return rootPanel;
    }

    protected void refresh() {
        this.propertyActionPanel.getSaveButton().setEnabled(false);
        final String refreshTitle = String.format("Refreshing SQL server(%s)...", this.server.getName());
        AzureTaskManager.getInstance().runInBackground(refreshTitle, () -> {
            this.server.refresh();
            AzureTaskManager.getInstance().runLater(this::rerender);
        });
    }
}
