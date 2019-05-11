/*
Copyright (c) 2018, Dr. Hans-Walter Latz
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * The name of the author may not be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY EXPRESS
OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package dev.hawala.xns;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import dev.hawala.xns.network.NetMachine;
import dev.hawala.xns.network.NetSwitch;

/**
 * (probably obsolete)
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2016-2018)
 */
public class Network {
	
	private final static long networkId;
	private final static String networkName;
	
	private final static NetMachine defaultMachine;
	
	private final static NetSwitch netSwitch;
	
	static {
		PropertiesExt props = new PropertiesExt();
		props.setProperty("netdevice", "tap0");
		props.setProperty("network.id", "3333"); // (hex)
		props.setProperty("network.name", "no:where");
		props.setProperty("host.0.name", "unknown");
		props.setProperty("host.0.address", "11.00.00.00.00.00");
		
		InputStream propsStream = Network.class.getClassLoader().getResourceAsStream("network.properties");
		if (propsStream != null) {
			try {
				props.load(propsStream);
				propsStream.close();
			} catch (IOException e) {
				// ignored
			}
		}
		
		networkId = props.getHexLong("network.id", 0x3333L);
		networkName = props.getString("network.name", "no:where");
		
		netSwitch = new NetSwitch(networkId, null);
		defaultMachine  = null; // TODO
	}
	
	public static long getNetworkId() {
		return networkId;
	}

	public static iSppSocket sppListen(int localPort) {
		return null; // FIXME
	}

	public static iSppSocket sppListen(String localHostName, int localPort) {
		return null; // FIXME
	}
	
	public static iSppSocket sppConnect(String remoteHostName, int remotePort) {
		return null; // FIXME
	}
	
	public static iSppSocket sppConnect(long remoteHostMacAddress, int remotePort) {
		return null; // FIXME
	}
	
	public static iSppSocket sppConnect(String localHostName, String remoteHostName, int remotePort) {
		return null; // FIXME
	}
	
	public static iSppSocket sppConnect(String localHostName, long remoteHostMacAddress, int remotePort) {
		return null; // FIXME
	} 
	
	/**
	 * Extension of the standard Java Properties class, allowing to get special values resp. 
	 * values with defaults. 
	 * 
	 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
	 *
	 */
	private static class PropertiesExt extends Properties {
		private static final long serialVersionUID = -2593299943684111928L;
		
		public String getString(String name, String defValue) {
			if (!this.containsKey(name)) { return defValue; }
			String val = this.getProperty(name);
			if (val == null || val.length() == 0) { return null; }
			return val;
		}
		
		public String getString(String name) {
			return this.getString(name, "");
		}
		
		public Integer getInt(String name, Integer defValue, int base) {
			if (!this.containsKey(name)) { return defValue; }
			try {
				return Integer.parseInt(this.getProperty(name), base);
			} catch (NumberFormatException exc) {
				return defValue;
			}
		}
		
		public Integer getInt(String name) {
			return this.getInt(name, null, 10);
		}
		
		public Integer getHexInt(String name) {
			return this.getInt(name, null, 16);
		}
		
		public Long getLong(String name, Long defValue, int base) {
			if (!this.containsKey(name)) { return defValue; }
			try {
				return Long.parseLong(this.getProperty(name), base);
			} catch (NumberFormatException exc) {
				return defValue;
			}
		}
		
		public Long getLong(String name) {
			return this.getLong(name, null, 10);
		}
		
		public Long getHexLong(String name, Long defaultValue) {
			return this.getLong(name, defaultValue, 16);
		}
		
		public Long getAddress(String name) {
			if (!this.containsKey(name)) { return null; }
			String propValue = this.getProperty(name);
			if (propValue == null || propValue.isEmpty()) { return null; }
			propValue = propValue.replaceAll("-", "");
			propValue = propValue.replaceAll(".", "");
			try {
				return Long.parseLong(propValue, 16);
			} catch (NumberFormatException exc) {
				return null;
			}
		}
		
		public boolean getBoolean(String name, boolean defValue) {
			if (!this.containsKey(name)) { return defValue; }
			String val = this.getProperty(name);
			if (val == null || val.length() == 0) { return false; }
			val = val.toLowerCase();
			return val.equals("true") || val.equals("yes") || val.equals("y");
		}
		
		public boolean getBoolean(String name) {
			return this.getBoolean(name, false);
		}
	}
}
