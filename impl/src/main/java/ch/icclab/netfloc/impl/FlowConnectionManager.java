/*
 * Copyright (c) ZHAW and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package ch.icclab.netfloc.impl;

import ch.icclab.netfloc.iface.INetworkPath;
import ch.icclab.netfloc.iface.IBridgeOperator;
import ch.icclab.netfloc.iface.IPortOperator;
import ch.icclab.netfloc.iface.IBridgeListener;
import ch.icclab.netfloc.iface.IBroadcastListener;
import ch.icclab.netfloc.iface.IFlowBroadcastPattern;
import ch.icclab.netfloc.iface.IFlowPathPattern;
import ch.icclab.netfloc.iface.IFlowChainPattern;
import ch.icclab.netfloc.iface.IFlowBridgePattern;
import ch.icclab.netfloc.iface.INetworkPathListener;
import ch.icclab.netfloc.iface.IServiceChainListener;
import ch.icclab.netfloc.iface.IFlowprogrammer;
import ch.icclab.netfloc.iface.IServiceChain;
import ch.icclab.netfloc.iface.IMacLearningListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import com.google.common.util.concurrent.FutureCallback;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowConnectionManager implements IBroadcastListener, INetworkPathListener, IBridgeListener, IServiceChainListener {
	
	private static final Logger LOG = LoggerFactory.getLogger(FlowConnectionManager.class);
	// how do we decide which pattern to map to which path?
	private List<INetworkPath> networkPaths = new LinkedList<INetworkPath>();
	private List<IFlowPathPattern> flowPathPatterns = new LinkedList<IFlowPathPattern>();
	private List<IFlowBroadcastPattern> broadcastPatterns = new LinkedList<IFlowBroadcastPattern>();
	private List<IFlowChainPattern> flowChainPatterns = new LinkedList<IFlowChainPattern>();

	// how do we decide which pattern to map to which bridge?
	// mb reference them with strings or sth (map) ???
	private List<IBridgeOperator> bridges = new LinkedList<IBridgeOperator>();
	private List<IFlowBridgePattern> flowBridgePatterns = new LinkedList<IFlowBridgePattern>();

	private final Map<Set<INetworkPath>, IFlowBroadcastPattern> programBroadcastSuccess = new HashMap<Set<INetworkPath>, IFlowBroadcastPattern>();
	private final Map<Set<INetworkPath>, IFlowBroadcastPattern> programBroadcastFailure = new HashMap<Set<INetworkPath>, IFlowBroadcastPattern>();
	private final Map<INetworkPath, IFlowPathPattern> programPathSuccess = new HashMap<INetworkPath, IFlowPathPattern>();
	private final Map<INetworkPath, IFlowPathPattern> programPathFailure = new HashMap<INetworkPath, IFlowPathPattern>();
	private final Map<IServiceChain, IFlowChainPattern> programChainSuccess = new HashMap<IServiceChain, IFlowChainPattern>();
	private final Map<IServiceChain, IFlowChainPattern> programChainFailure = new HashMap<IServiceChain, IFlowChainPattern>();
	private final Map<IBridgeOperator, IFlowBridgePattern> programBridgeSuccess = new HashMap<IBridgeOperator, IFlowBridgePattern>();
	private final Map<IBridgeOperator, IFlowBridgePattern> programBridgeFailure = new HashMap<IBridgeOperator, IFlowBridgePattern>();

	private final Map<Integer, IMacLearningListener> macListeners = new HashMap<Integer, IMacLearningListener>();

	private IFlowprogrammer flowprogrammer;
	private ReactiveFlowListener reactiveFlowListener;

	// todo singleton
	public FlowConnectionManager(IFlowprogrammer flowprogrammer, ReactiveFlowListener reactiveFlowListener) {
		this.flowprogrammer = flowprogrammer;
		this.reactiveFlowListener = reactiveFlowListener;
	}

	// API
	public IFlowPathPattern getSuccessfulConnection(INetworkPath np) {
		return this.programPathSuccess.get(np);
	}

	public IFlowPathPattern getFailedConnection(INetworkPath np) {
		return this.programPathFailure.get(np);
	}

	public IFlowChainPattern getSuccessfulChainConnection(IServiceChain nc) {
		return this.programChainSuccess.get(nc);
	}

	public IFlowChainPattern getFailedChainConnection(IServiceChain nc) {
		return this.programChainFailure.get(nc);
	}

	public void registerPathPattern(IFlowPathPattern pattern) {
		// currently we have no way to use more than one pattern (TODO)
		this.flowPathPatterns.add(pattern);
	}

	public void registerChainPattern(IFlowChainPattern pattern) {
		this.flowChainPatterns.add(pattern);
	}

	public void registerBridgePattern(IFlowBridgePattern pattern) {
		this.flowBridgePatterns.add(pattern);
	}

	public void registerBroadcastPattern(IFlowBroadcastPattern pattern) {
		this.broadcastPatterns.add(pattern);
	}

	@Override
	public void broadcastCreated(final Set<INetworkPath> nps) {
		final IFlowBroadcastPattern pattern = this.broadcastPatterns.get(0);

		this.programBroadcastFlows(nps, pattern);
	}

	@Override
	public void broadcastDeleted(final Set<INetworkPath> nps) {
		final IFlowBroadcastPattern pattern = this.broadcastPatterns.get(0);

		this.checkBroadcastFlowsDelete(nps, pattern);
	}


	private void programBroadcastFlows(final Set<INetworkPath> nps, final IFlowBroadcastPattern pattern) {
		for (Map.Entry<IBridgeOperator, List<Flow>> flowEntry : pattern.apply(nps).entrySet()) {
			for (Flow flow : flowEntry.getValue()) {
				flowprogrammer.programFlow(flow, flowEntry.getKey(), new FutureCallback<Void>() {
					public void onSuccess(Void result) {
						programBroadcastSuccess.put(nps, pattern);
					}

					public void onFailure(Throwable t) {
						programBroadcastFailure.put(nps, pattern);
					}
				});
			}
		}
	}

	private void deleteBroadcastFlows(final Set<INetworkPath> nps, final IFlowBroadcastPattern pattern) {
		for (Map.Entry<IBridgeOperator, List<Flow>> flowEntry : pattern.apply(nps).entrySet()) {
			for (Flow flow : flowEntry.getValue()) {
				flowprogrammer.deleteFlow(flow, flowEntry.getKey(), new FutureCallback<Void>() {
					public void onSuccess(Void result) {
						programBroadcastSuccess.remove(nps);
					}

					public void onFailure(Throwable t) {
						// todo
						//deleteBroadcastFailure.put(nps, pattern);
					}
				});
			}
		}
	}

	private void checkBroadcastFlowsDelete(final Set<INetworkPath> nps, final IFlowBroadcastPattern pattern) {
		List<Set<INetworkPath>> toDelete = new LinkedList<Set<INetworkPath>>();
		for (Map.Entry<Set<INetworkPath>, IFlowBroadcastPattern> successEntry : this.programBroadcastSuccess.entrySet()) {

			if (!successEntry.getValue().equals(pattern)) {
				continue;
			}

			boolean found = false;
			for (INetworkPath newPath : nps) {
				for (INetworkPath oldPath : successEntry.getKey()) {
					if (oldPath.getBeginPort().equals(newPath.getEndPort())) {
						toDelete.add(successEntry.getKey());
						found = true;
						break;
					}
				}
				if (found) {
					break;
				}
			}
		}

		for (Set<INetworkPath> deleteSet : toDelete) {
			this.deleteBroadcastFlows(deleteSet, pattern);
		}
	}

	@Override
	public void networkPathCreated(final INetworkPath np) {
		// TODO: decide which pattern
		final IFlowPathPattern pattern = this.flowPathPatterns.get(0);

		for (Map.Entry<IBridgeOperator, List<Flow>> flowEntry : pattern.apply(np).entrySet()) {
			for (Flow flow : flowEntry.getValue()) {
				flowprogrammer.programFlow(flow, flowEntry.getKey(), new FutureCallback<Void>() {
					public void onSuccess(Void result) {
						programPathSuccess.put(np, pattern);
					}

					public void onFailure(Throwable t) {
						programPathFailure.put(np, pattern);
					}
				});
			}
		}
	}

	@Override
	public void networkPathUpdated(final INetworkPath oldNp, final INetworkPath nNp) {
		// TODO: decide which pattern
		final IFlowPathPattern pattern = this.flowPathPatterns.get(0);

		for (Map.Entry<IBridgeOperator, List<Flow>> flowEntry : pattern.apply(oldNp).entrySet()) {
			for (Flow flow : flowEntry.getValue()) {
				flowprogrammer.deleteFlow(flow, flowEntry.getKey(), new FutureCallback<Void>() {
					public void onSuccess(Void result) {
						programPathSuccess.put(oldNp, pattern);
					}

					public void onFailure(Throwable t) {
						programPathFailure.put(oldNp, pattern);
					}
				});
			}
		}

		for (Map.Entry<IBridgeOperator, List<Flow>> flowEntry : pattern.apply(nNp).entrySet()) {
			for (Flow flow : flowEntry.getValue()) {
				flowprogrammer.programFlow(flow, flowEntry.getKey(), new FutureCallback<Void>() {
					public void onSuccess(Void result) {
						programPathSuccess.put(nNp, pattern);
					}

					public void onFailure(Throwable t) {
						programPathFailure.put(nNp, pattern);
					}
				});
			}
		}
	}
	
	@Override
	public void networkPathDeleted(final INetworkPath np) {
		// TODO: decide which pattern
		final IFlowPathPattern pattern = this.flowPathPatterns.get(0);

		for (Map.Entry<IBridgeOperator, List<Flow>> flowEntry : pattern.apply(np).entrySet()) {
			for (Flow flow : flowEntry.getValue()) {
				flowprogrammer.deleteFlow(flow, flowEntry.getKey(), new FutureCallback<Void>() {
					public void onSuccess(Void result) {
						programPathSuccess.put(np, pattern);
					}
 
					public void onFailure(Throwable t) {
						programPathFailure.put(np, pattern);
					}
				});
			}
		}
	}

	@Override
	public void serviceChainCreated(final IServiceChain sc) {

		final IFlowChainPattern pattern = this.flowChainPatterns.get(1);

		LOG.info("Chain in FlowConnectionManager: {}", sc);
		LOG.info("Pattern FlowConnectionManager: {}", pattern);

		for (Map<IBridgeOperator, List<Flow>> map : pattern.apply(sc)) {

			for (Map.Entry<IBridgeOperator, List<Flow>> flowEntry : map.entrySet()) {
				for (Flow flow : flowEntry.getValue()) {
					flowprogrammer.programFlow(flow, flowEntry.getKey(), new FutureCallback<Void>() {
						public void onSuccess(Void result) {
							programChainSuccess.put(sc, pattern);
						}

						public void onFailure(Throwable t) {
							programChainFailure.put(sc, pattern);
						}
					});
				}
			}
		}

		// TODO: parameterize this and add a check or sth. this should be tied to the flow pattern somehow
		// for now we just keep this here for testing purposee
		IPortOperator beginBridgeEndPort = (sc.getBegin().getBegin().equals(sc.getBegin().getEnd())) ? sc.getBegin().getEndPort() : sc.getBegin().getNextLink(sc.getBegin().getBegin());
		IPortOperator endBridgeBeginPort = (sc.getEnd().getBegin().equals(sc.getEnd().getEnd())) ? sc.getEnd().getBeginPort() : sc.getEnd().getPreviousLink(sc.getEnd().getEnd());
		IMacLearningListener reactiveFlowWriter = new ServiceChainMacLearningFlowWriter(
			sc.getChainId(),
			sc.getBegin().getBegin(),
			sc.getEnd().getEnd(),
			sc.getBegin().getBeginPort(),
			// bridge not path
			beginBridgeEndPort,
			// bridge not path
			endBridgeBeginPort,
			sc.getEnd().getEndPort(),
			(sc.getEnd().getBegin().equals(sc.getEnd().getEnd())) ? sc.getNumberHops() - 1 : sc.getNumberHops(),
			this.flowprogrammer
			);
		this.reactiveFlowListener.registerMacLearningListener(reactiveFlowWriter);
		this.macListeners.put(sc.getChainId(), reactiveFlowWriter);
	}

	@Override
	public void serviceChainDeleted(final IServiceChain sc) {

		final IFlowChainPattern pattern = this.flowChainPatterns.get(1);		

		for (Map<IBridgeOperator, List<Flow>> map : pattern.apply(sc)) {

			for (Map.Entry<IBridgeOperator, List<Flow>> flowEntry : map.entrySet()) {
				for (Flow flow : flowEntry.getValue()) {
					flowprogrammer.deleteFlow(flow, flowEntry.getKey(), new FutureCallback<Void>() {
						public void onSuccess(Void result) {
							programChainSuccess.remove(sc);
						}

						public void onFailure(Throwable t) {
							programChainFailure.put(sc, pattern);
						}
					});
				}
			}
		}
		IMacLearningListener listener = this.macListeners.get(sc.getChainId());
		if (listener != null) {
			// delete the listeners flows first
			listener.shutDown();
			this.reactiveFlowListener.unregisterMacLearningListener(listener);
			this.macListeners.remove(sc.getChainId());
		}
	}
	
	@Override	
	public void bridgeCreated(final IBridgeOperator bo) {
		final IFlowBridgePattern pattern = this.flowBridgePatterns.get(0);

		for (Flow flow : pattern.apply(bo)) {
			flowprogrammer.programFlow(flow, bo, new FutureCallback<Void>() {
				public void onSuccess(Void result) {
					programBridgeSuccess.put(bo, pattern);
				}

				public void onFailure(Throwable t) {
					programBridgeFailure.put(bo, pattern);
				}
			});
		}
	}
	
	@Override		
	public void bridgeUpdated(final IBridgeOperator oldBo, final IBridgeOperator nBo) {
		// not needed?
	}
	
	@Override		
	public void bridgeDeleted(final IBridgeOperator bo) {
		// not needed?
	}

}