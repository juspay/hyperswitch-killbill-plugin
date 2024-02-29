

package org.killbill.billing.plugin.hyperswitch;



import java.util.HashMap;
import java.util.Map;
import com.hyperswitch.client.model.PaymentsResponse;


public abstract class HyperswitchPluginProperties {
    public static Map<String, Object> toAdditionalDataMap(final PaymentsResponse hyperswitchPaymentResponse) {
        final Map<String, Object> additionalDataMap = new HashMap<String, Object>();

        additionalDataMap.put("amount", hyperswitchPaymentResponse.getAmount());
        additionalDataMap.put("payment_id", hyperswitchPaymentResponse.getPaymentId());
        additionalDataMap.put("status", hyperswitchPaymentResponse.getStatus());
        additionalDataMap.put("customer_id", hyperswitchPaymentResponse.getCustomerId());
        additionalDataMap.put("reference_id",hyperswitchPaymentResponse.getReferenceId());
        additionalDataMap.put("profile_id", hyperswitchPaymentResponse.getProfileId());
        return additionalDataMap;
    }
}