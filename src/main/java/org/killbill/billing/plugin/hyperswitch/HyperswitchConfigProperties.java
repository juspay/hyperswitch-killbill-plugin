package org.killbill.billing.plugin.hyperswitch;


// import java.net.InetSocketAddress;
// import java.net.Proxy;
// import java.net.Proxy.Type;
// import java.security.GeneralSecurityException;
// import java.util.HashMap;
// import java.util.LinkedHashMap;
// import java.util.Map;
// import java.util.Map.Entry;
// import java.util.Properties;

// import javax.annotation.Nullable;
// import javax.net.ssl.HostnameVerifier;
// import javax.net.ssl.HttpsURLConnection;
// import javax.net.ssl.SSLContext;
// import javax.net.ssl.SSLSocketFactory;

// import org.joda.time.Period;
// import org.killbill.billing.plugin.util.http.SslUtils;

// import com.google.common.base.Ascii;
// import com.google.common.base.MoreObjects;
// import com.google.common.base.Strings;
import java.util.Map;
import java.util.Properties;

// import com.fasterxml.jackson.databind.annotation.JsonAppend.Prop;



public class HyperswitchConfigProperties {
    private static final String PROPERTY_PREFIX = "org.killbill.billing.plugin.hyperswitch.";


	public static final String HYPERSWITCH_API_KEY = "HYPERSWITCH_API_KEY";
	public static final String HYPERSWITCH_PROFILE_ID= "HYPERSWITCH_PROFILE_ID";
	public static final String HPYERSWITCH_ENVIRONMENT_KEY = "HPYERSWITCH_ENVIRONMENT";


	private final String hyperswitchApikey;
	private final String environment;
	private final String profileId;
    

	public enum Environment {
		PRODUCTION, 
		SANDBOX
	}
	
	public HyperswitchConfigProperties(final Properties properties, final String region) {
		this.hyperswitchApikey = properties.getProperty(PROPERTY_PREFIX + "hyperswitchApikey");
		this.profileId = properties.getProperty(PROPERTY_PREFIX + "profileId");
		this.environment = properties.getProperty(PROPERTY_PREFIX + "environment", "sandbox"); // defaults to sandbox
	}
	

	public String getHSApiKey() {
		if (hyperswitchApikey == null || hyperswitchApikey.isEmpty()) {
			return getClient(hyperswitchApikey, null);
		}
		return hyperswitchApikey;
	}
	
	public String getEnvironment() {
		if (environment == null || environment.isEmpty()) {
			return getClient(environment, null);
		}
		return environment;
	}
	
	public String getProfileId(){
		if (profileId == null || profileId.isEmpty()) {
			return getClient(profileId, null);
		}
		return profileId;
	}
	
	private String getClient(String envKey, String defaultValue) {
		Map<String, String> env = System.getenv();

		String value = env.get(envKey);

		if (value == null || value.isEmpty()) {
			return defaultValue;
		}

		return value;
	}	

}