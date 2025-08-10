package io.monpanel.panel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GameEgg {

    @JsonProperty("name")
    private String name;

    @JsonProperty("docker_image")
    private String dockerImage;

    private String nestName;

    // Getters et Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public void setDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    public String getNestName() {
        return nestName;
    }

    public void setNestName(String nestName) {
        this.nestName = nestName;
    }
}