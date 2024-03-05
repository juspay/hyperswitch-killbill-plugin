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
import org.killbill.billing.plugin.util.KillBillMoney;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import java.sql.SQLException;
import feign.FeignException.BadRequest;

import com.hyperswitch.client.HsApiClient;
import com.hyperswitch.client.api.PaymentsApi;
import com.hyperswitch.client.api.RefundsApi;
import com.hyperswitch.client.model.PaymentsCreateRequest;
import com.hyperswitch.client.model.PaymentsResponse;
import com.hyperswitch.client.model.RefundRequest;
import com.hyperswitch.client.model.RefundResponse;
import com.hyperswitch.client.model.RefundStatus;
import com.hyperswitch.client.model.CaptureMethod;
import com.hyperswitch.client.model.IntentStatus;
import com.hyperswitch.client.model.PaymentsCancelRequest;
import com.hyperswitch.client.model.PaymentsCaptureRequest;

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
        HyperswitchResponsesRecord hyperswitchRecord = null;
        PaymentsCreateRequest paymentsCreateRequest = new PaymentsCreateRequest();
        paymentsCreateRequest.setAmount(KillBillMoney.toMinorUnits(currency.toString(), amount));
        paymentsCreateRequest.setCurrency(convertCurrency(currency));
        paymentsCreateRequest.confirm(true);
        paymentsCreateRequest.customerId(kbAccountId.toString());
        paymentsCreateRequest.offSession(true);
        paymentsCreateRequest.setCaptureMethod(CaptureMethod.MANUAL);
        PaymentPluginStatus paymentPluginStatus = null;
        PaymentsResponse response = null;
        try {
            HyperswitchPaymentMethodsRecord record = this.hyperswitchDao.getPaymentMethod(kbPaymentMethodId.toString());
            String mandate_id = record.getHyperswitchId();
            paymentsCreateRequest.setMandateId(mandate_id);
            PaymentsApi ClientApi = buildHyperswitchClient(context);
            try {
                response = ClientApi.createAPayment(paymentsCreateRequest);
                try {
                    paymentPluginStatus = convertPaymentStatus(response.getStatus());
                    final DateTime utcNow = clock.getUTCNow();
                    hyperswitchRecord = this.hyperswitchDao.addResponse(
                            kbAccountId,
                            kbPaymentId,
                            kbTransactionId,
                            TransactionType.AUTHORIZE,
                            amount,
                            currency,
                            response,
                            utcNow,
                            context.getTenantId());
                } catch (SQLException e) {
                    logger.error("[AuthorizePayment]  encountered a database error ", e);
                    return HyperswitchPaymentTransactionInfoPlugin.cancelPaymentTransactionInfoPlugin(
                            TransactionType.AUTHORIZE, "[authorizePayment]  encountered a database error ");
                }
            } catch (BadRequest e) {
                String responseBody = e.contentUTF8();
                String message = responseBody.toString();
                throw new PaymentPluginApiException(message, e);
            }
        } catch (SQLException e) {
            throw new PaymentPluginApiException("Couldn't find payment method id for account", e);
        }
        return new HyperswitchPaymentTransactionInfoPlugin(
                hyperswitchRecord,
                kbPaymentId,
                kbTransactionId,
                TransactionType.AUTHORIZE,
                amount,
                currency,
                paymentPluginStatus,
                response.getErrorMessage(),
                response.getErrorCode(),
                response.getPaymentId(),
                response.getReferenceId(),
                DateTime.now(),
                DateTime.now(),
                null);
    }

    @Override
    public PaymentTransactionInfoPlugin capturePayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        HyperswitchResponsesRecord hyperswitchRecord = null;
        try {
            hyperswitchRecord = this.hyperswitchDao.getSuccessfulResponse(kbPaymentId, context.getTenantId());
            if (this.refundValidations(hyperswitchRecord, amount) != null) {
                return this.refundValidations(hyperswitchRecord, amount);
            }
        } catch (SQLException e) {
            logger.error("[capturePayment]  but we encountered a database error", e);
            return HyperswitchPaymentTransactionInfoPlugin.cancelPaymentTransactionInfoPlugin(
                    TransactionType.CAPTURE, "[capturePayment] but we encountered a database error");
        }
        PaymentPluginStatus paymentPluginStatus = null;
        String payment_id = hyperswitchRecord.getPaymentAttemptId();
        PaymentsCaptureRequest paymentsRequest = new PaymentsCaptureRequest();
        Long amountToCapture = KillBillMoney.toMinorUnits(hyperswitchRecord.getCurrency(), amount);
        paymentsRequest.setAmountToCapture(amountToCapture);
        PaymentsApi ClientApi = buildHyperswitchClient(context);
        PaymentsResponse response = null;
        try {
            response = ClientApi.captureAPayment(payment_id, paymentsRequest);
            paymentPluginStatus = convertPaymentStatus(response.getStatus());
            try {
                hyperswitchRecord = this.hyperswitchDao.addResponse(
                        kbAccountId,
                        kbPaymentMethodId, kbPaymentMethodId, TransactionType.CAPTURE, amount, currency, response,
                        DateTime.now(), context.getTenantId());
            } catch (final SQLException e) {
                throw new PaymentPluginApiException("Unable to refresh payment", e);
            }
        } catch (BadRequest e) {
            String responseBody = e.contentUTF8();
            String message = responseBody.toString();
            throw new PaymentPluginApiException(message, e);
        }

        return new HyperswitchPaymentTransactionInfoPlugin(
                hyperswitchRecord,
                kbPaymentId,
                kbTransactionId,
                TransactionType.CAPTURE,
                amount,
                currency,
                paymentPluginStatus,
                response.getErrorMessage(),
                response.getErrorCode(),
                response.getPaymentId(),
                response.getReferenceId(),
                DateTime.now(),
                DateTime.now(),
                null);
    }

    @Override
    public PaymentTransactionInfoPlugin purchasePayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency,
            final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        logger.info("[purchasePayment] calling purchase payment");
        HyperswitchResponsesRecord hyperswitchRecord = null;
        PaymentsCreateRequest paymentsCreateRequest = new PaymentsCreateRequest();
        paymentsCreateRequest.setAmount(KillBillMoney.toMinorUnits(currency.toString(), amount));
        paymentsCreateRequest.setCurrency(convertCurrency(currency));
        paymentsCreateRequest.confirm(true);
        paymentsCreateRequest.customerId(kbAccountId.toString());
        paymentsCreateRequest.offSession(true);
        PaymentPluginStatus paymentPluginStatus = null;
        PaymentsResponse response = null;
        try {
            HyperswitchPaymentMethodsRecord record = this.hyperswitchDao.getPaymentMethod(kbPaymentMethodId.toString());
            String mandate_id = record.getHyperswitchId();
            paymentsCreateRequest.setMandateId(mandate_id);
            PaymentsApi ClientApi = buildHyperswitchClient(context);
            try {
                response = ClientApi.createAPayment(paymentsCreateRequest);
                try {
                    paymentPluginStatus = convertPaymentStatus(response.getStatus());
                    final DateTime utcNow = clock.getUTCNow();
                    hyperswitchRecord = this.hyperswitchDao.addResponse(
                            kbAccountId,
                            kbPaymentId,
                            kbTransactionId,
                            TransactionType.PURCHASE,
                            amount,
                            currency,
                            response,
                            utcNow,
                            context.getTenantId());
                } catch (SQLException e) {
                    logger.error("[purchasePayment]  encountered a database error ", e);
                    return HyperswitchPaymentTransactionInfoPlugin.cancelPaymentTransactionInfoPlugin(
                            TransactionType.PURCHASE, "[purchasePayment]  encountered a database error ");
                }
            } catch (BadRequest e) {
                String responseBody = e.contentUTF8();
                String message = responseBody.toString();
                throw new PaymentPluginApiException(message, e);
            }
        } catch (SQLException e) {
            throw new PaymentPluginApiException("Couldn't find payment method id for account", e);
        }
        return new HyperswitchPaymentTransactionInfoPlugin(
                hyperswitchRecord,
                kbPaymentId,
                kbTransactionId,
                TransactionType.PURCHASE,
                amount,
                currency,
                paymentPluginStatus,
                response.getErrorMessage(),
                response.getErrorCode(),
                response.getPaymentId(),
                response.getReferenceId(),
                DateTime.now(),
                DateTime.now(),
                null);
    }

    @Override
    public PaymentTransactionInfoPlugin voidPayment(final UUID kbAccountId, final UUID kbPaymentId,
            final UUID kbTransactionId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties,
            final CallContext context) throws PaymentPluginApiException {
                HyperswitchResponsesRecord hyperswitchRecord = null;
                try {
                    hyperswitchRecord = this.hyperswitchDao.getSuccessfulResponse(kbPaymentId, context.getTenantId());
                } catch (SQLException e) {
                    logger.error("[voidPayment]  but we encountered a database error", e);
                    return HyperswitchPaymentTransactionInfoPlugin.cancelPaymentTransactionInfoPlugin(
                            TransactionType.VOID, "[voidPayment] but we encountered a database error");
                }
                PaymentPluginStatus paymentPluginStatus = null;
                String payment_id = hyperswitchRecord.getPaymentAttemptId();
                PaymentsCancelRequest paymentsRequest = new PaymentsCancelRequest();
                PaymentsApi ClientApi = buildHyperswitchClient(context);
                PaymentsResponse response = null;
                try {
                    response = ClientApi.cancelPayment(payment_id, paymentsRequest);
                    paymentPluginStatus = convertPaymentStatus(response.getStatus());
                    try {
                        hyperswitchRecord = this.hyperswitchDao.addResponse(
                                kbAccountId,
                                kbPaymentMethodId, kbPaymentMethodId, TransactionType.VOID, hyperswitchRecord.getAmount(), null, response,
                                DateTime.now(), context.getTenantId());
                    } catch (final SQLException e) {
                        throw new PaymentPluginApiException("Unable to refresh payment", e);
                    }
                } catch (BadRequest e) {
                    String responseBody = e.contentUTF8();
                    String message = responseBody.toString();
                    throw new PaymentPluginApiException(message, e);
                }
        
                return new HyperswitchPaymentTransactionInfoPlugin(
                        hyperswitchRecord,
                        kbPaymentId,
                        kbTransactionId,
                        TransactionType.VOID,
                        hyperswitchRecord.getAmount(),
                        null,
                        paymentPluginStatus,
                        response.getErrorMessage(),
                        response.getErrorCode(),
                        response.getPaymentId(),
                        response.getReferenceId(),
                        DateTime.now(),
                        DateTime.now(),
                        null);
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
        logger.info("Refund Payment for account {}", kbAccountId);
        HyperswitchResponsesRecord hyperswitchRecord = null;
        try {
            hyperswitchRecord = this.hyperswitchDao.getSuccessfulResponse(kbPaymentId, context.getTenantId());
            if (this.refundValidations(hyperswitchRecord, amount) != null) {
                return this.refundValidations(hyperswitchRecord, amount);
            }
        } catch (SQLException e) {
            logger.error("[refundPayment]  but we encountered a database error", e);
            return HyperswitchPaymentTransactionInfoPlugin.cancelPaymentTransactionInfoPlugin(
                    TransactionType.REFUND, "[refundPayment] but we encountered a database error");
        }
        PaymentPluginStatus paymentPluginStatus = null;
        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setPaymentId(hyperswitchRecord.getPaymentAttemptId());
        Long refundAmount = KillBillMoney.toMinorUnits(hyperswitchRecord.getCurrency(), amount);
        refundRequest.setAmount(refundAmount);
        RefundsApi ClientApi = buildHyperswitchRefundsClient(context);
        RefundResponse response = null;
        try {
            response = ClientApi.createARefund(refundRequest);
            paymentPluginStatus = convertRefundStatus(response.getStatus());
            try {
                hyperswitchRecord = this.hyperswitchDao.addResponse(
                        kbAccountId,
                        kbPaymentMethodId, kbPaymentMethodId, TransactionType.REFUND, amount, currency, response,
                        DateTime.now(), context.getTenantId());
            } catch (final SQLException e) {
                throw new PaymentPluginApiException("Unable to refresh payment", e);
            }
        } catch (BadRequest e) {
            String responseBody = e.contentUTF8();
            String message = responseBody.toString();
            throw new PaymentPluginApiException(message, e);
        }

        return new HyperswitchPaymentTransactionInfoPlugin(
                hyperswitchRecord,
                kbPaymentId,
                kbTransactionId,
                TransactionType.REFUND,
                amount,
                currency,
                paymentPluginStatus,
                response.getErrorMessage(),
                response.getErrorCode(),
                response.getPaymentId(),
                null,
                DateTime.now(),
                DateTime.now(),
                null);
    }

    @Override
    public List<PaymentTransactionInfoPlugin> getPaymentInfo(final UUID kbAccountId, final UUID kbPaymentId,
            final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        logger.info("[getPaymentInfo] getPaymentInfo for account {}", kbAccountId);
        final List<PaymentTransactionInfoPlugin> transactions = super.getPaymentInfo(kbAccountId, kbPaymentId,
                properties, context);

        if (transactions.isEmpty()) {
            // We don't know about this payment (maybe it was aborted in a control plugin)
            return transactions;
        }
        boolean wasRefreshed = false;
        for (final PaymentTransactionInfoPlugin transaction : transactions) {
            if (transaction.getStatus() == PaymentPluginStatus.PENDING) {
                HyperswitchResponsesRecord hyperswitchRecord = null;
                final String paymentIntentId = PluginProperties.findPluginPropertyValue("payment_id",
                        transaction.getProperties());
                PaymentsApi ClientApi = buildHyperswitchClient(context);
                PaymentsResponse response = ClientApi.retrieveAPaymentwithForcesync(paymentIntentId);
                try {
                    hyperswitchRecord = this.hyperswitchDao.updateResponse(
                            kbAccountId,
                            response,
                            context.getTenantId());
                } catch (final SQLException e) {
                    throw new PaymentPluginApiException("Unable to refresh payment", e);
                }
            }
        }
        return wasRefreshed ? super.getPaymentInfo(kbAccountId, kbPaymentId, properties, context) : transactions;
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
            case REQUIRES_CAPTURE:
            case SUCCEEDED:
            case PARTIALLY_CAPTURED:
            case PARTIALLY_CAPTURED_AND_CAPTURABLE:
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

    private PaymentPluginStatus convertRefundStatus(RefundStatus refundStatus) {
        switch (refundStatus) {
            case SUCCEEDED:
                return PaymentPluginStatus.PROCESSED;
            case PENDING:
                return PaymentPluginStatus.PENDING;
            case FAILED:
                return PaymentPluginStatus.ERROR;
            case REVIEW:
                return PaymentPluginStatus.PENDING;
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
    protected PaymentTransactionInfoPlugin buildPaymentTransactionInfoPlugin(
            final HyperswitchResponsesRecord hyperswitchRecord) {
        return HyperswitchPaymentTransactionInfoPlugin.build(hyperswitchRecord);
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
        final HyperswitchConfigProperties config = hyperswitchConfigurationHandler
                .getConfigurable(tenantContext.getTenantId());
        if (config == null || config.getHSApiKey() == null || config.getHSApiKey().isEmpty()) {
            logger.warn("Per-tenant properties not configured");
            return null;
        }
        final PaymentsApi client = new HsApiClient("api_key", config.getHSApiKey()).buildClient(PaymentsApi.class);
        return client;
    }

    private RefundsApi buildHyperswitchRefundsClient(final TenantContext tenantContext) {
        final HyperswitchConfigProperties config = hyperswitchConfigurationHandler
                .getConfigurable(tenantContext.getTenantId());
        if (config == null || config.getHSApiKey() == null || config.getHSApiKey().isEmpty()) {
            logger.warn("Per-tenant properties not configured");
            return null;
        }
        final RefundsApi client = new HsApiClient("api_key", config.getHSApiKey()).buildClient(RefundsApi.class);
        return client;
    }

    public PaymentTransactionInfoPlugin refundValidations( // Validate this function with currency unit of amount
            HyperswitchResponsesRecord hyperswitchRecord, BigDecimal amount) {
        if (hyperswitchRecord == null) {
            logger.error("[refundPayment] Purchase do not exists");
            return HyperswitchPaymentTransactionInfoPlugin.cancelPaymentTransactionInfoPlugin(
                    TransactionType.REFUND, "Purchase do not exists");
        }

        if (hyperswitchRecord.getAmount().compareTo(amount) < 0) {
            logger.error("[refundPayment] The refund amount is more than the transaction amount");
            return HyperswitchPaymentTransactionInfoPlugin.cancelPaymentTransactionInfoPlugin(
                    TransactionType.REFUND, "The refund amount is more than the transaction amount");
        }
        if (BigDecimal.ZERO.compareTo(amount) == 0) {
            logger.error("[refundPayment] The refund amount can not be zero");
            return HyperswitchPaymentTransactionInfoPlugin.cancelPaymentTransactionInfoPlugin(
                    TransactionType.REFUND, "The refund amount can not be zero");
        } else {
            return null;
        }
    }
}
