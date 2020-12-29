package com.zy.robotmonitor2;

import com.zy.robotmonitor2.chrome.ChromeHandle;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class Robotmonitor2Application {

    public static void main(String[] args) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(Robotmonitor2Application.class);
        //SpringApplication.run(SystemctlApplication.class, args);
        builder.headless(false)
                // .web(WebApplicationType.NONE)
                // .bannerMode(Banner.Mode.OFF)
                .run(args);
        ChromeHandle.init();
    }

}
