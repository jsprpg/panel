package io.monpanel.panel;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GameEgg {

    private String name;
    
    // Le nom du champ en Java correspond maintenant exactement au JSON
    private String docker_image;
    
    // Champ pour déterminer l'interface à utiliser ("server" ou "llm")
    private String view_type = "server"; 

    // Champs pour une configuration Docker flexible
    private Map<String, String> ports; // ex: {"8080": "80"} -> Hôte:Conteneur
    private Map<String, String> environment; // Variables d'environnement

    // --- Getters et Setters pour tous les champs ---

    public String getName() { 
        return name; 
    }
    public void setName(String name) { 
        this.name = name; 
    }

    public String getDocker_image() { 
        return docker_image; 
    }
    public void setDocker_image(String docker_image) { 
        this.docker_image = docker_image; 
    }

    public String getView_type() { 
        return view_type; 
    }
    public void setView_type(String view_type) { 
        this.view_type = view_type; 
    }
    
    public Map<String, String> getPorts() { 
        return ports; 
    }
    public void setPorts(Map<String, String> ports) { 
        this.ports = ports; 
    }

    public Map<String, String> getEnvironment() { 
        return environment; 
    }
    public void setEnvironment(Map<String, String> environment) { 
        this.environment = environment; 
    }
}