import java.util.HashMap;

/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet TCP manager</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */
public class TCPManager {
    private Node node;
    private int addr;
    private Manager manager;
    TCPSock listenSock;
    HashMap<SockKey, TCPSock> clientSock;

    private static final byte dummy[] = new byte[0];

    public TCPManager(Node node, int addr, Manager manager) {
        this.node = node;
        this.addr = addr;
        this.manager = manager;
    }

    /**
     * Start this TCP manager
     */
    public void start() {

    }

    public Packet findMatch(Packet packet) {
        Transport transPkt = Transport.unpack(packet.getPayload());
        SockKey sockKey = new SockKey(packet.getDest(), transPkt.getDestPort(), packet.getSrc(), transPkt.getSrcPort());
        //If the corresponding TCPSock is a listening sock
        //Or the client socket has already closed
        Transport replyTransPkt = null;
        if(!clientSock.containsKey(sockKey) || clientSock.get(sockKey).isClosed()) {
            replyTransPkt = listenSock.onReceive(transPkt, packet.getSrc());
        }
        //If the corresponding TCPSock is a r/w client sock
        else {
            TCPSock tcpSock = clientSock.get(sockKey);
            replyTransPkt = tcpSock.onReceive(transPkt, packet.getSrc());
        }
        if(replyTransPkt==null) {
            return null;
        }
        Packet replyPkt = new Packet(packet.getSrc(), this.addr, Packet.MAX_TTL, Protocol.TRANSPORT_PKT, node.getSeqIncre(),
                replyTransPkt.pack());
        return replyPkt;
    }

    public int getAddr() {
        return addr;
    }
    public void logOutput(String output) {
        node.logOutput(output);
    }
    public void logError(String error) {
        node.logError(error);
    }
    /*
     * Begin socket API
     */

    /**
     * Create a socket
     *
     * @return TCPSock the newly created socket, which is not yet bound to
     *                 a local port
     */
    public TCPSock socket() {
        listenSock = new TCPSock(this);
        return listenSock;
    }

    /*
     * End Socket API
     */
}
