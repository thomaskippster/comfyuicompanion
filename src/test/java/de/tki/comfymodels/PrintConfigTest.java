package de.tki.comfymodels;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.PathResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PrintConfigTest {

    @Test
    public void testPrintConfig() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Main.AppConfig.class);
        try {
            ConfigService configService = context.getBean(ConfigService.class);
            PathResolver pathResolver = context.getBean(PathResolver.class);
            System.out.println("=== CONFIGURATION PRINT ===");
            System.out.println("Models Path: " + configService.getModelsPath());
            System.out.println("ComfyUI Path: " + configService.getComfyUIPath());
            System.out.println("Archive Path: " + configService.getArchivePath());
            System.out.println("Extra Model Paths:");
            for (Map.Entry<String, List<Path>> entry : pathResolver.getAllExtraModelPaths().entrySet()) {
                System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
            }
            System.out.println("==========================");
        } finally {
            context.close();
        }
    }
}
