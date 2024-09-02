package jowita.drozdowicz;

import jowita.drozdowicz.db.DataDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SimpleClientApplication {

    private static final Logger log = LoggerFactory.getLogger(SimpleClientApplication.class);

    public static void main(String[] args) {
        System.out.println("Version - super finale");
        SpringApplication.run(SimpleClientApplication.class, args);
    }

}

