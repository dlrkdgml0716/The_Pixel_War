package com.thepixelwar.dto;

// record를 사용하면 x(), y(), color() 메서드를 자동으로 만들어줍니다.
public record PixelRequest(double lat, double lng, String color, String userId) {
}