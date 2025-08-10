package io.monpanel.panel;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class EggService {

    private static final Logger log = LoggerFactory.getLogger(EggService.class);
    private final Map<String, List<GameEgg>> cachedEggs = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("Chargement des Eggs depuis les ressources locales...");
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:eggs/*.json");
            ObjectMapper mapper = new ObjectMapper();

            List<GameEgg> eggs = new ArrayList<>();
            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    GameEgg egg = mapper.readValue(inputStream, GameEgg.class);
                    eggs.add(egg);
                }
            }
            // Pour la simplicité, on les met tous dans une seule catégorie "Jeux"
            cachedEggs.put("Jeux", eggs);
            log.info("{} Eggs ont été chargés localement.", eggs.size());

        } catch (Exception e) {
            log.error("Impossible de charger les Eggs locaux.", e);
        }
    }

    public Map<String, List<GameEgg>> getGroupedEggs() {
        return this.cachedEggs;
    }
}