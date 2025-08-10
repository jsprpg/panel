package io.monpanel.panel;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

    // La structure de données est maintenant une "Map dans une Map" pour gérer les sous-catégories
    private final Map<String, Map<String, List<GameEgg>>> cachedEggs = new TreeMap<>();

    @PostConstruct
    public void init() {
        log.info("Chargement des Eggs par catégorie et sous-catégorie...");
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            // Le pattern scanne maintenant un niveau de dossier plus profond
            Resource[] resources = resolver.getResources("classpath:eggs/*/*/*.json");
            ObjectMapper mapper = new ObjectMapper();

            if (resources.length == 0) {
                log.warn("Aucun Egg trouvé. Créez une structure de dossiers comme 'resources/eggs/jeux/minecraft/paper.json'.");
                return;
            }

            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    GameEgg egg = mapper.readValue(inputStream, GameEgg.class);

                    // On extrait les noms de catégorie et sous-catégorie depuis le chemin du fichier
                    Path filePath = Paths.get(resource.getURI());
                    Path subCategoryPath = filePath.getParent();
                    Path categoryPath = subCategoryPath.getParent();
                    
                    String subCategoryName = subCategoryPath.getFileName().toString();
                    String categoryName = categoryPath.getFileName().toString();
                    
                    String capCat = capitalize(categoryName);
                    String capSub = capitalize(subCategoryName);

                    // On ajoute l'Egg dans la structure de Map imbriquée
                    cachedEggs.computeIfAbsent(capCat, k -> new TreeMap<>())
                              .computeIfAbsent(capSub, k -> new ArrayList<>())
                              .add(egg);

                    log.info("Egg '{}' chargé dans {} -> {}", egg.getName(), capCat, capSub);
                }
            }
            log.info("Chargement des Eggs terminé.");

        } catch (Exception e) {
            log.error("Impossible de charger les Eggs locaux.", e);
        }
    }

    // La méthode pour la vue renvoie maintenant la nouvelle structure de données
    public Map<String, Map<String, List<GameEgg>>> getGroupedEggs() {
        return this.cachedEggs;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}