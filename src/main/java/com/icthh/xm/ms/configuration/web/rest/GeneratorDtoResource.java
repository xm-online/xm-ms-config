package com.icthh.xm.ms.configuration.web.rest;

import static com.icthh.xm.ms.configuration.config.Constants.API_PREFIX;
import static com.icthh.xm.ms.configuration.config.Constants.GENERATE;

import com.icthh.xm.ms.configuration.domain.dto.GenerateSpecDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(API_PREFIX + GENERATE)
public class GeneratorDtoResource {

    @PostMapping
    public ResponseEntity<Void> generate(@RequestBody GenerateSpecDto generateSpecDto) {
        return null;
    }
}
