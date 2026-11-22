package com.ozerlabs.triterra.car;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** One stop on the car screen: what the driver sees and where navigation goes. */
public final class Branch {
    public final String name;
    public final String address;
    /** NaN when the stop has no usable coordinates. */
    public final double lat;
    public final double lng;

    public Branch(String name, String address, double lat, double lng) {
        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
    }

    public boolean hasLocation() {
        return !Double.isNaN(lat) && !Double.isNaN(lng);
    }

    /**
     * A geo: form documented for CarContext.ACTION_NAVIGATE: "geo:lat,lng", or a place-name search
     * "geo:0,0?q=..." when coordinates are missing. The host opens the default nav app (Waze / Google Maps).
     */
    public String geoUri() {
        if (hasLocation()) return "geo:" + lat + "," + lng;
        try {
            return "geo:0,0?q=" + URLEncoder.encode(address.isEmpty() ? name : name + " " + address, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e); // UTF-8 always exists
        }
    }
}
