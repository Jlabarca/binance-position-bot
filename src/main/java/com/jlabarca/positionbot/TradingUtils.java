package com.jlabarca.positionbot;

import java.math.BigDecimal;
import java.util.List;

import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.TickerStatistics;

public class TradingUtils {
    private static Logger log = LoggerFactory.getLogger(TradingUtils.class);

    public BinanceApiRestClient getClient() {
        return client;
    }

    public void setClient(BinanceApiRestClient client) {
        this.client = client;
    }

    private BinanceApiRestClient client;
    private String baseCurrency;
    private String tradeCurrency;
    private String symbol;
    private ExchangeInfo exchangeInfo;
    public TradingUtils(String baseCurrency, String tradeCurrency) {
        this.baseCurrency = baseCurrency;
        this.tradeCurrency = tradeCurrency;
        this.client = BinanceApiClientFactory.getInstance().getClient();
        symbol = tradeCurrency + baseCurrency;
        exchangeInfo = client.getExchangeInfo();
    }

    // The bid price represents the maximum price that a buyer is willing to pay for a security.
    // The ask price represents the minimum price that a seller is willing to receive.
    public OrderBook getOrderBook() {
        return client.getOrderBook(symbol, 5);
    }

    public AssetBalance getBaseBalance() {
        return client.getAccount().getAssetBalance(baseCurrency);
    }

    public AssetBalance getTradingBalance() {
        return client.getAccount().getAssetBalance(tradeCurrency);
    }

    public float assetBalanceTofloat(AssetBalance balance) {
        return  Float.parseFloat(balance.getFree()) + Float.parseFloat((balance.getLocked()));
    }

    public float getAllTradingBalance() {
        AssetBalance tradingBalance = getTradingBalance();
        return assetBalanceTofloat(tradingBalance);
    }

    public boolean tradingBalanceAvailable(AssetBalance tradingBalance) {
        return assetBalanceTofloat(tradingBalance) > 1;
    }

    public List<AssetBalance> getBalances() {
        return client.getAccount().getBalances();
    }

    public List<Order> getOpenOrders() {
        OrderRequest request = new OrderRequest(symbol);
        return client.getOpenOrders(request);
    }

    public void cancelAllOrders() {
        getOpenOrders().forEach(order -> client.cancelOrder(new CancelOrderRequest(symbol, order.getOrderId())));
    }

    // * GTC (Good-Til-Canceled) orders are effective until they are executed or canceled.
    // * IOC (Immediate or Cancel) orders fills all or part of an order immediately and cancels the remaining part of the order.
    public NewOrderResponse buy(float quantity, float price) {
        String priceString = String.format("%.8f", price).replace(",", ".");
        String qString = String.format("%.8f", quantity).replace(",", ".");
        log.info(String.format("Buying %d for %s\n", quantity, priceString));
        NewOrder order = new NewOrder(symbol, OrderSide.BUY, OrderType.LIMIT, TimeInForce.GTC, qString, priceString);
        return client.newOrder(order);
    }

    public void sell(float quantity, float price) {
        String priceString = String.format("%.8f", price).replace(",", ".");
        String qString = String.format("%.8f", quantity).replace(",", ".");
        log.info(String.format("Selling %d for %s\n", quantity, priceString));
        NewOrder order = new NewOrder(symbol, OrderSide.SELL, OrderType.LIMIT, TimeInForce.GTC, qString, priceString);
        client.newOrder(order);
    }


    public void buyMarket(float quantity) {
        String qString = prepareQuantity(quantity);
        log.info("Buying to MARKET with quantity " + qString);
        NewOrder order = new NewOrder(symbol, OrderSide.BUY, OrderType.MARKET, null, qString);
        client.newOrder(order);
    }

    public void sellMarket(Float quantity) {
        if (quantity > 0) {
            String qString = prepareQuantity(quantity);
            log.info("Selling to MARKET with quantity " + qString);
            NewOrder order = new NewOrder(symbol, OrderSide.SELL, OrderType.MARKET, null, qString);
            client.newOrder(order);
        } else {
            log.info("not executing - 0 quantity sell");
        }
    }

    private String prepareQuantity(Float quantity){
        SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(symbol);
        SymbolFilter priceFilter = symbolInfo.getSymbolFilter(FilterType.LOT_SIZE);
        int decimals = getDecimals(priceFilter.getStepSize());
        String qString = quantity.toString().replace(",", ".");
        log.info("Preparing quantity " + quantity +" "+decimals +" "+priceFilter.getStepSize());

        if(decimals == 0)
            qString = quantity.intValue()+"";
        else
            qString = new BigDecimal(quantity).setScale(decimals,BigDecimal.ROUND_DOWN).toString();
        return qString;
    }
    private int getDecimals(String tick){
        for (int i = 0 ; i<tick.length() ; i++)
            if (tick.charAt(i) == '1') {
                if(i>0)
                    return i-1;
                else
                    return 0;
            }
        return 0;
    }

    public Order getOrder(long orderId) {
        return client.getOrderStatus(new OrderStatusRequest(symbol, orderId));
    }

    public float lastPrice() {
        return Float.parseFloat(client.get24HrPriceStatistics(symbol).getLastPrice());
    }

    public void cancelOrder(long orderId) {
        log.info("Cancelling order " + orderId);
        client.cancelOrder(new CancelOrderRequest(symbol, orderId));
    }

    public void panicSell(float lastKnownAmount, float lastKnownPrice) {
        log.error("!!!! PANIC SELL !!!!");
        log.warn(String.format("Probably selling %.8f for %.8f", lastKnownAmount, lastKnownPrice));
        cancelAllOrders();
        sellMarket(Float.parseFloat(getTradingBalance().getFree()));
    }

    public float getLastPrice(){
        TickerStatistics tickerStatistics = client.get24HrPriceStatistics(symbol);
        return Float.parseFloat(tickerStatistics.getLastPrice());
    }

    private float getPercentage(int percentage, float balance, float buyPrice) {
        return buyPrice/getPercentage(percentage,balance);
    }
    private float getPercentage(int percentage, float balance) {
        return (percentage*balance)/100;
    }

    public Float getFreeBalanceOf(String asset) {
        AssetBalance f = getBalances().stream().filter(assetBalance -> assetBalance.getAsset().equalsIgnoreCase(asset)).findFirst().get();
        if (f != null)
            return Float.parseFloat(f.getFree());
        return null;
    }
}