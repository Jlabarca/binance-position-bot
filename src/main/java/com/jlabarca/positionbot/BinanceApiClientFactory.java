package com.jlabarca.positionbot;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.impl.BinanceApiAsyncRestClientImpl;
import com.binance.api.client.impl.BinanceApiRestClientImpl;
import com.binance.api.client.impl.BinanceApiWebSocketClientImpl;

/**
 * @author JLabarca
 */
public class BinanceApiClientFactory {
    private String apiKey;
    private String secret;
    private static BinanceApiClientFactory binanceApiClientFactory;
    private static BinanceApiRestClientImpl client;

    private BinanceApiClientFactory(String apiKey, String secret) {
        this.apiKey = apiKey;
        this.secret = secret;
    }
    public static BinanceApiClientFactory getInstance(String apiKey, String secret) {
        if (binanceApiClientFactory == null) {
            binanceApiClientFactory = newInstance(apiKey, secret);
            client = new BinanceApiRestClientImpl(apiKey, secret);
        }
        return binanceApiClientFactory;
    }

    public static BinanceApiClientFactory getInstance() {
        if (binanceApiClientFactory != null)
            return binanceApiClientFactory;
        else
            return null;
    }

    public static BinanceApiClientFactory newInstance(String apiKey, String secret) {
        binanceApiClientFactory = new BinanceApiClientFactory(apiKey, secret);
        return binanceApiClientFactory;
    }

    public static BinanceApiClientFactory newInstance() {
        return new BinanceApiClientFactory((String)null, (String)null);
    }

    public BinanceApiRestClient newRestClient() {
        return new BinanceApiRestClientImpl(this.apiKey, this.secret);
    }

    public BinanceApiAsyncRestClient newAsyncRestClient() {
        return new BinanceApiAsyncRestClientImpl(this.apiKey, this.secret);
    }

    public BinanceApiWebSocketClient newWebSocketClient() {
        return new BinanceApiWebSocketClientImpl();
    }

    public BinanceApiRestClientImpl getClient() {
        return client;
    }

    public void setClient(BinanceApiRestClientImpl client) {
        this.client = client;
    }
}
