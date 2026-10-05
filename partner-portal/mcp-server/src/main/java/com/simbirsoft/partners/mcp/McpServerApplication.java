package com.simbirsoft.partners.mcp;

import com.simbirsoft.partners.mcp.catalog.OpenApiToolCatalog;
import com.simbirsoft.partners.mcp.catalog.OpenApiToolCatalog.ToolCatalog;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpServerApplication {
    public static void main(String[] args) { SpringApplication.run(McpServerApplication.class,args); }

    @Bean
    ToolCatalog partnerToolCatalog(OpenApiToolCatalog loader,@Value("${BACKEND_OPENAPI_URL}") String openApiUrl) {
        return loader.load(URI.create(openApiUrl));
    }
}
