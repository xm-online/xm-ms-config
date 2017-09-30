package com.icthh.xm.ms.configuration.config;

import com.hazelcast.nio.Address;
import com.hazelcast.spi.discovery.DiscoveryNode;
import lombok.SneakyThrows;
import org.bitsofinfo.hazelcast.discovery.consul.BaseRegistrator;
import org.springframework.cloud.commons.util.InetUtils;

import java.util.Map;

public class CustomDiscoveryNodeRegistrator extends BaseRegistrator {

	/** properties that are supported in the JSON value for the 'consul-registrator-config' config property
	 *  in ADDITION to those defined in BaseRegistrator
	 */
	public static final String CONFIG_PROP_PREFER_PUBLIC_ADDRESS = "preferPublicAddress";

	@Override
	@SneakyThrows
	public Address determineMyLocalAddress(DiscoveryNode localDiscoveryNode, Map<String, Object> registratorConfig) {
		
		Address myLocalAddress = localDiscoveryNode.getPrivateAddress();
		 
		Object usePublicAddress = (Object)registratorConfig.get(CONFIG_PROP_PREFER_PUBLIC_ADDRESS);
		if (usePublicAddress != null && usePublicAddress instanceof Boolean && (Boolean)usePublicAddress) {
			logger.info("Registrator config property: " + CONFIG_PROP_PREFER_PUBLIC_ADDRESS +":"+usePublicAddress + " attempting to use it...");
			Address publicAddress = localDiscoveryNode.getPublicAddress();
			if (publicAddress != null) {
				myLocalAddress = publicAddress;
			}
		}

		return new Address(InetUtils.getFirstNonLoopbackHostInfo().getIpAddress(), myLocalAddress.getPort());
	}


}