package com.jlabarca.positionbot;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.UUID;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.FilterType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;

/**
 * @author JLabarca
 */
@Data
@Log
@NoArgsConstructor
//@RequiredArgsConstructor
@AllArgsConstructor
public class Position {
    @JsonIgnore
    private TradingUtils tradingUtils;
    private String id;
    private String tradeCurrency;
    private String baseCurrency;
    private Float quantity;
    private Float percentage;
    private Float buyPrice;
    private Float sellPrice;
    private Float stopLossPrice;
    private LocalDateTime positionSetTime;
    private LocalDateTime sellTime;
    private LinkedList<Float> history;
    private PositionState state;
    private Float realBuyPrice = null;
    private Float realSellPrice = null;
    private boolean percentageOnBuy = true;


    public Position(String tradeCurrency, String baseCurrency, Float quantity, Float buyPrice, Float sellPrice, Float stopLossPrice,Float percentage, boolean percentageOnBuy, PositionState initialState) {
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.stopLossPrice = stopLossPrice;
        this.tradeCurrency = tradeCurrency;
        this.baseCurrency = baseCurrency;
        this.state = initialState;
        this.history = new LinkedList<>();
        this.quantity = quantity;
        this.percentage = percentage;
        this.percentageOnBuy = percentageOnBuy;
        init();
    }

    public void init() {
        if(id == null)
            id = "pos"+UUID.randomUUID().toString().split("-")[4];
        log.warning("INIT "+id);
        this.tradingUtils  = new TradingUtils(baseCurrency,tradeCurrency);
        if(percentage!=null && !percentageOnBuy) {
            log.warning(baseCurrency+": "+tradingUtils.getFreeBalanceOf(baseCurrency));
            this.quantity = ((tradingUtils.getFreeBalanceOf(baseCurrency)*percentage)/100)/buyPrice;
        } else if (percentageOnBuy) {
            this.quantity = null;
        } else {
            String stepSize = tradingUtils.getClient().getExchangeInfo().getSymbols().stream().filter
                    (symbol -> symbol.getSymbol().equals(getSymbol())).findFirst().get().getFilters().stream().filter
                    (filter -> filter.getFilterType()== FilterType.LOT_SIZE).findFirst().get().getStepSize();

            log.info(""+stepSize);
            log.info(""+(stepSize.split("1")[0].length() - 2));
            String pattern = "#.";
            for (int i = 0; i < (stepSize.split("1")[0].length() - 2); i++) {
                pattern+="#";
            }

            DecimalFormat df = new DecimalFormat(pattern);
            df.setRoundingMode(RoundingMode.FLOOR);
            this.quantity = Float.parseFloat(df.format(this.quantity).replace(",","."));
            log.info(""+this.quantity);
        }
    }

    public void buy() {
        if(percentageOnBuy && percentage != null) {
            this.quantity = ((tradingUtils.getFreeBalanceOf(baseCurrency)*percentage)/100)/buyPrice;
        }
        float realBuyPrice = buyPrice;
        if(buyPrice > getCurrentPrice())
            realBuyPrice = getCurrentPrice();
        this.realBuyPrice = realBuyPrice;
        //getTradingUtils().buy(quantity, realBuyPrice);
        getTradingUtils().buyMarket(quantity);
        this.state = PositionState.TAKEN;
    }

    public void takeProfit() {
        float realSellPrice = sellPrice;
        if(sellPrice > getCurrentPrice())
            realSellPrice = getCurrentPrice();
        this.realSellPrice = realSellPrice;
        //getTradingUtils().sell(quantity,realSellPrice);
        getTradingUtils().sellMarket(quantity);
        this.state = PositionState.PROFITTAKEN;
    }

    public void stopLoss() {
        float realSellPrice = sellPrice;
        if(stopLossPrice > getCurrentPrice())
            realSellPrice = getCurrentPrice();
        this.realSellPrice = realSellPrice;
        //getTradingUtils().sell(quantity,realSellPrice);
        getTradingUtils().sellMarket(quantity);
        this.state = PositionState.STOPLOSS;
    }

    public void sell() {
        getTradingUtils().sellMarket(quantity);
        this.state = PositionState.PANICSELL;
    }

    public PositionState getState() {
        return state;
    }

    public void setState(PositionState state) {
        this.state = state;
    }
    /*
     Podria usar un metodo como este para tomar ordenes creadas en la pagina y esperar a que se cumplan para
     dejar la posicion como taken
     */
    public boolean ensureTaken(){
        boolean found = false;
        for  (Order order: getTradingUtils().getOpenOrders()) {
            //log.info(order.getSymbol()+" = "+getSymbol()+ " || "+(order.getSymbol().equalsIgnoreCase(getSymbol())));
            //log.info(Float.parseFloat(order.getPrice())+" = "+buyPrice+ " || "+(Float.parseFloat(order.getPrice()) == buyPrice));
            if(order.getSymbol().equalsIgnoreCase(getSymbol()) &&
                   Float.parseFloat(order.getPrice()) == buyPrice) {
                found = true;
                break;
            }
        }
        if(found)
            state = PositionState.TAKEN;
        else
            state = PositionState.NOTTAKEN;

        return found;
    }
    @JsonIgnore
    public String getSymbol(){
        return tradeCurrency+baseCurrency;
    }

    @JsonIgnore
    public float getCurrentPrice(){
        return getTradingUtils().getLastPrice();
    }

    public Float getRealBuyPrice() {
        return realBuyPrice;
    }

    public void setRealBuyPrice(Float realBuyPrice) {
        this.realBuyPrice = realBuyPrice;
    }

    public Float getRealSellPrice() {
        return realSellPrice;
    }

    public void setRealSellPrice(Float realSellPrice) {
        this.realSellPrice = realSellPrice;
    }

    /*
        NOTTAKEN: is not bought yet
        //BUYORDER: There is an order placed tu buy
        TAKEN: position was bought
        //TAKENWITHSTOPLOSS: position was bought and a stop loss order was placed
        CANCELED: canceled position
        PROFITTAKEN: the quantity was sold for taking profit
        STOPLOSSTAKEN: stoploss triggered
     */
    public enum PositionState {
        NOTTAKEN,TAKEN,PROFITTAKEN,STOPLOSS, SOLDBYOTHER, PANICSELL, PAUSED, CANTBUY
    }

    public void addHistory(float value){
        if (this.history.size() >= 50){
            promHistory();
        }
        this.history.add(value);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    private void promHistory(){
        LinkedList<Float> aux = new LinkedList<>();
        for (int i = 1; i < history.size()-1 ; i++) {
            if(i%2 == 0)
                aux.add((history.get(i)+history.get(i-1))/2);
        }
        history = aux;
    }

    public TradingUtils getTradingUtils() {
        if(tradingUtils == null)
            tradingUtils = new TradingUtils(baseCurrency,tradeCurrency);
        return tradingUtils;
    }

    public Float getPercentage() {
        return percentage;
    }

    public void setPercentage(Float percentage) {
        this.percentage = percentage;
    }

    public boolean getPercentageOnBuy() {
        return percentageOnBuy;
    }

    public void setPercentageOnBuy(boolean percentageOnBuy) {
        this.percentageOnBuy = percentageOnBuy;
    }

    public void setTradingUtils(TradingUtils tradingUtils) {
        this.tradingUtils = tradingUtils;
    }

    public String getTradeCurrency() {
        return tradeCurrency;
    }

    public void setTradeCurrency(String tradeCurrency) {
        this.tradeCurrency = tradeCurrency;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public void setQuantity(Float quantity) {
        this.quantity = quantity;
    }

    public Float getBuyPrice() {
        return buyPrice;
    }

    public void setBuyPrice(Float buyPrice) {
        this.buyPrice = buyPrice;
    }

    public Float getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(Float sellPrice) {
        this.sellPrice = sellPrice;
    }

    public Float getStopLossPrice() {
        return stopLossPrice;
    }

    public void setStopLossPrice(Float stopLossPrice) {
        this.stopLossPrice = stopLossPrice;
    }

    public LocalDateTime getPositionSetTime() {
        return positionSetTime;
    }

    public void setPositionSetTime(LocalDateTime positionSetTime) {
        this.positionSetTime = positionSetTime;
    }

    public LocalDateTime getSellTime() {
        return sellTime;
    }

    public void setSellTime(LocalDateTime sellTime) {
        this.sellTime = sellTime;
    }

    public LinkedList<Float> getHistory() {
        return history;
    }

    public void setHistory(LinkedList<Float> history) {
        this.history = history;
    }

    public boolean isPercentageOnBuy() {
        return percentageOnBuy;
    }

    public Float getQuantity() {
        return quantity;
    }
}
