/*
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

package org.killbill.billing.plugin.hyperswitch.dao;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.sql.DataSource;

import org.joda.time.DateTime;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.plugin.api.PluginProperties;
import org.killbill.billing.plugin.dao.payment.PluginPaymentDao;

import com.fasterxml.jackson.annotation.JsonInclude.Include;

import org.killbill.billing.plugin.hyperswitch.dao.gen.tables.HyperswitchPaymentMethods;
import org.killbill.billing.plugin.hyperswitch.dao.gen.tables.HyperswitchResponses;
import org.killbill.billing.plugin.hyperswitch.dao.gen.tables.records.HyperswitchHppRequestsRecord;
import org.killbill.billing.plugin.hyperswitch.dao.gen.tables.records.HyperswitchPaymentMethodsRecord;
import org.killbill.billing.plugin.hyperswitch.dao.gen.tables.records.HyperswitchResponsesRecord;

import static org.killbill.billing.plugin.hyperswitch.dao.gen.tables.HyperswitchHppRequests.HYPERSWITCH_HPP_REQUESTS;
import static org.killbill.billing.plugin.hyperswitch.dao.gen.tables.HyperswitchPaymentMethods.HYPERSWITCH_PAYMENT_METHODS;
import static org.killbill.billing.plugin.hyperswitch.dao.gen.tables.HyperswitchResponses.HYPERSWITCH_RESPONSES;

public class HyperswitchDao extends PluginPaymentDao<HyperswitchResponsesRecord, HyperswitchResponses, HyperswitchPaymentMethodsRecord, HyperswitchPaymentMethods> {

    public HyperswitchDao(final DataSource dataSource) throws SQLException {
        super(HYPERSWITCH_RESPONSES, HYPERSWITCH_PAYMENT_METHODS, dataSource);
        // Save space in the database
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);
    }

    // Payment methods

    public void addPaymentMethod(final UUID kbAccountId,
                                 final UUID kbPaymentMethodId,
                                 final Map<String, String> additionalDataMap,
                                 final String hyperswitchId,
                                 final UUID kbTenantId) throws SQLException {
        execute(dataSource.getConnection(),
                new WithConnectionCallback<HyperswitchResponsesRecord>() {
                    @Override
                    public HyperswitchResponsesRecord withConnection(final Connection conn) throws SQLException {
                        System.out.println("Adding payment method");
                        DSL.using(conn, dialect, settings)
                           .insertInto(HYPERSWITCH_PAYMENT_METHODS,
                                       HYPERSWITCH_PAYMENT_METHODS.KB_ACCOUNT_ID,
                                       HYPERSWITCH_PAYMENT_METHODS.KB_PAYMENT_METHOD_ID,
                                       HYPERSWITCH_PAYMENT_METHODS.HYPERSWITCH_ID,
                                       HYPERSWITCH_PAYMENT_METHODS.IS_DELETED,
                                       HYPERSWITCH_PAYMENT_METHODS.ADDITIONAL_DATA,
                                       HYPERSWITCH_PAYMENT_METHODS.CREATED_DATE,
                                       HYPERSWITCH_PAYMENT_METHODS.UPDATED_DATE,
                                       HYPERSWITCH_PAYMENT_METHODS.KB_TENANT_ID)
                           .values(kbAccountId.toString(),
                                   kbPaymentMethodId.toString(),
                                   hyperswitchId,
                                   (short) FALSE,
                                   asString(additionalDataMap),
                                   toLocalDateTime(new DateTime()),
                                   toLocalDateTime(new DateTime()),
                                   kbTenantId.toString()
                                   )
                           .execute();
                        System.out.println("Added payment method successfully");
                        return null;
                    }
                });
    }

    public HyperswitchPaymentMethodsRecord getPaymentMethod(final String kbPaymentMethodId)
      throws SQLException {
    return execute(
        dataSource.getConnection(),
        new WithConnectionCallback<HyperswitchPaymentMethodsRecord>() {
          @Override
          public HyperswitchPaymentMethodsRecord withConnection(final Connection conn)
              throws SQLException {
            return DSL.using(conn, dialect, settings)
                .selectFrom(HYPERSWITCH_PAYMENT_METHODS)
                .where(
                    DSL.field(HYPERSWITCH_PAYMENT_METHODS.KB_PAYMENT_METHOD_ID).equal(kbPaymentMethodId))
                .fetchOne();
          }
        });
  }

    // public void updatePaymentMethod(final UUID kbPaymentMethodId,
    //                                 final Map<String, Object> additionalDataMap,
    //                                 final String hyperswitchId,
    //                                 final DateTime utcNow,
    //                                 final UUID kbTenantId) throws SQLException {
    //     execute(dataSource.getConnection(),
    //             new WithConnectionCallback<HyperswitchResponsesRecord>() {
    //                 @Override
    //                 public HyperswitchResponsesRecord withConnection(final Connection conn) throws SQLException {
    //                     DSL.using(conn, dialect, settings)
    //                        .update(HYPERSWITCH_PAYMENT_METHODS)
    //                        .set(HYPERSWITCH_PAYMENT_METHODS.ADDITIONAL_DATA, asString(additionalDataMap))
    //                        .set(HYPERSWITCH_PAYMENT_METHODS.UPDATED_DATE, toLocalDateTime(utcNow))
    //                        .where(HYPERSWITCH_PAYMENT_METHODS.KB_PAYMENT_METHOD_ID.equal(kbPaymentMethodId.toString()))
    //                        .and(HYPERSWITCH_PAYMENT_METHODS.HYPERSWITCH_ID.equal(hyperswitchId))
    //                        .and(HYPERSWITCH_PAYMENT_METHODS.KB_TENANT_ID.equal(kbTenantId.toString()))
    //                        .execute();
    //                     return null;
    //                 }
    //             });
    // }

    // HPP requests

    // public void addHppRequest(final UUID kbAccountId,
    //                           final UUID kbPaymentId,
    //                           final UUID kbPaymentTransactionId,
    //                           final Session hyperswitchSession,
    //                           final DateTime utcNow,
    //                           final UUID kbTenantId) throws SQLException {
    //     final Map<String, Object> additionalDataMap = HyperswitchPluginProperties.toAdditionalDataMap(hyperswitchSession, null);

    //     execute(dataSource.getConnection(),
    //             new WithConnectionCallback<Void>() {
    //                 @Override
    //                 public Void withConnection(final Connection conn) throws SQLException {
    //                     DSL.using(conn, dialect, settings)
    //                        .insertInto(HYPERSWITCH_HPP_REQUESTS,
    //                                    HYPERSWITCH_HPP_REQUESTS.KB_ACCOUNT_ID,
    //                                    HYPERSWITCH_HPP_REQUESTS.KB_PAYMENT_ID,
    //                                    HYPERSWITCH_HPP_REQUESTS.KB_PAYMENT_TRANSACTION_ID,
    //                                    HYPERSWITCH_HPP_REQUESTS.SESSION_ID,
    //                                    HYPERSWITCH_HPP_REQUESTS.ADDITIONAL_DATA,
    //                                    HYPERSWITCH_HPP_REQUESTS.CREATED_DATE,
    //                                    HYPERSWITCH_HPP_REQUESTS.KB_TENANT_ID)
    //                        .values(kbAccountId.toString(),
    //                                kbPaymentId == null ? null : kbPaymentId.toString(),
    //                                kbPaymentTransactionId == null ? null : kbPaymentTransactionId.toString(),
    //                                hyperswitchSession.getId(),
    //                                asString(additionalDataMap),
    //                                toLocalDateTime(utcNow),
    //                                kbTenantId.toString())
    //                        .execute();
    //                     return null;
    //                 }
    //             });
    // }

    // public HyperswitchHppRequestsRecord getHppRequest(final String sessionId,
    //                                              final String kbTenantId) throws SQLException {
    //     return execute(dataSource.getConnection(),
    //                    new WithConnectionCallback<HyperswitchHppRequestsRecord>() {
    //                        @Override
    //                        public HyperswitchHppRequestsRecord withConnection(final Connection conn) throws SQLException {
    //                            return DSL.using(conn, dialect, settings)
    //                                      .selectFrom(HYPERSWITCH_HPP_REQUESTS)
    //                                      .where(HYPERSWITCH_HPP_REQUESTS.SESSION_ID.equal(sessionId))
    //                                      .and(HYPERSWITCH_HPP_REQUESTS.KB_TENANT_ID.equal(kbTenantId))
    //                                      .orderBy(HYPERSWITCH_HPP_REQUESTS.RECORD_ID.desc())
    //                                      .limit(1)
    //                                      .fetchOne();
    //                        }
    //                    });
    // }

    // // Responses

    // public HyperswitchResponsesRecord addResponse(final UUID kbAccountId,
    //                                          final UUID kbPaymentId,
    //                                          final UUID kbPaymentTransactionId,
    //                                          final TransactionType transactionType,
    //                                          final BigDecimal amount,
    //                                          final Currency currency,
    //                                          @Nullable final PaymentIntent hyperswitchPaymentIntent,
    //                                          @Nullable final Charge lastCharge,
    //                                          @Nullable final HyperswitchException hyperswitchException,
    //                                          final DateTime utcNow,
    //                                          final UUID kbTenantId) throws SQLException {
    //     final Map<String, Object> additionalDataMap;
    //     if (hyperswitchPaymentIntent != null) {
    //         additionalDataMap = HyperswitchPluginProperties.toAdditionalDataMap(hyperswitchPaymentIntent, lastCharge);
    //     } else if (hyperswitchException != null) {
    //         additionalDataMap = HyperswitchPluginProperties.toAdditionalDataMap(hyperswitchException);
    //     } else {
    //         additionalDataMap = Collections.emptyMap();
    //     }

    //     return execute(dataSource.getConnection(),
    //                    conn -> DSL.using(conn, dialect, settings).transactionResult(configuration -> {
    //                        final DSLContext dslContext = DSL.using(configuration);
    //                        dslContext.insertInto(HYPERSWITCH_RESPONSES,
    //                                              HYPERSWITCH_RESPONSES.KB_ACCOUNT_ID,
    //                                              HYPERSWITCH_RESPONSES.KB_PAYMENT_ID,
    //                                              HYPERSWITCH_RESPONSES.KB_PAYMENT_TRANSACTION_ID,
    //                                              HYPERSWITCH_RESPONSES.TRANSACTION_TYPE,
    //                                              HYPERSWITCH_RESPONSES.AMOUNT,
    //                                              HYPERSWITCH_RESPONSES.CURRENCY,
    //                                              HYPERSWITCH_RESPONSES.HYPERSWITCH_ID,
    //                                              HYPERSWITCH_RESPONSES.ADDITIONAL_DATA,
    //                                              HYPERSWITCH_RESPONSES.CREATED_DATE,
    //                                              HYPERSWITCH_RESPONSES.KB_TENANT_ID)
    //                           .values(kbAccountId.toString(),
    //                                   kbPaymentId.toString(),
    //                                   kbPaymentTransactionId.toString(),
    //                                   transactionType.toString(),
    //                                   amount,
    //                                   currency == null ? null : currency.name(),
    //                                   hyperswitchPaymentIntent == null ? null : hyperswitchPaymentIntent.getId(),
    //                                   asString(additionalDataMap),
    //                                   toLocalDateTime(utcNow),
    //                                   kbTenantId.toString())
    //                           .execute();
    //                        return dslContext.fetchOne(
    //                                HYPERSWITCH_RESPONSES,
    //                                HYPERSWITCH_RESPONSES.RECORD_ID.eq(HYPERSWITCH_RESPONSES.RECORD_ID.getDataType().convert(dslContext.lastID())));
    //                    }));
    // }

    // public HyperswitchResponsesRecord updateResponse(final UUID kbPaymentTransactionId,
    //                                             final PaymentIntent hyperswitchPaymentIntent,
    //                                             @Nullable final Charge lastCharge,
    //                                             final UUID kbTenantId) throws SQLException {
    //     final Map<String, Object> additionalDataMap = HyperswitchPluginProperties.toAdditionalDataMap(hyperswitchPaymentIntent, lastCharge);
    //     return updateResponse(kbPaymentTransactionId, additionalDataMap, kbTenantId);
    // }

    // public HyperswitchResponsesRecord updateResponse(final UUID kbPaymentTransactionId,
    //                                             final Iterable<PluginProperty> additionalPluginProperties,
    //                                             final UUID kbTenantId) throws SQLException {
    //     final Map<String, Object> additionalProperties = PluginProperties.toMap(additionalPluginProperties);
    //     return updateResponse(kbPaymentTransactionId, additionalProperties, kbTenantId);
    // }

    // public HyperswitchResponsesRecord updateResponse(final UUID kbPaymentTransactionId,
    //                                             final Map<String, Object> additionalProperties,
    //                                             final UUID kbTenantId) throws SQLException {
    //     return execute(dataSource.getConnection(),
    //                    new WithConnectionCallback<HyperswitchResponsesRecord>() {
    //                        @Override
    //                        public HyperswitchResponsesRecord withConnection(final Connection conn) throws SQLException {
    //                            final HyperswitchResponsesRecord response = DSL.using(conn, dialect, settings)
    //                                                                      .selectFrom(HYPERSWITCH_RESPONSES)
    //                                                                      .where(HYPERSWITCH_RESPONSES.KB_PAYMENT_TRANSACTION_ID.equal(kbPaymentTransactionId.toString()))
    //                                                                      .and(HYPERSWITCH_RESPONSES.KB_TENANT_ID.equal(kbTenantId.toString()))
    //                                                                      .orderBy(HYPERSWITCH_RESPONSES.RECORD_ID.desc())
    //                                                                      .limit(1)
    //                                                                      .fetchOne();

    //                            if (response == null) {
    //                                return null;
    //                            }

    //                            final Map originalData = new HashMap(fromAdditionalData(response.getAdditionalData()));
    //                            originalData.putAll(additionalProperties);

    //                            DSL.using(conn, dialect, settings)
    //                               .update(HYPERSWITCH_RESPONSES)
    //                               .set(HYPERSWITCH_RESPONSES.ADDITIONAL_DATA, asString(originalData))
    //                               .where(HYPERSWITCH_RESPONSES.RECORD_ID.equal(response.getRecordId()))
    //                               .execute();
    //                            return response;
    //                        }
    //                    });
    // }

    // public void updateResponse(final HyperswitchResponsesRecord hyperswitchResponsesRecord,
    //                            final Map additionalMetadata) throws SQLException {
    //     final Map additionalDataMap = fromAdditionalData(hyperswitchResponsesRecord.getAdditionalData());
    //     additionalDataMap.putAll(additionalMetadata);

    //     execute(dataSource.getConnection(),
    //             new WithConnectionCallback<Void>() {
    //                 @Override
    //                 public Void withConnection(final Connection conn) throws SQLException {
    //                     DSL.using(conn, dialect, settings)
    //                        .update(HYPERSWITCH_RESPONSES)
    //                        .set(HYPERSWITCH_RESPONSES.ADDITIONAL_DATA, asString(additionalDataMap))
    //                        .where(HYPERSWITCH_RESPONSES.RECORD_ID.equal(hyperswitchResponsesRecord.getRecordId()))
    //                        .execute();
    //                     return null;
    //                 }
    //             });
    // }

    // @Override
    // public HyperswitchResponsesRecord getSuccessfulAuthorizationResponse(final UUID kbPaymentId, final UUID kbTenantId) throws SQLException {
    //     return execute(dataSource.getConnection(),
    //                    new WithConnectionCallback<HyperswitchResponsesRecord>() {
    //                        @Override
    //                        public HyperswitchResponsesRecord withConnection(final Connection conn) throws SQLException {
    //                            return DSL.using(conn, dialect, settings)
    //                                      .selectFrom(responsesTable)
    //                                      .where(DSL.field(responsesTable.getName() + "." + KB_PAYMENT_ID).equal(kbPaymentId.toString()))
    //                                      .and(
    //                                              DSL.field(responsesTable.getName() + "." + TRANSACTION_TYPE).equal(TransactionType.AUTHORIZE.toString())
    //                                                 .or(DSL.field(responsesTable.getName() + "." + TRANSACTION_TYPE).equal(TransactionType.PURCHASE.toString()))
    //                                          )
    //                                      .and(DSL.field(responsesTable.getName() + "." + KB_TENANT_ID).equal(kbTenantId.toString()))
    //                                      .orderBy(DSL.field(responsesTable.getName() + "." + RECORD_ID).desc())
    //                                      .limit(1)
    //                                      .fetchOne();
    //                        }
    //                    });
    // }

    public static Map fromAdditionalData(@Nullable final String additionalData) {
        if (additionalData == null) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(additionalData, Map.class);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
