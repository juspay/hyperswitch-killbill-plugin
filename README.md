# hyperswitch-killbill-plugin

Killbill payment plugin to use [Hyperswitch](https://hyperswitch.io/) as a payment orchastrator.


## Requirements

The plugin needs a database. The latest version of the schema can be found [here](https://github.com/juspay/hyperswitch-killbill-plugin.git/blob/d07af03287fe91354278a6b2202b6e82bf08d07a/src/main/resources/ddl.sql).

## Installation

Locally:

```
kpm install_java_plugin hyperswitch --from-source-file target/hyperswitchplugin-*-SNAPSHOT.jar --destination /var/tmp/bundles
```

## Configuration

Create an apikey from hyperswitch dashboard. To know more click [here](https://docs.hyperswitch.io/hyperswitch-open-source/account-setup/using-hyperswitch-control-center#user-content-create-an-api-key)

Then, go to the Kaui plugin configuration page (`/admin_tenants/1?active_tab=PluginConfig`), and configure the `hyperswitch` plugin with your key:

```java
org.killbill.billing.plugin.hyperswitch.hyperswitchApikey=API_KEY
org.killbill.billing.plugin.hyperswitch.environment=ENVIRONMENT
```

Alternatively, you can upload the configuration directly:

```bash
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: admin' \
     -H 'Content-Type: text/plain' \
     -d 'org.killbill.billing.plugin.hyperswitch.hyperswitchApikey=API_KEY
org.killbill.billing.plugin.hyperswitch.environment=ENVIRONMENT' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/hyperswitch-plugin
```
# Add mandate id to payment method

Create mandate id with killbill customer id at hyperswitch pass this mandate id to killbill. add `idDefault=true` in query parmas to make this payment method to default for killbill account.

```
curl --location --request POST 'http://127.0.0.1:8080/1.0/kb/accounts/<KB_ACCOUNT_ID>/paymentMethods?isDefault=true' \
--header 'X-Killbill-ApiKey: bob' \
--header 'X-Killbill-ApiSecret: lazar' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--header 'X-Killbill-CreatedBy: demo' \
--header 'X-Killbill-Reason: demo' \
--header 'X-Killbill-Comment: demo' \
--data-raw '{
  			"pluginName": "hyperswitch-plugin",
  			"pluginInfo": {
    			"isDefaultPaymentMethod": true,
    			"properties": [
      				{
        				"key": "mandateId",
        				"value": "YOUR_MANDATE_ID",
        				"isUpdatable": false
      				}
    			]
  			}
		}'
```
Note : If your customer is not same as killbill account id pass the pass it with properties(This flow will be updated).

# Purchase payment 

Inorder to make merchant initiated transaction call payments api at killbill.

```
curl --location --request POST 'http://127.0.0.1:8080/1.0/kb/accounts/<KB_ACCOUNT_ID>/payments' \
--header 'X-Killbill-ApiKey: bob' \
--header 'X-Killbill-ApiSecret: lazar' \
--header 'X-Killbill-CreatedBy: tutorial' \
--header 'Content-Type: application/json' \\
--data-raw '{
    "transactionType": "PURCHASE",
    "amount": "60",
    "currency" : "USD"
}'
```

# Payments retrieve

By default, Hyperswitch calls payment gateway whenever you call this method for non terminal state for plugin payment status.

```
curl --location --request GET 'http://127.0.0.1:8080/1.0/kb/payments/<KB_PAYMENT_ID>?withPluginInfo=true' \
--header 'X-Killbill-ApiKey: bob' \
--header 'X-Killbill-ApiSecret: lazar' \
--header 'Accept: application/json' \
```



## About

Hyperswitch is a community-led, open payments switch to enable access to the best payments infrastructure for every digital business.Get updates on Hyperswitch development and chat with the community:

[Discord server](https://discord.com/invite/wJZ7DVW8mm) for questions related to contributing to hyperswitch, questions about the architecture, components, etc.
[Slack Workspace](https://hyperswitch-io.slack.com/ssb/redirect) for questions related to integrating hyperswitch, integrating a connector in hyperswitch, etc.
