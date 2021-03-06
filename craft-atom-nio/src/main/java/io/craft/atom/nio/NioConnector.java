package io.craft.atom.nio;

import io.craft.atom.io.Channel;
import io.craft.atom.io.IoConnector;
import io.craft.atom.io.IoConnectorX;
import io.craft.atom.io.IoHandler;
import io.craft.atom.io.IoReactorX;
import io.craft.atom.nio.api.NioConnectorConfig;
import io.craft.atom.nio.spi.NioBufferSizePredictorFactory;
import io.craft.atom.nio.spi.NioChannelEventDispatcher;
import io.craft.atom.util.thread.NamedThreadFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Selector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import lombok.ToString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects to server based on TCP or UDP, communicates with the server, and fires events.
 * 
 * @author mindwind
 * @version 1.0, Feb 24, 2013
 * @see NioTcpConnector
 */
@ToString(callSuper = true, of = { "config" })
abstract public class NioConnector extends NioReactor implements IoConnector {
	
	
	private static final Logger LOG = LoggerFactory.getLogger(NioConnector.class);
	
	
	protected final    NioConnectorConfig config                                                                                             ;                                                                                          ;                                                                                             ;
	protected final    ExecutorService    executorService = Executors.newCachedThreadPool(new NamedThreadFactory("craft-atom-nio-connector"));
	protected volatile boolean            selectable      = false                                                                            ;
	protected volatile boolean            shutdown        = false                                                                            ;
	protected volatile Selector           selector                                                                                           ;
	
	
	// ~ ------------------------------------------------------------------------------------------------------------
	
	
	/**
	 * Constructs a new connector with default configuration.
	 *  
	 * @param handler
	 */
	public NioConnector(IoHandler handler) {
		this(handler, new NioConnectorConfig());
	}
	
	/**
	 * Constructs a new connector with the specified configuration.
	 *  
	 * @param handler
	 * @param config
	 */
	public NioConnector(IoHandler handler, NioConnectorConfig config) {
		this(handler, config, new NioOrderedDirectChannelEventDispatcher(config.getTotalEventSize()), new NioAdaptiveBufferSizePredictorFactory());
	}
	
	/**
	 * Constructs a new connector with the specified configuration and dispatcher.
	 * 
	 * @param handler
	 * @param config
	 * @param dispatcher
	 */
	public NioConnector(IoHandler handler, NioConnectorConfig config, NioChannelEventDispatcher dispatcher) {
		this(handler, config, dispatcher, new NioAdaptiveBufferSizePredictorFactory());
	}
	
	/**
	 * Constructs a new connector with the specified configuration, dispatcher and predictor.
	 * 
	 * @param handler
	 * @param config
	 * @param dispatcher
	 * @param predictorFactory
	 */
	public NioConnector(IoHandler handler, NioConnectorConfig config, NioChannelEventDispatcher dispatcher, NioBufferSizePredictorFactory predictorFactory) {
		if (handler == null) {
			throw new IllegalArgumentException("Handler should not be null!");
		}
		
		this.config = (config == null ? new NioConnectorConfig() : config);
		this.handler = handler;
		this.dispatcher = dispatcher;
		this.predictorFactory = predictorFactory;
		this.pool = new NioProcessorPool(config, handler, dispatcher);
		try {
			init();
		} catch (IOException e) {
			throw new RuntimeException("Failed to construct.", e);
		} finally {
			if (!selectable && selector != null) {
				try {
					selector.close();
				} catch (IOException e) {
					LOG.warn("[CRAFT-ATOM-NIO] Close selector exception", e);
				}
			}
		}
	}
	
	
	// ~ ------------------------------------------------------------------------------------------------------------
	
	
	private void init() throws IOException {
		selector = Selector.open();
		selectable = true;
	}
	
	@Override
	public Future<Channel<byte[]>> connect(String ip, int port) throws IOException {
		SocketAddress remoteAddress = new InetSocketAddress(ip, port);
		return connect(remoteAddress);
	}
	
	@Override
	public Future<Channel<byte[]>> connect(SocketAddress remoteAddress) throws IOException {
        return connect(remoteAddress, null);
    }
	
	@Override
	public Future<Channel<byte[]>> connect(SocketAddress remoteAddress, SocketAddress localAddress) throws IOException {
		if (!this.selectable) {
			throw new IllegalStateException("The connector is already shutdown.");
		}

		if (remoteAddress == null) {
			throw new IllegalArgumentException("Remote address is null.");
		}
		
		if (handler == null) {
			throw new IllegalStateException("Handler is not be set!");
		}
		
		return connectByProtocol(remoteAddress, localAddress);
	}
	
	@Override
	public void shutdown() {
		this.selectable = false;
		this.shutdown = true;
		this.selector.wakeup();
	}
	
	@Override
	public IoConnectorX x() {
		NioConnectorX x = new NioConnectorX();
		IoReactorX rx = super.x();
		x.setNewChannelCount(rx.newChannelCount());
		x.setFlushingChannelCount(rx.flushingChannelCount());
		x.setClosingChannelCount(rx.closingChannelCount());
		x.setAliveChannelCount(rx.aliveChannelCount());
		xByProtocol(x);
		return x;
	}
	
	abstract protected Future<Channel<byte[]>> connectByProtocol(SocketAddress remoteAddress, SocketAddress localAddress) throws IOException;
	abstract protected void xByProtocol(NioConnectorX x);
}
