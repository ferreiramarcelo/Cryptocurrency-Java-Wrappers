package org.jaewanyun.wrapper.poloniex;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jaewanyun.wrapper.CustomNameValuePair;
import org.jaewanyun.wrapper.Utility;
import org.jaewanyun.wrapper.poloniex.data.Chart;
import org.jaewanyun.wrapper.poloniex.data.Currency;
import org.jaewanyun.wrapper.poloniex.data.LoanOrder;
import org.jaewanyun.wrapper.poloniex.data.OrderBook;
import org.jaewanyun.wrapper.poloniex.data.Ticker;
import org.jaewanyun.wrapper.poloniex.data.Trade;
import org.jaewanyun.wrapper.poloniex.data.Volume;

/**
 * Wrapper for Poloniex public API and trading API. https://poloniex.com/support/api
 */
public class Poloniex {

	private static final String TRADING_URL = "https://poloniex.com/tradingApi";
	private static final String PUBLIC_URL = "https://poloniex.com/public";
	private static CloseableHttpClient httpClient;

	static {httpClient = HttpClients.createDefault();}


	/**
	 * Wrapper for Poloniex public API. According to Poloniex API documentation, there are six public methods, all of which take HTTP GET requests and return output in JSON format
	 */
	public static class PublicApi {

		/**
		 * @return Ticker for all markets
		 */
		public static Ticker[] ticker() {

			HttpGet get = new HttpGet(PUBLIC_URL + "?command=returnTicker");
			String response = null;

			try(CloseableHttpResponse httpResponse = httpClient.execute(get)) {
				response = EntityUtils.toString(httpResponse.getEntity());
			} catch (IOException e) {
				e.printStackTrace();
			}

			@SuppressWarnings("rawtypes")
			ArrayList<CustomNameValuePair<String, CustomNameValuePair>> a = Utility.evaluateExpression(response);
			ArrayList<Ticker> b = new ArrayList<>();

			for(int j = 0; j < a.size(); j++) {
				Ticker tickerData = new Ticker(
						a.get(j++).getName(),
						Integer.parseInt(a.get(j++).getValue().toString()),
						Double.parseDouble(a.get(j++).getValue().toString()),
						Double.parseDouble(a.get(j++).getValue().toString()),
						Double.parseDouble(a.get(j++).getValue().toString()),
						Double.parseDouble(a.get(j++).getValue().toString()),
						Double.parseDouble(a.get(j++).getValue().toString()),
						Double.parseDouble(a.get(j++).getValue().toString()),
						Boolean.parseBoolean(a.get(j++).getValue().toString()),
						Double.parseDouble(a.get(j++).getValue().toString()),
						Double.parseDouble(a.get(j).getValue().toString()));
				b.add(tickerData);
			}

			return b.toArray(new Ticker[b.size()]);

		}

		/**
		 * @return 24-hour volume for all markets, plus totals for primary currencies
		 */
		public static Volume volume() {

			HttpGet get = new HttpGet(PUBLIC_URL + "?command=return24hVolume");
			String response = null;

			try(CloseableHttpResponse httpResponse = httpClient.execute(get)) {
				response = EntityUtils.toString(httpResponse.getEntity());
			} catch (IOException e) {
				e.printStackTrace();
			}

			@SuppressWarnings("rawtypes")
			ArrayList<CustomNameValuePair<String, CustomNameValuePair>> a = Utility.evaluateExpression(response);

			Volume volumeData = new Volume();

			for(int j = 0; j < a.size(); j++) {

				if(a.get(j).getName().equals("totalBTC") || a.get(j).getName().equals("totalETH") || a.get(j).getName().equals("totalUSDT") || a.get(j).getName().equals("totalXMR") || a.get(j).getName().equals("totalXUSD")) {

					Volume.Total volumeTotalData = new Volume.Total(
							a.get(j).getName(),
							Double.parseDouble(a.get(j).getValue().toString()));
					volumeData.totals.add(volumeTotalData);

				} else {

					Volume.Pair volumePairData = new Volume.Pair(
							a.get(j++).getName(),
							a.get(j++).getName(),
							Double.parseDouble(a.get(j).getValue().toString()),
							a.get(j).getName(),
							Double.parseDouble(a.get(j).getValue().toString()));
					volumeData.pairs.add(volumePairData);

				}
			}

			return volumeData;

		}

		/**
		 * @param currencyPair Pair of cryptocurrencies (e.g. BTC_ETH)
		 * @return Order book for a given market, as well as a sequence number for use with the Push API and an indicator specifying whether the market is frozen
		 */
		public static OrderBook orderBook(String currencyPair) {

			HttpGet get = new HttpGet(PUBLIC_URL + "?command=returnOrderBook&currencyPair=" + currencyPair);
			String response = null;

			try(CloseableHttpResponse httpResponse = httpClient.execute(get)) {
				response = EntityUtils.toString(httpResponse.getEntity());
			} catch (IOException e) {
				e.printStackTrace();
			}

			@SuppressWarnings("rawtypes")
			ArrayList<CustomNameValuePair<String, CustomNameValuePair>> a = Utility.evaluateExpression(response);

			ArrayList<OrderBook.Order> asks = new ArrayList<>();
			ArrayList<OrderBook.Order> bids = new ArrayList<>();

			boolean asksDoneParsing = false;

			boolean isFrozen = false;
			long seq = 0;

			for(CustomNameValuePair<?, ?> each : a) {

				if(each.getName().equals("asks"))
					asksDoneParsing = false;
				else if(each.getName().equals("bids"))
					asksDoneParsing = true;
				else if(each.getName().equals("isFrozen"))
					isFrozen = Boolean.parseBoolean(each.getValue().toString());
				else if(each.getName().equals("seq"))
					seq = Long.parseLong(each.getValue().toString());
				else {
					OrderBook.Order order = new OrderBook.Order(
							Double.parseDouble(each.getName().toString()),
							Double.parseDouble(each.getValue().toString()));
					if(!asksDoneParsing)
						asks.add(order);
					else
						bids.add(order);
				}
			}

			return new OrderBook(currencyPair, asks, bids, isFrozen, seq);

		}

		/**
		 * @return Order book for all markets, as well as a sequence number for use with the Push API and an indicator specifying whether the market is frozen
		 */
		public static OrderBook[] orderBookAll() {

			HttpGet get = new HttpGet(PUBLIC_URL + "?command=returnOrderBook&currencyPair=all");
			String response = null;

			try(CloseableHttpResponse httpResponse = httpClient.execute(get)) {
				response = EntityUtils.toString(httpResponse.getEntity());
			} catch (IOException e) {
				e.printStackTrace();
			}

			@SuppressWarnings("rawtypes")
			ArrayList<CustomNameValuePair<String, CustomNameValuePair>> a = Utility.evaluateExpression(response);

			ArrayList<OrderBook> orderBookDatas = new ArrayList<>();

			ArrayList<OrderBook.Order> asks = new ArrayList<>();
			ArrayList<OrderBook.Order> bids = new ArrayList<>();

			boolean asksDoneParsing = false;

			for(@SuppressWarnings("rawtypes") CustomNameValuePair<String, CustomNameValuePair> each : a) {

				if(each.getName().equals("asks"))
					asksDoneParsing = false;
				else if(each.getName().equals("bids"))
					asksDoneParsing = true;
				else if(each.getName().equals("isFrozen"))
					orderBookDatas.get(orderBookDatas.size()-1).isFrozen = Boolean.parseBoolean(each.getValue().toString());
				else if(each.getName().equals("seq"))
					orderBookDatas.get(orderBookDatas.size()-1).seq = Long.parseLong(each.getValue().toString());
				else if(each.getValue() == null) {
					orderBookDatas.add(new OrderBook(each.getName()));
				} else {
					OrderBook.Order order = new OrderBook.Order(
							Double.parseDouble(each.getName().toString()),
							Double.parseDouble(each.getValue().toString()));
					if(!asksDoneParsing) {
						asks.add(order);
						if(orderBookDatas.get(orderBookDatas.size()-1).asks == null)
							orderBookDatas.get(orderBookDatas.size()-1).asks = new ArrayList<>();
						orderBookDatas.get(orderBookDatas.size()-1).asks.add(order);
					} else {
						bids.add(order);
						if(orderBookDatas.get(orderBookDatas.size()-1).bids == null)
							orderBookDatas.get(orderBookDatas.size()-1).bids = new ArrayList<>();
						orderBookDatas.get(orderBookDatas.size()-1).bids.add(order);
					}
				}
			}

			return orderBookDatas.toArray(new OrderBook[orderBookDatas.size()]);

		}

		/**
		 * @param currencyPair Pair of cryptocurrencies (e.g. BTC_ETH)
		 * @return Past 200 trades for a given market
		 */
		public static Trade[] tradeHistory(String currencyPair) {

			HttpGet get = new HttpGet(PUBLIC_URL + "?command=returnTradeHistory&currencyPair=" + currencyPair);
			String response = null;

			try(CloseableHttpResponse httpResponse = httpClient.execute(get)) {
				response = EntityUtils.toString(httpResponse.getEntity());
			} catch (IOException e) {
				e.printStackTrace();
			}

			@SuppressWarnings("rawtypes")
			ArrayList<CustomNameValuePair<String, CustomNameValuePair>> a = Utility.evaluateExpression(response);

			ArrayList<Trade> tradeHistoryDatas = new ArrayList<>();

			Trade current = new Trade();

			for(CustomNameValuePair<?, ?> each : a) {

				if(each.getName().equals("globalTradeID"))
					current.globalTradeID = Long.parseLong(each.getValue().toString());
				else if(each.getName().equals("tradeID"))
					current.tradeID = Long.parseLong(each.getValue().toString());
				else if(each.getName().equals("date"))
					current.date = each.getValue().toString();
				else if(each.getName().equals("type"))
					current.type = each.getValue().toString();
				else if(each.getName().equals("rate"))
					current.rate = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("amount"))
					current.amount = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("total")) {
					current.total = Double.parseDouble(each.getValue().toString());
					tradeHistoryDatas.add(current);
					current = new Trade();
				}
			}

			return tradeHistoryDatas.toArray(new Trade[tradeHistoryDatas.size()]);

		}

		/**
		 * @param unixStartDate Start date in UNIX timestamp
		 * @param unixEndDate End date in UNIX timestamp
		 * @param currencyPair Pair of cryptocurrencies (e.g. BTC_ETH)
		 * @return Past trades for a given market, up to 50,000 trades in a range specified in UNIX timestamps
		 */
		public static Trade[] tradeHistory(long unixStartDate, long unixEndDate, String currencyPair) {

			HttpGet get = new HttpGet(PUBLIC_URL + "?command=returnTradeHistory&currencyPair=" + currencyPair
					+ "&start=" + unixStartDate + "&end=" + unixEndDate);
			String response = null;

			try(CloseableHttpResponse httpResponse = httpClient.execute(get)) {
				response = EntityUtils.toString(httpResponse.getEntity());
			} catch (IOException e) {
				e.printStackTrace();
			}

			@SuppressWarnings("rawtypes")
			ArrayList<CustomNameValuePair<String, CustomNameValuePair>> a = Utility.evaluateExpression(response);

			ArrayList<Trade> tradeHistoryDatas = new ArrayList<>();

			Trade current = new Trade();

			for(CustomNameValuePair<?, ?> each : a) {

				if(each.getName().equals("globalTradeID"))
					current.globalTradeID = Long.parseLong(each.getValue().toString());
				else if(each.getName().equals("tradeID"))
					current.tradeID = Long.parseLong(each.getValue().toString());
				else if(each.getName().equals("date"))
					current.date = each.getValue().toString();
				else if(each.getName().equals("type"))
					current.type = each.getValue().toString();
				else if(each.getName().equals("rate"))
					current.rate = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("amount"))
					current.amount = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("total")) {
					current.total = Double.parseDouble(each.getValue().toString());
					tradeHistoryDatas.add(current);
					current = new Trade();
				}
			}

			return tradeHistoryDatas.toArray(new Trade[tradeHistoryDatas.size()]);

		}

		/**
		 * @param unixStartDate Start date in UNIX timestamp
		 * @param unixEndDate End date in UNIX timestamp
		 * @param currencyPair Pair of cryptocurrencies (e.g. BTC_ETH)
		 * @param period Candlestick period in seconds; valid values are 300, 900, 1800, 7200, 14400, and 86400
		 * @return Candlestick chart data for the specified date range for the data returned in UNIX timestamps
		 */
		public static Chart[] chartData(long unixStartDate, long unixEndDate, String currencyPair, int period) {

			HttpGet get = new HttpGet(PUBLIC_URL + "?command=returnChartData&currencyPair=" + currencyPair
					+ "&start=" + unixStartDate + "&end=" + unixEndDate + "&period=" + period);
			String response = null;

			try(CloseableHttpResponse httpResponse = httpClient.execute(get)) {
				response = EntityUtils.toString(httpResponse.getEntity());
			} catch (IOException e) {
				e.printStackTrace();
			}

			@SuppressWarnings("rawtypes")
			ArrayList<CustomNameValuePair<String, CustomNameValuePair>> a = Utility.evaluateExpression(response);

			ArrayList<Chart> chartDatas = new ArrayList<>();

			Chart current = new Chart();

			for(CustomNameValuePair<?, ?> each : a) {

				if(each.getName().equals("date"))
					current.date = Long.parseLong(each.getValue().toString());
				else if(each.getName().equals("high"))
					current.high = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("low"))
					current.low = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("open"))
					current.open = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("close"))
					current.close = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("volume"))
					current.volume = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("quoteVolume"))
					current.quoteVolume = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("weightedAverage")) {
					current.weightedAverage = Double.parseDouble(each.getValue().toString());
					chartDatas.add(current);
					current = new Chart();
				}
			}

			return chartDatas.toArray(new Chart[chartDatas.size()]);

		}

		/**
		 * @return Information on all currencies
		 */
		public static Currency[] currencies() {

			HttpGet get = new HttpGet(PUBLIC_URL + "?command=returnCurrencies");
			String response = null;

			try(CloseableHttpResponse httpResponse = httpClient.execute(get)) {
				response = EntityUtils.toString(httpResponse.getEntity());
			} catch (IOException e) {
				e.printStackTrace();
			}

			@SuppressWarnings("rawtypes")
			ArrayList<CustomNameValuePair<String, CustomNameValuePair>> a = Utility.evaluateExpression(response);

			ArrayList<Currency> currencyDatas = new ArrayList<>();

			Currency current = new Currency();

			for(CustomNameValuePair<?, ?> each : a) {

				if(each.getName().equals("id"))
					current.id = Integer.parseInt(each.getValue().toString());
				else if(each.getName().equals("name"))
					current.name = each.getValue().toString();
				else if(each.getName().equals("txFee"))
					current.txFee = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("minConf"))
					current.minConf = Integer.parseInt(each.getValue().toString());
				else if(each.getName().equals("depositAddress"))
					current.depositAddress = each.getValue().toString();
				else if(each.getName().equals("disabled"))
					current.disabled = Boolean.parseBoolean(each.getValue().toString());
				else if(each.getName().equals("delisted"))
					current.delisted = Boolean.parseBoolean(each.getValue().toString());
				else if(each.getName().equals("frozen")) {
					current.frozen = Boolean.parseBoolean(each.getValue().toString());
					currencyDatas.add(current);
					current = new Currency();
				}
				else
					current.currency = each.getName().toString();
			}

			return currencyDatas.toArray(new Currency[currencyDatas.size()]);

		}

		/**
		 * @param currency Cryptocurrency name in symbol (e.g. BTC)
		 * @return List of loan offers and demands for a given currency
		 */
		public static LoanOrder loanOrders(String currency) {

			HttpGet get = new HttpGet(PUBLIC_URL + "?command=returnLoanOrders&currency=" + currency);
			String response = null;

			try(CloseableHttpResponse httpResponse = httpClient.execute(get)) {
				response = EntityUtils.toString(httpResponse.getEntity());
			} catch (IOException e) {
				e.printStackTrace();
			}

			@SuppressWarnings("rawtypes")
			ArrayList<CustomNameValuePair<String, CustomNameValuePair>> a = Utility.evaluateExpression(response);

			ArrayList<LoanOrder.Order> offers = new ArrayList<>();
			ArrayList<LoanOrder.Order> demands = new ArrayList<>();

			boolean offersDoneParsing = false;

			LoanOrder.Order current = new LoanOrder.Order();

			for(CustomNameValuePair<?, ?> each : a) {

				if(each.getName().equals("offers"))
					offersDoneParsing = false;
				else if(each.getName().equals("demands"))
					offersDoneParsing = true;
				else if(each.getName().equals("rate"))
					current.rate = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("amount"))
					current.amount = Double.parseDouble(each.getValue().toString());
				else if(each.getName().equals("rangeMin"))
					current.rangeMin = Integer.parseInt(each.getValue().toString());
				else if(each.getName().equals("rangeMax")) {
					current.rangeMax = Integer.parseInt(each.getValue().toString());
					if(!offersDoneParsing)
						offers.add(current);
					else
						demands.add(current);
					current = new LoanOrder.Order();
				}
			}

			return new LoanOrder(currency, offers, demands);

		}

	}

	/**
	 * Wrapper for Poloniex trading API. According to Poloniex API documentation, there is a default limit of 6 calls per second
	 */
	public static class TradeApi {

		private final String secretKey;
		private final String key;

		/**
		 * Uses the HMAC-SHA512 method to sign the query's POST data
		 * @param secretKey Your secret key provided by Poloniex
		 * @param queryArgs Your query's POST data
		 * @return Signed query's POST data
		 */
		public static String sign(String secretKey, String queryArgs) {

			String sign = null;

			try {
				Mac shaMac = Mac.getInstance("HmacSHA512");
				SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA512");
				shaMac.init(keySpec);
				final byte[] macData = shaMac.doFinal(queryArgs.getBytes());
				sign = Hex.encodeHexString(macData);
			} catch (NoSuchAlgorithmException nsae) {
				nsae.printStackTrace();
			} catch (InvalidKeyException ike) {
				ike.printStackTrace();
			}

			return sign;

		}

		/**
		 * Reads API keys from res/api_keys.txt
		 */
		public TradeApi() {

			String secretKey = null;
			String key = null;

			try(BufferedReader br = new BufferedReader(new InputStreamReader(Poloniex.class.getResourceAsStream("/api_keys.txt")))) {

				for(String currentLine = br.readLine(); currentLine != null; currentLine = br.readLine()) {

					if(currentLine.startsWith("poloniex_api_key:")) {
						int startIndex = currentLine.indexOf(":");
						key = currentLine.substring(startIndex + 1, currentLine.length());
					} else if(currentLine.startsWith("poloniex_secret_key:")) {
						int startIndex = currentLine.indexOf(":");
						secretKey = currentLine.substring(startIndex + 1, currentLine.length());
					}

				}


			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			this.secretKey = secretKey;
			this.key = key;

		}

		/**
		 * @param secretKey Your secret key provided by Poloniex
		 * @param key Your API key
		 */
		public TradeApi(String secretKey, String key) {
			this.secretKey = secretKey;
			this.key = key;
		}

		/**
		 * Returns all of your available balances
		 */
		public void balances() {

			String nonce = String.valueOf(System.currentTimeMillis());
			String queryArgs = "command=returnBalances&nonce=" + nonce;

			String sign = sign(secretKey, queryArgs);

			HttpPost post = new HttpPost(TRADING_URL);
			try {
				post.addHeader("Key", key);
				post.addHeader("Sign", sign);
				post.setEntity(new ByteArrayEntity(queryArgs.getBytes("UTF-8")));

				List<NameValuePair> params = new ArrayList<>();
				params.add(new BasicNameValuePair("command", "returnBalances"));
				params.add(new BasicNameValuePair("nonce", nonce));
				post.setEntity(new UrlEncodedFormEntity(params));
			} catch (UnsupportedEncodingException uee) {
				uee.printStackTrace();
			}

			String response = null;

			try(CloseableHttpResponse httpResponse = httpClient.execute(post)) {
				response = EntityUtils.toString(httpResponse.getEntity());
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}

			Utility.evaluateExpression(response);

		}

		/**
		 * Returns all of your balances, including available balance, balance on orders, and the estimated BTC value of your balance. By default, this call is limited to your exchange account
		 */
		public void completeBalances() {
			// TODO
		}

		/**
		 * Returns all of your deposit addresses
		 */
		public void depositAddresses() {
			// TODO
		}

		/**
		 * Generates a new deposit address for the currency specified by the currency parameter
		 * @param currency Currency for which to generate a new deposit (e.g. BTC)
		 */
		public void generateNewAddress(String currency) {
			// TODO
		}

		/**
		 * Returns your deposit and withdrawal history within a range, specified by the "start" and "end" parameters
		 * @param unixStartDate Start date in UNIX timestamp
		 * @param unixEndDate End date in UNIX timestamp
		 */
		public void depositWithdrawals(long unixStartDate, long unixEndDate) {
			// TODO
		}

		/**
		 * Returns your open orders for a given market, specified by the "currencyPair" parameter
		 * @param currencyPair Pair of cryptocurrencies (e.g. BTC_ETH)
		 */
		public void openOrders(String currencyPair) {
			// TODO
		}

		/**
		 * Returns your open orders for all markets
		 */
		public void openOrdersAll() {
			// TODO
		}

		/**
		 * Returns your trade history for a given market, specified by the "currencyPair" parameter
		 * @param currencyPair Pair of cryptocurrencies (e.g. BTC_ETH)
		 */
		public void tradeHistory(String currencyPair) {
			// TODO
		}

		/**
		 * Returns your trade history for all markets
		 */
		public void tradeHistoryAll() {
			// TODO
		}

		/**
		 * Returns all trades involving a given order, specified by the "orderNumber" parameter
		 * @param orderNumber Order number
		 */
		public void orderTrades(String orderNumber) {
			// TODO
		}

		/**
		 * Places a limit buy order in a given market. Required parameters are "currencyPair", "rate", and "amount". If successful, the method will return the order number
		 * @param currencyPair Pair of cryptocurrencies (e.g. BTC_ETH)
		 * @param rate Rate at which to buy
		 * @param amount Amount to buy
		 * @param options You may optionally set this to "fillOrKill", "immediateOrCancel", "postOnly"; otherwise leave as null. A fill-or-kill order will either fill in its entirety or be completely aborted. An immediate-or-cancel order can be partially or completely filled, but any portion of the order that cannot be filled immediately will be canceled rather than left on the order book. A post-only order will only be placed if no portion of it fills immediately; this guarantees you will never pay the taker fee on any part of the order that fills
		 */
		public void buy(String currencyPair, double rate, double amount, String options) {
			// TODO
		}

		/**
		 * Places a sell order in a given market. Required parameters are "currencyPair", "rate", and "amount"
		 * @param currencyPair Pair of cryptocurrencies (e.g. BTC_ETH)
		 * @param rate Rate at which to sell
		 * @param amount Amount to sell
		 */
		public void sell(String currencyPair, double rate, double amount) {
			// TODO
		}

		/**
		 * Cancels an order you have placed in a given market. Required parameter is "orderNumber"
		 * @param orderNumber Order number
		 * @return If successful, the method will return true
		 */
		public boolean cancelOrder(String orderNumber) {
			// TODO
			return false;
		}

		/**
		 * Cancels an order and places a new one of the same type in a single atomic transaction, meaning either both operations will succeed or both will fail. Required parameters are "orderNumber" and "rate"; you may optionally specify "amount" if you wish to change the amount of the new order
		 * @param orderNumber Order number
		 * @param rate Rate
		 * @param amount Optional parameter. Default is null
		 */
		public void moveOrder(String orderNumber, double rate, Double amount) {
			// TODO
		}

		/**
		 * Immediately places a withdrawal for a given currency, with no email confirmation. In order to use this method, the withdrawal privilege must be enabled for your API key. Required parameters are "currency", "amount", and "address"
		 * @param currency Currency to withdraw (e.g. BTC)
		 * @param amount Amount to withdraw
		 * @param address Address into which to withdraw
		 */
		public void withdraw(String currency, double amount, String address) {
			// TODO
		}

		/**
		 * If you are enrolled in the maker-taker fee schedule, returns your current trading fees and trailing 30-day volume in BTC. This information is updated once every 24 hours
		 */
		public void feeInfo() {
			// TODO
		}

		/**
		 * Returns your balances sorted by account. You may optionally specify the "account" parameter if you wish to fetch only the balances of one account. Please note that balances in your margin account may not be accessible if you have any open margin positions or orders
		 * @param account Optional parameter. Default is null
		 */
		public void availableAccountBalances(String account) {
			// TODO
		}

		/**
		 * Returns your current tradable balances for each currency in each market for which margin trading is enabled. Please note that these balances may vary continually with market conditions
		 */
		public void tradableBalances() {
			// TODO
		}

		/**
		 * Transfers funds from one account to another (e.g. from your exchange account to your margin account). Required parameters are "currency", "amount", "fromAccount", and "toAccount"
		 * @param currency Currency to transfer (e.g. BTC)
		 * @param amount Amount to transfer
		 * @param fromAccount Account from which to transfer
		 * @param toAccount Account into which to transfer
		 */
		public void transferBalance(String currency, double amount, String fromAccount, String toAccount) {
			// TODO
		}

		/**
		 * Returns a summary of your entire margin account. This is the same information you will find in the Margin Account section of the Margin Trading page, under the Markets list
		 */
		public void marginAccountSummary() {
			// TODO
		}

		/**
		 * Places a margin buy order in a given market. Required parameters are "currencyPair", "rate", and "amount". You may optionally specify a maximum lending rate using the "lendingRate" parameter
		 * @param currencyPair Pair of cryptocurrencies (e.g. BTC_ETH)
		 * @param rate Rate at which to buy
		 * @param amount Amount to buy
		 * @param lendingRate Optional parameter. Default is null
		 */
		public void marginBuy(String currencyPair, double rate, double amount, Double lendingRate) {
			// TODO
		}

		/**
		 * Places a margin sell order in a given market. Required parameters are "currencyPair", "rate", and "amount"
		 * @param currencyPair Pair of cryptocurrencies (e.g. BTC_ETH)
		 * @param rate Rate at which to sell
		 * @param amount Amount to sell
		 */
		public void marginSell(String currencyPair, double rate, double amount) {
			// TODO
		}

		/**
		 * Returns information about your margin position in a given market, specified by the "currencyPair" parameter
		 * @param currencyPair Pair of cryptocurrencies (e.g. BTC_ETH)
		 */
		public void getMarginPosition(String currencyPair) {
			// TODO
		}

		/**
		 * Fetch all of your margin positions at once
		 */
		public void getMarginPositionAll() {
			// TODO
		}

		/**
		 * Closes your margin position in a given market (specified by the "currencyPair" parameter) using a market order
		 * @param currencyPair Pair of cryptocurrencies (e.g. BTC_ETH)
		 */
		public void closeMarginPosition(String currencyPair) {
			// TODO
		}

		/**
		 * Creates a loan offer for a given currency. Required parameters are "currency", "amount", "duration", "autoRenew" (0 or 1), and "lendingRate"
		 * @param currency Currency for which to create a loan offer
		 * @param amount Amount to offer
		 * @param duration Duration of loan
		 * @param autoRenew Automatically renew
		 * @param lendingRate Rate at which to lend
		 */
		public void createLoanOffer(String currency, double amount, double duration, boolean autoRenew, double lendingRate) {
			// TODO
		}

		/**
		 * Cancels a loan offer specified by the "orderNumber" POST parameter
		 * @param orderNumber Order number
		 */
		public void cancelLoanOffer(String orderNumber) {
			// TODO
		}

		/**
		 * Returns your open loan offers for each currency
		 */
		public void openLoanOffers() {
			// TODO
		}

		/**
		 * Returns your active loans for each currency
		 */
		public void activeLoans() {
			// TODO
		}

		/**
		 * Returns your lending history within a time range specified by the "start" and "end" parameters as UNIX timestamps. "limit" may also be specified to limit the number of rows returned
		 * @param unixStartDate Start date in UNIX timestamp
		 * @param unixEndDate End date in UNIX timestamp
		 * @param limit Optional parameter. Default is null
		 */
		public void lendingHistory(long unixStartDate, long unixEndDate, Integer limit) {
			// TODO
		}

		/**
		 * Toggles the autoRenew setting on an active loan, specified by the "orderNumber" parameter
		 * @param orderNumber Order number
		 * @return If successful, "message" will indicate the new autoRenew setting
		 */
		public boolean autoRenew(String orderNumber) {
			// TODO
			return false;
		}

	}

}
