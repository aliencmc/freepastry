package rice.pastry.socket;

import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import java.util.*;

import net.sbbi.upnp.Discovery;
import net.sbbi.upnp.impls.InternetGatewayDevice;
import net.sbbi.upnp.messages.*;

import rice.Continuation;
import rice.Continuation.ExternalContinuation;
import rice.environment.Environment;
import rice.environment.logging.*;
import rice.environment.params.Parameters;
import rice.environment.params.simple.SimpleParameters;
import rice.environment.processing.Processor;
import rice.environment.processing.simple.SimpleProcessor;
import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.Id;
import rice.pastry.dist.DistPastryNodeFactory;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.socket.messaging.*;
import rice.pastry.standard.*;
import rice.selector.*;
import rice.pastry.NodeHandle;
import rice.pastry.messaging.Message;

/**
 * Pastry node factory for Socket-linked nodes.
 * 
 * @version $Id: SocketPastryNodeFactory.java,v 1.6 2004/03/08 19:53:57 amislove
 *          Exp $
 * @author Alan Mislove
 */
public class SocketPastryNodeFactory extends DistPastryNodeFactory {
  public static final int ALWAYS = 1;

  public static final int PREFIX_MATCH = 2;

  public static final int NEVER = 3;

  public static final int OVERWRITE = 1;

  public static final int USE_DIFFERENT_PORT = 2;

  public static final int FAIL = 3;

  private Environment environment;

  private NodeIdFactory nidFactory;

  private int port;

  /**
   * Large period (in seconds) means infrequent, 0 means never.
   */
  private int leafSetMaintFreq;

  private int routeSetMaintFreq;

  private RandomSource random;

  private InetAddress localAddress;

  protected int testFireWallPolicy;

  protected int findFireWallPolicy;

  /**
   * Constructor.
   * 
   * Here is order for bind address 1) bindAddress parameter 2) if bindAddress
   * is null, then parameter: socket_bindAddress (if it exists) 3) if
   * socket_bindAddress doesn't exist, then InetAddress.getLocalHost()
   * 
   * @param nf The factory for building node ids
   * @param bindAddress which address to bind to
   * @param startPort The port to start creating nodes on
   * @param env The environment.
   */
  public SocketPastryNodeFactory(NodeIdFactory nf, InetAddress bindAddress,
      int startPort, Environment env) throws IOException {
    super(env);
    localAddress = bindAddress;
    if (localAddress == null) {
      if (env.getParameters().contains("socket_bindAddress")) {
        localAddress = env.getParameters().getInetAddress("socket_bindAddress");
      }
    }
    if (localAddress == null) {
      localAddress = InetAddress.getLocalHost();
      try {
        ServerSocket test = new ServerSocket();
        test.bind(new InetSocketAddress(localAddress, port));
      } catch (SocketException e) {
        Socket temp = new Socket("yahoo.com", 80);
        localAddress = temp.getLocalAddress();
        temp.close();

        if (logger.level <= Logger.WARNING)
          logger.log("Error binding to default IP, using " + localAddress);
      }
    }

    environment = env;
    nidFactory = nf;
    port = startPort;
    Parameters params = env.getParameters();
    leafSetMaintFreq = params.getInt("pastry_leafSetMaintFreq");
    routeSetMaintFreq = params.getInt("pastry_routeSetMaintFreq");

    if (params.contains("pastry_socket_use_own_random")
        && params.getBoolean("pastry_socket_use_own_random")) {
      if (params.contains("pastry_socket_random_seed")
          && !params.getString("pastry_socket_random_seed").equalsIgnoreCase(
              "clock")) {
        this.random = new SimpleRandomSource(params
            .getLong("pastry_socket_random_seed"), env.getLogManager(),
            "socket");
      } else {
        this.random = new SimpleRandomSource(env.getLogManager(), "socket");
      }
    } else {
      this.random = env.getRandomSource();
    }
  }

  public SocketPastryNodeFactory(NodeIdFactory nf, int startPort,
      Environment env) throws IOException {
    this(nf, null, startPort, env);
  }

  /**
   * This method returns the routes a remote node is using
   * 
   * @param handle The node to connect to
   * @return The leafset of the remote node
   */
  public SourceRoute[] getRoutes(NodeHandle handle, NodeHandle local)
      throws IOException {
    SocketNodeHandle wHandle = (SocketNodeHandle) handle;

    RoutesResponseMessage lm = (RoutesResponseMessage) getResponse(wHandle
        .getAddress(), new RoutesRequestMessage());

    return lm.getRoutes();
  }

  /**
   * This method returns the remote leafset of the provided handle to the
   * caller, in a protocol-dependent fashion. Note that this method may block
   * while sending the message across the wire.
   * 
   * @param handle The node to connect to
   * @return The leafset of the remote node
   */
  public LeafSet getLeafSet(NodeHandle handle) throws IOException {
    SocketNodeHandle wHandle = (SocketNodeHandle) handle;

    LeafSetResponseMessage lm = (LeafSetResponseMessage) getResponse(wHandle
        .getAddress(), new LeafSetRequestMessage());

    return lm.getLeafSet();
  }

  public CancellableTask getLeafSet(NodeHandle handle, final Continuation c) {
    SocketNodeHandle wHandle = (SocketNodeHandle) handle;

    return getResponse(wHandle.getAddress(), new LeafSetRequestMessage(),
        new Continuation() {
          public void receiveResult(Object result) {
            LeafSetResponseMessage lm = (LeafSetResponseMessage) result;
            c.receiveResult(lm.getLeafSet());
          }

          public void receiveException(Exception result) {
            c.receiveException(result);
          }
        });
  }

  /**
   * This method returns the remote route row of the provided handle to the
   * caller, in a protocol-dependent fashion. Note that this method may block
   * while sending the message across the wire.
   * 
   * @param handle The node to connect to
   * @param row The row number to retrieve
   * @return The route row of the remote node
   */
  public RouteSet[] getRouteRow(NodeHandle handle, int row) throws IOException {
    SocketNodeHandle wHandle = (SocketNodeHandle) handle;

    RouteRowResponseMessage rm = (RouteRowResponseMessage) getResponse(wHandle
        .getAddress(), new RouteRowRequestMessage(row));

    return rm.getRouteRow();
  }

  public CancellableTask getRouteRow(NodeHandle handle, int row,
      final Continuation c) {
    SocketNodeHandle wHandle = (SocketNodeHandle) handle;

    return getResponse(wHandle.getAddress(), new RouteRowRequestMessage(row),
        new Continuation() {
          public void receiveResult(Object result) {
            RouteRowResponseMessage rm = (RouteRowResponseMessage) result;
            c.receiveResult(rm.getRouteRow());
          }

          public void receiveException(Exception result) {
            c.receiveException(result);
          }
        });
  }

  /**
   * This method determines and returns the proximity of the current local node
   * to the provided NodeHandle. This will need to be done in a protocol-
   * dependent fashion and may need to be done in a special way.
   * 
   * @param handle The handle to determine the proximity of
   * @param local DESCRIBE THE PARAMETER
   * @return The proximity of the provided handle
   */
  public int getProximity(NodeHandle local, NodeHandle handle) {
    EpochInetSocketAddress lAddress = ((SocketNodeHandle) local)
        .getEpochAddress();
    EpochInetSocketAddress rAddress = ((SocketNodeHandle) handle)
        .getEpochAddress();

    // lAddress = new EpochInetSocketAddress(new InetSocketAddress(lAddress
    // .getAddress().getAddress(), lAddress.getAddress().getPort() + 1));

    // if this is a request for an old version of us, then we return
    // infinity as an answer
    if (lAddress.getAddress().equals(rAddress.getAddress())) {
      return Integer.MAX_VALUE;
    }

    DatagramSocket socket = null;
    SourceRoute route = SourceRoute
        .build(new EpochInetSocketAddress[] { rAddress });

    try {
      socket = new DatagramSocket(lAddress.getAddress().getPort());
      socket.setSoTimeout(5000);

      // byte[] data = PingManager.addHeader(route, new PingMessage(route, route
      // .reverse(lAddress), environment.getTimeSource().currentTimeMillis()),
      // lAddress, environment, logger);

      SocketBuffer sb = new SocketBuffer(lAddress, route, new PingMessage(/*
                                                                           * route,
                                                                           * route
                                                                           * .reverse(lAddress),
                                                                           */environment.getTimeSource().currentTimeMillis()));

      if (logger.level <= Logger.FINE)
        logger.log("Sending Ping to " + rAddress + " from " + lAddress);
      socket.send(new DatagramPacket(sb.getBuffer().array(), sb.getBuffer()
          .limit(), rAddress.getAddress()));

      long start = environment.getTimeSource().currentTimeMillis();
      socket.receive(new DatagramPacket(new byte[10000], 10000));
      return (int) (environment.getTimeSource().currentTimeMillis() - start);
    } catch (IOException e) {
      return Integer.MAX_VALUE - 1;
    } finally {
      if (socket != null)
        socket.close();
    }
  }

  /**
   * Way to generate a NodeHandle with a maximum timeout to receive the result.
   * Helper funciton for using the non-blocking version. However this method
   * behaves as a blocking call.
   * 
   * @param address
   * @param timeout maximum time in millis to return the result. <= 0 will use
   *          the blocking version.
   * @return
   */
  public NodeHandle generateNodeHandle(InetSocketAddress address, int timeout) {
    if (timeout <= 0)
      return generateNodeHandle(address);

    TimerContinuation c = new TimerContinuation();

    CancellableTask task = generateNodeHandle(address, c);

    if (task == null)
      return null;

    synchronized (c) {
      try {
        c.wait(timeout);
      } catch (InterruptedException ie) {
        return null;
      }
    }
    task.cancel();

    if (logger.level <= Logger.FINER)
      logger.log("SPNF.generateNodeHandle() returning " + c.ret
          + " after trying to contact " + address);

    return (NodeHandle) c.ret;
  }

  class TimerContinuation implements Continuation {
    public Object ret = null;

    public void receiveResult(Object result) {
      ret = result;
      synchronized (this) {
        this.notify();
      }
    }

    public void receiveException(Exception result) {
      synchronized (this) {
        this.notify();
      }
    }
  }

  /**
   * Method which contructs a node handle (using the socket protocol) for the
   * node at address NodeHandle.
   * 
   * @param address The address of the remote node.
   * @return A NodeHandle cooresponding to that address
   */
  public NodeHandle generateNodeHandle(InetSocketAddress address) {
    // send nodeId request to remote node, wait for response
    // allocate enought bytes to read a node handle
    if (logger.level <= Logger.FINE)
      logger.log("Socket: Contacting bootstrap node " + address);

    try {
      NodeIdResponseMessage rm = (NodeIdResponseMessage) getResponse(address,
          new NodeIdRequestMessage());

      return new SocketNodeHandle(new EpochInetSocketAddress(address, rm
          .getEpoch()), rm.getNodeId());
    } catch (IOException e) {
      if (logger.level <= Logger.FINE) {
        logger.logException("Error connecting to address " + address + ": ", e);
      } else {
        if (logger.level <= Logger.WARNING)
          logger.log("Error connecting to address " + address + ": " + e);
      }
      return null;
    }
  }

  public CancellableTask generateNodeHandle(final InetSocketAddress address,
      final Continuation c) {
    if (logger.level <= Logger.FINE)
      logger.log("Socket: Contacting bootstrap node " + address);

    return getResponse(address, new NodeIdRequestMessage(), new Continuation() {
      public void receiveResult(Object result) {
        NodeIdResponseMessage rm = (NodeIdResponseMessage) result;
        c.receiveResult(new SocketNodeHandle(new EpochInetSocketAddress(
            address, rm.getEpoch()), rm.getNodeId()));
      }

      public void receiveException(Exception result) {
        if (logger.level <= Logger.WARNING)
          logger.log("Error connecting to address " + address + ": " + result);
        c.receiveException(result);
      }
    });
  }

  /**
   * Method which creates a Pastry node from the next port with a randomly
   * generated NodeId.
   * 
   * @param bootstrap Node handle to bootstrap from.
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(NodeHandle bootstrap) {
    // if (bootstrap == null) {
    // return newNode(bootstrap, NodeId.buildNodeId());
    // }
    return newNode(bootstrap, nidFactory.generateNodeId());
  }

  /**
   * Method which creates a Pastry node from the next port with a randomly
   * generated NodeId.
   * 
   * @param bootstrap Node handle to bootstrap from.
   * @param nodeId DESCRIBE THE PARAMETER
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(final NodeHandle bootstrap, Id nodeId) {
    return newNode(bootstrap, nodeId, null);
  }

  /**
   * Method which creates a Pastry node from the next port with a randomly
   * generated NodeId.
   * 
   * @param bootstrap Node handle to bootstrap from.
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(NodeHandle bootstrap, InetSocketAddress proxy) {
    return newNode(bootstrap, nidFactory.generateNodeId(), proxy);
  }

  /**
   * Method which creates a Pastry node from the next port with a randomly
   * generated NodeId.
   * 
   * @param bootstrap Node handle to bootstrap from.
   * @param nodeId DESCRIBE THE PARAMETER
   * @param address The address to claim that this node is at - used for proxies
   *          behind NATs
   * @return A node with a random ID and next port number.
   */
  public PastryNode newNode(NodeHandle bootstrap, Id nodeId,
      InetSocketAddress pAddress) {
    try {
      return newNode(bootstrap, nodeId, pAddress, true); // fix the method just
                                                          // below if you change
                                                          // this
    } catch (IOException e) {

      if (logger.level <= Logger.WARNING)
        logger.log("Warning: " + e);

      if (environment.getParameters().getBoolean(
          "pastry_socket_increment_port_after_construction")) {
        port++;
        try {
          return newNode(bootstrap, nodeId, pAddress); // recursion, this will
                                                        // prevent from things
                                                        // getting too out of
                                                        // hand in
          // case the node can't bind to anything, expect a
          // StackOverflowException
        } catch (StackOverflowError soe) {
          if (logger.level <= Logger.SEVERE)
            logger
                .log("SEVERE: SocketPastryNodeFactory: Could not bind on any ports!"
                    + soe);
          throw soe;
        }
      } else {
        throw new RuntimeException(e);
      }
    }
  }

  public synchronized PastryNode newNode(NodeHandle bootstrap, Id nodeId,
      InetSocketAddress pAddress, boolean throwException) throws IOException {
    if (!throwException)
      return newNode(bootstrap, nodeId, pAddress); // yes, this is sort of
                                                    // bizarre
    // the idea is that we can't throw an exception by default because it will
    // break reverse compatibility
    // so this method gets called twice if throwException is false. But the
    // second time,
    // it will be called with true, but will be
    // wrapped with the above function which will catch the exception.
    // -Jeff May 12, 2006
    if (bootstrap == null)
      if (logger.level <= Logger.WARNING)
        logger
            .log("No bootstrap node provided, starting a new ring binding to address "
                + localAddress + ":" + port + "...");

    // this code builds a different environment for each PastryNode
    Environment environment = this.environment;
    if (this.environment.getParameters().getBoolean(
        "pastry_factory_multipleNodes")) {
      if (this.environment.getLogManager() instanceof CloneableLogManager) {
        LogManager lman = ((CloneableLogManager) this.environment
            .getLogManager()).clone("0x" + nodeId.toStringBare());
        SelectorManager sman = this.environment.getSelectorManager();
        Processor proc = this.environment.getProcessor();
        if (this.environment.getParameters().getBoolean(
            "pastry_factory_selectorPerNode")) {
          sman = new SelectorManager(nodeId.toString() + " Selector",
              this.environment.getTimeSource(), lman);
        }
        if (this.environment.getParameters().getBoolean(
            "pastry_factory_processorPerNode")) {
          proc = new SimpleProcessor(nodeId.toString() + " Processor");
        }

        environment = new Environment(sman, proc, this.environment
            .getRandomSource(), this.environment.getTimeSource(), lman,
            this.environment.getParameters());

        this.environment.addDestructable(environment);
      }
    }

    // NOTE: We _don't_ want to use the environment RandomSource because this
    // will cause
    // problems if we run the same node twice quickly with the same seed. Epochs
    // should really
    // be different every time.
    long epoch = random.nextLong();

    EpochInetSocketAddress localAddress = null;
    EpochInetSocketAddress proxyAddress = null;

    // getNearest uses the port inside the SNH, so this needs to be the local
    // address
    SocketNodeHandle temp = new SocketNodeHandle(getEpochAddress(port, epoch),
        nodeId);
    NodeHandle nearest = getNearest(temp, bootstrap);

    final SocketPastryNode pn = new SocketPastryNode(nodeId, environment);

    SocketSourceRouteManager srManager = null;

    try {
      synchronized (this) {
        localAddress = getEpochAddress(port, epoch);

        findFireWallIfNecessary();
        if (pAddress == null) {
          // may need to find and set the firewall
          if (fireWallExternalAddress == null) {
            proxyAddress = localAddress;
          } else {
            // configure the firewall if necessary, can be any port, start with
            // the freepastry port
            int availableFireWallPort = findAvailableFireWallPort(port, port);
            openFireWallPort(port, availableFireWallPort);
            proxyAddress = new EpochInetSocketAddress(new InetSocketAddress(
                fireWallExternalAddress, availableFireWallPort), epoch);
          }
        } else {
          // configure the firewall if necessary, but to the specified port
          if (fireWallExternalAddress != null) {
            int availableFireWallPort = findAvailableFireWallPort(port,
                pAddress.getPort());
            if (availableFireWallPort == pAddress.getPort()) {
              openFireWallPort(port, availableFireWallPort);
            } else {
              // decide how to handle this
              switch (getFireWallPolicyVariable("nat_state_policy")) {
                case OVERWRITE:
                  openFireWallPort(port, pAddress.getPort());
                  break;
                case FAIL:
                  // todo: would be useful to pass the app that is bound to that
                  // port
                  throw new BindException(
                      "Firewall is already bound to the requested port:"
                          + pAddress);
                case USE_DIFFERENT_PORT:
                  openFireWallPort(port, availableFireWallPort);
                  pAddress = new InetSocketAddress(pAddress.getAddress(),
                      availableFireWallPort);
                  break;
              }
            }
          }
          proxyAddress = new EpochInetSocketAddress(pAddress, epoch);
        }

        srManager = new SocketSourceRouteManager(pn, localAddress,
            proxyAddress, random); // throws an exception if cant bind
        if (environment.getParameters().getBoolean(
            "pastry_socket_increment_port_after_construction"))
          port++; // this statement must go after the construction of srManager
                  // because the
      }
    } catch (IOException ioe) {
      // this will useually be a bind exception

      // clean up Environment
      if (this.environment.getParameters().getBoolean(
          "pastry_factory_multipleNodes")) {
        environment.destroy();
      }
      throw ioe;
    } catch (UPNPResponseException ure) {
      throw new RuntimeException(ure);
    }

    // calling method can decide what to do with port incrementation if can't
    // bind

    pn.setSocketSourceRouteManager(srManager);
    SocketNodeHandle localhandle = new SocketNodeHandle(proxyAddress, nodeId);
    localhandle = (SocketNodeHandle) pn.coalesce(localhandle);
    MessageDispatch msgDisp = new MessageDispatch(pn);
    RoutingTable routeTable = new RoutingTable(localhandle, rtMax, rtBase,
        environment);
    LeafSet leafSet = new LeafSet(localhandle, lSetSize);

    StandardRouter router = new StandardRouter(pn);

    StandardRouteSetProtocol rsProtocol = new StandardRouteSetProtocol(pn,
        routeTable, environment);

    pn.setElements(localhandle, msgDisp, leafSet, routeTable);
    router.register();
    rsProtocol.register();
    pn.setSocketElements(proxyAddress, leafSetMaintFreq, routeSetMaintFreq);

    PeriodicLeafSetProtocol lsProtocol = new PeriodicLeafSetProtocol(pn,
        localhandle, leafSet, routeTable);
    lsProtocol.register();
    // msgDisp.registerReceiver(lsProtocol.getAddress(), lsProtocol);
    ConsistentJoinProtocol jProtocol = new ConsistentJoinProtocol(pn,
        localhandle, routeTable, leafSet);
    jProtocol.register();

    if (bootstrap != null) {
      bootstrap = (SocketNodeHandle) pn.coalesce(bootstrap);

      switch (getFireWallPolicyVariable("nat_test_policy")) {
        case NEVER:
          break;
        case PREFIX_MATCH:
          if (!localAddressIsProbablyNatted())
            break;
        case ALWAYS:
        default:
          ExternalContinuation ec = new ExternalContinuation();
          pn.testFireWall(bootstrap, ec, 5000);
          ec.sleep();
          Boolean resultB = (Boolean) ec.getResult();
          boolean result = resultB.booleanValue();
          if (result) {
            // continue
          } else { // should change to IOException in FP 2.0
            throw new RuntimeException("Firewall test failed for local:"
                + localAddress.getAddress() + " external:"
                + proxyAddress.getAddress());
          }
      } // switch
    }

    // asdf // why is this here?
//    try {
//      Thread.sleep(1000);
//    } catch (InterruptedException e) {
//    }

    if (nearest != null)
      nearest = pn.coalesce(nearest);

    pn.doneNode(nearest);
    // pn.doneNode(bootstrap);

    return pn;
  }

  MessageDeserializer deserializer = new SPNFDeserializer();

  // this one doesn't properly coalsece NodeHandles, but when this is used,
  // there is no PastryNode yet!
  NodeHandleFactory nhf = new NodeHandleFactory() {
    public NodeHandle readNodeHandle(InputBuffer buf) throws IOException {
      return SocketNodeHandle.build(buf);
    }
  };

  class SPNFDeserializer implements MessageDeserializer {
    public rice.p2p.commonapi.Message deserialize(InputBuffer buf, short type,
        byte priority, rice.p2p.commonapi.NodeHandle sender) throws IOException {
      switch (type) {
        case NodeIdResponseMessage.TYPE:
          return new NodeIdResponseMessage(buf);
        case LeafSetResponseMessage.TYPE:
          return new LeafSetResponseMessage(buf, nhf);
        case RoutesResponseMessage.TYPE:
          return new RoutesResponseMessage(buf);
        case RouteRowResponseMessage.TYPE:
          return new RouteRowResponseMessage(buf, nhf);
        default:
          if (logger.level <= Logger.SEVERE)
            logger.log("SERIOUS ERROR: Received unknown message address: " + 0
                + "type:" + type);
          return null;
      }
    }
  }

  protected void findFireWallIfNecessary() throws IOException,
      UPNPResponseException {
    switch (getFireWallPolicyVariable("nat_search_policy")) {
      case NEVER:
        return;
      case PREFIX_MATCH:
        if (!localAddressIsProbablyNatted())
          return;
      case ALWAYS:
      default:
        findFireWall(localAddress);
    }
  }

  protected int getFireWallPolicyVariable(String key) {
    String val = environment.getParameters().getString(key);
    if (val.equalsIgnoreCase("prefix"))
      return PREFIX_MATCH;
    if (val.equalsIgnoreCase("change"))
      return USE_DIFFERENT_PORT;
    if (val.equalsIgnoreCase("never"))
      return NEVER;
    if (val.equalsIgnoreCase("overwrite"))
      return OVERWRITE;
    if (val.equalsIgnoreCase("always"))
      return ALWAYS;
    if (val.equalsIgnoreCase("fail"))
      return FAIL;
    throw new RuntimeException("Unknown value " + val + " for " + key);
  }

  /**
   * @return true if ip address matches firewall prefix
   */
  protected boolean localAddressIsProbablyNatted() {
    String ip = localAddress.getHostAddress();
    String nattedNetworkPrefixes = environment.getParameters().getString(
        "nat_network_prefixes");

    String[] nattedNetworkPrefix = nattedNetworkPrefixes.split(";");
    for (int i = 0; i < nattedNetworkPrefix.length; i++) {
      if (ip.startsWith(nattedNetworkPrefix[i])) {
        return true;
      }
    }
    return false;
  }

  /**
   * This method anonymously sends the given message to the remote address,
   * blocks until a response is received, and then closes the socket and returns
   * the response.
   * 
   * @param address The address to send to
   * @param message The message to send
   * @return The response
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  protected Message getResponse(InetSocketAddress address, Message message)
      throws IOException {
    // create reader and writer
    SocketChannelWriter writer;
    SocketChannelReader reader;
    writer = new SocketChannelWriter(environment, SourceRoute
        .build(new EpochInetSocketAddress(address,
            EpochInetSocketAddress.EPOCH_UNKNOWN)));
    reader = new SocketChannelReader(environment, SourceRoute
        .build(new EpochInetSocketAddress(address,
            EpochInetSocketAddress.EPOCH_UNKNOWN)));

    // bind to the appropriate port
    SocketChannel channel = SocketChannel.open();
    channel.configureBlocking(true);
    channel.socket().connect(address, 20000);
    channel.socket().setSoTimeout(20000);

    writer.enqueue(TOTAL_HEADER);
    writer.enqueue(message);
    writer.write(channel);
    SocketBuffer o = null;

    while (o == null) {
      o = reader.read(channel);
    }

    if (logger.level <= Logger.FINER)
      logger.log("SPNF.getResponse(): Closing " + channel);
    channel.socket().shutdownOutput();
    channel.socket().close();
    channel.close();
    if (logger.level <= Logger.FINER)
      logger.log("SPNF.getResponse(): Closed " + channel);

    return o.deserialize(deserializer);
  }

  public static byte[] TOTAL_HEADER;
  static {
    TOTAL_HEADER = new byte[SocketCollectionManager.TOTAL_HEADER_SIZE + 4]; // plus
                                                                            // the
                                                                            // appId
    System.arraycopy(SocketCollectionManager.PASTRY_MAGIC_NUMBER, 0,
        TOTAL_HEADER, 0, SocketCollectionManager.PASTRY_MAGIC_NUMBER.length);
    // System.arraycopy(new byte[4],0,TOTAL_HEADER,4,4); // version 0 // can
    // just leave blank zero, cause java zeros everything automatically
    System.arraycopy(SocketCollectionManager.HEADER_DIRECT, 0, TOTAL_HEADER, 8,
        SocketCollectionManager.HEADER_SIZE);
    // System.arraycopy(new byte[4],0,TOTAL_HEADER,12,4); // appId 0 // can just
    // leave blank zero, cause java zeros everything automatically
  }

  protected CancellableTask getResponse(final InetSocketAddress address,
      final Message message, final Continuation c) {
    // create reader and writer
    final SocketChannelWriter writer;
    final SocketChannelReader reader;
    writer = new SocketChannelWriter(environment, SourceRoute
        .build(new EpochInetSocketAddress(address,
            EpochInetSocketAddress.EPOCH_UNKNOWN)));
    reader = new SocketChannelReader(environment, SourceRoute
        .build(new EpochInetSocketAddress(address,
            EpochInetSocketAddress.EPOCH_UNKNOWN)));
    writer.enqueue(TOTAL_HEADER);
    try {
      writer.enqueue(message);
    } catch (IOException ioe) {
      c.receiveException(ioe);
      return null;
    }

    // bind to the appropriate port
    try {
      final SocketChannel channel = SocketChannel.open();
      channel.configureBlocking(false);
      final SelectionKey key = environment.getSelectorManager().register(
          channel, new SelectionKeyHandler() {
            public void connect(SelectionKey key) {
              if (logger.level <= Logger.FINE)
                logger.log("SPNF.getResponse(" + address + "," + message
                    + ").connect()");
              try {
                if (channel.finishConnect())
                  key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);

                if (logger.level <= Logger.FINE)
                  logger
                      .log("(SPNF) Found connectable channel - completed connection");
                // channel.socket().connect(address, 20000);
                // channel.socket().setSoTimeout(20000);
              } catch (IOException ioe) {
                handleException(ioe);
              }
            }

            public void read(SelectionKey key) {
              if (logger.level <= Logger.FINE)
                logger.log("SPNF.getResponse(" + address + "," + message
                    + ").read()");
              try {
                SocketBuffer o = null;

                while (o == null) {
                  o = reader.read(channel);
                }
                channel.socket().close();
                channel.close();
                key.cancel();
                c.receiveResult(o.deserialize(deserializer));
              } catch (IOException ioe) {
                handleException(ioe);
              }
            }

            public void write(SelectionKey key) {
              if (logger.level <= Logger.FINE)
                logger.log("SPNF.getResponse(" + address + "," + message
                    + ").write()");
              try {
                if (writer.write(channel)) {
                  key.interestOps(SelectionKey.OP_READ);
                }
              } catch (IOException ioe) {
                handleException(ioe);
              }
            }

            public void handleException(Exception e) {
              try {
                channel.socket().close();
                channel.close();
                channel.keyFor(environment.getSelectorManager().getSelector())
                    .cancel();
              } catch (IOException ioe) {

                if (logger.level <= Logger.WARNING)
                  logger.logException("Error while trying requesting "
                      + message + " from " + address, e);
              } finally {
                c.receiveException(e);
              }
            }
          }, 0);

      if (logger.level <= Logger.FINE)
        logger.log("(SPNF) Initiating socket connection to address " + address);

      if (channel.connect(address))
        key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
      else
        key.interestOps(SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE
            | SelectionKey.OP_READ);

      return new CancellableTask() {
        public void run() {
        }

        public boolean cancel() {
          environment.getSelectorManager().invoke(new Runnable() {
            public void run() {

              try {
                synchronized (key) {
                  channel.socket().close();
                  channel.close();
                  // if (logger.level <= Logger.WARNING) {
                  // if (!environment.getSelectorManager().isSelectorThread()) {
                  // logger.logException("WARNING: cancelling key:"+key+" on the
                  // wrong thread.", new Exception("Stack Trace"));
                  // }
                  // }
                  key.cancel();
                }
                // return true;
              } catch (Exception ioe) {
                if (logger.level <= Logger.WARNING)
                  logger.logException("Error cancelling task.", ioe);
                // return false;
              }
            }
          });
          return true;
        }

        public long scheduledExecutionTime() {
          return 0;
        }
      };
    } catch (IOException ioe) {
      c.receiveException(ioe);
      return null;
    }
  }

  /**
   * Method which constructs an InetSocketAddres for the local host with the
   * specifed port number.
   * 
   * @param portNumber The port number to create the address at.
   * @return An InetSocketAddress at the localhost with port portNumber.
   */
  private EpochInetSocketAddress getEpochAddress(int portNumber, long epoch) {
    EpochInetSocketAddress result = null;

    result = new EpochInetSocketAddress(new InetSocketAddress(localAddress,
        portNumber), epoch);
    return result;
  }

  /**
   * Method which can be used to test the connectivity contstrains of the local
   * node. This (optional) method is designed to be called by applications to
   * ensure that the local node is able to connect through the network - checks
   * can be done to check TCP/UDP connectivity, firewall setup, etc...
   * 
   * If the method works, then nothing should be done and the method should
   * return. If an error condition is detected, an exception should be thrown.
   */
  public static InetSocketAddress verifyConnection(int timeout,
      InetSocketAddress local, InetSocketAddress[] existing, Environment env,
      Logger logger) throws IOException {
    if (logger.level <= Logger.INFO)
      logger.log("Verifying connection of local node " + local + " using "
          + existing[0] + " and " + existing.length + " more");
    DatagramSocket socket = null;

    try {
      socket = new DatagramSocket(local);
      socket.setSoTimeout(timeout);

      for (int i = 0; i < existing.length; i++) {
        // byte[] buf = PingManager
        // .addHeader(SourceRoute
        // .build(new EpochInetSocketAddress(existing[i])),
        // new IPAddressRequestMessage(env.getTimeSource()
        // .currentTimeMillis()), new EpochInetSocketAddress(local),
        // env, logger);
        SocketBuffer sb = new SocketBuffer(
            new EpochInetSocketAddress(local),
            SourceRoute.build(new EpochInetSocketAddress(existing[i])),
            new IPAddressRequestMessage(env.getTimeSource().currentTimeMillis()));
        DatagramPacket send = new DatagramPacket(sb.getBuffer().array(), sb
            .getBuffer().limit(), existing[i]);
        socket.send(send);
      }

      DatagramPacket receive = new DatagramPacket(new byte[10000], 10000);
      socket.receive(receive);

      byte[] data = new byte[receive.getLength() - 38];
      System.arraycopy(receive.getData(), 38, data, 0, data.length);

      return ((IPAddressResponseMessage) new SocketBuffer(data)
          .deserialize(new PingManager.PMDeserializer(logger))).getAddress();

      // return ((IPAddressResponseMessage) PingManager.deserialize(data, env,
      // null, logger)).getAddress();
    } finally {
      if (socket != null)
        socket.close();
    }
  }

  boolean searchedForFireWall = false;

  InternetGatewayDevice fireWall;

  InetAddress fireWallExternalAddress;

  public synchronized InetAddress findFireWall(InetAddress bindAddress)
      throws IOException, UPNPResponseException {
    NetworkInterface ni = NetworkInterface.getByInetAddress(bindAddress);
    if (searchedForFireWall)
      return fireWallExternalAddress;
    searchedForFireWall = true;
    int discoveryTimeout = environment.getParameters().getInt(
        "nat_discovery_timeout");

    InternetGatewayDevice[] IGDs = InternetGatewayDevice
        .getDevices(discoveryTimeout);
    // use this code with the next version of sbbi's upnp library, it will only
    // search for the firewall on the given NetworkInterface
    // InternetGatewayDevice[] IGDs =
    // InternetGatewayDevice.getDevices(discoveryTimeout,
    // Discovery.DEFAULT_TTL,
    // Discovery.DEFAULT_MX,
    // ni);
    if (IGDs != null) {
      // no idea how to handle this if there are 2 firewalls... handle the first
      // one
      // if they have that interesting of a network, then they know what they
      // are doing
      // and can configure port forwarding
      fireWall = IGDs[0];
      fireWallExternalAddress = InetAddress.getByName(fireWall
          .getExternalIPAddress());
    } else {
      throw new IOException(
          "Could not find firewall.  Please enable UPnP on firewall, or set 'find_firewall_policy = never'");
    }
    return fireWallExternalAddress;
  }

  /**
   * Returns the external port to use based on querying the firewall for both
   * TCP and UDP
   * 
   * @param internal // the internal port we expect it to be mapped to
   * @param external // the first port to try, will search by incrementing
   * @return
   * @throws IOException
   * @throws UPNPResponseException
   */
  int findPortTries = 0;

  public int findAvailableFireWallPort(int internal, int external)
      throws IOException, UPNPResponseException {
    findPortTries++;
    if (findPortTries > environment.getParameters().getInt(
        "nat_find_port_max_tries"))
      throw new IOException("Couldn't find available port on firewall");
    if (logger.level <= Logger.FINE)
      logger.log("findFireWallPort(" + internal + "," + external + ")");
    String appName = environment.getParameters().getString("nat_app_name");
    ActionResponse response = null;
    response = fireWall.getSpecificPortMappingEntry(null, external, "TCP");
    if (checkSpecificPortMappingEntryResponse(response, internal, external,
        "TCP", appName)) {
      // continue
    } else {
      return findAvailableFireWallPort(internal, external + 1);
    }

    response = fireWall.getSpecificPortMappingEntry(null, external, "UDP");
    if (checkSpecificPortMappingEntryResponse(response, internal, external,
        "UDP", appName)) {
      // continue
    } else {
      return findAvailableFireWallPort(internal, external + 1);
    }
    return external;
  }

  public static final int MAX_PORT = 65535;

  /**
   * We return true if the response matches our app exactly, or if the entries
   * are invalid (because some firewalls do this instad of returning an error
   * that there is no entry.)
   * 
   * @param response
   * @param internal
   * @param external
   * @param type TCP or UDP
   * @return
   */
  private boolean checkSpecificPortMappingEntryResponse(
      ActionResponse response, int internal, int external, String type,
      String app) {
    if (response == null)
      return true;

    if (logger.level <= Logger.FINEST) {
      Set s = response.getOutActionArgumentNames();
      Iterator i = s.iterator();
      while (i.hasNext()) {
        String key = (String) i.next();
        String val = response.getOutActionArgumentValue(key);
        System.out.println("  " + key + " -> " + val);
      }
    }

    boolean ret = true;
    // NewInternalPort->9001
    String internalPortStr = response
        .getOutActionArgumentValue("NewInternalPort");
    if (internalPortStr != null) {
      try {
        int internalPort = Integer.parseInt(internalPortStr);
        if (internalPort > MAX_PORT) {
          if (logger.level < Logger.WARNING)
            logger
                .log("Warning, NAT "
                    + fireWall.getIGDRootDevice().getModelName()
                    + " returned an invalid value for entry NewInternalPort.  Expected an integer less than "
                    + MAX_PORT + ", got: " + internalPort + ".  Query "
                    + external + ":" + type + "... overwriting entry.");
          return true; // a bogus entry from the firewall,
        }
        if (internalPort != internal) {
          if (logger.level <= Logger.FINER)
            logger.log("internalPort(" + internalPort + ") != internal("
                + internal + ")");
          ret = false; // but don't return yet, maybe it will become more clear
                        // with another clue
        }
      } catch (NumberFormatException nfe) {
        // firewall bug, assume we can overwrite
        if (logger.level < Logger.WARNING)
          logger
              .log("Warning, NAT "
                  + fireWall.getIGDRootDevice().getModelName()
                  + " returned an invalid value for entry NewInternalPort.  Expected an integer, got: "
                  + internalPortStr + ".  Query " + external + ":" + type
                  + "... overwriting entry.");
        return true;
      }
    }

    // NewPortMappingDescription->freepastry
    String appName = response
        .getOutActionArgumentValue("NewPortMappingDescription");
    if (appName != null) {
      // this is in case the app name is just a bunch of spaces
      String tempName = appName.replaceAll(" ", "");
      if ((tempName.equals("")) || appName.equalsIgnoreCase(app)) {
        // do nothing yet
      } else {
        if (logger.level <= Logger.FINER)
          logger.log("appName(" + appName + ") != app(" + app + ")");
        ret = false;
      }
    }

    // NewInternalClient->192.168.1.64
    String newInternalClientString = response
        .getOutActionArgumentValue("NewInternalClient");
    if (newInternalClientString == null) {
      if (logger.level < Logger.WARNING)
        logger
            .log("Warning, NAT "
                + fireWall.getIGDRootDevice().getModelName()
                + " returned no value for entry NewInternalClient.  Expected an IP address, got: "
                + newInternalClientString + ".  Query " + external + ":" + type
                + "... overwriting entry.");
      return true;
    }
    try {
      InetAddress client = InetAddress.getByName(newInternalClientString);
      if (!client.equals(localAddress)) {
        if (logger.level <= Logger.FINER)
          logger.log("client(" + client + ") != localAddress(" + localAddress
              + ")");
        ret = false;
      }
    } catch (Exception e) {
      if (logger.level < Logger.WARNING)
        logger
            .log("Warning, NAT "
                + fireWall.getIGDRootDevice().getModelName()
                + " returned an invalid value for entry NewInternalClient.  Expected an IP address, got: "
                + newInternalClientString + ".  Query " + external + ":" + type
                + "... overwriting entry.");
      return true;
    }

    // NewEnabled->1
    String enabledString = response.getOutActionArgumentValue("NewEnabled");
    if (enabledString != null) {
      try {
        int enabled = Integer.parseInt(enabledString);
        if (enabled == 0) {
          if (logger.level < Logger.FINE)
            logger
                .log("Warning, NAT "
                    + fireWall.getIGDRootDevice().getModelName()
                    + " had an existing rule that was disabled, implicitly overwriting.  "
                    + "Query " + external + ":" + type + "."
                    + "\n  NewInternalPort -> " + internalPortStr
                    + "\n  NewPortMappingDescription -> " + appName
                    + "\n  NewInternalClient -> " + newInternalClientString
                    + "\n  NewEnabled -> " + enabledString);
          return true; // the current rule is not used, go ahead and overwrite
        } else if (enabled == 1) {
          // nothing, depend on previous settings of ret to determine what to do
        } else {
          if (logger.level < Logger.WARNING)
            logger
                .log("Warning, NAT "
                    + fireWall.getIGDRootDevice().getModelName()
                    + " returned an invalid value for entry NewEnabled.  Expected 0 or 1, got: "
                    + enabled + ".  Query " + external + ":" + type
                    + "... overwriting entry.");
          return true; // a bogus entry from the firewall,
        }
      } catch (NumberFormatException nfe) {
        // firewall bug, assume we can overwrite
        if (logger.level < Logger.WARNING)
          logger
              .log("Warning, NAT "
                  + fireWall.getIGDRootDevice().getModelName()
                  + " returned an invalid value for entry NewEnabled.  Expected 0 or 1, got: "
                  + enabledString + ".  Query " + external + ":" + type
                  + "... overwriting entry.");
        return true;
      }
    } else {
      // router didn't specify enable string, no info, do nothing
    }

    if (ret == false) {
      if (logger.level < Logger.INFO)
        logger.log("Warning, NAT " + fireWall.getIGDRootDevice().getModelName()
            + " had an existing rule, trying different port.  " + "Query "
            + external + ":" + type + "." + "\n  NewInternalPort -> "
            + internalPortStr + "\n  NewPortMappingDescription -> " + appName
            + "\n  NewInternalClient -> " + newInternalClientString
            + "\n  NewEnabled -> " + enabledString);
    }
    return ret;
  }

  public void openFireWallPort(int local, int external) throws IOException,
      UPNPResponseException {
    boolean mapped = true;
    mapped = fireWall.addPortMapping(environment.getParameters().getString(
        "nat_app_name"), null, local, external, localAddress.getHostAddress(),
        0, "TCP");
    if (!mapped)
      throw new IOException(
          "Could not set firewall TCP port forwarding from external:"
              + fireWallExternalAddress + ":" + external + " -> local:"
              + localAddress + ":" + local);
    mapped = fireWall.addPortMapping(environment.getParameters().getString(
        "nat_app_name"), null, local, external, localAddress.getHostAddress(),
        0, "UDP");
    if (!mapped)
      throw new IOException(
          "Could not set firewall UDP port forwarding from external:"
              + fireWallExternalAddress + ":" + external + " -> local:"
              + localAddress + ":" + local);
  }
}
