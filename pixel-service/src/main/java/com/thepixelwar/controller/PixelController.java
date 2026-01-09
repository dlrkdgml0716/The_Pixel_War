package com.thepixelwar.controller;

import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.service.PixelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pixels")
@RequiredArgsConstructor // private final이 붙은 필드를 자동으로 조립해줍니다.
public class PixelController {

    // 1. 반드시 'final'이 붙어야 하고, 변수명은 소문자 pixelService여야 합니다.
    private final PixelService pixelService;

    @PostMapping
    public String updatePixel(@RequestBody PixelRequest request) {
        // 2. 클래스 이름(PixelService)이 아니라, 위에서 선언한 변수 이름(pixelService)을 써야 합니다.
        return pixelService.updatePixel(request);
    }
}