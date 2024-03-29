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

package dev.hawala.xns.level4.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.hawala.xns.Log;
import dev.hawala.xns.MachineIds;
import dev.hawala.xns.PropertiesExt;
import dev.hawala.xns.level3.courier.JsonStringReader;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.iWireData;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.chs.CHEntries0;
import dev.hawala.xns.level4.chs.CHEntries0.AuthenticationLevelValue;
import dev.hawala.xns.level4.chs.CHEntries0.MailboxesValue;
import dev.hawala.xns.level4.chs.CHEntries0.UserDataValue;
import dev.hawala.xns.level4.chs.Clearinghouse3;
import dev.hawala.xns.level4.chs.Clearinghouse3.Item;
import dev.hawala.xns.level4.chs.Clearinghouse3.Properties;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.NetworkAddress;
import dev.hawala.xns.level4.common.AuthChsCommon.NetworkAddressList;
import dev.hawala.xns.level4.common.AuthChsCommon.ObjectName;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;

/**
 * Clearinghouse database, supporting exactly one domain and
 * initialized from property files holding the entry data.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018,2019,2022,2023)
 */
public class ChsDatabase {
	
	private final boolean allowBlanksInObjectNames;

	private final int networkId;
	private final String organizationName;
	private final String domainName;
	private final String domOrgPartLc;
	private final String domainAndOrganization;
	
	// public name/password-items for accessing/enumerating the clearinghouse
	private final String chsQueryUser = "Clearinghouse Service:CHServers:CHServers";
	private final Name chsQueryName;
	private final String chsQueryUserPwPlain = "chs-pw";
	private final byte[] chsQueryUserPwStrong;
	private final int chsQueryUserPwSimple;
	
	// public name/password-items for accessing the mailservice
	private final String msQueryUser = "Mail Service:CHServers:CHServers";
	private final Name msQueryName;
	private final String msQueryUserPwPlain = "ms-pw";
	private final byte[] msQueryUserPwStrong;
	private final int msQueryUserPwSimple;
	
	public String getChsQueryUser() { return this.chsQueryUser; }
	
	public Name getChsQueryName() { return this.chsQueryName; }
	
	public byte[] getChsQueryUserPwStrong() { return this.chsQueryUserPwStrong; }
	
	// items for the unique mail service
	private ChsMailSvc mailService = null;
	private String mailServiceFqn = null;
	
	// naming:
	//   fqn = full qualified name :: mixed case primary 3-part name (also called "distinguished name")
	//   nqn = normalized qualified name :: lowercase version of the fqn used for searching/lookups
	//   pn  = primary name :: first component of the fqn
	
	/** map any-lc(nqn,alias) => fqn */
	private final Map<String,String> fqns;
	
	/** map fqn => chs-entry */
	private final Map<String,ChsEntry> entries;
	
	/** all lowercase fqn-local-names */
	private final List<String> localNames = new ArrayList<>();
	
	/** all lowercase-aliases */
	private final List<String> localAliases = new ArrayList<>();
	
	/**
	 * Construction of a clearinghouse database from a set of property files defining the
	 * objects in this domain and organization.
	 * 
	 * @param networkId the network number for all machines registered in this clearinghouse database.
	 * @param organization the organization handled by this clearinghouse database.
	 * @param domain the domain handled by this clearinghouse database.
	 * @param chsDatabaseRoot the name of the directory where the property files
	 * 		defining the objects in this domain are located.
	 * @param allowBlanksInObjectNames allow blanks in object names created from the chs database
	 * 		.properties file or remove the blanks?
	 */
	public ChsDatabase(long networkId, String organization, String domain, String chsDatabaseRoot, boolean allowBlanksInObjectNames) {
		// save configuration data
		this.networkId = (int)(networkId & 0xFFFFFFFFL);
		this.organizationName = organization;
		this.domainName = domain;
		this.domOrgPartLc = ":" + domain.toLowerCase() + ":" + organization.toLowerCase();
		this.domainAndOrganization = ":" + domain + ":" + organization;
		this.allowBlanksInObjectNames = allowBlanksInObjectNames;
		
		// create clearinghouse-service query user items
		this.chsQueryName = Name.make();
		this.chsQueryName.from(this.chsQueryUser);
		this.chsQueryUserPwStrong = this.computePasswordStrongHash(this.chsQueryUserPwPlain);
		this.chsQueryUserPwSimple = this.computePasswordSimpleHash(this.chsQueryUserPwPlain);
		
		// create mail-service query user items
		this.msQueryName = Name.make();
		this.msQueryName.from(this.msQueryUser);
		this.msQueryUserPwStrong = this.computePasswordStrongHash(this.msQueryUserPwPlain);
		this.msQueryUserPwSimple = this.computePasswordSimpleHash(this.msQueryUserPwPlain);
		
		// check for CHS database existence
		// if none => allow any user with password = name-part of user
		if (chsDatabaseRoot == null) {
			this.fqns = null;
			this.entries = null;
			return;
		}
		File chsRoot = new File(chsDatabaseRoot);
		if (!chsRoot.exists() || !chsRoot.canRead() || !chsRoot.isDirectory()) {
			this.fqns = null;
			this.entries = null;
			return;
		}
		
		// ok, we have a CHS database (possibly empty, but we have it)
		this.fqns = new HashMap<>();
		this.entries = new HashMap<>();
		File[] entryFiles = chsRoot.listFiles( (dir,name) -> name.toLowerCase().endsWith(".properties") );
		for (File ef : entryFiles) {
			if (ef.isDirectory() || !ef.canRead()) { continue; }
			String fn = ef.getName().toLowerCase();
			
			ChsEntry entry = null;
			try {
				if (fn.startsWith("c~")) { entry = new ChsClearinghouseSvc(ef); } else
				if (fn.startsWith("f~")) { entry = new ChsFileSvc(ef); } else
				if (fn.startsWith("m~")) { entry = new ChsMailSvc(ef); } else
				if (fn.startsWith("p~")) { entry = new ChsPrintSvc(ef); } else
				if (fn.startsWith("u~")) { entry = new ChsUser(ef); } else
				if (fn.startsWith("ug~")) { entry = new ChsUserGroup(ef); } else
				if (fn.startsWith("ws~")) { entry = new ChsWorkstation(ef); } else
				if (fn.startsWith("ec-tty~")) { entry = new ChsEcsGapTTY(ef); } else
				if (fn.startsWith("ec-ibm~")) { entry = new ChsEcsGapIBM(ef); }
			} catch (Exception e) {
				System.out.printf("Unable to load CHS entry from file '%s': %s\n", ef.getName(), e.getMessage());
				continue;
			}
			if (entry == null) {
				System.out.printf("Unknonwn CHS entry type for file '%s'\n", ef.getName());
				continue;
			}
			
			String fqn = entry.getObjectName() + this.domainAndOrganization;
			String nqn = fqn.toLowerCase();
			if (this.entries.containsKey(nqn)) {
				System.out.printf(
						"Warning: Duplicate entry for FQN '%s' from file '%s' ignored (entry defined in: %s)\n",
						fqn, ef.getName(), this.entries.get(nqn).getCfgFilename());
				continue;
			}
			
			if (entry instanceof ChsMailSvc && this.mailService == null) {
				this.mailService = (ChsMailSvc)entry;
				this.mailServiceFqn = fqn;
			}
			
			this.entries.put(fqn, entry);
			
			this.fqns.put(nqn, fqn);
			for (String a : entry.getCfgAliases()) {
				String alias = a + this.domOrgPartLc;
				if (!this.fqns.containsKey(alias)) {
					this.fqns.put(alias,  fqn);
					entry.addConfirmedAlias(alias);
				} else {
					System.out.printf(
							"Warning: Ingoring duplicate alias '%s' for '%s', nqn already defined for '%s'\n",
							alias, fqn, this.fqns.get(alias));
				}
			}
		}
		
		// check if a mail service is defined
		if (this.mailService == null) {			
			// create a dummy mailservice name to allow users to be created
			String dummyMailSvcName = "fallback-mail-office";
			String fqn = dummyMailSvcName + this.domainAndOrganization;
			String lcFqn = fqn.toLowerCase();
			int i = 0;
			while(this.fqns.containsKey(lcFqn)) {
				dummyMailSvcName = String.format("fallback-mail-office-%d", i++);
				fqn = dummyMailSvcName + this.domainAndOrganization;
				lcFqn = fqn.toLowerCase();
			}
			this.fqns.put(lcFqn, fqn);
			this.mailServiceFqn = fqn;
			
			// but request an invalid address for it to ensure that startup will stop
			MachineIds.resolve("missing mail service => " + dummyMailSvcName);
		}
		
		// post process all database entries
		for (Entry<String, ChsEntry> entry : this.entries.entrySet()) {
			entry.getValue().postpare(entry.getKey());
		}
		
		// check consistency in all database entries
		for (Entry<String, ChsEntry> entry : this.entries.entrySet()) {
			entry.getValue().postcheck();
		}
		
		// create localNames and localAliases lists
		for (String fqn : this.entries.keySet()) {
			String[] fqnParts = fqn.split(":");
			this.localNames.add(fqnParts[0].toLowerCase());
		}
		for (String name : this.fqns.keySet()) {
			String[] nameParts = name.split(":");
			String cand = nameParts[0].toLowerCase();
			if (!this.localNames.contains(cand) && !this.localAliases.contains(cand)) {
				this.localAliases.add(cand);
			}
		}
	}
	
	/**
	 * @return the name of the XNS organization handled in this clearinghouse database.
	 */
	public String getOrganizationName() {
		return this.organizationName;
	}
	
	/**
	 * @return the name of the XNS domain handled in this clearinghouse database.
	 */
	public String getDomainName() {
		return this.domainName;
	}
	
	/**
	 * Get the distinguished name for a given name.
	 * 
	 * @param forName the name to find and return the distinguished name for.
	 * @param distinguishedName the name to fill with the matching name.
	 * @return {@code true} if the object was found or {@code false} if no such
	 * 		clearinghouse entry exists.
	 */
	public boolean getDistinguishedName(ThreePartName forName, ThreePartName distinguishedName) {
		if (this.fqns == null) {
			distinguishedName.object.set(forName.object.get());
			distinguishedName.domain.set(forName.domain.get());
			distinguishedName.organization.set(forName.organization.get());
			return true;
		}
		String fqn = this.getFqnForName(forName);
		if (fqn == null) { return false; }
		String[] parts = fqn.split(":");
		distinguishedName.object.set(parts[0]);
		distinguishedName.domain.set(parts[1]);
		distinguishedName.organization.set(parts[2]);
		return true;
	}
	
	/**
	 * Find the distinguished name matching a pattern be it a
	 * distinguished name or an alias.
	 * 
	 * @param forPattern the name pattern to find and return the distinguished
	 *     name for, the name must already be a lowercase {@code String}-pattern.
	 * @param distinguishedName the name to fill with the matching name.
	 * @return {@code true} if the object was found or {@code false} if no such
	 * 		clearinghouse entry exists.
	 */
	public boolean findDistinguishedName(ThreePartName forPattern, ThreePartName distinguishedName) {
		String objectName = forPattern.object.get();
		String domain = forPattern.domain.get();
		String organization = forPattern.organization.get();
		String fullname = objectName + ":" + domain + ":" + organization;
		String nqn = fullname.toLowerCase();
		
		if (!nqn.contains("*")) {
			return this.getDistinguishedName(forPattern, distinguishedName);
		}
		
		for (Entry<String, String> cand : this.fqns.entrySet()) {
			if (Pattern.matches(nqn, cand.getKey())) {
				String[] parts = cand.getValue().split(":");
				distinguishedName.object.set(parts[0]);
				distinguishedName.domain.set(parts[1]);
				distinguishedName.organization.set(parts[2]);
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Return the simple password for a user (16 bit hash value
	 * of the plain text password).
	 * <p>
	 * <b>WARNING</p>: if no chs database if configured, passwords are faked,
	 * the object name (case insensitive) is the password!
	 * </p>
	 * 
	 * @param forName the full qualified name of the user
	 * @return the simple password of the user or {@code null}
	 * 		if the user does not have a simple password.
	 * @throws IllegalArgumentException if the user does not exist.
	 */
	public Integer getSimplePassword(Name forName) {
		if (this.fqns != null) {
			ChsEntry entry = this.getEntryForName(forName);
			if (entry == null) {
				String fqn = this.getNqnForName(forName);
				if (this.chsQueryUser.equalsIgnoreCase(fqn)) {
					return this.chsQueryUserPwSimple;
				}
				if (this.msQueryUser.equalsIgnoreCase(fqn)) {
					return this.msQueryUserPwSimple;
				}
				Log.AUTH.printf("ChsDb", "getSimplePassword(): forName '%s:%s:%s' does not exist\n",
						forName.object.get(), forName.domain.get(), forName.organization.get());
				throw new IllegalArgumentException("Object not found");
			}
			if (entry instanceof ChsEntryWithPw) {
				return ((ChsEntryWithPw)entry).getSimplePwHash();
			}
			return -1; // invoker must map this somehow
		}
		
		// fallback if no database present
		String objectName = forName.object.get();
		int objectNameHash = this.computePasswordSimpleHash(objectName);
		return Integer.valueOf(objectNameHash);
	}
	
	/**
	 * Return the strong password for a user or service.
	 * <p>
	 * <b>WARNING</p>: if no chs database if configured, passwords are faked,
	 * the object name (case insensitive) is the password!
	 * </p>
	 * 
	 * @param forName the full qualified name of the user
	 * @return the strong password of the user or {@code null}
	 * 		if the user does not have a simple password.
	 * @throws IllegalArgumentException if the user does not exist.
	 */
	public byte[] getStrongPassword(ThreePartName forName) {
		if (this.fqns != null) {
			ChsEntry entry = this.getEntryForName(forName);
			if (entry == null) {
				String fqn = this.getNqnForName(forName);
				if (this.chsQueryUser.equalsIgnoreCase(fqn)) {
					return this.chsQueryUserPwStrong;
				}
				if (this.msQueryUser.equalsIgnoreCase(fqn)) {
					return this.msQueryUserPwStrong;
				}
				Log.AUTH.printf("ChsDb", "getStrongPassword(): forName '%s:%s:%s' does not exist\n",
						forName.object.get(), forName.domain.get(), forName.organization.get());
				throw new IllegalArgumentException("Object not found");
			}
			if (entry instanceof ChsEntryWithPw) {
				return ((ChsEntryWithPw)entry).getStrongPwHash();
			}
			return null; // invoker must map this to strongKeyDoesNotExist-error
		}
			
		// fallback if no database present
		String objectName = forName.object.get();
		try {
			byte[] keyBytes = StrongAuthUtils.getStrongKey(objectName, true);
			return keyBytes;
		} catch (Exception e) {
			// let's pretend that the user does not exist if no password can be generated
			Log.AUTH.printf("Cannot make strong key for '%s:%s:%s' pretending: forName does not exist\n",
					objectName, forName.domain.get(), forName.organization.get());
			throw new IllegalArgumentException("Object not found");
		}
	}
	
	/**
	 * return the FQN for the single mail service or {@code null} if no mail service is available.
	 */
	public String getMailServiceFqn() {
		return this.mailServiceFqn;
	}
	
	/**
	 * return the generic mail service name used when "any" mail service is contacted by clients  
	 */
	public Name getGenericMailServiceName() {
		return this.msQueryName;
	}
	
	
	/*
	 * ******************** internal utilities
	 */
	
	private String getFqnForName(ThreePartName name) {
		String nqn = this.getNqnForName(name);
		return this.fqns.get(nqn);
	}
	
	private String getNqnForName(ThreePartName name) {
		String objectName = name.object.get();
		String domain = name.domain.get();
		String organization = name.organization.get();
		String fullname = objectName + ":" + domain + ":" + organization;
		return fullname.toLowerCase();
	}
	
	private ChsEntry getEntryForName(ThreePartName name) {
		String fqn = this.getFqnForName(name);
		if (fqn == null) { return null; }
		return this.entries.get(fqn);
	}
	
	private ChsEntry findEntryForPattern(ThreePartName forPattern) {
		String objectName = forPattern.object.get();
		String domain = forPattern.domain.get();
		String organization = forPattern.organization.get();
		String fullname = objectName + ":" + domain + ":" + organization;
		String nqn = fullname.toLowerCase();
		
		final String fqn;
		if (!nqn.contains("*")) {
			fqn = this.fqns.get(nqn);
		} else {
			String tmp = null;
			for (Entry<String, String> cand : this.fqns.entrySet()) {
				if (Pattern.matches(nqn, cand.getKey())) {
					tmp = cand.getValue();
				}
			}
			fqn = tmp;
		}
		return (fqn != null) ? this.entries.get(fqn) : null;
	}
	
	private byte[] computePasswordStrongHash(String password) {
		try {
			byte[] keyBytes = StrongAuthUtils.getStrongKey(password, true);
			return keyBytes;
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to create strong password for '" + password + "'");
		}
	}
	
	private int computePasswordSimpleHash(String password) {
		String passwd = password.toLowerCase();
		long hash = 0;
		for (int i = 0; i < passwd.length(); i++) {
			char c = passwd.charAt(i);
			int cv = c;
			hash = ((hash << 16) + cv) % 65357;
		}
		return (int)(hash & 0xFFFFL);
	}
	
	private static long getMachineId(PropertiesExt props, String forKind, String name) {
		String mId = props.getProperty("machineId", null);
		if (mId == null) {
			throw new IllegalStateException("No machineId defined for " + forKind + ": " + name);
		}
		long macId = MachineIds.resolve(mId);
		return macId;
	}
	
	/*
	 * ***** in-memory representation of clearinghouse entries 
	 */
	
	private abstract class ChsEntry implements iUpdatableChsEntry {
		
		private final String cfgFilename;
		protected final String cfgAbsoluteFilename; 
		
		protected final String objNameFromCfgFile;
		protected final int entryType;
		protected final PropertiesExt props;
		
		private final List<String> cfgAliases = new ArrayList<>();
		private final List<String> aliasFqns = new ArrayList<>();
		
		protected String fqn = null;
		
		private final Map<Integer,Item> itemProperties = new HashMap<>();
		private final List<Integer> dynItemPropertyIds = new ArrayList<>();
		private final Map<Integer,List<ThreePartName>> groupProperties = new HashMap<>();
		
		public ChsEntry(int type, File propsFile) {
			this.cfgFilename = propsFile.getName();
			this.cfgAbsoluteFilename = propsFile.getAbsolutePath();
			this.entryType = type;
			this.props = new PropertiesExt(propsFile);
			
			// extract the name component from the filename
			String filename = propsFile.getName();
			String basename = filename.substring(0, filename.length() - 11); // remove .properties
			if (!allowBlanksInObjectNames) {
				basename = basename.replace(" ", ""); // remove blanks if not requested to keep them
			}
			if (basename.contains(":")) {
				basename = basename.split(":")[0];
			}
			int tildeIdx = basename.indexOf("~");
			if ((tildeIdx + 1) >= basename.length()) {
				throw new IllegalArgumentException("Invalid entry filename (no name component): " + filename);
			}
			this.objNameFromCfgFile = basename.substring(tildeIdx + 1);
			
			// get the aliases
			String aliasesList = this.props.getString("aliases", "");
			String[] cfgAliases = aliasesList.split(",");
			for (String ca : cfgAliases) {
				this.addCfgAlias(ca.trim());
			}
			
			// get dynamic properties
			String dynPropIdList = this.props.getString("dynamicProperties", "");
			String[] dynPropIds = dynPropIdList.split(",");
			for (String dynId : dynPropIds) {
				String id = dynId.trim();
				if (id.isEmpty()) { continue; }
				try {
					this.dynItemPropertyIds.add(Integer.parseInt(id));
				} catch (NumberFormatException nfe) {
					System.out.printf("file '%s': invalid value '%s' in dynamicProperties\n", filename, id);
				}
			}
		}
		
		public String getCfgFilename() {
			return this.cfgFilename;
		}
		
		public int getType() { return this.entryType; }
		
		public abstract String getDescription();
		
		public abstract String getObjectName();
		
		public void postpare(String fqn) {
			this.fqn = fqn;
			this.putItemProperty(this.entryType, STRING.make().set(this.getDescription()));
		}
		
		protected void putItemProperty(int property, iWireData value) {
			try {
				this.itemProperties.put(property, Item.from(value));
			} catch (NoMoreWriteSpaceException nmse) {
				// tell about the error but ignore this property...
				System.out.printf(
					"Warning: '%s' has invalid property %d (value size exceeds 500 words!)\n",
					this.fqn, property);
			}
		}
		
		protected void putGroupProperty(int property, List<ThreePartName> groupMemberss) {
			this.groupProperties.put(property, groupMemberss);
		}
		
		public void postcheck() {
			// check consistency, e.g. for cyclic references, recursive groups
		}
		
		@Override
		public String toString() {
			return this.getClass().getSimpleName() + "[ pn='" + this.getObjectName() + "' , description='" + this.getDescription() + "' ]";
		}
		
		protected void dumpSpecifics() {
			System.out.printf("  name         : %s\n", this.fqn);
			System.out.printf("  cfgFilename  : %s\n", this.cfgFilename);
			System.out.printf("  objNameCfg   : %s\n", this.objNameFromCfgFile);
			System.out.printf("  entryType    : %d\n", this.entryType);
		}
		
		public void dump() {
			System.out.println(this.getClass().getSimpleName());
			this.dumpSpecifics();
			if (!this.aliasFqns.isEmpty()) {
				System.out.printf("  aliases      :\n");
				for (String m : this.aliasFqns) {
					System.out.printf("      %s\n", m);
				}
				System.out.printf("  ---------------------------------- end aliases\n");
			}
			
			this.getItemProperty(-1); // possibly complete initializations
			if (!this.itemProperties.isEmpty()) {
				System.out.printf("  item properties:\n");
				for (Entry<Integer, Item> e : this.itemProperties.entrySet()) {
					System.out.printf("    %7d : 0x", e.getKey());
					Item item = e.getValue();
					for (int i = 0; i < item.size(); i++) {
						System.out.printf(" %04X", item.get(i).get() & 0xFFFF);
					}
					System.out.println();
				}
			}
			
			this.getGroupProperty(-1); // possibly complete initializations
			if (!this.groupProperties.isEmpty()) {
				System.out.printf("  group properties:\n");
				for (Entry<Integer, List<ThreePartName>> e : this.groupProperties.entrySet()) {
					System.out.printf("    %7d :", e.getKey());
					List<ThreePartName> members = e.getValue();
					String sep = " ";
					for (ThreePartName member : members) {
						System.out.printf(
								"%s%s:%s:%s", 
								sep,
								member.object.get(), member.domain.get(), member.organization.get());
						sep = " ; ";
					}
					System.out.println();
				}
			}
		}
		
		protected String getLcObjName(String any) {
			if (any == null) { throw new IllegalArgumentException("Invalid null argument"); }
			String lcAny = any.toLowerCase();
			return lcAny.split(":")[0].trim();
		}
		
		protected void addCfgAlias(String alias) {
			if (alias == null || alias.isEmpty()) { return; }
			String objName = this.getLcObjName(alias);
			if (!this.cfgAliases.contains(objName)) {
				this.cfgAliases.add(objName);
			}
		}
		
		public List<String> getCfgAliases() {
			return this.cfgAliases;
		}
		
		public void addConfirmedAlias(String aliasFqn) {
			if (!this.aliasFqns.contains(aliasFqn)) {
				this.aliasFqns.add(aliasFqn);
			}
		}
		
		public List<String> getAliases() {
			return this.aliasFqns;
		}
		
		public Item getItemProperty(int property) {
			if (this.itemProperties.containsKey(property)) {
				return this.itemProperties.get(property);
			}
			
			if (this.dynItemPropertyIds.contains(property)) {
				iWireData value = loadDynamicData(this.cfgAbsoluteFilename, property);
				if (value != null) {
					try {
						return Item.from(value);
					} catch (NoMoreWriteSpaceException e) {
						return null;
					}
				}
			}
			
			return null;
		}
		
		public List<ThreePartName> getGroupProperty(int property) {
			return this.groupProperties.get(property);
		}
		
		public Set<Integer> getItemPropertyIds() {
			Set<Integer> ids = new HashSet<>(this.itemProperties.keySet());
			ids.addAll(this.dynItemPropertyIds);
			return ids;
		}
		
		public Set<Integer> getGroupPropertyIds() {
			return this.groupProperties.keySet();
		}
		
		// iUpdatableChsEntry methods
		
		@Override
		public String getFqn() {
			return this.fqn;
		}
		
		@Override
		public List<String> getUninterpretedProperties() {
			return Collections.emptyList();
		}

		@Override
		public String getUninterpretedProperty(String name) {
			return null;
		}
		
		@Override
		public void setChsProperty(int property, iWireData value) {
			// unimplemented by default
		}
		
	}
	
	private abstract class ChsEntryWithPw extends ChsEntry {
		
		private final int simplePwHash;
		private final byte[] strongPwHash;

		public ChsEntryWithPw(int type, File propsFile) {
			super(type, propsFile);
			
			// get/generate the password hashes, default password is: (objectname from filename) + ".passwd"
			String plainPw = props.getString("password", this.objNameFromCfgFile + ".passwd");
			this.simplePwHash = computePasswordSimpleHash(plainPw);
			this.strongPwHash = computePasswordStrongHash(plainPw);
		}
		
		public int getSimplePwHash() {
			return this.simplePwHash;
		}
		
		public byte[] getStrongPwHash() {
			return this.strongPwHash;
		}
		
		protected void dumpSpecifics() {
			super.dumpSpecifics();
			System.out.printf("  simplePwHash : %d\n", this.simplePwHash & 0xFFFF);
			System.out.printf("  strongPwHash : 0x");
			for (byte b : this.strongPwHash) {
				System.out.printf(" %02X", b & 0xFF);
			}
			System.out.println();
		}
		
	}
	
	private class ChsUser extends ChsEntryWithPw {
		
		private final String username;
		private final int lastnameIndex;
		
		private final ThreePartName mailservice = ThreePartName.make();
		private final long mailboxTimeMillisecs;
		private final String fileservice;
		
		public ChsUser(File propsFile) {
			super(CHEntries0.user, propsFile);
			
			String firstname = this.props.getProperty("firstname", "");
			String lastname = this.props.getProperty("lastname", "");
			if (lastname.isEmpty()) {
				this.username = this.objNameFromCfgFile;
				this.lastnameIndex = 0;
			} else if (firstname.isEmpty()) {
				this.username = lastname;
				this.lastnameIndex = 0;
				this.addCfgAlias(this.objNameFromCfgFile);
			} else {
				this.username = firstname + " " + lastname;
				this.lastnameIndex = firstname.length() + 1;
				this.addCfgAlias(this.objNameFromCfgFile);
			}

			this.fileservice = this.getLcObjName(this.props.getProperty("fileservice", "none")) + domOrgPartLc;
			this.mailservice.from(this.getLcObjName(this.props.getProperty("mailservice", "none")) + domOrgPartLc);
			this.mailboxTimeMillisecs = propsFile.lastModified();
		}
		
		@Override
		public void postpare(String fqn) {
			super.postpare(fqn);
			
			String fsName = fqns.get(this.fileservice);
			if (fsName == null) {
				fsName = "undefined file service => " + this.fileservice;
				MachineIds.resolve(fsName); // high probably resolves to an invalid address
			}
			
			UserDataValue userDataValue = UserDataValue.make();
			userDataValue.fileService.from(fsName);
			userDataValue.lastNameIndex.set(this.lastnameIndex);
			this.putItemProperty(CHEntries0.userData, userDataValue);
			
			this.mailservice.from(ChsDatabase.this.mailServiceFqn);
			MailboxesValue mailboxesValue = MailboxesValue.make();
			mailboxesValue.mailService.add().from(this.mailservice);
			mailboxesValue.time.fromUnixMillisecs(this.mailboxTimeMillisecs);
			this.putItemProperty(CHEntries0.mailboxes, mailboxesValue);
		}
		
		protected void dumpSpecifics() {
			super.dumpSpecifics();
			System.out.printf("  username     : %s\n", this.username);
			System.out.printf("  lastnameIndex: %d\n", this.lastnameIndex);
			System.out.printf("  mailservice  : %s\n", this.mailservice.getString());
			System.out.printf("  fileservice  : %s\n", this.fileservice);
		}

		@Override
		public String getDescription() {
			return this.username;
		}

		@Override
		public String getObjectName() {
			return this.username;
		}

		public int getLastnameIndex() {
			return this.lastnameIndex;
		}

		public String getMailserviceFqn() {
			return this.mailservice.getString();
		}

		public String getFileserviceFqn() {
			return this.fileservice;
		}
		
	}
	
	private abstract class ChsSvc extends ChsEntryWithPw {
		
		private final String description;
		
		private final boolean authLevelSimple;
		private final boolean authLevelStrong;
		
		private final long machineId;
		
		protected ChsSvc(int type, File cfgFile) {
			super(type, cfgFile);
			
			String desc = this.props.getProperty("description", null);
			if (desc == null) {
				desc = "Service " + this.objNameFromCfgFile + ", type = " + type;
			}
			this.description = desc;
			
			String authLevel = this.props.getProperty("authLevel", "simple").toLowerCase();
			switch(authLevel) {
			case "both":
				this.authLevelSimple = true;
				this.authLevelStrong = true;
				break;
			case "simple":
				this.authLevelSimple = true;
				this.authLevelStrong = false;
				break;
			case "strong":
				this.authLevelSimple = false;
				this.authLevelStrong = true;
				break;
			case "none":
				this.authLevelSimple = false;
				this.authLevelStrong = false;
				break;
			default:
				System.out.printf("Warn: invalid authLevel '%s' for service '%s', assuming 'simple'\n", authLevel, this.objNameFromCfgFile);
				this.authLevelSimple = true;
				this.authLevelStrong = false;
				break;
			}
			
			this.machineId = ChsDatabase.getMachineId(this.props, "service", this.objNameFromCfgFile);
		}
		
		@Override
		public void postpare(String fqn) {
			super.postpare(fqn);
			
			AuthenticationLevelValue authLevelValue = AuthenticationLevelValue.make();
			authLevelValue.simpleSupported.set(this.authLevelSimple);
			authLevelValue.strongSupported.set(this.authLevelStrong);
			this.putItemProperty(CHEntries0.authenticationLevel, authLevelValue);
			
			NetworkAddressList addrList = NetworkAddressList.make();
			NetworkAddress addr = addrList.add();
			addr.network.set(networkId);
			addr.host.set(this.machineId);
			addr.socket.set(0);
			this.putItemProperty(CHEntries0.addressList, addrList);
		}
		
		protected void dumpSpecifics() {
			super.dumpSpecifics();
			System.out.printf("  description  : %s\n", this.description);
			System.out.printf("  authLvlSimple: %s\n", this.authLevelSimple ? "true" : "false");
			System.out.printf("  authLvlStrong: %s\n", this.authLevelStrong ? "true" : "false");
			System.out.printf("  machineId    : %012X\n", this.machineId);
		}

		@Override
		public String getDescription() {
			return this.description;
		}

		@Override
		public String getObjectName() {
			return this.objNameFromCfgFile;
		}

		public boolean isAuthLevelSimpleSupported() {
			return authLevelSimple;
		}

		public boolean isAuthLevelStrongSupported() {
			return authLevelStrong;
		}

		public long getMachineId() {
			return machineId;
		}
		
	}
	
	private class ChsClearinghouseSvc extends ChsSvc {
		
		private final String mailservice;
		private final long mailboxTimeMillisecs;
		
		public ChsClearinghouseSvc(File cfgFile) {
			super(CHEntries0.clearinghouseService, cfgFile);
			this.mailservice = this.getLcObjName(this.props.getProperty("mailservice", "none")) + domOrgPartLc;
			this.mailboxTimeMillisecs = cfgFile.lastModified();
		}
		
		@Override
		public void postpare(String fqn) {
			super.postpare(fqn);
			
			MailboxesValue mailboxesValue = MailboxesValue.make();
			mailboxesValue.mailService.add().from(ChsDatabase.this.mailServiceFqn);
			mailboxesValue.time.fromUnixMillisecs(this.mailboxTimeMillisecs);
			this.putItemProperty(CHEntries0.mailboxes, mailboxesValue);
		}

		public String getMailserviceFqn() {
			return mailservice;
		}
		
		protected void dumpSpecifics() {
			super.dumpSpecifics();
			System.out.printf("  mailservice  : %s\n", this.mailservice);
		}
		
	}
	
	private class ChsFileSvc extends ChsSvc {
		public ChsFileSvc(File cfgFile) {
			super(CHEntries0.fileService, cfgFile);
		}
	}
	
	private class ChsPrintSvc extends ChsSvc {
		public ChsPrintSvc(File cfgFile) {
			super(CHEntries0.printService, cfgFile);
		}
	}
	
	private class ChsMailSvc extends ChsSvc {
		public ChsMailSvc(File cfgFile) {
			super(CHEntries0.mailService, cfgFile);
		}
	}
	
	private abstract class ChsGroupEntry extends ChsEntry {
		
		protected final String description;
		
		// group members as configured
		protected final List<String> cfgMembers = new ArrayList<>();
		
		// group members as fqns, filled in postpare() phase from cfgMembers
		protected final List<String> memberFqns = new ArrayList<>();
		
		public ChsGroupEntry(int type, File cfgFile) {
			super(type, cfgFile);
			
			String desc = this.props.getProperty("description", null);
			if (desc == null) {
				desc = "Usergroup " + this.objNameFromCfgFile;
			}
			this.description = desc;
			
			String cfgvalue = this.props.getProperty("members", "");
			String[] members = cfgvalue.split(",");
			for (String member : members) {
				String m = this.getLcObjName(member);
				if (m.isEmpty()) { continue; }
				m = m + domOrgPartLc;
				if (!this.cfgMembers.contains(m)) {
					this.cfgMembers.add(m);
				}
			}
		}
		
		protected void dumpSpecifics() {
			super.dumpSpecifics();
			System.out.printf("  description  : %s\n", this.description);
			System.out.printf("  members      :\n");
			for (String m : this.memberFqns) {
				System.out.printf("      %s\n", m);
			}
			System.out.printf("  ---------------------------------- end members\n");
		}

		@Override
		public String getDescription() {
			return this.description;
		}

		@Override
		public String getObjectName() {
			return this.objNameFromCfgFile;
		}
		
		@Override
		public void postpare(String fqn) {
			super.postpare(fqn);
			this.memberFqns.clear();
			for(String m : this.cfgMembers) {
				if (fqns.containsKey(m)) {
					String aliasFqn = fqns.get(m);
					if (!this.memberFqns.contains(aliasFqn)) {
						this.memberFqns.add(aliasFqn);
					}
				}
			}
		}
		
		private boolean deepCheck(String memberFqn) {
			ChsEntry e = entries.get(memberFqn);
			if (e == this) {
				return true;
			}
			if (e instanceof ChsGroupEntry) {
				ChsGroupEntry ge = (ChsGroupEntry)e;
				for (String fqn : ge.getMemberFqns()) {
					if (this.deepCheck(fqn)) {
						return true;
					}
				}
			}
			return false;
		}
		
		@Override
		public void postcheck() {
			boolean recheck = false;
			for (String memberFqn : this.memberFqns) {
				if (this.deepCheck(memberFqn)) {
					System.out.printf(
						"Warning: cyclic reference to group '%s' through member '%s', removing '%s' from member list of '%s'\n",
						this.fqn, memberFqn, memberFqn, this.fqn);
					this.memberFqns.remove(memberFqn);
					break;
				}
			}
			if (recheck) {
				this.postcheck();
			}
		}
		
		public List<String> getMemberFqns() {
			return this.memberFqns;
		}
	}
	
	private class ChsUserGroup extends ChsGroupEntry {
		
		private boolean initializeGroupProperties = true;
		
		public ChsUserGroup(File cfgFile) {
			super(CHEntries0.userGroup, cfgFile);
		}
		
		@Override
		public List<ThreePartName> getGroupProperty(int property) {
			if (this.initializeGroupProperties) {
				List<ThreePartName> memberNames = new ArrayList<>();
				for (String fqn : this.getMemberFqns()) {
					memberNames.add(Name.make().from(fqn));
				}
				this.putGroupProperty(CHEntries0.members, memberNames);
				this.initializeGroupProperties = false;
			}
			
			return super.getGroupProperty(property);
		}
		
	}
	
	private abstract class ChsNetworkEntry extends ChsEntry {
		
		private final String description;
		private final long machineId;
		private final Map<String,String> uninterpretedProperties = new HashMap<>();
		
		protected ChsNetworkEntry(int type, String machinePropName, String defaultDescPrefix, File cfgFile) {
			super(type, cfgFile);
			
			String desc = this.props.getProperty("description", null);
			if (desc == null) {
				desc =  defaultDescPrefix + this.objNameFromCfgFile;
			}
			this.description = desc;
			
			this.machineId = ChsDatabase.getMachineId(this.props, machinePropName, this.objNameFromCfgFile);
		}
		
		@Override
		public void postpare(String fqn) {
			super.postpare(fqn);
			
			NetworkAddressList addrList = NetworkAddressList.make();
			NetworkAddress addr = addrList.add();
			addr.network.set(networkId);
			addr.host.set(this.machineId);
			addr.socket.set(0);
			this.putItemProperty(CHEntries0.addressList, addrList);
		}
		
		protected void dumpSpecifics() {
			super.dumpSpecifics();
			System.out.printf("  description  : %s\n", this.description);
			System.out.printf("  machineId    : %012X\n", this.machineId);
		}

		@Override
		public String getDescription() {
			return this.description;
		}

		@Override
		public String getObjectName() {
			return this.objNameFromCfgFile;
		}
		
		public long getMachineId() {
			return machineId;
		}
		
		@Override
		public List<String> getUninterpretedProperties() {
			return new ArrayList<>(this.uninterpretedProperties.keySet());
		}
		
		protected void setUninterpretedProperty(String name, String value) {
			this.uninterpretedProperties.put(name, value);
		}

		@Override
		public String getUninterpretedProperty(String name) {
			return this.uninterpretedProperties.get(name);
		}
		
		@Override
		public void setChsProperty(int property, iWireData value) {
			this.putItemProperty(property, value);
		}
	}
	
	private class ChsWorkstation extends ChsNetworkEntry {
		
		protected ChsWorkstation(File cfgFile) {
			super(CHEntries0.workstation, "workstation", "Workstation ", cfgFile);
		}
		
	}
	
	private class ChsEcsGapTTY extends ChsNetworkEntry {
		
		protected ChsEcsGapTTY(File cfgFile) {
			super(CHEntries0.rs232CPort, "machineId", "TTY-Gateway ", cfgFile);
			
			int lineNumber = this.props.getInt("lineNumber", -1);
			if (lineNumber < 1 || lineNumber > 65535) {
				String key = this.objNameFromCfgFile.toLowerCase() + "#" + this.objNameFromCfgFile.toUpperCase();
				lineNumber = key.hashCode() & 0xFFFF;
			}
			this.setUninterpretedProperty("lineNumber", Integer.toString(lineNumber));
			
			String ttyType = this.props.getString("ttyType", "telnet").toLowerCase();
			ttyType = (!"telnet".equals(ttyType) && !"shell".equals(ttyType)  && !"simple".equals(ttyType)) ? "simple" : ttyType;
			String connectAddress = this.props.getString("connectAddress", "localhost");
			int connectPort = this.props.getInt("connectPort", 23);
			String terminalType = this.props.getString("terminalType", "vt100");
			String mapCarriageReturn = this.props.getString("mapCarriageReturn", "none");    // string-enum: none, crlf, lf
			String mapBreak = this.props.getString("mapBreak", "CtrlC");    // string-enum: none, ctrlC, ctrlNextChar
			String mapBackspace = this.props.getString("mapBackspace", "none"); // string-enum: none, rubout
			String connectScript = this.props.getString("connectScript", "gap/" + this.objNameFromCfgFile.toLowerCase());
			
			this.setUninterpretedProperty("ttyType", ttyType);
			this.setUninterpretedProperty("connectAddress", connectAddress);
			this.setUninterpretedProperty("connectPort", Integer.toString(connectPort));
			this.setUninterpretedProperty("terminalType", terminalType);
			this.setUninterpretedProperty("mapCarriageReturn", mapCarriageReturn);
			this.setUninterpretedProperty("mapBreak", mapBreak);
			this.setUninterpretedProperty("mapBackspace", mapBackspace);
			this.setUninterpretedProperty("connectScript", connectScript);
		}
		
	}
	
	private class ChsEcsGapIBM extends ChsNetworkEntry {
		
		protected ChsEcsGapIBM(File cfgFile) {
			super(CHEntries0.ibm3270Host, "machineId", "IBM3270Host-Gateway ", cfgFile);
			
			int controllerAddress = this.props.getInt("controllerAddress", -1);
			if (controllerAddress < 1 || controllerAddress > 65535) {
				String key = this.objNameFromCfgFile.toLowerCase() + "#" + this.objNameFromCfgFile.toUpperCase();
				controllerAddress = key.hashCode() & 0xFFFF;
			}
			this.setUninterpretedProperty("controllerAddress", Integer.toString(controllerAddress));

			String connectType = this.props.getString("connectType", "host").toLowerCase();
			connectType = (!"host".equals(connectType) && !"simple".equals(connectType)) ? "simple" : connectType;
			String connectAddress = this.props.getString("connectAddress", "localhost");
			int connectPort = this.props.getInt("connectPort", 3270);
			String connectLUName = this.props.getString("connectLUName", null);
			String language = this.props.getString("language", null);
			
			this.setUninterpretedProperty("connectType", connectType);
			this.setUninterpretedProperty("connectAddress", connectAddress);
			this.setUninterpretedProperty("connectPort", Integer.toString(connectPort));
			this.setUninterpretedProperty("connectLUName", connectLUName);
			this.setUninterpretedProperty("language", language);
		}
		
	}
	
	/*
	 * methods to access database entries
	 */
	
	public boolean isValidName(ThreePartName name) {
		ChsEntry e = this.getEntryForName(name);
		return (e != null);
	}
	
	public String resolveName(ThreePartName name) {
		ChsEntry e = this.getEntryForName(name);
		if (e == null) { return null; }
		return e.fqn;
	}
	
	public List<String> findFullQualifiedNames(String pattern, long reqProperty) {
		List<String> results = new ArrayList<>();
		for (String name : this.findObjectNames(this.localNames, pattern, reqProperty)) {
			results.add(name + this.domainAndOrganization);
		}
		return results;
	}
	
	public List<String> findNames(String pattern, long reqProperty) {
		return this.findObjectNames(this.localNames, pattern, reqProperty);
	}
	
	public List<String> findAliases(String pattern, long reqProperty) {
		return this.findObjectNames(this.localAliases, pattern, reqProperty);
	}
	
	private List<String> findObjectNames(List<String> list, String pattern, long reqProperty) {
		
		System.out.printf("+++ ChsDatabase.findObjectNames( pattern = '%s' , reqProperty = %d )\n",
				pattern, reqProperty);
		
		int property = (int)(reqProperty & 0xFFFFFFFFL);
		List<String> names = new ArrayList<>();
		boolean isAnyName = ".*".equals(pattern);
		boolean isAnyProperty = (property == Clearinghouse3.all);
		
		for (String cand : list) {
			if (isAnyName || Pattern.matches(pattern, cand)) {
				if (isAnyProperty) {
					if (!names.contains(cand)) { names.add(cand); }
					continue;
				}
				String candFqn = this.fqns.get(cand + this.domOrgPartLc);
				ChsEntry candEntry = this.entries.get(candFqn);
				if (candEntry != null && candEntry.getItemProperty(property) != null) {
					if (!names.contains(cand)) {
						String candName = candEntry.getObjectName();
						System.out.printf("+++ ChsDatabase.findObjectNames() -> found '%s'\n", candName);
						names.add(candName);
					}
				}
			}
		}
		
		return names;
	}
	
	public List<String> getAliasesOf(ThreePartName name) {
		List<String> aliases = new ArrayList<>();
		ChsEntry entry = this.getEntryForName(name);
		if (entry != null) {
			for (String aliasFqn : entry.getAliases()) {
				String[] aliasParts = aliasFqn.split(":");
				aliases.add(aliasParts[0]);
			}
		}
		return aliases; 
	}
	
	// returns if entry was found, 'distinguishedObject' and 'properties' filled accordingly
	public boolean getPropertyList(
			ThreePartName forPattern,
			ObjectName distinguishedObject,
			Properties properties) {
		ChsEntry e = this.findEntryForPattern(forPattern);
		if (e == null) { return false; }
		
		if (distinguishedObject != null) {
			distinguishedObject.object.set(e.getObjectName());
			distinguishedObject.domain.set(this.domainName);
			distinguishedObject.organization.set(this.organizationName);
		}
		
		for (int propId : e.getItemPropertyIds()) {
			properties.add().set(propId & 0xFFFFFFFFL);
		}
		for (int propId : e.getGroupPropertyIds()) {
			properties.add().set(propId & 0xFFFFFFFFL);
		}
		
		return true;
	}
	
	// 0 => entry not found
	// 1 => entry found, but not property
	// 2 => entry found, but wrong property type (group, not item)
	// 3 => both found
	public int getEntryProperty(
						ThreePartName forPattern,
						int property,
						ObjectName distinguishedObject,
						Item value) {
		ChsEntry e = this.findEntryForPattern(forPattern);
		if (e == null) { return 0; }
		
		if (distinguishedObject != null) {
			distinguishedObject.object.set(e.getObjectName());
			distinguishedObject.domain.set(this.domainName);
			distinguishedObject.organization.set(this.organizationName);
		}
		
		Item item = e.getItemProperty(property);
		if (item != null) {
			value.clear();
			for (int i = 0; i < item.size(); i++) {
				value.add().set(item.get(i).get());
			}
			return 3;
		} else {
			return (e.getGroupProperty(property) != null) ? 2 : 1;
		}
	}
	
	// IllegalArgumentexception => entry not found
	// null => entry found, but property not found
	// (other) => entry and group property found
	public List<ThreePartName> getEntryGroupMembers(
						ThreePartName forPattern,
						int property,
						ObjectName distinguishedObject) {
		ChsEntry e = this.findEntryForPattern(forPattern);
		if (e == null) {
			throw new IllegalArgumentException("forPattern not found");
		}
		
		if (distinguishedObject != null) {
			distinguishedObject.object.set(e.getObjectName());
			distinguishedObject.domain.set(this.domainName);
			distinguishedObject.organization.set(this.organizationName);
		}
		
		List<ThreePartName> members = e.getGroupProperty(property);
		if (members == null) { return null; }
		
		return new ArrayList<>(members);
	}
	
	/*
	 * items for loading properties dynamically from files 
	 */
	
	private static iWireData errMessage(String msg) {
		System.out.printf("\n##\n### loadDynamicData(): %s\n##\n", msg);
		return null;
	}
	
	public static iWireData loadDynamicData(String cfgAbsoluteFilename, int propertyId) {
		String fn = cfgAbsoluteFilename + "--" + propertyId + ".dyn";
		File f = new File(fn);
		if (!f.canRead()) { return errMessage("file not found: " + fn); } // file not found
		
		String typeName = null;
		String jsonData = null; 
		try(BufferedReader br = new BufferedReader(new FileReader(f))) {
			String line = br.readLine();
			if (line == null) { return errMessage("file is empty: " + fn); }
			typeName = line;
			
			line = br.readLine();
			if (line == null || !line.startsWith("####")) {
				return errMessage("missing separator line:" + fn);
			}
			
			StringBuilder sb = new StringBuilder();
			line = br.readLine();
			String sep = "";
			while (line != null) {
				sb.append(sep).append(line);
				sep = "\n";
				line = br.readLine();
			}
			jsonData = sb.toString();
		} catch (IOException e) {
			return errMessage("error reading file: " + fn + "\n### reason: " + e.getMessage());
		}
		
		iWireData propertyData = null;
		try {
			Class<?> clazz = Class.forName(typeName);
			Method make = clazz.getMethod("make");
			propertyData = (iWireData)make.invoke(null);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return errMessage("ClassNotFoundException on class: " + typeName);
		} catch (NoSuchMethodException e) {
			return errMessage("NoSuchMethodException on class: " + typeName);
		} catch (SecurityException e) {
			return errMessage("SecurityException on class: " + typeName);
		} catch (IllegalAccessException e) {
			return errMessage("IllegalAccessException when ::make() on class: " + typeName);
		} catch (IllegalArgumentException e) {
			return errMessage("IllegalArgumentException when ::make() on class: " + typeName);
		} catch (InvocationTargetException e) {
			return errMessage("InvocationTargetException when ::make() on class: " + typeName);
		} catch(Exception e) {
			return errMessage(e.getClass().getSimpleName() + " on class: " + typeName + "\n### reason: " + e.getMessage());
		}
		if (propertyData == null) {
			return errMessage("make() returned null for file:" + fn);
		}
		
		JsonStringReader jsonReader = new JsonStringReader(jsonData);
		try {
			propertyData.deserialize(jsonReader);
		} catch(Exception e) {
			return errMessage("invalid JSON in file: " + typeName + "\n### reason: " + e.getMessage());
		}
		return propertyData;
	}
	
	public List<iUpdatableChsEntry> listUpdatableEntries(int type) {
		return this.entries.values().stream().filter( e -> e.getType() == type ).collect(Collectors.toList());
	}
	
	/*
	 * debug functionality
	 */
	
	public void dump() {
		if (this.fqns == null) {
			System.out.printf("no CHS database configured\n");
			return;
		}
		
		System.out.printf("\n## fqn -> entry map:\n");
		for (Entry<String, ChsEntry> e : this.entries.entrySet()) {
			System.out.printf(" %40s => %s\n", e.getKey(), e.getValue().toString());
		}
		
		System.out.printf("\n## any -> fqn map\n");
		for (Entry<String, String> m : this.fqns.entrySet()) {
			System.out.printf(" %40s => %s\n", m.getKey(), m.getValue());
		}
		
		System.out.printf("\n## entries:\n");
		for (Entry<String, ChsEntry> e : this.entries.entrySet()) {
			System.out.println();
			e.getValue().dump();
		}
		
		System.out.printf("\n##########\n");
	}
	
}
