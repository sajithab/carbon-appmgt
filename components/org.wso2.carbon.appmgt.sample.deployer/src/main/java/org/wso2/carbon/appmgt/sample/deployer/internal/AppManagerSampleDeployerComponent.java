/*
*  Copyright (c) 2005-2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.carbon.appmgt.sample.deployer.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.appmgt.impl.AppManagerConfiguration;
import org.wso2.carbon.appmgt.impl.AppManagerConfigurationService;
import org.wso2.carbon.appmgt.impl.AppManagerConfigurationServiceImpl;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.ConfigurationContextService;

import java.io.File;

/**
 * @scr.component name="org.wso2.carbon.appmgt.sample.deployer" immediate="true"
 * @scr.reference name="configuration.context.service"
 * interface="org.wso2.carbon.utils.ConfigurationContextService" cardinality="1..1"
 * policy="dynamic" bind="setConfigurationContextService" unbind="unsetConfigurationContextService"
 */
public class AppManagerSampleDeployerComponent {

    private static final Log log = LogFactory.getLog(AppManagerSampleDeployerComponent.class);
    private static AppManagerConfigurationService amConfigService;
    private BundleContext bundleContext;

    /**
     * This method is triggered when component is activated
     *
     * @param context smaple deployer component context
     */
    protected void activate(ComponentContext context) {
        bundleContext = context.getBundleContext();
        if (log.isDebugEnabled()) {
            log.debug("App Manager sampl deployer component activated");
        }
        String filePath = null;
        try {
            //Initializing ApiManager Configuration
            AppManagerConfiguration configuration = new AppManagerConfiguration();
            filePath = CarbonUtils.getCarbonConfigDirPath() + File.separator + "app-manager.xml";
            configuration.load(filePath);
            amConfigService = new AppManagerConfigurationServiceImpl(configuration);
            ServiceReferenceHolder.getInstance().setAPIManagerConfigurationService(amConfigService);

        } catch (Throwable e) {
            log.error("APPManagerConfiguration initialization failed from file path : " + filePath, e);
        }
    }

    /**
     * This method is triggered when component is deactivated
     *
     * @param context smaple deployer component context
     */
    protected void deactivate(ComponentContext context) {
        if (log.isDebugEnabled()) {
            log.debug("WebApp handlers component deactivated");
        }

    }

    /**
     * Set the given ConfigurationContextService to ServiceReferenceHolder instance
     *
     * @param cfgCtxService smaple deployer component configuration context service
     */
    protected void setConfigurationContextService(ConfigurationContextService cfgCtxService) {
        if (log.isDebugEnabled()) {
            log.debug("Configuration context service bound to the WebApp handlers");
        }
        ServiceReferenceHolder.getInstance().setConfigurationContextService(cfgCtxService);
    }

    /**
     * Unset the ConfigurationContextService from ServiceReferenceHolder instance
     *
     * @param cfgCtxService smaple deployer component configuration context service
     */
    protected void unsetConfigurationContextService(ConfigurationContextService cfgCtxService) {
        if (log.isDebugEnabled()) {
            log.debug("Configuration context service unbound from the WebApp handlers");
        }
        ServiceReferenceHolder.getInstance().setConfigurationContextService(null);
    }

}

