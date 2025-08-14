package io.monpanel.panel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GameEgg {

    private String name;
    private String docker_image;
    private String view_type = "server"; 
    private Map<String, String> ports;
    private Map<String, String> environment;

    // --- Getters et Setters pour TOUS les champs ---

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