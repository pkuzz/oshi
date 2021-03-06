/**
 * Oshi (https://github.com/dblock/oshi)
 *
 * Copyright (c) 2010 - 2017 The Oshi Project Team
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Maintainers:
 * dblock[at]dblock[dot]org
 * widdis[at]gmail[dot]com
 * enrico.bianchi[at]gmail[dot]com
 *
 * Contributors:
 * https://github.com/dblock/oshi/graphs/contributors
 */
package oshi.json.software.os;

import oshi.json.json.OshiJsonObject;

/**
 * NetworkParams presents network parameters of running OS, such as DNS, host
 * name etc.
 */
public interface NetworkParams extends OshiJsonObject {

    /**
     * @return Gets host name
     */
    String getHostName();

    /**
     * @return Gets domain name
     */
    String getDomainName();

    /**
     * @return Gets DNS servers
     */
    String[] getDnsServers();

    /**
     * @return Gets default gateway(routing destination for 0.0.0.0/0) for IPv4,
     *         empty string if not defined.
     */
    String getIpv4DefaultGateway();

    /**
     * @return Gets default gateway(routing destination for ::/0) for IPv6,
     *         empty string if not defined.
     */
    String getIpv6DefaultGateway();
}
