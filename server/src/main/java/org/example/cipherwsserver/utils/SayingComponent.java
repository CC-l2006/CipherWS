package org.example.cipherwsserver.utils;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cipherwsserver.entity.Say;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class SayingComponent {

    @Bean(name = "sayList")
    public List<Say> getSayList(){
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            File sayingFile = new File("src/main/resources/saying.json");
            InputStream sayingFileInputStream = new FileInputStream(sayingFile);

            List<Say> sayList = objectMapper.readValue(sayingFileInputStream, new TypeReference<List<Say>>() {});
            if (sayList == null){
                throw new NullPointerException("saying读取失败");
            }
            return sayList;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
