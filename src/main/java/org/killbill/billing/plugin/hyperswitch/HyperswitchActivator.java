/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.plugin.hyperswitch;


import java.util.Hashtable;
import java.util.Properties;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillEventDispatcher;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIFrameworkEventHandler;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.plugin.api.notification.PluginConfigurationEventHandler;
import org.killbill.billing.plugin.core.config.PluginEnvironmentConfig;
import org.killbill.billing.plugin.core.resources.jooby.PluginApp;
import org.killbill.billing.plugin.core.resources.jooby.PluginAppBuilder;
import org.killbill.billing.plugin.hyperswitch.dao.HyperswitchDao;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HyperswitchActivator extends KillbillActivatorBase {
    private static final Logger logger = LoggerFactory.getLogger(HyperswitchActivator.class);
    public static final String PLUGIN_NAME = "hyperswitch-plugin";

    private HyperswitchConfigurationHandler hyperswitchConfigurationHandler;
    private OSGIKillbillEventDispatcher.OSGIKillbillEventHandler killbillEventHandler;

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);

        final String region = PluginEnvironmentConfig.getRegion(configProperties.getProperties());
        final HyperswitchDao hyperswitchDao = new HyperswitchDao(dataSource.getDataSource());
        logger.info(" starting plugin {}", PLUGIN_NAME);
        // Register an event listener for plugin configuration (optional)
        logger.info("Registering an event listener for plugin configuration");
        hyperswitchConfigurationHandler = new HyperswitchConfigurationHandler(region, PLUGIN_NAME, killbillAPI);
        final HyperswitchConfigProperties globalConfiguration = hyperswitchConfigurationHandler
                .createConfigurable(configProperties.getProperties());
        hyperswitchConfigurationHandler.setDefaultConfigurable(globalConfiguration);
        // Register an event listener (optional)
        killbillEventHandler = new HyperswitchListener(killbillAPI);

        // As an example, this plugin registers a PaymentPluginApi (this could be
        // changed to any other plugin api)
        logger.info("Registering an APIs");
        final PaymentPluginApi paymentPluginApi = new HyperswitchPaymentPluginApi(hyperswitchConfigurationHandler,killbillAPI,configProperties,clock.getClock(),hyperswitchDao);
        registerPaymentPluginApi(context, paymentPluginApi);

        logger.info("Registering healthcheck");
        // Expose a healthcheck (optional), so other plugins can check on the plugin status
        final Healthcheck healthcheck = new HyperswitchHealthcheck();
        registerHealthcheck(context, healthcheck);

        // // This Plugin registers a InvoicePluginApi
        // final InvoicePluginApi invoicePluginApi = new HyperswitchInvoicePluginApi(killbillAPI, configProperties, null);
        // registerInvoicePluginApi(context, invoicePluginApi);

        // Register a servlet (optional)
        // final PluginApp pluginApp = new PluginAppBuilder(PLUGIN_NAME, killbillAPI, dataSource, super.clock,
        //                                                  configProperties).withRouteClass(HyperswitchServlet.class)
        //                                                                   .withRouteClass(HyperswitchHealthcheckServlet.class).withService(healthcheck).build();
        // final HttpServlet httpServlet = PluginApp.createServlet(pluginApp);
        // registerServlet(context, httpServlet);

        registerHandlers();
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        // Do additional work on shutdown (optional)
        super.stop(context);
    }

    private void registerHandlers() {
        final PluginConfigurationEventHandler configHandler = new PluginConfigurationEventHandler(
                hyperswitchConfigurationHandler);

        dispatcher.registerEventHandlers(configHandler,
                                         (OSGIFrameworkEventHandler) () -> dispatcher.registerEventHandlers(killbillEventHandler));
    }

    private void registerPaymentPluginApi(final BundleContext context, final PaymentPluginApi api) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, PaymentPluginApi.class, api, props);
    }

    private void registerHealthcheck(final BundleContext context, final Healthcheck healthcheck) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, Healthcheck.class, healthcheck, props);
    }
}
