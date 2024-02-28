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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.api.payment.PluginPaymentPluginApi;
import org.killbill.billing.plugin.hyperswitch.dao.HyperswitchDao;
import org.killbill.billing.plugin.hyperswitch.dao.gen.tables.HyperswitchPaymentMethods;
import org.killbill.billing.plugin.hyperswitch.dao.gen.tables.HyperswitchResponses;
import org.killbill.billing.plugin.hyperswitch.dao.gen.tables.records.HyperswitchPaymentMethodsRecord;
import org.killbill.billing.plugin.hyperswitch.dao.gen.tables.records.HyperswitchResponsesRecord;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import java.sql.SQLException;

import com.google.common.collect.ImmutableMap;
import com.hyperswitch.client.HsApiClient;
import com.hyperswitch.client.api.PaymentsApi;
import com.hyperswitch.client.model.PaymentsCreateRequest;
import com.hyperswitch.client.model.PaymentsResponse;
import com.hyperswitch.client.model.IntentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//
// A 'real' payment plugin would of course implement this interface.
//
public class HyperswitchPaymentPluginApi extends
        PluginPaymentPluginApi<HyperswitchResponsesRecord, HyperswitchResponses, HyperswitchPaymentMethodsRecord, HyperswitchPaymentMethods> {
    private static final Logger logger = LoggerFactory.getLogger(HyperswitchPaymentPluginApi.class);
    private static String HS_API_KEY_PROPERTY = "HS_API_KEY_PROPERTY";
    private final HyperswitchConfigurationHandler hyperswitchConfigurationHandler;
    private final HyperswitchDao hyperswitchDao;

    public HyperswitchPaymentPluginApi(
            final HyperswitchConfigurationHandler hyperswitchConfigPropertiesConfigurationHandler,
            final OSGIKillbillAPI killbillAPI,
            final OSGIConfigPropertiesService configProperties,
            final Clock clock,
            final HyperswitchDao dao) {
        super(killbillAPI, configProperties, clock, dao);
        this.hyperswitchConfigurationHandler = hyperswitchConfigPropertiesConfigurationHandler;
        this.hyperswitchDao = dao;
    }

    @Override
    public PaymentTransactionInfoPlugin authorizePayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        PaymentTransactionInfoPlugin paymentTransactionInfoPlugin = new HyperswitchPaymentTransactionInfoPlugin(
                kbPaymentId, kbTransactionId,
                TransactionType.AUTHORIZE, amount, currency, PaymentPluginStatus.CANCELED, null,
                null, null, null, new DateTime(), null, null);
        return paymentTransactionInfoPlugin;
    }

    @Override
    public PaymentTransactionInfoPlugin capturePayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public PaymentTransactionInfoPlugin purchasePayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        System.out.println("Purchase Payment is getting called : ");
        HyperswitchPaymentTransactionInfoPlugin hyperswitchPaymentTransactionInfoPlugin;
        PaymentsCreateRequest paymentsCreateRequest = new PaymentsCreateRequest();
        paymentsCreateRequest.setAmount(amount.longValue());
        paymentsCreateRequest.setCurrency(convertCurrency(currency));
        paymentsCreateRequest.confirm(true);
        paymentsCreateRequest.customerId(kbAccountId.toString());
        paymentsCreateRequest.offSession(true);
        try {
            HyperswitchPaymentMethodsRecord record =  this.hyperswitchDao.getPaymentMethod(kbPaymentMethodId.toString());
            String mandate_id = record.getHyperswitchId();
            paymentsCreateRequest.setMandateId(mandate_id);
            PaymentsApi ClientApi = buildHyperswitchClient(context);
            PaymentsResponse response = ClientApi.createAPayment(paymentsCreateRequest);
            hyperswitchPaymentTransactionInfoPlugin = new HyperswitchPaymentTransactionInfoPlugin(kbPaymentId,
                    kbTransactionId,
                    TransactionType.CAPTURE, null, null, convertPaymentStatus(response.getStatus()), null,
                    null, response.getPaymentId(), null, new DateTime(), null, null);
            System.out.println(response.toString());
            return hyperswitchPaymentTransactionInfoPlugin;
        } catch (SQLException e) {
            throw new PaymentPluginApiException("Couldn't find payment method id for account", e);
        }
    }

    @Override
    public PaymentTransactionInfoPlugin voidPayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties,
            final CallContext context) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public PaymentTransactionInfoPlugin creditPayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public PaymentTransactionInfoPlugin refundPayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public List<PaymentTransactionInfoPlugin> getPaymentInfo(final UUID kbAccountId, final UUID kbPaymentId,
            final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public Pagination<PaymentTransactionInfoPlugin> searchPayments(final String searchKey, final Long offset,
            final Long limit, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentPluginApiException {
        return null;
    }

    @Override
    public void addPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId,
            final PaymentMethodPlugin paymentMethodProps, final boolean setDefault,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        logger.info("[addPaymentMethod] Adding Payment Method");
        final Map<String, String> allProperties = PluginProperties.toStringMap(paymentMethodProps.getProperties(),
                properties);
        String paymentMethodIdInHs = allProperties.get("mandateId");
        if (allProperties.containsKey("mandateId")) {
            try {
                this.hyperswitchDao.addPaymentMethod(
                        kbAccountId,
                        kbPaymentMethodId,
                        allProperties,
                        paymentMethodIdInHs,
                        context.getTenantId());
            } catch (SQLException e) {
                throw new PaymentPluginApiException("Error calling Hyperswitch while adding payment method", e);
            }
        }

    }

    @Override
    public void deletePaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {

    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final UUID kbAccountId, final UUID kbPaymentMethodId,
            final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public void setDefaultPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {

    }

    @Override
    public List<PaymentMethodInfoPlugin> getPaymentMethods(final UUID kbAccountId, final boolean refreshFromGateway,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public Pagination<PaymentMethodPlugin> searchPaymentMethods(final String searchKey, final Long offset,
            final Long limit, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentPluginApiException {
        return null;
    }

    @Override
    public void resetPaymentMethods(final UUID kbAccountId, final List<PaymentMethodInfoPlugin> paymentMethods,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {

    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final UUID kbAccountId,
            final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties,
            final CallContext context) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public GatewayNotification processNotification(final String notification, final Iterable<PluginProperty> properties,
            final CallContext context) throws PaymentPluginApiException {
        return null;
    }

    private PaymentPluginStatus convertPaymentStatus(IntentStatus paymentStatus) {
        switch (paymentStatus) {
            case SUCCEEDED:
                return PaymentPluginStatus.PROCESSED;
            case PROCESSING:
                return PaymentPluginStatus.PENDING;
            case CANCELLED:
                return PaymentPluginStatus.CANCELED;
            case FAILED:
                return PaymentPluginStatus.ERROR;
            default:
                return PaymentPluginStatus.UNDEFINED;
        }
    }

    private com.hyperswitch.client.model.Currency convertCurrency(
            Currency currency) {
        switch (currency) {
            case USD:
                return com.hyperswitch.client.model.Currency.USD;
            case AUD:
                return com.hyperswitch.client.model.Currency.AUD;
            case CAD:
                return com.hyperswitch.client.model.Currency.CAD;
            case DKK:
                return com.hyperswitch.client.model.Currency.DKK;
            case EUR:
                return com.hyperswitch.client.model.Currency.EUR;
            case GBP:
                return com.hyperswitch.client.model.Currency.GBP;
            case NZD:
                return com.hyperswitch.client.model.Currency.NZD;
            case SEK:
                return com.hyperswitch.client.model.Currency.SEK;
            default:
                return null;

        }
    }

    @Override
    protected PaymentTransactionInfoPlugin buildPaymentTransactionInfoPlugin(HyperswitchResponsesRecord record) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'buildPaymentTransactionInfoPlugin'");
    }


    @Override
    protected PaymentMethodPlugin buildPaymentMethodPlugin(HyperswitchPaymentMethodsRecord record) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'buildPaymentMethodPlugin'");
    }


    @Override
    protected PaymentMethodInfoPlugin buildPaymentMethodInfoPlugin(HyperswitchPaymentMethodsRecord record) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'buildPaymentMethodInfoPlugin'");
    }


    @Override
    protected String getPaymentMethodId(HyperswitchPaymentMethodsRecord input) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPaymentMethodId'");
    }

    private PaymentsApi buildHyperswitchClient(final TenantContext tenantContext) {
		final HyperswitchConfigProperties config = hyperswitchConfigurationHandler.getConfigurable(tenantContext.getTenantId());
	    if (config == null || config.getHSApiKey() == null || config.getHSApiKey().isEmpty()) {
	    	logger.warn("Per-tenant properties not configured");
	        return null;
	    }
        final PaymentsApi client = new HsApiClient("api_key",config.getHSApiKey()).buildClient(PaymentsApi.class);
	    return client;
	}
}
