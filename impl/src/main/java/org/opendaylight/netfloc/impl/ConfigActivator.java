/*
 * Copyright (c) ZHAW and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netfloc.impl;

import java.util.Dictionary;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import org.opendaylight.neutron.spi.*;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigActivator implements BundleActivator {

	private static final Logger LOG = LoggerFactory.getLogger(NetflocProvider.class);
	private ProviderContext providerContext;
	private List<ServiceRegistration<?>> registrations = new ArrayList<ServiceRegistration<?>>();

	public ConfigActivator(ProviderContext providerContext) {
		this.providerContext = providerContext;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		LOG.info("ConfigActivator start");

		NetworkGraph network = new NetworkGraph();

		Dictionary<String, Object> floatingIPHandlerProperties = new Hashtable<>();
		FloatingIPHandler floatingIPHandler = new FloatingIPHandler();
		registerService(context,
				new String[] {INeutronFloatingIPAware.class.getName()},
				floatingIPHandlerProperties, floatingIPHandler);

		Dictionary<String, Object> networkHandlerProperties = new Hashtable<>();
		// final?
		NetworkHandler networkHandler = new NetworkHandler();
		registerService(context,
				new String[]{INeutronNetworkAware.class.getName()},
				networkHandlerProperties, networkHandler);

		Dictionary<String, Object> subnetHandlerProperties = new Hashtable<>();
		SubnetHandler subnetHandler = new SubnetHandler();
		registerService(context,
				new String[] {INeutronSubnetAware.class.getName()},
				subnetHandlerProperties, subnetHandler);

		Dictionary<String, Object> portHandlerProperties = new Hashtable<>();
		PortHandler portHandler = new PortHandler(network);
		registerService(context,
				new String[]{INeutronPortAware.class.getName()},
				portHandlerProperties, portHandler);

		Dictionary<String, Object> routerHandlerProperties = new Hashtable<>();
		RouterHandler routerHandler = new RouterHandler();
		registerService(context,
				new String[]{INeutronRouterAware.class.getName()},
				routerHandlerProperties, routerHandler);

		OvsdbDataChangeListener ovsdbDataChangeListener = new OvsdbDataChangeListener(providerContext.getSALService(DataBroker.class), network);
        registerService(context,
                new String[] {OvsdbDataChangeListener.class.getName()}, null, ovsdbDataChangeListener);

        final NotificationProviderService notificationService = providerContext.getSALService(NotificationProviderService.class);

        LinkDiscoveryListener linkDiscoveryListener = new LinkDiscoveryListener();
        notificationService.registerNotificationListener(linkDiscoveryListener);

        LinkDataChangeListener linkDataChangeListener = new LinkDataChangeListener(providerContext.getSALService(DataBroker.class), network);
        registerService(context,
                new String[] {LinkDataChangeListener.class.getName()}, null, linkDataChangeListener);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		LOG.info("ConfigActivator stop");
	}

	private ServiceRegistration<?> registerService(BundleContext bundleContext, String[] interfaces,
												   Dictionary<String, Object> properties, Object impl) {
		ServiceRegistration<?> serviceRegistration = bundleContext.registerService(interfaces, impl, properties);
		if (serviceRegistration != null) {
			registrations.add(serviceRegistration);
		}
		return serviceRegistration;
	}

}
