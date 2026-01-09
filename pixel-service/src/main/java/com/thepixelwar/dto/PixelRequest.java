package com.thepixelwar.dto;

// record를 사용하면 x(), y(), color() 메서드를 자동으로 만들어줍니다.
public record PixelRequest(int x, int y, String color, String userId) {
}