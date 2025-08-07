package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;
import static com.icthh.xm.ms.configuration.config.Constants.GENERATE;

import com.icthh.xm.ms.configuration.service.generator.dto.GenerateSpecDto;
import com.icthh.xm.ms.configuration.service.GeneratorDtoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(API_PREFIX + GENERATE)
@RequiredArgsConstructor
public class GeneratorDtoResource {

    private final GeneratorDtoService generatorDtoService;

    @PostMapping
    public ResponseEntity<Void> generate(@RequestBody GenerateSpecDto generateSpecDto) {
        generatorDtoService.generateDto(generateSpecDto);
        return ResponseEntity.ok().build();
    }
}
