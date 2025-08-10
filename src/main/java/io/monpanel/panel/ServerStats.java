package io.monpanel.panel;

public class ServerStats {
    private String cpuPercent = "0%";
    private String memoryUsage = "0 MiB / 0 MiB";
    private String netIO = "0 B / 0 B";
    private String blockIO = "0 B / 0 B";
    private boolean isOffline = true;
    private String error = null; // Nouveau champ

    // Getters et Setters pour tous les champs, y compris 'error'
    public String getCpuPercent() { return cpuPercent; }
    public void setCpuPercent(String cpuPercent) { this.cpuPercent = cpuPercent; }
    public String getMemoryUsage() { return memoryUsage; }
    public void setMemoryUsage(String memoryUsage) { this.memoryUsage = memoryUsage; }
    public String getNetIO() { return netIO; }
    public void setNetIO(String netIO) { this.netIO = netIO; }
    public String getBlockIO() { return blockIO; }
    public void setBlockIO(String blockIO) { this.blockIO = blockIO; }
    public boolean isOffline() { return isOffline; }
    public void setOffline(boolean offline) { isOffline = offline; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}