public class SockKey {
    public int localAddr;
    public int localPort;
    public int remoteAddr;
    public int remotePort;
    SockKey(int localAddr, int localPort, int remoteAddr, int remotePort) {
        this.localAddr = localAddr;
        this.localPort = localPort;
        this.remoteAddr = remoteAddr;
        this.remotePort = remotePort;
    }
    /*
    SockKey(Packet packet) {
        this.localAddr = packet.getDest();
        this.remoteAddr = packet.getSrc();
        byte[] payload = packet.getPayload();
        Transport transPkt = Transport.unpack(payload);
        this.localPort = transPkt.getDestPort();
        this.remotePort = transPkt.getSrcPort();
    }
    */
    @Override
    public int hashCode() {
        return localAddr;
    }
    @Override
    public boolean equals(Object o) {
        SockKey target = (SockKey)o;
        return (this.localAddr==target.localAddr && this.localPort==target.localPort &&
                this.remoteAddr==target.remoteAddr && this.remotePort==target.remotePort);
    }
}