package com.thelads.core.client.capes.handler;

public record MCMData(String cape_url, String animated_cape_url) {
    public String getCape_url() {
        return cape_url;
    }

    public String getAnimated_cape_url() {
        return animated_cape_url;
    }
}
