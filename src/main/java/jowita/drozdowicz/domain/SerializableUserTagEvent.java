package jowita.drozdowicz.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;

public class SerializableUserTagEvent implements Serializable {

    public Long time;
    public String cookie;

    public String country;
    public String device;
    public String action;
    public String origin;

    public int productId;
    public String brandId;
    public String categoryId;
    public int price;

    public SerializableUserTagEvent() {

    }

    public SerializableUserTagEvent(UserTagEvent event) {
        this.time = event.getTime().toEpochMilli();
        this.cookie = event.getCookie();
        this.country = event.getCountry();
        this.device = event.getDevice().toString();
        this.action = event.getAction().toString();
        this.origin = event.getOrigin();
        this.productId = event.getProductInfo().getProductId();
        this.brandId = event.getProductInfo().getBrandId();
        this.categoryId = event.getProductInfo().getCategoryId();
        this.price = event.getProductInfo().getPrice();
    }

    public UserTagEvent toUserTagEvent() {
        return new UserTagEvent(
                Instant.ofEpochMilli(time),
                cookie,
                country,
                Device.valueOf(device),
                Action.valueOf(action),
                origin,
                new Product(productId, brandId, categoryId, price)
        );
    }

}
