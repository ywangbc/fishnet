import java.util.LinkedList;
import java.util.Queue;

/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet socket implementation</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */

public class TCPSock {
    // TCP socket states
    enum State {
        // protocol states
        BIND,
        CLOSED,
        LISTEN,
        SYN_SENT,
        ESTABLISHED,
        SHUTDOWN // close requested, FIN not sent (due to unsent data in queue)
    }
    private State state;
    private int localAddr;
    private int localPort;
    private int remoteAddr;
    private int remotePort;
    private TCPManager tcpMan;
    private Queue<TCPSock> SYNConnections;
    private int backlog;


    public TCPSock(TCPManager tcpManager) {
        this.state = State.CLOSED;
        this.tcpMan = tcpManager;
    }

    public TCPSock (State state, TCPManager tcpManager) {
        this.state = state;
        this.tcpMan = tcpManager;
        this.localAddr = tcpManager.getAddr();
        this.localPort = -1;
        this.remoteAddr = -1;
        this.remotePort = -1;
    }

    public TCPSock (State state, TCPManager tcpManager, SockKey sockKey) {
        this.state = state;
        this.tcpMan = tcpManager;
        assert(sockKey.localAddr==tcpManager.getAddr());
        this.localAddr = sockKey.localAddr;
        this.localPort = sockKey.localPort;
        this.remoteAddr = sockKey.remoteAddr;
        this.remotePort = sockKey.remotePort;
    }

    /*
     * The following are the socket APIs of TCP transport service.
     * All APIs are NON-BLOCKING.
     */

    /**
     * Bind a socket to a local port
     *
     * @param localPort int local port number to bind the socket to
     * @return int 0 on success, -1 otherwise
     */
    public int bind(int localPort) {
        if(this.state !=State.CLOSED) {
            tcpMan.logError("Bind failed, current socket is not closed");
            return -1;
        }
        this.localPort = localPort;
        this.state = State.BIND; //change the state to BIND for LISTEN
        return 0;
    }

    /**
     * Listen for connections on a socket
     * @param backlog int Maximum number of pending connections
     * @return int 0 on success, -1 otherwise
     */
    public int listen(int backlog) {
        if(this.state!=State.BIND) {
            tcpMan.logError("Need to bind to a socket before listen");
            return -1;
        }
        this.state = State.LISTEN;
        SYNConnections = new LinkedList<TCPSock>();
        this.backlog = backlog;
        return 0;
    }

    /**
     * Accept a connection on a socket
     *
     * @return TCPSock The first established connection on the request queue
     */
    public TCPSock accept() {
        if(state != State.LISTEN) {
            tcpMan.logError("Current socket is not listening");
            return null;
        }
        if(SYNConnections==null) {
            tcpMan.logError("SYN Connection queue not initialized");
            return null;
        }
        return SYNConnections.poll();
    }

    /**
     * Accept a transport packet
     * @return A transport packet for replying to client
     */
    public Transport onReceive(Transport transPkt, int clientAddr) {
        if(transPkt.getSrcPort() > Transport.MAX_PORT_NUM) {
            tcpMan.logError("srcPort "+transPkt.getSrcPort()+" out of range");
        }
        if(transPkt.getDestPort() > Transport.MAX_PORT_NUM) {
            tcpMan.logError("destPort "+transPkt.getSrcPort()+" out of range");
        }
        int pktType = transPkt.getType();
        if(pktType!=Transport.SYN && pktType!= Transport.ACK && pktType!= Transport.FIN && pktType!=Transport.DATA) {
            tcpMan.logError("transPkt with illegal type: "+transPkt.getType());
            return null;
        }

        //If the socket is listening,
        if(isListening()) {
            //but the message is for a client socket
            //Reply with a fin message
            if(pktType==Transport.DATA) {
                return new Transport(localPort, transPkt.getSrcPort(), Transport.FIN, transPkt.getWindow(), transPkt.getSeqNum(), new byte[0]);
            }
            else if(pktType == Transport.FIN) {
                tcpMan.logError("Listening socket received FIN packet from client");
                return null;
            }
            else if(pktType == Transport.ACK) {
                tcpMan.logError("Client should send ACK message to listening server socket");
                return null;
            }
            //legal type, update local client sockets, send ack message
            else if(pktType == Transport.SYN) {
                SockKey sockKey = new SockKey(localAddr, localPort, clientAddr, transPkt.getSrcPort());
                TCPSock clientSock = new TCPSock(State.ESTABLISHED, tcpMan, sockKey);
                tcpMan.clientSock.put(sockKey, clientSock);
                return new Transport(localPort, transPkt.getSrcPort(), Transport.ACK, transPkt.getWindow(), transPkt.getSeqNum(), new byte[0]);
            }
        }
        //If the socket is a r/w client socket
        else {

        }


        return null;
    }

    public boolean isConnectionPending() {
        return (state == State.SYN_SENT);
    }

    public boolean isClosed() {
        return (state == State.CLOSED);
    }

    public boolean isConnected() {
        return (state == State.ESTABLISHED);
    }

    public boolean isClosurePending() {
        return (state == State.SHUTDOWN);
    }

    public boolean isListening() {
        return (state == State.LISTEN);
    }

    /**
     * Initiate connection to a remote socket
     *
     * @param destAddr int Destination node address
     * @param destPort int Destination port
     * @return int 0 on success, -1 otherwise
     */
    public int connect(int destAddr, int destPort) {
        return -1;
    }

    /**
     * Initiate closure of a connection (graceful shutdown)
     */
    public void close() {
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    public void release() {
    }

    /**
     * Write to the socket up to len bytes from the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer to write from
     * @param pos int starting position in buffer
     * @param len int number of bytes to write
     * @return int on success, the number of bytes written, which may be smaller
     *             than len; on failure, -1
     */
    public int write(byte[] buf, int pos, int len) {
        return -1;
    }

    /**
     * Read from the socket up to len bytes into the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer
     * @param pos int starting position in buffer
     * @param len int number of bytes to read
     * @return int on success, the number of bytes read, which may be smaller
     *             than len; on failure, -1
     */
    public int read(byte[] buf, int pos, int len) {
        return -1;
    }

    /*
     * End of socket API
     */
}
