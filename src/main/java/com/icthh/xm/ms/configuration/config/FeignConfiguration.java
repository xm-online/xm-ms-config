package com.icthh.xm.ms.configuration.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.icthh.xm.ms.configuration")
public class FeignConfiguration {

}
