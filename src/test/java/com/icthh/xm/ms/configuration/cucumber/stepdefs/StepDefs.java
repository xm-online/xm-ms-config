package com.icthh.xm.ms.configuration.cucumber.stepdefs;

import com.icthh.xm.ms.configuration.ConfigurationApp;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.ResultActions;

@WebAppConfiguration
@SpringBootTest
@ContextConfiguration(classes = ConfigurationApp.class)
public abstract class StepDefs {

    protected ResultActions actions;

}
