/*
Copyright (c) 2020, Dr. Hans-Walter Latz
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Management for machine names and IDs, allowing to use symbolic
 * names for processor-ids in Dodo configuration and CHS database files
 * as well as assigning machine specific parameters to several configuration
 * items. This allows Dodo to adapt to machines with different networking
 * speed or other characteristics (e.g. Darkstar clients with slow network
 * and inaccurate clock vs. clients on speedier emulators).
 * 
 * @author Dr. Hans-Walter Latz / Berlin 2020
 */
public class MachineIds {
	
	/*
	 * parameter names for potentially client specific configuration items
	 */
	
	/**
	 * Shall authentication skip the credentials expiration and verifier timestamp checks?
	 */
	public static final String CFG_AUTH_SKIP_TIMESTAMP_CHECKS = "authSkipTimestampChecks";
	
	/**
	 * Millisecond interval between data packets sent with the simple boot service protocol.
	 */
	public static final String CFG_BOOTSVC_SIMPLEDATA_SEND_INTERVAL = "bootService.simpleDataSendInterval";
	
	/**
	 * Millisecond wait interval when sending spp data packets with the spp boot service protocol.
	 */
	public static final String CFG_BOOTSVC_SPPDATA_SEND_INTERVAL = "bootService.sppDataSendInterval";
	
	/**
	 * Milliseconds to wait for transmitting next packet after having sent
	 * a packet.
	 * <br/>Default is 5 msecs.
	 */
	public static final String CFG_SPP_SENDING_TIME_GAP = "spp.sendingTimeGap";
	
	/**
	 * Number of check intervals after receiving the others send-ack before sending our ack packet.
	 * <br/>Default is 4, giving the local service 30..40 ms processing time to react on the ingone
	 * data and sending result data packets before the ack-packet from our side is sent.
	 */
	public static final String CFG_SPP_HANDSHAKE_SENDACK_COUNTDOWN = "spp.handshakeSendackCountdown";
	
	/**
	 * Milliseconds after a packet send before sending the acknowledge-request on missing acknowledge,
	 * initiating our resend standard procedure.
	 * <br/>Default is 20 msecs.
	 */
	public static final String CFG_SPP_RESEND_DELAY = "spp.resendDelay";
	
	/**
	 * Number of check intervals after sending our request for acknowledgment before
	 * starting resending our packets if no acknowledge arrives in the meantime.
	 * <br/> Default is 50, giving the other side about 500 ms before we resend some
	 * of the unacknowledged packets.
	 */
	public static final String CFG_SPP_HANDSHAKE_RESEND_COUNTDOWN = "spp.handshakeResendCountdown";
	
	/**
	 * Max. number of resend cycles without acknowledge from the other side before the
	 * connection is considered dead and hard-closed from this side.
	 * <br/>Default is 5.
	 */
	public static final String CFG_SPP_HANDSHAKE_MAX_RESENDS = "spp.handshakeMaxResends";
	
	/**
	 * Max. number of packets (starting with the oldest i.e. lowest sequence number) in
	 * one resend cycle.
	 * <br/>Default is 2.
	 */
	public static final String CFG_SPP_RESEND_PACKET_COUNT = "spp.resendPacketCount";
	
	/**
	 * PUP network number (high byte) and the PUP host number (low byte) in the format
	 * {@code octal-network#octal-host#}, where both *octal*-values are in the range 0..377 (octal).
	 * <br/>Default: (none)
	 */
	public static final String CFG_PUP_HOST_ADDRESS = "pup.hostAddress";
	
	
	/*
	 * internal maps etc. for machine IDs and assigned values
	 */
	
	// configured values if no machine specifics are defined
	private final static Map<String,Long> defaultValues = new HashMap<>();
	
	// map name => machine-id
	private final static Map<String,Long> machineIds = new HashMap<>();
	
	// map machine-id => specific parameter values
	private final static Map<Long,Map<String,Long>> machineValues = new HashMap<>();
	
	// machine-id used internally to tell "not a valid numeric machine-id"
	private static final long INVALID_MACHINE_ID = 0xFF00_FFFF_FFFF_FFFFL;
	
	// list of machine names given so far that were invalid numeric values or undefined names
	private static final List<String> invalidMachineNames = new ArrayList<>();
	
	// counter for temp machine-ids assigned if an invalid one was specified...
	private static long lastDummyId = 0x0000_FF00_0000_0000L;
	
	/**
	 * Resolve a machine name or id-string to the corresponding machine id,
	 * either by mapping the name or parsing the numeric id string, given in
	 * the format {@code XX-XX-XX-XX-XX-XX} or {@code XXXXXXXXXXXX} (with
	 * {@code X} being a hex digit).
	 * 
	 * @param name the name or id-string to resolve
	 * @return the mapped machine-id or a dummy id if the name is unknown resp.
	 *   the id string is invalid.
	 */
	public static long resolve(String name) {
		String idString = name.trim().toLowerCase();
		if (machineIds.containsKey(idString)) {
			return machineIds.get(idString);
		}
		long machineId = parseMachineId(idString);
		if (machineId == INVALID_MACHINE_ID) {
			machineId = ++lastDummyId;
			invalidMachineNames.add(name);
			machineIds.put(idString, machineId);
			return machineId;
		}
		return machineId;
	}
	
	/**
	 * Get the list of erroneous machine names or ids encountered while
	 * resolving values given in configuration or chs database files.
	 * 
	 * @return list of invalid machine names or ids.
	 */
	public static List<String> getUndefinedMachineNames() {
		return invalidMachineNames;
	}
	
	/**
	 * Get the configured (long integer) value of the configuration parameter
	 * for the given machine-id, either are specific value or machine independent
	 * value or the given {@code defaultValue).
	 * 
	 * @param machineId the machine-id to get the configured value
	 * @param cfgName the name of the configuration parameter to look up
	 * @param defaultValue the fallback value if no machine specific or
	 *   machine independent value is configured
	 * @return the resulting configuration parameter value
	 */
	public static long getCfgLong(long machineId, String cfgName, long defaultValue) {
		Long value = null;
		if (machineValues.containsKey(machineId)) {
			value = machineValues.get(machineId).get(cfgName);
		} else {
			value = defaultValues.get(cfgName);
		}
		return (value != null) ? value : defaultValue;
	}
	
	/**
	 * Get the configured (integer) value of the configuration parameter
	 * for the given machine-id, either are specific value or machine independent
	 * value or the given {@code defaultValue).
	 * 
	 * @param machineId the machine-id to get the configured value
	 * @param cfgName the name of the configuration parameter to look up
	 * @param defaultValue the fallback value if no machine specific or
	 *   machine independent value is configured
	 * @return the resulting configuration parameter value
	 */
	public static int getCfgInt(long machineId, String cfgName, int defaultValue) {
		return (int)(getCfgLong(machineId, cfgName, defaultValue) & 0xFFFFFFFFL);
	}
	
	/**
	 * Get the configured (boolean) value of the configuration parameter
	 * for the given machine-id, either are specific value or machine independent
	 * value or the given {@code defaultValue).
	 * 
	 * @param machineId the machine-id to get the configured value
	 * @param cfgName the name of the configuration parameter to look up
	 * @param defaultValue the fallback value if no machine specific or
	 *   machine independent value is configured
	 * @return the resulting configuration parameter value
	 */
	public static boolean getCfgBoolean(long machineId, String cfgName, boolean defaultValue) {
		return (getCfgLong(machineId, cfgName, defaultValue ? 1L : 0L) != 0L);
	}
	
	/**
	 * Set the machine independent configuration value for the parameter name.
	 * 
	 * @param key the configuration parameter name
	 * @param value the machine independent configuration value
	 */
	public static void setDefault(String key, long value) {
		defaultValues.put(key, value);
	}
	
	/**
	 * Set the machine independent configuration value for the parameter name.
	 * 
	 * @param key the configuration parameter name
	 * @param value the machine independent configuration value
	 */
	public static void setDefault(String key, boolean value) {
		setDefault(key, value ? 1L : 0L);
	}
	
	/**
	 * Load the machine definitions and specific configurations from
	 * the given or default file.
	 * 
	 * @param paramFilename the file to load the machine definitions from
	 * @return {@code true} if reading and loading the file was successful
	 *    (or none was given and the default was not found)
	 */
	public static boolean loadDefinitions(String paramFilename) {
		String filename = paramFilename;
		if (filename == null || filename.isEmpty()) {
			filename = "machines.cfg";
		}
		File cfgFile = new File(filename);
		if (!cfgFile.exists()) {
			// ignore missing machineid-file if not given on the command line
			return (paramFilename == null || paramFilename.isEmpty());
		}
		
		boolean ok = true;
		String currMachineName = null;
		long currMachineId = -1;
		
		try (BufferedReader br = new BufferedReader(new FileReader(cfgFile))) {
			String newLine = br.readLine();
			while(newLine != null) {
				String line = newLine.trim();
				
				if (line.isEmpty() || line.startsWith("#")) {
					// ignore empty or comment line
				} else if (line.startsWith("+")) {
					// machine specific configuration
					if (currMachineName != null) {
						String[] parts = line.substring(1).trim().split("=");
						if (parts.length == 2) {
							String key = parts[0].trim();
							String valueText = parts[1].trim().toLowerCase();
							if (valueText.matches("[0-7]+#[0-7]+#")) {
								String[] addrParts = valueText.split("#");
								int pupNetwork = Integer.parseInt(addrParts[0], 8);
								int pupHost = Integer.parseInt(addrParts[1], 8);
								if (pupNetwork >= 0 && pupNetwork <= 255 && pupHost >= 0 && pupHost <= 255) {
									long pupAddress = (pupNetwork << 8) | pupHost;
									machineValues.get(currMachineId).put(key, pupAddress);
								} else {
									System.out.printf("** machine '%s': invalid value '%s' given for '%s'\n", currMachineName, valueText, key);
								}
							} else {
								try {
									final long value;
									if ("false".equals(valueText)) {
										value = 0L;
									} else if ("true".equals(valueText)) {
										value = 1L;
									} else {
										value = Long.parseLong(valueText);
									}
									machineValues.get(currMachineId).put(key, value);
								} catch(NumberFormatException nfe) {
									System.out.printf("** machine '%s': invalid value '%s' given for '%s'\n", currMachineName, parts[1], parts[0]);
								}
							}
						}
					}
				} else if (line.toLowerCase().startsWith(":like ")) {
					// copy specific properties form a previously defined machine
					if (currMachineName != null) {
						String other = line.substring(":like ".length()).trim().toLowerCase();
						if (machineIds.containsKey(other)) {
							Map<String,Long> othersData = machineValues.get(machineIds.get(other));
							if (othersData != null) {
								machineValues.get(currMachineId).putAll(othersData);
							}
						}
					}
				} else {
					// new machine definition
					String[] parts = line.split("=");
					if (parts.length == 2) {
						String machineName = parts[0].trim().toLowerCase();
						String idText = parts[1].trim();
						try {
							long id = parseMachineId(idText);
							if (id == INVALID_MACHINE_ID) {
								id = ++lastDummyId;
								invalidMachineNames.add(parts[0].trim());
								invalidMachineNames.add(idText + " (machine-id for: " + parts[0].trim() + ")");
								machineIds.put(machineName, id);
							}
							currMachineName = machineName;
							currMachineId = id;
							machineIds.put(machineName, id);
							machineValues.put(id, new HashMap<>());
						} catch (Exception e) {
							currMachineName = null;
							ok = false;
						}
					}
				}
				
				newLine = br.readLine();
			}
		} catch (IOException e) {
			System.out.printf("Error: problem reading machine-ids file '%s': %s\n", filename, e.getMessage());
			return false;
		}
		return ok;
	}
	
	/**
	 * Dump the current machine definitions to {@code strdout}.
	 */
	public static void dump() {
		System.out.println("Machines:");
		for (Entry<String,Long> pair : machineIds.entrySet()) {
			System.out.printf("-- name: '%s' -> 0x%012X\n", pair.getKey(), pair.getValue());
			Map<String,Long> cfgs = machineValues.get(pair.getValue());
			if (cfgs != null) {
				for (Entry<String,Long> cfg : cfgs.entrySet()) {
					System.out.printf("   - '%s' => %d\n", cfg.getKey(), cfg.getValue());
				}
			}
		}
		System.out.println("Defaults:");
		for (Entry<String,Long> cfg : defaultValues.entrySet()) {
			System.out.printf("- '%s' => %d\n", cfg.getKey(), cfg.getValue());
		}
	}
	
	private static long parseMachineId(String mId) {
		String[] submacs = mId.split("-");
		if (submacs.length == 1) {
			try {
				return Long.parseLong(mId, 16) & 0x0000FFFFFFFFFFFFL;
			} catch (NumberFormatException nfe) {
				return INVALID_MACHINE_ID;
			}
		}
		if (submacs.length != 6) {
			return INVALID_MACHINE_ID;
		}
		
		long macId = 0;
		for (int i = 0; i < submacs.length; i++) {
			macId = macId << 8;
			try {
				macId |= Integer.parseInt(submacs[i], 16) & 0xFF;
			} catch (Exception e) {
				return INVALID_MACHINE_ID;
			}
		}
		return macId;
	}

}
