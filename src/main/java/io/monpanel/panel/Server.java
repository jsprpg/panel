package io.monpanel.panel;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "servers")
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String containerId;
    private String dockerImage;
    private int hostPort;
    private String hostPath; // Chemin sur la machine hôte, ex: /var/panel/servers/uuid

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User owner;

    private int memory; // en Mo (ex: 1024)
    private double cpu; // en cœurs (ex: 1.5)
    private int disk;   // en Go (ex: 5)

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    public String getDockerImage() { return dockerImage; }
    public void setDockerImage(String dockerImage) { this.dockerImage = dockerImage; }
    public int getHostPort() { return hostPort; }
    public void setHostPort(int hostPort) { this.hostPort = hostPort; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public int getMemory() { return memory; }
    public void setMemory(int memory) { this.memory = memory; }
    public double getCpu() { return cpu; }
    public void setCpu(double cpu) { this.cpu = cpu; }
    public int getDisk() { return disk; }
    public void setDisk(int disk) { this.disk = disk; }
    public String getHostPath() { return hostPath; }
    public void setHostPath(String hostPath) { this.hostPath = hostPath; }
}