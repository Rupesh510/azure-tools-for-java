/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azuretools.core.mvp.model.webapp;

import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.AzureAppService;
import com.microsoft.azure.toolkit.lib.appservice.entity.AppServicePlanEntity;
import com.microsoft.azure.toolkit.lib.appservice.model.DeployType;
import com.microsoft.azure.toolkit.lib.appservice.model.DiagnosticConfig;
import com.microsoft.azure.toolkit.lib.appservice.model.DockerConfiguration;
import com.microsoft.azure.toolkit.lib.appservice.model.Runtime;
import com.microsoft.azure.toolkit.lib.appservice.model.WebContainer;
import com.microsoft.azure.toolkit.lib.appservice.plan.AppServicePlan;
import com.microsoft.azure.toolkit.lib.appservice.plan.AppServicePlanDraft;
import com.microsoft.azure.toolkit.lib.appservice.webapp.AzureWebApp;
import com.microsoft.azure.toolkit.lib.appservice.webapp.WebApp;
import com.microsoft.azure.toolkit.lib.appservice.webapp.WebAppBase;
import com.microsoft.azure.toolkit.lib.appservice.webapp.WebAppDeploymentSlot;
import com.microsoft.azure.toolkit.lib.appservice.webapp.WebAppDeploymentSlotDraft;
import com.microsoft.azure.toolkit.lib.appservice.webapp.WebAppDraft;
import com.microsoft.azure.toolkit.lib.common.action.Action;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.model.ResourceGroup;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.resource.task.CreateResourceGroupTask;
import com.microsoft.azuretools.utils.IProgressIndicator;
import lombok.extern.java.Log;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// todo: Refactor to tasks in app service library
@Deprecated
@Log
public class AzureWebAppMvpModel {

    public static final String DO_NOT_CLONE_SLOT_CONFIGURATION = "Don't clone configuration from an existing slot";

    private static final String STOP_WEB_APP = "Stopping web app...";
    private static final String STOP_DEPLOYMENT_SLOT = "Stopping deployment slot...";
    private static final String DEPLOY_SUCCESS_WEB_APP = "Deploy succeed, restarting web app...";
    private static final String DEPLOY_SUCCESS_DEPLOYMENT_SLOT = "Deploy succeed, restarting deployment slot...";

    private AzureWebAppMvpModel() {
    }

    public static AzureWebAppMvpModel getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * API to create Web App on Docker.
     *
     * @param model parameters
     * @return instance of created WebApp
     */
    @AzureOperation(
            name = "webapp.create_app.app|subscription|image",
            params = {
                "model.getWebAppName()",
                "model.getSubscriptionId()",
                "model.getPrivateRegistryImageSetting().getImageNameWithTag()"
            },
            type = AzureOperation.Type.SERVICE
    )
    public WebApp createAzureWebAppWithPrivateRegistryImage(@Nonnull WebAppOnLinuxDeployModel model) {
        final ResourceGroup resourceGroup = getOrCreateResourceGroup(model.getSubscriptionId(), model.getResourceGroupName(), model.getLocationName());
        final AppServicePlanEntity servicePlanEntity = AppServicePlanEntity.builder()
            .id(model.getAppServicePlanId())
            .subscriptionId(model.getSubscriptionId())
            .name(model.getAppServicePlanName())
            .resourceGroup(model.getResourceGroupName())
            .region(model.getLocationName())
            .operatingSystem(com.microsoft.azure.toolkit.lib.appservice.model.OperatingSystem.DOCKER)
            .pricingTier(com.microsoft.azure.toolkit.lib.appservice.model.PricingTier.fromString(model.getPricingSkuSize())).build();
        final AppServicePlan appServicePlan = getOrCreateAppServicePlan(servicePlanEntity);
        final PrivateRegistryImageSetting pr = model.getPrivateRegistryImageSetting();
        // todo: support start up file in docker configuration
        final DockerConfiguration dockerConfiguration = DockerConfiguration.builder()
            .image(pr.getImageTagWithServerUrl())
            .registryUrl(pr.getServerUrl())
            .userName(pr.getUsername())
            .password(pr.getPassword())
            .startUpCommand(pr.getStartupFile()).build();
        final WebAppDraft draft = Azure.az(AzureWebApp.class).webApps(model.getSubscriptionId())
            .create(model.getWebAppName(), model.getResourceGroupName());
        draft.setAppServicePlan(appServicePlan);
        draft.setRuntime(Runtime.DOCKER);
        draft.setDockerConfiguration(dockerConfiguration);
        return draft.createIfNotExist();
    }

    /**
     * Update container settings for existing Web App on Linux.
     *
     * @param webAppId     id of Web App on Linux instance
     * @param imageSetting new container settings
     * @return instance of the updated Web App on Linux
     */
    @AzureOperation(
        name = "docker.update_image.app|image",
        params = {"nameFromResourceId(webAppId)", "imageSetting.getImageNameWithTag()"},
        type = AzureOperation.Type.SERVICE
    )
    public WebApp updateWebAppOnDocker(String webAppId, ImageSetting imageSetting) {
        final WebApp app = Objects.requireNonNull(Azure.az(AzureWebApp.class).webApp(webAppId));
        // clearTags(app);
        if (imageSetting instanceof PrivateRegistryImageSetting) {
            final PrivateRegistryImageSetting pr = (PrivateRegistryImageSetting) imageSetting;
            final DockerConfiguration dockerConfiguration = DockerConfiguration.builder()
                .image(pr.getImageTagWithServerUrl())
                .registryUrl(pr.getServerUrl())
                .userName(pr.getUsername())
                .password(pr.getPassword())
                .startUpCommand(pr.getStartupFile()).build();
            final WebAppDraft draft = (WebAppDraft) app.update();
            draft.setDockerConfiguration(dockerConfiguration);
            draft.updateIfExist();
        }
        // status-free restart.
        app.restart();
        return app;
    }

    /**
     * API to create new Web App by setting model.
     */
    @AzureOperation(
            name = "webapp.create_app.app",
            params = {"model.getWebAppName()"},
            type = AzureOperation.Type.SERVICE
    )
    public WebApp createWebAppFromSettingModel(@Nonnull WebAppSettingModel model) {
        final ResourceGroup resourceGroup = getOrCreateResourceGroup(model.getSubscriptionId(), model.getResourceGroup(), model.getRegion());
        final AppServicePlanEntity servicePlanEntity = AppServicePlanEntity.builder()
            .id(model.getAppServicePlanId())
            .subscriptionId(model.getSubscriptionId())
            .name(model.getAppServicePlanName())
            .resourceGroup(model.getResourceGroup())
            .region(model.getRegion())
            .operatingSystem(com.microsoft.azure.toolkit.lib.appservice.model.OperatingSystem.fromString(model.getOperatingSystem()))
            .pricingTier(com.microsoft.azure.toolkit.lib.appservice.model.PricingTier.fromString(model.getPricing())).build();
        final AppServicePlan appServicePlan = getOrCreateAppServicePlan(servicePlanEntity);
        final DiagnosticConfig diagnosticConfig = DiagnosticConfig.builder()
            .enableApplicationLog(model.isEnableApplicationLog())
            .applicationLogLevel(com.microsoft.azure.toolkit.lib.appservice.model.LogLevel.fromString(model.getApplicationLogLevel()))
            .enableWebServerLogging(model.isEnableWebServerLogging())
            .webServerLogQuota(model.getWebServerLogQuota())
            .webServerRetentionPeriod(model.getWebServerRetentionPeriod())
            .enableDetailedErrorMessage(model.isEnableDetailedErrorMessage())
            .enableFailedRequestTracing(model.isEnableFailedRequestTracing()).build();
        final WebAppDraft draft = Azure.az(AzureWebApp.class).webApps(model.getSubscriptionId()).create(model.getWebAppName(), model.getResourceGroup());
        draft.setAppServicePlan(appServicePlan);
        draft.setRuntime(model.getRuntime());
        draft.setDiagnosticConfig(diagnosticConfig);
        return draft.createIfNotExist();
    }

    // todo: Move duplicated codes to azure common library
    private ResourceGroup getOrCreateResourceGroup(String subscriptionId, String resourceGroup, String region) {
        return new CreateResourceGroupTask(subscriptionId, resourceGroup, Region.fromName(region)).execute();
    }

    private AppServicePlan getOrCreateAppServicePlan(AppServicePlanEntity servicePlanEntity) {
        final String rg = Optional.ofNullable(servicePlanEntity.getResourceGroup()).filter(StringUtils::isNotBlank)
            .orElseGet(() -> ResourceId.fromString(servicePlanEntity.getId()).resourceGroupName());
        final String name = Optional.ofNullable(servicePlanEntity.getName()).filter(StringUtils::isNotBlank)
            .orElseGet(() -> ResourceId.fromString(servicePlanEntity.getId()).name());
        final AzureAppService az = Azure.az(AzureAppService.class);
        final AppServicePlan appServicePlan = az.plans(servicePlanEntity.getSubscriptionId()).getOrDraft(name, rg);
        if (appServicePlan.exists()) {
            return appServicePlan;
        }
        final AppServicePlanDraft draft = (AppServicePlanDraft) appServicePlan;
        draft.setRegion(Region.fromName(servicePlanEntity.getRegion()));
        draft.setPricingTier(servicePlanEntity.getPricingTier());
        draft.setOperatingSystem(servicePlanEntity.getOperatingSystem());
        return draft.createIfNotExist();
    }

    /**
     * API to create a new Deployment Slot by setting model.
     */
    @AzureOperation(
            name = "webapp.create_deployment.deployment|app",
            params = {"model.getNewSlotName()", "model.getWebAppName()"},
            type = AzureOperation.Type.SERVICE
    )
    public WebAppDeploymentSlot createDeploymentSlotFromSettingModel(@Nonnull final WebApp webApp, @Nonnull final WebAppSettingModel model) {
        String configurationSource = model.getNewSlotConfigurationSource();
        if (StringUtils.equalsIgnoreCase(configurationSource, webApp.name())) {
            configurationSource = WebAppDeploymentSlotDraft.CONFIGURATION_SOURCE_PARENT;
        }
        if (StringUtils.equalsIgnoreCase(configurationSource, DO_NOT_CLONE_SLOT_CONFIGURATION)) {
            configurationSource = WebAppDeploymentSlotDraft.CONFIGURATION_SOURCE_NEW;
        }
        final WebAppDeploymentSlotDraft draft = webApp.slots().create(model.getNewSlotName(), webApp.getResourceGroupName());
        draft.setConfigurationSource(configurationSource);
        return draft.createIfNotExist();
    }

    @AzureOperation(
        name = "webapp.upload_artifact.artifact|app",
        params = {"file.getName()", "deployTarget.name()"},
        type = AzureOperation.Type.SERVICE
    )
    public void deployArtifactsToWebApp(@Nonnull final WebAppBase<?, ?, ?> deployTarget, @Nonnull final File file,
                                        boolean isDeployToRoot, @Nonnull final IProgressIndicator progressIndicator) {
        final Action<Void> retry = Action.retryFromFailure(() -> deployArtifactsToWebApp(deployTarget, file, isDeployToRoot, progressIndicator));
        if (!(deployTarget instanceof WebApp || deployTarget instanceof WebAppDeploymentSlot)) {
            final String error = "the deployment target is not a valid (deployment slot of) Web App";
            final String action = "select a valid Web App or deployment slot to deploy the artifact";
            throw new AzureToolkitRuntimeException(error, action, retry);
        }
        // stop target app service
        final String stopMessage = deployTarget instanceof WebApp ? STOP_WEB_APP : STOP_DEPLOYMENT_SLOT;
        progressIndicator.setText(stopMessage);
        deployTarget.stop();
        // todo: @hanli migrate to use WebAppDeployTask
        final DeployType deployType = Optional.ofNullable(DeployType.fromString(FilenameUtils.getExtension(file.getName()))).orElse(DeployType.ZIP);
        // java se runtime will always deploy to root
        if (isDeployToRoot ||
            Objects.equals(Objects.requireNonNull(deployTarget.getRuntime()).getWebContainer(), WebContainer.JAVA_SE)) {
            deployTarget.deploy(deployType, file);
        } else {
            final String webappPath = String.format("webapps/%s", FilenameUtils.getBaseName(file.getName()).replaceAll("#", StringUtils.EMPTY));
            deployTarget.deploy(deployType, file, webappPath);
        }

        final String successMessage = deployTarget instanceof WebApp ? DEPLOY_SUCCESS_WEB_APP : DEPLOY_SUCCESS_DEPLOYMENT_SLOT;
        progressIndicator.setText(successMessage);
        deployTarget.start();
    }

    /**
     * Update app settings of deployment slot.
     * todo: move to app service library
     */
    @AzureOperation(
            name = "webapp.update_deployment_settings.deployment|app",
            params = {"slot.entity().getName()", "slot.entity().getWebappName()"},
            type = AzureOperation.Type.SERVICE
    )
    public void updateDeploymentSlotAppSettings(final WebAppDeploymentSlot slot, final Map<String, String> toUpdate) {
        final WebAppDeploymentSlotDraft draft = (WebAppDeploymentSlotDraft) slot.update();
        draft.setAppSettings(toUpdate);
        draft.updateIfExist();
    }

    private static final class SingletonHolder {
        private static final AzureWebAppMvpModel INSTANCE = new AzureWebAppMvpModel();
    }
}