package com.thelads.core.client.capes.handler;

public record CosmeticaData(CapeData cape) {
    public CapeData getCape() {
        return cape;
    }

    public record CapeData(String origin, String image, int extraInfo) {
        public String getOrigin() {
            return origin;
        }

        public String getImage() {
            return image;
        }

        public int getExtraInfo() {
            return extraInfo;
        }

        public boolean isAnimated() {
            return extraInfo > 0;
        }
    }
}
