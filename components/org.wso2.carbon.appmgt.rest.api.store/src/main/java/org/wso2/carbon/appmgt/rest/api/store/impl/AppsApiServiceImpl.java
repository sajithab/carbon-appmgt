/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.appmgt.rest.api.store.impl;

import com.google.gson.JsonObject;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.NativeObject;
import org.wso2.carbon.appmgt.api.APIConsumer;
import org.wso2.carbon.appmgt.api.APIProvider;
import org.wso2.carbon.appmgt.api.AppManagementException;
import org.wso2.carbon.appmgt.api.model.APIIdentifier;
import org.wso2.carbon.appmgt.api.model.APIStatus;
import org.wso2.carbon.appmgt.api.model.App;
import org.wso2.carbon.appmgt.api.model.Documentation;
import org.wso2.carbon.appmgt.api.model.FileContent;
import org.wso2.carbon.appmgt.api.model.MobileApp;
import org.wso2.carbon.appmgt.api.model.OneTimeDownloadLink;
import org.wso2.carbon.appmgt.api.model.PlistTemplateContext;
import org.wso2.carbon.appmgt.api.model.Subscriber;
import org.wso2.carbon.appmgt.api.model.Subscription;
import org.wso2.carbon.appmgt.api.model.Tag;
import org.wso2.carbon.appmgt.api.model.WebApp;
import org.wso2.carbon.appmgt.impl.AppMConstants;
import org.wso2.carbon.appmgt.impl.AppRepository;
import org.wso2.carbon.appmgt.impl.DefaultAppRepository;
import org.wso2.carbon.appmgt.impl.service.ServiceReferenceHolder;
import org.wso2.carbon.appmgt.impl.utils.AppManagerUtil;
import org.wso2.carbon.appmgt.impl.workflow.WorkflowConstants;
import org.wso2.carbon.appmgt.impl.workflow.WorkflowException;
import org.wso2.carbon.appmgt.impl.workflow.WorkflowExecutor;
import org.wso2.carbon.appmgt.impl.workflow.WorkflowExecutorFactory;
import org.wso2.carbon.appmgt.mobile.store.Operations;
import org.wso2.carbon.appmgt.mobile.utils.HostResolver;
import org.wso2.carbon.appmgt.mobile.utils.MobileApplicationException;
import org.wso2.carbon.appmgt.mobile.utils.MobileConfigurations;
import org.wso2.carbon.appmgt.rest.api.store.AppsApiService;
import org.wso2.carbon.appmgt.rest.api.store.dto.AppDTO;
import org.wso2.carbon.appmgt.rest.api.store.dto.AppListDTO;
import org.wso2.carbon.appmgt.rest.api.store.dto.AppRatingInfoDTO;
import org.wso2.carbon.appmgt.rest.api.store.dto.AppRatingListDTO;
import org.wso2.carbon.appmgt.rest.api.store.dto.DocumentDTO;
import org.wso2.carbon.appmgt.rest.api.store.dto.DocumentListDTO;
import org.wso2.carbon.appmgt.rest.api.store.dto.EventsDTO;
import org.wso2.carbon.appmgt.rest.api.store.dto.FavouritePageDTO;
import org.wso2.carbon.appmgt.rest.api.store.dto.InstallDTO;
import org.wso2.carbon.appmgt.rest.api.store.dto.ScheduleDTO;
import org.wso2.carbon.appmgt.rest.api.store.dto.TagListDTO;
import org.wso2.carbon.appmgt.rest.api.store.dto.UserIdListDTO;
import org.wso2.carbon.appmgt.rest.api.store.utils.mappings.APPMappingUtil;
import org.wso2.carbon.appmgt.rest.api.store.utils.mappings.DocumentationMappingUtil;
import org.wso2.carbon.appmgt.rest.api.util.RestApiConstants;
import org.wso2.carbon.appmgt.rest.api.util.utils.RestApiUtil;
import org.wso2.carbon.appmgt.rest.api.util.validation.BeanValidator;
import org.wso2.carbon.appmgt.rest.api.util.validation.CommonValidator;
import org.wso2.carbon.appmgt.usage.publisher.AppMUIActivitiesDASDataPublisher;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.social.core.SocialActivityException;
import org.wso2.carbon.social.core.service.SocialActivityService;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;
import org.wso2.mobile.utils.utilities.PlistTemplateBuilder;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppsApiServiceImpl extends AppsApiService {

    private static final Log log = LogFactory.getLog(AppsApiServiceImpl.class);
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX";

    BeanValidator beanValidator;

    /**
     * Download/Install mobile application
     *
     * @param contentType
     * @param install     InstallDTO
     * @return
     */
    
    @Override
    public Response appsDownloadPost(String contentType, InstallDTO install) {
        String username = RestApiUtil.getLoggedInUsername();
        try {
            APIProvider appProvider = RestApiUtil.getLoggedInUserProvider();
            String tenantDomainName = MultitenantUtils.getTenantDomain(username);
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(
                    tenantDomainName);
            String tenantUserName = MultitenantUtils.getTenantAwareUsername(username);
            String appId = install.getAppId();
            MobileApp mobileApp = appProvider.getMobileApp(appId);
            if (mobileApp == null) {
                RestApiUtil.handleResourceNotFoundError("Mobile Application", appId, log);
            }
            if (!APIStatus.PUBLISHED.getStatus().equals(mobileApp.getLifeCycleStatus().getStatus())) {
                RestApiUtil.handleBadRequest(
                        "Mobile application with uuid '" + appId + "' is not in '" + APIStatus.PUBLISHED + "' state",
                        log);
            }
            Operations mobileOperation = new Operations();
            String action = "install";
            String[] parameters = null;

            if ("user".equals(install.getType())) {
                parameters = new String[1];
                parameters[0] = username;
            } else if ("device".equals(install.getType())) {
                parameters = Arrays.copyOf(install.getDeviceIds().toArray(), install.getDeviceIds().toArray().length,
                                           String[].class);
                if (parameters == null) {
                    RestApiUtil.handleBadRequest("Device IDs should be provided to perform device app installation",
                            log);
                }
            } else {
                RestApiUtil.handleBadRequest("Invalid installation type.", log);
            }

            //TODO:Operations.performAction expects the user to be passed as a stringified object, so that
            //TODO:We are prviding a stringified user here
            JSONObject user = new JSONObject();
            user.put("username", tenantUserName);
            user.put("tenantDomain", tenantDomainName);
            user.put("tenantId", tenantId);
            //Check for app existance and app state


            appProvider.subscribeMobileApp(username, appId);
            String activityId = mobileOperation.performAction(user.toString(), action, tenantId, install.getType(),
                                                              appId, parameters, null, null);

            JSONObject response = new JSONObject();
            response.put("activityId", activityId);
            //mobileOperation.performAction(user.toString(), action, tenantId, appId, install.getType(), parameters, null);
            return Response.ok().entity(response.toString()).build();

        } catch (AppManagementException e) {
            RestApiUtil.handleInternalServerError("Internal Error occurred while installing", e, log);
        } catch (MobileApplicationException e) {
            RestApiUtil.handleBadRequest(e.getMessage(), log);
        } catch (UserStoreException e) {
            RestApiUtil.handleInternalServerError("User store related Error occurred while installing", e, log);
        } catch (JSONException e) {
            RestApiUtil.handleInternalServerError("Json casting Error occurred while installing", e, log);
        }
        return Response.serverError().build();

    }

    /**
     *
     * @param appId
     * @param contentType
     * @return
     */
    @Override
    public Response appsMobileIdAppIdDownloadPost(String appId, String contentType) {
        String username = RestApiUtil.getLoggedInUsername();
        Map<String, String> appURLResponse = new HashMap<>();
        String appURL = null;

        try {
            APIProvider appProvider = RestApiUtil.getLoggedInUserProvider();
            MobileApp mobileApp = appProvider.getMobileApp(appId);
            if (mobileApp == null) {
                RestApiUtil.handleResourceNotFoundError("Mobile Application", appId, log);
            }
            if (!APIStatus.PUBLISHED.getStatus().equals(mobileApp.getLifeCycleStatus().getStatus())) {
                RestApiUtil.handleBadRequest(
                        "Mobile application with uuid '" + appId + "' is not in '" + APIStatus.PUBLISHED + "' state",
                        log);
            }

            //Make user subscription, it the subscription is not available
            appProvider.subscribeMobileApp(username, appId);

            if (AppMConstants.MobileAppTypes.ENTERPRISE.equals(mobileApp.getType())) {
                String oneTimeDownloadUUID = appProvider.generateOneTimeDownloadLink(appId);
                if (AppMConstants.MOBILE_APPS_PLATFORM_ANDROID.equals(mobileApp.getPlatform())) {
                    appURL = HostResolver.getHost(MobileConfigurations.getInstance().getMDMConfigs().get(
                            MobileConfigurations.APP_DOWNLOAD_URL_HOST)) + RestApiUtil.getStoreRESTAPIContextPath() +
                            AppMConstants.MOBILE_ONE_TIME_DOWNLOAD_API_PATH + File.separator + oneTimeDownloadUUID;
                } else if (AppMConstants.MOBILE_APPS_PLATFORM_IOS.equals(mobileApp.getPlatform())) {
                    appURL = HostResolver.getHost(MobileConfigurations.getInstance().getMDMConfigs()
                            .get(MobileConfigurations.APP_DOWNLOAD_URL_HOST)) + RestApiUtil.getStoreRESTAPIContextPath()
                            + AppMConstants.MOBILE_PLIST_API_PATH + File.separator + appId + File.separator + oneTimeDownloadUUID;
                }
            } else if (AppMConstants.MOBILE_APPS_PLATFORM_WEBAPP.equals(mobileApp.getType()) ||
                    AppMConstants.MobileAppTypes.PUBLIC.equals(mobileApp.getType())) {
                appURL = mobileApp.getAppUrl();
            }
            Map<String,String> response = new HashMap<>();
            response.put("appUrl", appURL);
            return Response.ok().entity(response).build();
        } catch (AppManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e) || RestApiUtil.isDueToAuthorizationFailure(e)) {
                RestApiUtil.handleResourceNotFoundError(AppMConstants.MOBILE_ASSET_TYPE, appId, e, log);
            } else {
                RestApiUtil.handleInternalServerError(
                        "Error occurred while subscribing to mobile app with uuid : " + appId, e, log);
            }
        }
        return null;

    }

    @Override
    public Response appsEventPublishPost(EventsDTO events, String contentType) {
        beanValidator = new BeanValidator();
        //Validate common mandatory fields for mobile and webapp
        beanValidator.validate(events);

        if (events.getEvents().size() == 0) {
            RestApiUtil.handleBadRequest("Invalid event stream", log);
        }
        AppMUIActivitiesDASDataPublisher appMgtBAMPublishObj = new AppMUIActivitiesDASDataPublisher();

        NativeObject[] statsObjectArr = new NativeObject[events.getEvents().size()];
        for (int i = 0; i < events.getEvents().size(); i++) {
            HashMap statMap = ((HashMap) (events.getEvents().get(i)));
            NativeObject statObj = new NativeObject();
            statObj.put("action", statObj, statMap.get("action"));
            statObj.put("item", statObj, statMap.get("item"));
            statObj.put("timestamp", statObj, statMap.get("timestamp"));
            statObj.put("appId", statObj, statMap.get("appId"));
            statObj.put("userId", statObj, statMap.get("userId"));
            statObj.put("tenantId", statObj, statMap.get("tenantId"));
            statObj.put("appName", statObj, statMap.get("appName"));
            statObj.put("appVersion", statObj, statMap.get("appVersion"));
            statObj.put("context", statObj, statMap.get("context"));

            statsObjectArr[i] = statObj;
        }
        //Pass data to java class to save
        appMgtBAMPublishObj.processUiActivityObject(statsObjectArr);
        return Response.accepted().build();
    }

    /**
     * Get Favourite home page in App Store
     *
     * @param accept
     * @param ifNoneMatch
     * @return
     */
    @Override
    public Response appsFavouritePageGet(String accept, String ifNoneMatch) {
        FavouritePageDTO favouritePageDTO = new FavouritePageDTO();
        boolean isTenantFlowStarted = false;
        try {
            String username = RestApiUtil.getLoggedInUsername();
            String tenantDomainOfUser = RestApiUtil.getLoggedInUserTenantDomain();
            int tenantIdOfUser = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(
                    tenantDomainOfUser);
            if (tenantDomainOfUser != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(
                    tenantDomainOfUser)) {
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomainOfUser, true);
            }
            int tenantIdOFStore = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
            APIConsumer apiConsumer = RestApiUtil.getLoggedInUserConsumer();
            boolean hasFavouritePage = apiConsumer.hasFavouritePage(username, tenantIdOfUser, tenantIdOFStore);
            favouritePageDTO.setIsDefaultPage(hasFavouritePage);
        } catch (UserStoreException e) {
            RestApiUtil.handleInternalServerError("User Store Error occurred while retrieving Favourite page details",
                                                  e, log);
        } catch (AppManagementException e) {
            RestApiUtil.handleInternalServerError("Internal Error occurred while retrieving Favourite page details", e,
                                                  log);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return Response.ok().entity(favouritePageDTO).build();
    }

    /**
     * Set favourite page for currently logged in user in App Store
     *
     * @return
     */
    @Override
    public Response appsFavouritePagePost() {
        boolean isTenantFlowStarted = false;
        try {
            String username = RestApiUtil.getLoggedInUsername();
            String tenantDomainOfUser = RestApiUtil.getLoggedInUserTenantDomain();
            int tenantIdOfUser = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(
                    tenantDomainOfUser);
            if (tenantDomainOfUser != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(
                    tenantDomainOfUser)) {
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomainOfUser, true);
            }
            int tenantIdOFStore = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
            APIConsumer apiConsumer = RestApiUtil.getLoggedInUserConsumer();
            apiConsumer.setFavouritePage(username, tenantIdOfUser, tenantIdOFStore);
        } catch (UserStoreException e) {
            RestApiUtil.handleInternalServerError("User Store Error occurred while saving Favourite page", e, log);
        } catch (AppManagementException e) {
            RestApiUtil.handleInternalServerError("Internal Error occurred while saving Favourite page", e, log);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return Response.ok().build();
    }

    /**
     * Remove favourite page of the currently logged i user in App Store
     *
     * @return
     */
    @Override
    public Response appsFavouritePageDelete() {
        boolean isTenantFlowStarted = false;
        try {
            String username = RestApiUtil.getLoggedInUsername();
            String tenantDomainOfUser = RestApiUtil.getLoggedInUserTenantDomain();
            int tenantIdOfUser = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(
                    tenantDomainOfUser);
            if (tenantDomainOfUser != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(
                    tenantDomainOfUser)) {
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomainOfUser, true);
            }
            int tenantIdOFStore = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
            APIConsumer apiConsumer = RestApiUtil.getLoggedInUserConsumer();
            apiConsumer.removeFavouritePage(username, tenantIdOfUser, tenantIdOFStore);
        } catch (UserStoreException e) {
            RestApiUtil.handleInternalServerError("User Store Error occurred while removing Favourite page", e, log);
        } catch (AppManagementException e) {
            RestApiUtil.handleInternalServerError("Internal Error occurred while removing Favourite page", e, log);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return Response.ok().build();
    }

    /**
     * Retrieve mobile binary from storage
     *
     * @param fileName          binary file name
     * @param ifMatch
     * @param ifUnmodifiedSince
     * @return mobile app binary file content
     */
    @Override
    public Response appsMobileBinariesFileNameGet(String fileName, String ifMatch, String ifUnmodifiedSince) {
        File binaryFile = null;
        String contentType = null;
        try {

            if(!RestApiUtil.isValidFileName(fileName)){
                RestApiUtil.handleBadRequest("Invalid file '"+fileName +"' is provided", log);
            }

            String fileExtension = FilenameUtils.getExtension(fileName);
            if (AppMConstants.MOBILE_APPS_ANDROID_EXT.equals(fileExtension) ||
                    AppMConstants.MOBILE_APPS_IOS_EXT.equals(fileExtension)) {

                binaryFile = RestApiUtil.readFileFromStorage(fileName);

                contentType = RestApiUtil.readFileContentType(binaryFile.getAbsolutePath());
                if (!contentType.startsWith("application")) {
                    RestApiUtil.handleBadRequest("Invalid file '" + fileName + "' with unsupported file type requested",
                                                 log);
                }
            } else {
                RestApiUtil.handleBadRequest("Invalid file '" + fileName + "' with unsupported media type is requested",
                                             log);
            }
        } catch (AppManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e) || RestApiUtil.isDueToAuthorizationFailure(e)) {
                RestApiUtil.handleResourceNotFoundError("Static Content", fileName, e, log);
            } else {
                RestApiUtil.handleInternalServerError(
                        "Error occurred while retrieving mobile binary : " + fileName + "from storage", e, log);
            }
        }
        Response.ResponseBuilder response = Response.ok((Object) binaryFile);
        response.header(RestApiConstants.HEADER_CONTENT_DISPOSITION, RestApiConstants.CONTENT_DISPOSITION_ATTACHMENT
                + "; " + RestApiConstants.CONTENT_DISPOSITION_FILENAME + "=\"" + fileName + "\"");
        response.header(RestApiConstants.HEADER_CONTENT_TYPE, contentType);
        return response.build();
    }

    /**
     * Mobile app one-time download API
     *
     * @param uuid              one-time download link uuid
     * @param ifMatch
     * @param ifUnmodifiedSince
     * @return
     */
    @Override
    public Response appsMobileBinariesOneTimeUuidGet(String uuid, String ifMatch, String ifUnmodifiedSince) {
        File binaryFile = null;
        String contentType = null;
        try {
            APIProvider appProvider = RestApiUtil.getLoggedInUserProvider();
            OneTimeDownloadLink oneTimeDownloadLink = appProvider.getOneTimeDownloadLinkDetails(uuid);
            if (oneTimeDownloadLink.isDownloaded()) {
                RestApiUtil.handleForbiddenRequest("App binary one-time download API resource access with uuid '" +
                                                           uuid + "' is forbidden", log);
            }
            String fileName = oneTimeDownloadLink.getFileName();
            binaryFile = RestApiUtil.readFileFromStorage(fileName);
            contentType = RestApiUtil.readFileContentType(binaryFile.getAbsolutePath());
            if (!contentType.startsWith("application")) {
                RestApiUtil.handleBadRequest("Invalid file '" + fileName + "' with unsupported file type requested",
                                             log);
            }
            Response.ResponseBuilder response = Response.ok((Object) binaryFile);
            response.header(RestApiConstants.HEADER_CONTENT_DISPOSITION, RestApiConstants.CONTENT_DISPOSITION_ATTACHMENT
                    + "; " + RestApiConstants.CONTENT_DISPOSITION_FILENAME + "=\"" + fileName + "\"");
            response.header(RestApiConstants.HEADER_CONTENT_TYPE, contentType);
            oneTimeDownloadLink.setDownloaded(true);
            appProvider.updateOneTimeDownloadLinkStatus(oneTimeDownloadLink);
            return response.build();
        } catch (AppManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e) || RestApiUtil.isDueToAuthorizationFailure(e)) {
                RestApiUtil.handleResourceNotFoundError("Invalid downloadable link uuid", uuid, e, log);
            } else {
                RestApiUtil.handleInternalServerError(
                        "Error occurred while retrieving mobile binary via one-time download link with uuid" + uuid, e,
                        log);
            }
        }
        return null;
    }

    /**
     * @param appId
     * @param uuid
     * @param ifMatch
     * @param ifUnmodifiedSince
     * @return
     */
    @Override
    public Response appsMobilePlistAppIdUuidGet(String appId, String uuid, String ifMatch, String ifUnmodifiedSince) {

        try {
            APIProvider appProvider = RestApiUtil.getLoggedInUserProvider();
            OneTimeDownloadLink oneTimeDownloadLink = appProvider.getOneTimeDownloadLinkDetails(uuid);
            if (oneTimeDownloadLink == null) {
                RestApiUtil.handleResourceNotFoundError("one-time download link uuid", uuid, log);
            }
            PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            carbonContext.setUsername(oneTimeDownloadLink.getCreatedUserName());
            carbonContext.setTenantDomain(
                    org.wso2.carbon.utils.multitenancy.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
            carbonContext.setTenantId(oneTimeDownloadLink.getCreatedTenantID());
            appProvider = RestApiUtil.getLoggedInUserProvider();
            MobileApp mobileApp = appProvider.getMobileApp(appId);
            if (mobileApp == null) {
                RestApiUtil.handleResourceNotFoundError("Mobile Application", appId, log);
            }
            if (!APIStatus.PUBLISHED.getStatus().equals(mobileApp.getLifeCycleStatus().getStatus())) {
                RestApiUtil.handleBadRequest(
                        "Mobile application with uuid '" + appId + "' is not in '" + APIStatus.PUBLISHED + "' state",
                        log);
            }
            String oneTimeDownloadLinkAPIPath =
                    HostResolver.getHost(MobileConfigurations.getInstance().getMDMConfigs().get(
                            MobileConfigurations.APP_DOWNLOAD_URL_HOST)) + RestApiUtil.getStoreRESTAPIContextPath() +
                            AppMConstants.MOBILE_ONE_TIME_DOWNLOAD_API_PATH +  File.separator + uuid;
            PlistTemplateContext plistTemplateContext = new PlistTemplateContext();
            plistTemplateContext.setAppName(mobileApp.getAppName());
            plistTemplateContext.setBundleVersion(mobileApp.getBundleVersion());
            plistTemplateContext.setPackageName(mobileApp.getPackageName());
            plistTemplateContext.setOneTimeDownloadUrl(oneTimeDownloadLinkAPIPath);
            PlistTemplateBuilder plistTemplateBuilder = new PlistTemplateBuilder();
            String plistFileContent = plistTemplateBuilder.generatePlistConfig(plistTemplateContext);
            return Response.ok().entity(plistFileContent).build();
        } catch (AppManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e) || RestApiUtil.isDueToAuthorizationFailure(e)) {
                RestApiUtil.handleResourceNotFoundError("Invalid plist retrieval", appId, e, log);
            } else {
                RestApiUtil.handleInternalServerError(
                        "Error occurred while retrieving plist configuration for IOS mobile app '" + appId +
                                "' installation", e, log);
            }
        }
        return null;
    }

    /**
     * Retrieve a given static content from storage
     *
     * @param fileName          request file name
     * @param ifMatch
     * @param ifUnmodifiedSince
     * @return
     */
    @Override
    public Response appsStaticContentsFileNameGet(String appType, String fileName, String ifMatch,
                                                  String ifUnmodifiedSince) {
        CommonValidator.isValidAppType(appType);
        File staticContentFile = null;
        String contentType = null;

        try {
            if (AppMConstants.MOBILE_ASSET_TYPE.equals(appType)) {
                staticContentFile = RestApiUtil.readFileFromStorage(fileName);
                contentType = RestApiUtil.readFileContentType(staticContentFile.getAbsolutePath());
            } else if (AppMConstants.WEBAPP_ASSET_TYPE.equals(appType)) {
                OutputStream outputStream = null;
                AppRepository appRepository = new DefaultAppRepository(null);
                try {
                    FileContent fileContent = appRepository.getStaticContent(fileName);
                    staticContentFile = File.createTempFile("temp", ".tmp");
                    outputStream = new FileOutputStream(staticContentFile);
                    IOUtils.copy(fileContent.getContent(), outputStream);
                    contentType = fileContent.getContentType();
                } catch (IOException e) {
                    RestApiUtil.handleInternalServerError("Error occurred while retrieving static content '" +
                                                                  fileName + "'", e, log);
                }
            }
            if (staticContentFile == null || !staticContentFile.exists()) {
                RestApiUtil.handleResourceNotFoundError("Static Content", fileName, log);
            }
            if (contentType != null && !contentType.startsWith("image")) {
                RestApiUtil.handleBadRequest("Invalid file '" + fileName + "'with unsupported file type requested",
                        log);
            }

            Response.ResponseBuilder response = Response.ok((Object) staticContentFile);
            response.header(RestApiConstants.HEADER_CONTENT_DISPOSITION, RestApiConstants.CONTENT_DISPOSITION_ATTACHMENT
                    + "; " + RestApiConstants.CONTENT_DISPOSITION_FILENAME + "=\"" + fileName + "\"");
            response.header(RestApiConstants.HEADER_CONTENT_TYPE, contentType);
            return response.build();
        } catch (AppManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e) || RestApiUtil.isDueToAuthorizationFailure(e)) {
                RestApiUtil.handleResourceNotFoundError("Static Content", fileName, e, log);
            } else {
                RestApiUtil.handleInternalServerError(
                        "Error occurred while retrieving static content : " + fileName + "from storage", e, log);
            }
        }
        return null;
    }

    @Override
    public Response appsUninstallationPost(String contentType, InstallDTO install) {
        String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
        String username = RestApiUtil.getLoggedInUsername();
        try {

            APIProvider appProvider = RestApiUtil.getLoggedInUserProvider();
            String tenantDomainName = MultitenantUtils.getTenantDomain(username);
            int tenantId = 0;
            tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(
                    tenantDomainName);

            String tenantUserName = MultitenantUtils.getTenantAwareUsername(username);
            String appId = install.getAppId();

            Map<String, String> searchTerms = new HashMap<String, String>();
            searchTerms.put("id", appId);
            List<App> result = appProvider.searchApps(AppMConstants.MOBILE_ASSET_TYPE, searchTerms);
            if (result.isEmpty()) {
                String errorMessage = "Could not find requested application.";
                return RestApiUtil.buildNotFoundException(errorMessage, appId).getResponse();
            }

            Operations mobileOperation = new Operations();
            String action = "uninstall";
            String[] parameters = null;

            if ("user".equals(install.getType())) {
                parameters = new String[1];
                parameters[0] = username;
            } else if ("device".equals(install.getType())) {
                parameters = Arrays.copyOf(install.getDeviceIds().toArray(), install.getDeviceIds().toArray().length,
                                           String[].class);
                if (parameters == null) {
                    RestApiUtil.handleBadRequest("Device IDs should be provided to perform device app installation",
                            log);
                }
            } else {
                RestApiUtil.handleBadRequest("Invalid installation type.", log);
            }

            //TODO:Operations.performAction expects the user to be passed as a stringified object, so that
            //TODO:We are prviding a stringified user here
            JSONObject user = new JSONObject();
            user.put("username", tenantUserName);
            user.put("tenantDomain", tenantDomainName);
            user.put("tenantId", tenantId);

            boolean isUnSubscribed = appProvider.unSubscribeMobileApp(username, appId);
            if (!isUnSubscribed) {
                RestApiUtil.handlePreconditionFailedRequest(
                        "Application is not installed yet. Application with id : " + appId +
                                "must be installed prior to uninstall.", log);
            }
            String activityId = mobileOperation.performAction(user.toString(), action, tenantId, install.getType(),
                                                              appId, parameters, null, null);

            JSONObject response = new JSONObject();
            response.put("activityId", activityId);

            return Response.ok().entity(response.toString()).build();

        } catch (AppManagementException e) {
           // mobileOperation.performAction(user.toString(), action, tenantId, appId, install.getType(), parameters, null);
            RestApiUtil.handleInternalServerError("Internal Error occurred while uninstalling", e, log);
        } catch (MobileApplicationException e) {
            RestApiUtil.handleBadRequest(e.getMessage(), log);
        } catch (UserStoreException e) {
            RestApiUtil.handleInternalServerError("User Store related Error occurred while uninstalling", e, log);
        } catch (JSONException e) {
            RestApiUtil.handleInternalServerError("JSON casting related Error occurred while uninstalling", e, log);
        }
        return Response.serverError().build();
    }

    @Override
    public Response appsAppTypeGet(String appType, String query, String fieldFilter, Integer limit, Integer offset,
                                   String accept, String ifNoneMatch) {

        //setting default limit and offset values if they are not set
        limit = limit != null ? limit : RestApiConstants.PAGINATION_LIMIT_DEFAULT;
        offset = offset != null ? offset : RestApiConstants.PAGINATION_OFFSET_DEFAULT;
        query = query == null ? "" : query;

        try {
            //check if a valid asset type is provided
            CommonValidator.isValidAppType(appType);

            /*
            // If the asset type is 'site' we need to add the registry attribute filtering and make the appType as webapp
            // due to, publisher side we don't have separate asset type as 'site' rather then a flag as 'treatAsASite'
            // to the webapp asset type.
            */

            //Building registry filtering field.
            query = APPMappingUtil.buildQuery(appType, query);
            //Make the asset type as 'webapp'.
            appType = APPMappingUtil.updateAssetType(appType);

            APIProvider apiProvider = RestApiUtil.getLoggedInUserProvider();

            List<App> result = apiProvider.searchPublishedApps(appType, RestApiUtil.getSearchTerms(query));

            AppListDTO appListDTO = null;
            if (fieldFilter == null || "BASIC".equalsIgnoreCase(fieldFilter)) {
                appListDTO = APPMappingUtil.getAppListDTOWithBasicFields(result, offset, limit);

            } else {
                appListDTO = APPMappingUtil.getAppListDTOWithAllFields(result, offset, limit);
            }

            APPMappingUtil.setPaginationParams(appListDTO, query, offset, limit, result.size());
            return Response.ok().entity(appListDTO).build();
        } catch (AppManagementException e) {
            String errorMessage = "Error while retrieving Apps";
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }


    @Override
    public Response appsAppTypeIdAppIdGet(String appType, String appId, String accept, String ifNoneMatch,
                                          String ifModifiedSince) {
        AppDTO appToReturn = null;
        String query = "";
        try {
            //check if a valid asset type is provided
            CommonValidator.isValidAppType(appType);

            /*
            // If the asset type is 'site' we need to add the registry attribute filtering and make the appType as webapp
            // since the publisher side we don't have separate asset type as 'site' rather then a flag as 'treatAsASite'
            // to the webapp asset type.
            */

            //Building registry filtering field.
            query = APPMappingUtil.buildQuery(appType, query);
            //Make the asset type as 'webapp'.
            appType = APPMappingUtil.updateAssetType(appType);

            Map<String, String> searchTerms = new HashMap<String, String>();
            searchTerms.put("id", appId);
            searchTerms.putAll(RestApiUtil.getSearchTerms(query));

            APIProvider apiProvider = RestApiUtil.getLoggedInUserProvider();
            List<App> result = apiProvider.searchApps(appType, searchTerms);
            if (result.isEmpty()) {
                String errorMessage = "Could not find requested application.";
                return RestApiUtil.buildNotFoundException(errorMessage, appId).getResponse();
            }
            appToReturn = APPMappingUtil.fromAppToDTO(result.get(0));
            if (appToReturn == null) {
                String errorMessage = "Could not find requested application.";
                return RestApiUtil.buildNotFoundException(errorMessage, appId).getResponse();
            }

        } catch (AppManagementException e) {
            //Auth failure occurs when cross tenant accessing APIs. Sends 404, since we don't need to expose the
            // existence of the resource
            if (RestApiUtil.isDueToResourceNotFound(e) || RestApiUtil.isDueToAuthorizationFailure(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_API, appId, e, log);
            } else {
                String errorMessage = "Error while retrieving App : " + appId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return Response.ok().entity(appToReturn).build();
    }

    @Override
    public Response appsAppTypeIdAppIdFavouriteAppPost(String appType, String appId, String contentType) {
        boolean isTenantFlowStarted = false;
        String query = "";
        try {
            //check if a valid asset type is provided. Currently support only webapps
            if (!(appType.equalsIgnoreCase(AppMConstants.WEBAPP_ASSET_TYPE) ||
                    appType.equalsIgnoreCase(AppMConstants.SITE_ASSET_TYPE))) {
                String errorMessage = "Invalid Asset Type : " + appType;
                RestApiUtil.handleBadRequest(errorMessage, log);
            }

            /*
            // If the asset type is 'site' we need to add the registry attribute filtering and make the appType as webapp
            // since the publisher side we don't have separate asset type as 'site' rather then a flag as 'treatAsASite'
            // to the webapp asset type.
            */

            //Building registry filtering field.
            query = APPMappingUtil.buildQuery(appType, query);
            //Make the asset type as 'webapp'.
            appType = APPMappingUtil.updateAssetType(appType);

            //logged user's username
            String username = RestApiUtil.getLoggedInUsername();
            //logged user's tenant domain
            String tenantDomainOfUser = RestApiUtil.getLoggedInUserTenantDomain();
            //logged user's tenant id
            int tenantIdOfUser = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(
                    tenantDomainOfUser);
            if (tenantDomainOfUser != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(
                    tenantDomainOfUser)) {
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomainOfUser, true);
            }
            //tenant id of the store
            int tenantIdOFStore = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
            APIConsumer apiConsumer = RestApiUtil.getLoggedInUserConsumer();
            APIProvider apiProvider = RestApiUtil.getLoggedInUserProvider();

            Map<String, String> searchTerms = new HashMap<String, String>();
            searchTerms.put("id", appId);
            searchTerms.putAll(RestApiUtil.getSearchTerms(query));

            //get app details by id
            List<App> result = apiProvider.searchApps(appType, searchTerms);
            //check if it's valid app
            if (result.isEmpty() || result.size() == 0) {
                String errorMessage = "Could not find requested application.";
                return RestApiUtil.buildNotFoundException(errorMessage, appId).getResponse();
            }
            AppDTO appDTO = APPMappingUtil.fromAppToDTO(result.get(0));
            if (appDTO == null) {
                String errorMessage = "Could not find requested application.";
                return RestApiUtil.buildNotFoundException(errorMessage, appId).getResponse();
            }
            String providerName = appDTO.getProvider();
            String apiName = appDTO.getName();
            String version = appDTO.getVersion();

            APIIdentifier identifier = new APIIdentifier(providerName, apiName, version);
            boolean isFavouriteApp = apiConsumer.isFavouriteApp(identifier, username, tenantIdOfUser, tenantIdOFStore);

            //add to favourite if it is not already added
            if (!isFavouriteApp) {
                apiConsumer.addToFavouriteApps(identifier, username, tenantIdOfUser, tenantIdOFStore);
            }
        } catch (UserStoreException e) {
            RestApiUtil.handleInternalServerError("User Store Error occurred while adding as Favourite", e, log);
        } catch (AppManagementException e) {
            RestApiUtil.handleInternalServerError("Internal Error occurred while adding as Favourite", e, log);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return Response.ok().build();
    }

    @Override
    public Response appsAppTypeIdAppIdFavouriteAppDelete(String appType, String appId, String contentType) {
        boolean isTenantFlowStarted = false;
        String query = "";
        try {
            //check if a valid asset type is provided. Currently support only webapps
            if (!(appType.equalsIgnoreCase(AppMConstants.WEBAPP_ASSET_TYPE) ||
                    appType.equalsIgnoreCase(AppMConstants.SITE_ASSET_TYPE))) {
                String errorMessage = "Invalid Asset Type : " + appType;
                RestApiUtil.handleBadRequest(errorMessage, log);
            }

            /*
            // If the asset type is 'site' we need to add the registry attribute filtering and make the appType as webapp
            // since the publisher side we don't have separate asset type as 'site' rather then a flag as 'treatAsASite'
            // to the webapp asset type.
            */

            //Building registry filtering field.
            query = APPMappingUtil.buildQuery(appType, query);
            //Make the asset type as 'webapp'.
            appType = APPMappingUtil.updateAssetType(appType);

            //logged user's username
            String username = RestApiUtil.getLoggedInUsername();
            //logged user's tenant domain
            String tenantDomainOfUser = RestApiUtil.getLoggedInUserTenantDomain();
            //logged user's tenant id
            int tenantIdOfUser = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(
                    tenantDomainOfUser);
            if (tenantDomainOfUser != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(
                    tenantDomainOfUser)) {
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomainOfUser, true);
            }
            //tenant id of the store
            int tenantIdOFStore = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
            APIConsumer apiConsumer = RestApiUtil.getLoggedInUserConsumer();
            APIProvider apiProvider = RestApiUtil.getLoggedInUserProvider();

            Map<String, String> searchTerms = new HashMap<String, String>();
            searchTerms.put("id", appId);
            searchTerms.putAll(RestApiUtil.getSearchTerms(query));

            //get app details by id
            List<App> result = apiProvider.searchApps(appType, searchTerms);
            //check if it's valid app
            if (result.isEmpty() || result.size() == 0) {
                String errorMessage = "Could not find requested application.";
                return RestApiUtil.buildNotFoundException(errorMessage, appId).getResponse();
            }
            AppDTO appDTO = APPMappingUtil.fromAppToDTO(result.get(0));
            if (appDTO == null) {
                String errorMessage = "Could not find requested application.";
                return RestApiUtil.buildNotFoundException(errorMessage, appId).getResponse();
            }
            String providerName = appDTO.getProvider();
            String apiName = appDTO.getName();
            String version = appDTO.getVersion();

            APIIdentifier identifier = new APIIdentifier(providerName, apiName, version);
            boolean isFavouriteApp = apiConsumer.isFavouriteApp(identifier, username, tenantIdOfUser, tenantIdOFStore);

            //remove from favourite if is it a favourite app
            if (isFavouriteApp) {
                apiConsumer.removeFromFavouriteApps(identifier, username, tenantIdOfUser, tenantIdOFStore);
            }
        } catch (UserStoreException e) {
            RestApiUtil.handleInternalServerError("User Store Error occurred while adding as Favourite", e, log);
        } catch (AppManagementException e) {
            RestApiUtil.handleInternalServerError("Internal Error occurred while adding as Favourite", e, log);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return Response.ok().build();
    }

    @Override
    public Response appsAppTypeIdAppIdRateGet(String appType, String appId, Integer limit, Integer offset,
                                              String accept, String ifNoneMatch, String ifModifiedSince) {
        AppRatingListDTO appRatingListDTO = new AppRatingListDTO();
        limit = limit != null ? limit : RestApiConstants.PAGINATION_LIMIT_DEFAULT;
        offset = offset != null ? offset : RestApiConstants.PAGINATION_OFFSET_DEFAULT;
        String query = "";
        try {
            //check if a valid asset type is provided
            CommonValidator.isValidAppType(appType);

            /*
            // If the asset type is 'site' we need to add the registry attribute filtering and make the appType as webapp
            // since the publisher side we don't have separate asset type as 'site' rather then a flag as 'treatAsASite'
            // to the webapp asset type.
            */

            //Building registry filtering field.
            query = APPMappingUtil.buildQuery(appType, query);
            //Make the asset type as 'webapp'.
            appType = APPMappingUtil.updateAssetType(appType);

            //check App Id validity
            Map<String, String> searchTerms = new HashMap<String, String>();
            searchTerms.put("id", appId);
            searchTerms.putAll(RestApiUtil.getSearchTerms(query));

            APIProvider apiProvider = RestApiUtil.getLoggedInUserProvider();
            List<App> result = apiProvider.searchApps(appType, searchTerms);
            if (result.isEmpty()) {
                String errorMessage = "Could not find requested application.";
                return RestApiUtil.buildNotFoundException(errorMessage, appId).getResponse();
            }

            PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            SocialActivityService socialActivityService = (SocialActivityService) carbonContext.getOSGiService(
                    org.wso2.carbon.social.core.service.SocialActivityService.class, null);
            JsonObject rating = socialActivityService.getRating(appType + ":" + appId);
            if (rating != null && rating.get("rating") != null) {
                appRatingListDTO.setOverallRating(rating.get("rating").getAsBigDecimal());

                JSONObject socialObj;
                socialObj = new JSONObject(socialActivityService.getSocialObjectJson(appType + ":" + appId, "asc",
                                                                                     offset, limit));
                org.json.JSONArray socialArr = socialObj.getJSONArray("attachments");
                List<AppRatingInfoDTO> appRatingInfoDTOList = new ArrayList<>();
                for (int i = 0; i < socialArr.length(); i++) {
                    AppRatingInfoDTO appRatingInfoDTO = new AppRatingInfoDTO();
                    JSONObject ratingObj = (JSONObject) ((JSONObject) socialArr.get(i)).get("object");
                    appRatingInfoDTO.setRating(Integer.parseInt(ratingObj.get("rating").toString()));
                    appRatingInfoDTO.setId(Integer.parseInt(ratingObj.get("id").toString()));
                    appRatingInfoDTO.setReview(ratingObj.get("content").toString());
                    appRatingInfoDTO.setLikes(Integer.parseInt(((JSONObject) (ratingObj.get("likes"))).get("totalItems")
                                                                       .toString()));
                    appRatingInfoDTO.setDislikes(Integer.parseInt(((JSONObject) (ratingObj.get("dislikes"))).get(
                            "totalItems").toString()));
                    appRatingInfoDTOList.add(appRatingInfoDTO);
                }
                int totalRecords = rating.get("count").getAsInt();
                APPMappingUtil.setAppRatingPaginationParams(appRatingListDTO, offset, limit, totalRecords);
                appRatingListDTO.setRatingDetails(appRatingInfoDTOList);
                appRatingListDTO.setCount(appRatingInfoDTOList.size());
            } else {
                return RestApiUtil.buildNotFoundException("Rating", appId).getResponse();
            }
        } catch (SocialActivityException e) {
            String errorMessage = String.format("Can't get the rating for the app '%s:%s'", appType, appId);
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        } catch (AppManagementException e) {
            String errorMessage = String.format("Internal error while retrieving the rating for the app '%s:%s'",
                                                appType, appId);
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        } catch (JSONException e) {
            String errorMessage = String.format(
                    "JSONException occurred while casting. Can't get the rating for the app '%s:%s'", appType, appId);
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return Response.ok().entity(appRatingListDTO).build();
    }

    @Override
    public Response appsAppTypeIdAppIdRatePut(String appType, String appId, AppRatingInfoDTO rating, String contentType,
                                              String ifMatch, String ifUnmodifiedSince) {
        AppRatingInfoDTO appRatingInfoDTO = new AppRatingInfoDTO();
        String query = "";
        try {
            //check if a valid asset type is provided
            CommonValidator.isValidAppType(appType);

            /*
            // If the asset type is 'site' we need to add the registry attribute filtering and make the appType as webapp
            // since the publisher side we don't have separate asset type as 'site' rather then a flag as 'treatAsASite'
            // to the webapp asset type.
            */

            //Building registry filtering field.
            query = APPMappingUtil.buildQuery(appType, query);
            //Make the asset type as 'webapp'.
            appType = APPMappingUtil.updateAssetType(appType);

            //check App Id validity
            Map<String, String> searchTerms = new HashMap<String, String>();
            searchTerms.put("id", appId);
            searchTerms.putAll(RestApiUtil.getSearchTerms(query));

            APIProvider apiProvider = RestApiUtil.getLoggedInUserProvider();
            List<App> result = apiProvider.searchApps(appType, searchTerms);
            if (result.isEmpty()) {
                String errorMessage = "Could not find requested application.";
                return RestApiUtil.buildNotFoundException(errorMessage, appId).getResponse();
            }
            String tenantUserName = RestApiUtil.getLoggedInUsername() + "@" + RestApiUtil.getLoggedInUserTenantDomain();
            PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            SocialActivityService socialActivityService = (SocialActivityService) carbonContext.getOSGiService(
                    org.wso2.carbon.social.core.service.SocialActivityService.class, null);
            String activity =
                    "{\"verb\":\"post\",\"object\":{\"objectType\":\"review\",\"content\":" + rating.getReview() + "," +
                            "\"rating\":" + rating.getRating() +
                            ",\"likes\":{\"totalItems\":0},\"dislikes\":{\"totalItems\":0}}," + "\"target\":{\"id\":" +
                            "\"" + appType + ":" + appId + "\"" + "},\"actor\":{\"id\":" + tenantUserName + "\"," +
                            "objectType\":\"person\"}}";


            long id = socialActivityService.publish(activity);
            appRatingInfoDTO.setId((int) id);
            appRatingInfoDTO.setRating(rating.getRating());
            appRatingInfoDTO.setReview(rating.getReview());

        } catch (AppManagementException e) {
            String errorMessage = String.format("Internal error while saving the rating for the app '%s:%s'",
                                                appType, appId);
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        } catch (SocialActivityException e) {
            String errorMessage = String.format("Social component error while saving the rating for the app '%s:%s'",
                                                appType, appId);
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return Response.ok().entity(appRatingInfoDTO).build();
    }


    @Override
    public Response appsAppTypeIdAppIdStorageFileNameGet(String appType, String appId, String fileName, String ifMatch,
                                                         String ifUnmodifiedSince) {
        return null;
    }

    /**
     * Retrieve subscription of the given user for a given app
     *
     * @param appType
     * @param appId
     * @param accept
     * @param ifNoneMatch
     * @param ifModifiedSince
     * @return
     */
    @Override
    public Response appsAppTypeIdAppIdSubscriptionGet(String appType, String appId, String accept, String ifNoneMatch,
                                                      String ifModifiedSince) {

        APIConsumer apiConsumer = null;
        Map<String, String> subscriptionData = new HashMap<>();
        boolean isTenantFlowStarted = false;
        String username = AppManagerUtil.replaceEmailDomain(RestApiUtil.getLoggedInUsername());
        try {
            apiConsumer = RestApiUtil.getLoggedInUserConsumer();

            String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
            int tenantId = AppManagerUtil.getTenantId(username);
            if (tenantDomain != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
            }

            WebApp webApp = apiConsumer.getWebApp(appId);
            APIIdentifier appIdentifier = webApp.getId();
            int applicationId = AppManagerUtil.getApplicationId(AppMConstants.DEFAULT_APPLICATION_NAME, username);

            Subscription subscription = apiConsumer.getSubscription(appIdentifier, applicationId,
                                                                    Subscription.SUBSCRIPTION_TYPE_INDIVIDUAL);
            if (subscription != null) {
                subscriptionData.put("SubscriptionId", String.valueOf(subscription.getSubscriptionId()));
                subscriptionData.put("SubscriptionType", subscription.getSubscriptionType());
                subscriptionData.put("Status", subscription.getSubscriptionStatus());
                subscriptionData.put("SubscriptionTime", subscription.getSubscriptionTime());
                subscriptionData.put("SubscribedUser", subscription.getUserId());
            }
        } catch (AppManagementException e) {
            RestApiUtil.handleInternalServerError(
                    "Error occurred while retrieving subscription details of webapp with id : "
                            + appId + " for user " + username, e, log);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return Response.ok().entity(subscriptionData).build();
    }

    /**
     * Adding subscription for a given app
     *
     * @param appType     application type ie: webapp, mobileapp
     * @param appId       application uuid
     * @param contentType
     * @return
     */
    @Override
    public Response appsAppTypeIdAppIdSubscriptionPost(String appType, String appId, String contentType) {

        APIConsumer apiConsumer = null;
        boolean isTenantFlowStarted = false;
        String userName = AppManagerUtil.replaceEmailDomain(RestApiUtil.getLoggedInUsername());
        try {
            apiConsumer = RestApiUtil.getLoggedInUserConsumer();
            String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
            int tenantId = AppManagerUtil.getTenantId(userName);

            if (AppManagerUtil.isSelfSubscriptionEnable() || AppManagerUtil.isEnterpriseSubscriptionEnable()) {
                //Check for subscriber existence
                Subscriber subscriber = apiConsumer.getSubscriber(userName);
                if (subscriber == null) {
                    subscriber = new Subscriber(userName);
                    subscriber.setSubscribedDate(new Date());
                    subscriber.setEmail("");
                    subscriber.setTenantId(tenantId);
                    apiConsumer.addSubscriber(subscriber);
                }

                if (tenantDomain != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                    isTenantFlowStarted = true;
                    PrivilegedCarbonContext.startTenantFlow();
                    PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
                }
                WebApp webApp = apiConsumer.getWebApp(appId);
                APIIdentifier appIdentifier = webApp.getId();
                appIdentifier.setTier(AppMConstants.UNLIMITED_TIER);

            /* Tenant based validation for subscription*/
                String userDomain = MultitenantUtils.getTenantDomain(userName);
                boolean subscriptionAllowed = false;
                if (!userDomain.equals(tenantDomain)) {
                    String subscriptionAvailability = webApp.getSubscriptionAvailability();
                    if (AppMConstants.SUBSCRIPTION_TO_ALL_TENANTS.equals(subscriptionAvailability)) {
                        subscriptionAllowed = true;
                    } else if (AppMConstants.SUBSCRIPTION_TO_SPECIFIC_TENANTS.equals(subscriptionAvailability)) {
                        String subscriptionAllowedTenants = webApp.getSubscriptionAvailableTenants();
                        String allowedTenants[] = null;
                        if (subscriptionAllowedTenants != null) {
                            allowedTenants = subscriptionAllowedTenants.split(",");
                            if (allowedTenants != null) {
                                for (String tenant : allowedTenants) {
                                    if (tenant != null && userDomain.equals(tenant.trim())) {
                                        subscriptionAllowed = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    subscriptionAllowed = true;
                }

                if (!subscriptionAllowed) {
                    throw new AppManagementException("Subscription is not allowed for " + userDomain);
                }
                int applicationId = AppManagerUtil.getApplicationId(AppMConstants.DEFAULT_APPLICATION_NAME, userName);
                //TODO: Handle enterprise subscription
                String subscriptionStatus = apiConsumer.addSubscription(appIdentifier, "INDIVIDUAL", userName,
                                                                        applicationId, null);
            } else {
                RestApiUtil.handleBadRequest("Subscription is disabled", log);
            }
        } catch (AppManagementException e) {
            RestApiUtil.handleBadRequest(
                    "Error while subscribing the user:" + userName + " for " + appType + " with appId :" + appId, log);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return Response.ok().build();
    }

    @Override
    public Response appsAppTypeIdAppIdSubscriptionWorkflowPost(String appType, String appId, String contentType) {
        WorkflowExecutor workflowExecutor = null;
        try {
            workflowExecutor = WorkflowExecutorFactory.getInstance().getWorkflowExecutor(
                    WorkflowConstants.WF_TYPE_AM_SUBSCRIPTION_CREATION);
        } catch (WorkflowException e) {
            RestApiUtil.handleInternalServerError("Error occurred while retrieving subscription workflow status", e,
                                                  log);
        }
        boolean isAsynchronousFlow = workflowExecutor.isAsynchronus();
        return Response.ok().entity(isAsynchronousFlow).build();
    }

    /**
     * @param appType
     * @param appId
     * @param accept
     * @param ifNoneMatch
     * @param ifModifiedSince
     * @return
     */
    @Override
    public Response appsAppTypeIdAppIdSubscriptionUsersGet(String appType, String appId, String accept,
                                                           String ifNoneMatch, String ifModifiedSince) {
        UserIdListDTO userIdListDTO = new UserIdListDTO();
        try {
            APIProvider appProvider = RestApiUtil.getLoggedInUserProvider();
            if (AppMConstants.WEBAPP_ASSET_TYPE.equals(appType) || AppMConstants.SITE_ASSET_TYPE.equals(appType)) {
                if (AppManagerUtil.isSelfSubscriptionEnable() || AppManagerUtil.isEnterpriseSubscriptionEnable()) {
                    WebApp webApp = appProvider.getAppDetailsFromUUID(appId);
                    Set<Subscriber> subscriberSet = appProvider.getSubscribersOfAPI(webApp.getId());
                    userIdListDTO.setUserIds(subscriberSet);
                } else {
                    RestApiUtil.handleBadRequest("Subscription is disabled", log);
                }
            } else {
                RestApiUtil.handleBadRequest("Unsupported application type '" + appType + "' provided", log);
            }
        } catch (AppManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e) || RestApiUtil.isDueToAuthorizationFailure(e)) {
                RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_API, appId, e, log);
            } else {
                String errorMessage = "Error while changing lifecycle state of app with id : " + appId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return Response.ok().entity(userIdListDTO).build();
    }

    /**
     * Remove subscription of a given user for a given application
     *
     * @param appType
     * @param appId
     * @param contentType
     * @return
     */
    @Override
    public Response appsAppTypeIdAppIdUnsubscriptionPost(String appType, String appId, String contentType) {

        APIConsumer apiConsumer = null;
        boolean isTenantFlowStarted = false;
        String username = AppManagerUtil.replaceEmailDomain(RestApiUtil.getLoggedInUsername());
        try {
            apiConsumer = RestApiUtil.getLoggedInUserConsumer();
            String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();

            if (tenantDomain != null && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.startTenantFlow();
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
            }
            WebApp webApp = apiConsumer.getWebApp(appId);
            APIIdentifier appIdentifier = webApp.getId();
            apiConsumer.removeAPISubscription(appIdentifier, username, AppMConstants.DEFAULT_APPLICATION_NAME);
        } catch (AppManagementException e) {
            RestApiUtil.handleBadRequest("Error occurred while removing subscription user '" + username +
                                                 "' for webapp with id " + appId, log);
        } finally {
            if (isTenantFlowStarted) {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }
        return Response.ok().build();
    }


    @Override
    public Response appsAppTypeTagsGet(String appType, String accept, String ifNoneMatch) {
        TagListDTO tagListDTO = new TagListDTO();
        try {
            CommonValidator.isValidAppType(appType);

            APIConsumer appConsumer = RestApiUtil.getLoggedInUserConsumer();
            String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
            Map<String, String> attributeMap = new HashMap<>();
            if (AppMConstants.SITE_ASSET_TYPE.equalsIgnoreCase(appType)) {
                attributeMap.put(AppMConstants.APP_OVERVIEW_TREAT_AS_A_SITE, "TRUE");
            } else if (AppMConstants.WEBAPP_ASSET_TYPE.equalsIgnoreCase(appType)){
                attributeMap.put(AppMConstants.APP_OVERVIEW_TREAT_AS_A_SITE, "FALSE");
            }

            //Make the asset type as 'webapp'.
            appType = APPMappingUtil.updateAssetType(appType);

            List<String> tagList = new ArrayList<>();
            Iterator tagIterator = appConsumer.getAllTags(tenantDomain, appType, attributeMap).iterator();
            if (!tagIterator.hasNext()) {
                return RestApiUtil.buildNotFoundException("Tags", null).getResponse();
            }
            while (tagIterator.hasNext()) {
                Tag tag = (Tag) tagIterator.next();
                tagList.add(tag.getName());
            }
            tagListDTO.setTags(tagList);
        } catch (AppManagementException e) {
            String errorMessage = "Error retrieving tags for " + appType + "s.";
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return Response.ok().entity(tagListDTO).build();
    }


    @Override
    public Response appsMobileScheduleInstallPost(String contentType, ScheduleDTO schedule,
                                                  SecurityContext securityContext) {
        String username = RestApiUtil.getLoggedInUsername();
        try {
            Map<String, String> searchTerms = new HashMap<String, String>();
            searchTerms.put("id", schedule.getAppId());

            APIProvider apiProvider = RestApiUtil.getLoggedInUserProvider();
            List<App> result = apiProvider.searchApps(AppMConstants.MOBILE_ASSET_TYPE, searchTerms);
            if (result.isEmpty()) {
                String errorMessage = "Could not find requested application.";
                return RestApiUtil.buildNotFoundException(errorMessage, schedule.getAppId()).getResponse();
            }


            String tenantDomainName = MultitenantUtils.getTenantDomain(username);
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(
                    tenantDomainName);
            String tenantUserName = MultitenantUtils.getTenantAwareUsername(username);
            String appId = schedule.getAppId();
            Operations mobileOperation = new Operations();
            String action = "install";
            String type = "device";
            String[] parameters;
            parameters = Arrays.copyOf(schedule.getDeviceIds().toArray(), schedule.getDeviceIds().toArray().length,
                    String[].class);
            if (parameters == null) {
                RestApiUtil.handleBadRequest("Device IDs should be provided to perform device app installation",
                        log);
            }
            String scheduleTime = schedule.getScheduleTime();

            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            sdf.setLenient(false);
            Date date = sdf.parse(scheduleTime);

            JSONObject user = new JSONObject();
            user.put("username", tenantUserName);
            user.put("tenantDomain", tenantDomainName);
            user.put("tenantId", tenantId);


            String activityId = mobileOperation.performAction(user.toString(), action, tenantId, type, appId, parameters, scheduleTime, null);

            JSONObject response = new JSONObject();
            response.put("activityId", activityId);

            return Response.ok().entity(response.toString()).build();

        } catch (AppManagementException e) {
            RestApiUtil.handleInternalServerError("Internal Error occurred while installing", e, log);
        } catch (MobileApplicationException e) {
            RestApiUtil.handleBadRequest(e.getMessage(), log);
        } catch (UserStoreException e) {
            RestApiUtil.handleInternalServerError("User store related Error occurred while installing", e, log);
        } catch (JSONException e) {
            RestApiUtil.handleInternalServerError("Json casting Error occurred while installing", e, log);
        } catch (ParseException e) {
            RestApiUtil.handleBadRequest("Invalid schedule date format", log);
        }
        return Response.ok().build();
    }

    /**
     * Document Content retrieve
     * @param appId  id of the application
     * @param documentId id of the documentation
     * @param appType type of the application
     * @param accept
     * @param ifNoneMatch
     * @param ifModifiedSince
     * @return
     */
    @Override
    public Response appsAppTypeIdAppIdDocsDocumentIdContentGet(String appId, String documentId, String appType, String accept, String ifNoneMatch, String ifModifiedSince) {

        Documentation documentation;
        try {
            if(AppMConstants.WEBAPP_ASSET_TYPE.equals(appType) || AppMConstants.SITE_ASSET_TYPE.equals(appType)) {

                String username = RestApiUtil.getLoggedInUsername();
                APIProvider appProvider = RestApiUtil.getLoggedInUserProvider();
                String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();

                WebApp webApp = appProvider.getWebApp(appId);
                APIIdentifier appIdentifier = webApp.getId();
                documentation = appProvider.getDocumentation(documentId, tenantDomain);
                if (documentation == null) {
                    RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_DOCUMENTATION, documentId, log);
                    return null;
                }

                //gets the content depending on the type of the document
                if (documentation.getSourceType().equals(Documentation.DocumentSourceType.FILE)) {
                    String resource = documentation.getFilePath();
                    Map<String, Object> docResourceMap = AppManagerUtil.getDocument(username, resource, tenantDomain);
                    Object fileDataStream = docResourceMap.get(AppMConstants.DOCUMENTATION_RESOURCE_MAP_DATA);
                    Object contentType = docResourceMap.get(AppMConstants.DOCUMENTATION_RESOURCE_MAP_CONTENT_TYPE);
                    contentType = contentType == null ? RestApiConstants.APPLICATION_OCTET_STREAM : contentType;
                    String name = docResourceMap.get(AppMConstants.DOCUMENTATION_RESOURCE_MAP_NAME).toString();
                    return Response.ok(fileDataStream)
                            .header(RestApiConstants.HEADER_CONTENT_TYPE, contentType)
                            .header(RestApiConstants.HEADER_CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                            .build();
                } else if (documentation.getSourceType().equals(Documentation.DocumentSourceType.INLINE)) {
                    String content = appProvider.getDocumentationContent(appIdentifier, documentation.getName());
                    return Response.ok(content)
                            .header(RestApiConstants.HEADER_CONTENT_TYPE, AppMConstants.DOCUMENTATION_INLINE_CONTENT_TYPE)
                            .build();
                } else if (documentation.getSourceType().equals(Documentation.DocumentSourceType.URL)) {
                    String sourceUrl = documentation.getSourceUrl();
                    return Response.seeOther(new URI(sourceUrl)).build();
                }
            } else {
                RestApiUtil.handleBadRequest("App type " + appType + " is not supported", log);
            }
        } catch (AppManagementException e) {
            if (RestApiUtil.isDueToResourceNotFound(e) || RestApiUtil.isDueToAuthorizationFailure(e)) {
                RestApiUtil.handleResourceNotFoundError(appType, appId, e, log);
            } else {
                String errorMessage = "Error while retrieving document " + documentId + " of the " + appType +
                        " with id : " + appId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        } catch (URISyntaxException e) {
            String errorMessage = "Error while retrieving source URI location of " + documentId;
            RestApiUtil.handleInternalServerError(errorMessage, e, log);
        }
        return null;
    }


    /**
     * Retrieve documentation for a given documentationId and for a given app id
     * @param appType application type ie: webapp,mobileapp
     * @param appId application uuid
     * @param documentId  documentID
     * @param ifMatch
     * @param ifUnmodifiedSince
     * @return
     */
    @Override
    public Response appsAppTypeIdAppIdDocsDocumentIdGet(String appType, String appId, String documentId, String ifMatch, String ifUnmodifiedSince) {
        Documentation documentation;
        DocumentDTO documentDTO = null;
        try {
            if(AppMConstants.WEBAPP_ASSET_TYPE.equals(appType) || AppMConstants.SITE_ASSET_TYPE.equals(appType)) {
                //TODO:Check App access prmissions
                APIProvider appProvider = RestApiUtil.getLoggedInUserProvider();
                String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
                documentation = appProvider.getDocumentation(documentId, tenantDomain);
                if (documentation == null) {
                    RestApiUtil.handleResourceNotFoundError(RestApiConstants.RESOURCE_DOCUMENTATION, documentId, log);
                }
                documentDTO = DocumentationMappingUtil.fromDocumentationToDTO(documentation);
            }else {
                RestApiUtil.handleBadRequest("App type " + appType + " is not supported", log);
            }
        } catch (AppManagementException e) {
            //Auth failure occurs when cross tenant accessing APIs. Sends 404, since we don't need to expose the existence of the resource
            if (RestApiUtil.isDueToResourceNotFound(e) || RestApiUtil.isDueToAuthorizationFailure(e)) {
                RestApiUtil.handleResourceNotFoundError(appType, appId, e, log);
            } else {
                String errorMessage = "Error while retrieving document : " + documentId;
                RestApiUtil.handleInternalServerError(errorMessage, e, log);
            }
        }
        return Response.ok().entity(documentDTO).build();
    }

    /**
     *  Returns all the documents of the given APP uuid that matches to the search condition
     *
     * @param appType application type ie:webapp,mobileapp
     * @param appId application identifier
     * @param limit
     * @param offset
     * @param accept
     * @param ifNoneMatch
     * @return matched documents as a list if DocumentDTOs
     */
    @Override
    public Response appsAppTypeIdAppIdDocsGet(String appType, String appId, Integer limit, Integer offset, String accept, String ifNoneMatch) {
        //pre-processing
        //setting default limit and offset values if they are not set
        limit = limit != null ? limit : RestApiConstants.PAGINATION_LIMIT_DEFAULT;
        offset = offset != null ? offset : RestApiConstants.PAGINATION_OFFSET_DEFAULT;

        try {
            if(AppMConstants.WEBAPP_ASSET_TYPE.equals(appType) || AppMConstants.SITE_ASSET_TYPE.equals(appType)) {
                APIProvider appProvider = RestApiUtil.getLoggedInUserProvider();
                String tenantDomain = RestApiUtil.getLoggedInUserTenantDomain();
                //this will fail if user does not have access to the API or the API does not exist

                WebApp webApp = appProvider.getWebApp(appId);
                APIIdentifier appIdentifier = webApp.getId();

                List<Documentation> allDocumentation = appProvider.getAllDocumentation(appIdentifier);
                DocumentListDTO documentListDTO = DocumentationMappingUtil.fromDocumentationListToDTO(allDocumentation,
                        offset, limit);
                DocumentationMappingUtil
                        .setPaginationParams(documentListDTO, appId, offset, limit, allDocumentation.size());
                return Response.ok().entity(documentListDTO).build();
            } else {
                RestApiUtil.handleBadRequest("App type " + appType + " is not supported", log);
            }
        } catch (AppManagementException e) {
            //Auth failure occurs when cross tenant accessing APIs. Sends 404, since we don't need to expose the existence of the resource
            if (RestApiUtil.isDueToResourceNotFound(e) || RestApiUtil.isDueToAuthorizationFailure(e)) {
                RestApiUtil.handleResourceNotFoundError(appType, appId, e, log);
            } else {
                String msg = "Error while retrieving documents of App "+appType +" with appId "+ appId;
                RestApiUtil.handleInternalServerError(msg, e, log);
            }
        }
        return null;
    }

    @Override
    public Response appsMobileScheduleUpdatePost(String contentType, ScheduleDTO schedule,
                                                 SecurityContext securityContext) {
        String username = RestApiUtil.getLoggedInUsername();
        try {
            Map<String, String> searchTerms = new HashMap<String, String>();
            searchTerms.put("id", schedule.getAppId());

            APIProvider apiProvider = RestApiUtil.getLoggedInUserProvider();
            List<App> result = apiProvider.searchApps(AppMConstants.MOBILE_ASSET_TYPE, searchTerms);
            if (result.isEmpty()) {
                String errorMessage = "Could not find requested application.";
                return RestApiUtil.buildNotFoundException(errorMessage, schedule.getAppId()).getResponse();
            }


            String tenantDomainName = MultitenantUtils.getTenantDomain(username);
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().getTenantId(
                    tenantDomainName);
            String tenantUserName = MultitenantUtils.getTenantAwareUsername(username);
            String appId = schedule.getAppId();
            Operations mobileOperation = new Operations();
            String action = "update";
            String type = "device";
            String[] parameters;
            parameters = Arrays.copyOf(schedule.getDeviceIds().toArray(), schedule.getDeviceIds().toArray().length,
                    String[].class);
            if (parameters == null) {
                RestApiUtil.handleBadRequest("Device IDs should be provided to perform device app installation",
                        log);
            }
            String scheduleTime = schedule.getScheduleTime();

            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
            sdf.setLenient(false);
            Date date = sdf.parse(scheduleTime);

            JSONObject user = new JSONObject();
            user.put("username", tenantUserName);
            user.put("tenantDomain", tenantDomainName);
            user.put("tenantId", tenantId);


            String activityId = mobileOperation.performAction(user.toString(), action, tenantId, type, appId,
                                                              parameters, scheduleTime, null);
            JSONObject response = new JSONObject();
            response.put("activityId", activityId);

            return Response.ok().entity(response.toString()).build();

        } catch (AppManagementException e) {
            RestApiUtil.handleInternalServerError("Internal Error occurred while installing", e, log);
        } catch (MobileApplicationException e) {
            RestApiUtil.handleBadRequest(e.getMessage(), log);
        } catch (UserStoreException e) {
            RestApiUtil.handleInternalServerError("User store related Error occurred while installing", e, log);
        } catch (JSONException e) {
            RestApiUtil.handleInternalServerError("Json casting Error occurred while installing", e, log);
        } catch (ParseException e) {
            RestApiUtil.handleBadRequest("Invalid schedule date format", log);
        }
        return Response.ok().build();
    }

}
