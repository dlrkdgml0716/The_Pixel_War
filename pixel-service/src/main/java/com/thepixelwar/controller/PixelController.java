package com.thepixelwar.controller;

import com.thepixelwar.dto.PixelRequest;
import com.thepixelwar.service.PixelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController // @Controller + @ResponseBody 해당 컨트롤러는 json 형태의 테이터를 반환해주는 클래스로 지정, bean에 등록
@RequestMapping("/api/pixels") // 해당 주소로 오는 요청은 해당 컨트롤러가 처리
@RequiredArgsConstructor // final이 붙은 변수를 매개변수로 갖는 생성자를 자동으로 만들어줌
public class PixelController {

    private final PixelService pixelService; // 의존성 주입

    @PostMapping // http 메서드 post만 처리
    public String updatePixel(@RequestBody PixelRequest request) { // 받은 요청을 PixelRequest 객체로 변환
        return pixelService.updatePixel(request); // 서비스로직의 업데이트 픽셀 함수 호출
    }
    @GetMapping("/{x}/{y}")
    public String getPixel(@PathVariable int x, @PathVariable int y) {
        return pixelService.getPixelColor(x, y);
    }

    @GetMapping
    public List<PixelRequest> getAllPixels() {
        return pixelService.getAllPixels();
    }
}