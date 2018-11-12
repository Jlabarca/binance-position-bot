package com.jlabarca.positionbot;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.AssetBalance;
import org.springframework.beans.factory.annotation.Value;

public class PositionBot {

    private static Logger log = LoggerFactory.getLogger(PositionBot.class);
    private ConcurrentLinkedQueue<Position> positions;
    private BinanceApiRestClient client;
    @Value("${DEBUG}")
    private boolean debug = false;


    /*
      Bot para tomar posicion en una o mas monedas con precio de compra, venta y stoploss
     */
    public PositionBot(String key, String secret) {
        this.client = BinanceApiClientFactory.getInstance(key, secret).getClient();
        clear();
    }

    private float getCurrencyQty(String currency){
        String free = client.getAccount().getAssetBalance(currency).getFree();
        try {
            return Float.parseFloat(free);
        }catch (Exception e) {
            log.info("Float parse failed for "+free);
            return -1;
        }
    }

    private void getSymbolOrders(String symbol){
        OrderBook orderBook = client.getOrderBook(symbol, 10);
        List<OrderBookEntry> asks = orderBook.getAsks();
        OrderBookEntry firstAskEntry = asks.get(0);
        log.info(firstAskEntry.getPrice() + " / " + firstAskEntry.getQty());
    }
    void tick() {
        log.debug("TICK INIT");
        try {
            client.ping();
            ConcurrentLinkedQueue<Position> runningPositions = positions;/*.stream().
                    filter(
                            t -> t.getState() == Position.PositionState.NOTTAKEN
                                    || t.getState() == Position.PositionState.TAKEN
                                    || t.getState() == Position.PositionState.SOLDBYOTHER
                    ).
                    collect(Collectors.toList());*/
            log.info("runningPositions: "+runningPositions.size() +" debug: "+debug);
            for (Iterator<Position> iterator = runningPositions.iterator(); iterator.hasNext();) {
                Position position = iterator.next();
                //log.warn("TICK position "+position.getSymbol()+" "+position.getId());
                float currentPrice = position.getCurrentPrice();
                //ensureTaken
                log.info(position.getSymbol()+": "+position.getBuyPrice() +" state: "+position.getState() +" currentPrice: "+position.getCurrentPrice());
                //position.ensureTaken();
                position.addHistory(currentPrice);

                //if taken check if still have it, if not taken check base qty
                if(position.getState().equals(Position.PositionState.TAKEN)){
                    float free = getCurrencyQty(position.getTradeCurrency());
                    log.info("TAKEN "+position.getTradeCurrency()+" AVAILABLE: "+free+" > "+position.getQuantity());
                    if(position.getQuantity() != null && free < position.getQuantity()){
                        log.info("SOLD BY OTHER");
                        position.setState(Position.PositionState.SOLDBYOTHER);
                        continue;
                    }
                } /*else if(position.getState().equals(Position.PositionState.NOTTAKEN)){
                    float free = Float.parseFloat(client.getAccount().getAssetBalance(position.getBaseCurrency()).getFree());
                    log.info("NOT TAKEN "+position.getTradeCurrency()+" AVAILABLE BASE: "+free+" > "+position.getQuantity());
                    if(free < position.getQuantity()) {
                        log.info("CANT BUY");
                        position.setState(Position.PositionState.CANTBUY);
                    }
                }*/

                //if not taken and price under buyPrice, buy, end tick
                if(position.getState() == Position.PositionState.NOTTAKEN &&
                        position.getBuyPrice() != null &&
                        position.getBuyPrice() > currentPrice) {
                    log.info("not taken and price under buyPrice, buy, end tick");
                    if(!debug)
                        position.buy();
                    continue;
                }

                //if taken and price over sellPrice, takeProfit, end tick
                if(position.getState() == Position.PositionState.TAKEN &&
                        position.getSellPrice() != null &&
                        position.getSellPrice() < currentPrice) {
                    log.info("taken and price over sellPrice, takeProfit, end tick");
                    if(!debug)
                        position.takeProfit();
                    continue;
                }

                //if taken and price under stopLoss, sell, end tick
                if(position.getState() == Position.PositionState.TAKEN &&
                        position.getStopLossPrice() != null &&
                        position.getStopLossPrice() > currentPrice) {
                    log.info("taken and price under stopLoss, stopLoss, end tick");
                    if(!debug)
                        position.stopLoss();
                    continue;
                }

                log.info("DID NOTHING");
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void panicSellForCondition(double lastPrice, double lastKnownTradingBalance, boolean condition) {
        if (condition) {
            log.info("panicSellForCondition");
            //client.panicSell(lastKnownTradingBalance, lastPrice);
            clear();
        }
    }

    private void clear() {
    }

    List<AssetBalance> getBalances() {
        return client.getAccount().getBalances();
    }

    public ConcurrentLinkedQueue<Position> getPositions() {
        return positions;
    }

    public void setPositions(ConcurrentLinkedQueue<Position> positions) {
        this.positions = positions;
    }


}
