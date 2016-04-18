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
    private TCPSock listenSock;
    private HashMap<SockKey, TCPSock> clientSock;
    int sockNum; //
    private final int MAXSOCKNUM = 256;
    private TCPSock[] tcpSock;

    private static final byte dummy[] = new byte[0];

    public TCPManager(Node node, int addr, Manager manager) {
        this.node = node;
        this.addr = addr;
        this.manager = manager;
        this.sockNum = 0;
        listenSock = null;
        clientSock = new HashMap<>();
        tcpSock = new TCPSock[MAXSOCKNUM];
        for(int i = 0; i < MAXSOCKNUM; i++) {
            tcpSock[i] = new TCPSock(this);
        }
    }

    public void setListenSock(TCPSock socket) {
        this.listenSock = socket;
    }

    /**
     * Start this TCP manager
     */
    public void start() {

    }

    public HashMap<SockKey, TCPSock> getClientSock() {
        return this.clientSock;
    }

    public void findMatch(Packet packet) {
        Transport transPkt = Transport.unpack(packet.getPayload());
        SockKey sockKey = new SockKey(packet.getDest(), transPkt.getDestPort(), packet.getSrc(), transPkt.getSrcPort());
        //If the corresponding TCPSock is a listening sock
        //Or the client socket has already closed

        //logOutput("Finding match for: ");
        //logSockKey(sockKey);
        assert(transPkt!=null);
        assert(sockKey!=null);
        assert(clientSock!=null);

        //logOutput("ClientSock has size: "+clientSock.size());
        //printClientSock();

        if(!clientSock.containsKey(sockKey) || clientSock.get(sockKey).isClosed()) {
            assert(listenSock!=null);
            //logOutput("Socket not in client sock, use listen sock instead");
            listenSock.onReceive(transPkt, packet.getSrc());
        }
        //If the corresponding TCPSock is a r/w client sock
        else {
            //logOutput("Socket is in client sock");
            TCPSock tcpSock = clientSock.get(sockKey);
            assert(tcpSock!=null);
            tcpSock.onReceive(transPkt, packet.getSrc());
        }

        /*
        Packet replyPkt = new Packet(packet.getSrc(), this.addr, Packet.MAX_TTL, Protocol.TRANSPORT_PKT, node.getSeqIncre(),
                replyTransPkt.pack());
        return replyPkt;
        */
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
        for(int i=0; i < MAXSOCKNUM; i++) {
            if(tcpSock[i].isClosed()) {
                return tcpSock[i];
            }
        }
        return null;
    }


    public void sendTrans(int destAddr, Transport transPkt) {
        this.node.sendTrans(this.addr, destAddr, Protocol.TRANSPORT_PKT, transPkt.pack());
    }

    public void addTimer(int timeout, Callback cb) {
        this.manager.addTimer(this.addr, timeout, cb);
    }

    public void logSockKey(SockKey key) {
        logOutput("localAddr: " + key.localAddr + "; localPort: " + key.localPort + "; remoteAddr: " + key.remoteAddr + "; remotePort: " + key.remotePort);
    }

    public void printClientSock() {
        logOutput("[ Printing Client Sock: ");
        for (SockKey name: clientSock.keySet()){
            logSockKey(name);
            String value = clientSock.get(name).toString();
            logOutput(value);
        }
        logOutput("] Printing Client Sock finished: ");
    }

    public Node getNode() {
        return this.node;
    }
    /*
     * End Socket API
     */
}
