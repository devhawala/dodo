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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import dev.hawala.xns.Log;
import dev.hawala.xns.PropertiesExt;
import dev.hawala.xns.level4.chs.CHEntries0;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;

/**
 * Clearinghouse database, supporting exactly one domain and
 * initialized from property files holding the entry data.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class ChsDatabase {
	
	private final boolean produceStrongKeyAsSpecified;

	private final String organizationName;
	private final String domainName;
	private final String domOrgPartLc;
	private final String domainAndOrganization;
	
	// naming:
	//   fqn = full qualified name :: mixed case primary 3-part name (also called "distinguished name")
	//   nqn = normalized qualified name :: lowercase version of the fqn used for searching/lookups
	//   pn  = primary name :: first component of the fqn
	
	// map any-lc(nqn,alias) => fqn
	private final Map<String,String> fqns;
	
	// map fqn => chs-entry
	private final Map<String,ChsEntry> entries;
	
	/**
	 * Construction of a clearinghouse database from a set of propewrty files defining the
	 * objects in this domain and organization.
	 * 
	 * @param organization the organization handled by this clearinghouse database.
	 * @param domain the domain handled by this clearinghouse database.
	 * @param chsDatabaseRoot the name of the directory where the property files
	 * 		defining the objects in this domain are located.
	 * @param strongKeysAsSpecified how to handle the contradiction in the specification
	 * 		(Authentication Protocol, XSIS 098404, April 1984), where the data used in the
	 * 		example does not match the specification for the strong key generation (section 5.3):
	 * 		<br/>
	 * 		if {@code true} encode each 4 char-block with the password to produce the next
	 * 		password (as specified, but this does match <i>not</i> the data in the example), else (if
	 * 		{@code false}) exchange the encryption parameters, i.e. use each 4 char-block to
	 * 		encrypt the password of the last iteration to produce the new password (this
	 * 		contradicts the specification, but creates the data in the example...)
	 */
	public ChsDatabase(String organization, String domain, String chsDatabaseRoot, boolean strongKeysAsSpecified) {
		// save configuration data
		this.organizationName = organization;
		this.domainName = domain;
		this.domOrgPartLc = ":" + domain.toLowerCase() + ":" + organization.toLowerCase();
		this.domainAndOrganization = ":" + domain + ":" + organization;
		this.produceStrongKeyAsSpecified = strongKeysAsSpecified;
		
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
				if (fn.startsWith("ug~")) { entry = new ChsUserGroup(ef); }
			} catch (Exception e) {
				System.out.printf("Unable to load CHS entry from file '%s': %s\n", ef.getName(), e.getMessage());
				continue;
			}
			if (entry == null) {
				System.out.printf("Unknonwn CHS entry type for file '%s'\n", ef.getName());
				continue;
			}
			
			String fqn = entry.getPn() + this.domainAndOrganization;
			String nqn = fqn.toLowerCase();
			if (this.entries.containsKey(nqn)) {
				System.out.printf(
						"Warning: Duplicate entry for FQN '%s' from file '%s' ignored (entry defined in: %s)\n",
						fqn, ef.getName(), this.entries.get(nqn).getCfgFilename());
				continue;
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
		
		// post process all database entries
		for (Entry<String, ChsEntry> entry : this.entries.entrySet()) {
			entry.getValue().postpare(entry.getKey());
		}
		
		// check consistency in all database entries
		for (Entry<String, ChsEntry> entry : this.entries.entrySet()) {
			entry.getValue().postcheck();
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
	 * Find the distinguished name for a given name, returning
	 * {@code null} if the name to look for is not defined in the
	 * clearinghouse database as object name or as alias.
	 * 
	 * @param forName the name to find and return the distinguished name for.
	 * @return the distinguished name or {@code null} if no such clearinghouse
	 * 		entry exists.
	 */
	public ThreePartName getDistinguishedName(ThreePartName forName) {
		if (this.fqns == null) { return forName; }
		String fqn = this.getFqnForName(forName);
		if (fqn == null) { return null; }
		String[] parts = fqn.split(":");
		Name dn = Name.make();
		dn.object.set(parts[0]);
		dn.domain.set(parts[1]);
		dn.organization.set(parts[2]);
		return dn;
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
	public byte[] getStrongPassword(Name forName) {
		if (this.fqns != null) {
			ChsEntry entry = this.getEntryForName(forName);
			if (entry == null) {
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
			byte[] keyBytes = StrongAuthUtils.getStrongKey(objectName, this.produceStrongKeyAsSpecified);
			return keyBytes;
		} catch (Exception e) {
			// let's pretend that the user does not exist if no password can be generated
			Log.AUTH.printf("Cannot make strong key for '%s:%s:%s' pretending: forName does not exist\n",
					objectName, forName.domain.get(), forName.organization.get());
			throw new IllegalArgumentException("Object not found");
		}
	}
	
	
	/*
	 * ******************** internal utilities
	 */
	
	private String getFqnForName(ThreePartName name) {
		String objectName = name.object.get();
		String domain = name.domain.get();
		String organization = name.organization.get();
		String fullname = objectName + ":" + domain + ":" + organization;
		String nqn = fullname.toLowerCase();
		return this.fqns.get(nqn);
	}
	
	private ChsEntry getEntryForName(Name name) {
		String fqn = this.getFqnForName(name);
		if (fqn == null) { return null; }
		return this.entries.get(fqn);
	}
	
	private byte[] computePasswordStrongHash(String password) {
		try {
			byte[] keyBytes = StrongAuthUtils.getStrongKey(password, this.produceStrongKeyAsSpecified);
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
	
	/*
	 * ***** in-memory representation of clearinghouse entries 
	 */
	
	private abstract class ChsEntry {
		
		private final String cfgFilename;
		
		protected final String objNameFromCfgFile;
		protected final int entryType;
		protected final PropertiesExt props;
		
		private final List<String> cfgAliases = new ArrayList<>();
		private final List<String> aliasFqns = new ArrayList<>();
		
		protected String fqn = null;
		
		public ChsEntry(int type, File propsFile) {
			this.cfgFilename = propsFile.getName();
			this.entryType = type;
			this.props = new PropertiesExt(propsFile);
			
			// extract the name component from the filename
			String filename = propsFile.getName();
			String basename = filename.substring(0, filename.length() - 11).replace(" ", ""); // remove .properties and blanks
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
		}
		
		public String getCfgFilename() {
			return this.cfgFilename;
		}
		
		public int getType() { return this.entryType; }
		
		public abstract String getDescription();
		
		public abstract String getPn();
		
		public void postpare(String fqn) {
			this.fqn = fqn;
		}
		
		public void postcheck() {
			// check consistence, e.g. for cyclic references, recursive groups
		}
		
		@Override
		public String toString() {
			return this.getClass().getSimpleName() + "[ pn='" + this.getPn() + "' , description='" + this.getDescription() + "' ]";
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
		
		private final String mailservice;
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
			this.mailservice = this.getLcObjName(this.props.getProperty("mailservice", "none")) + domOrgPartLc;
		}
		
		protected void dumpSpecifics() {
			super.dumpSpecifics();
			System.out.printf("  username     : %s\n", this.username);
			System.out.printf("  lastnameIndex: %d\n", this.lastnameIndex);
			System.out.printf("  mailservice  : %s\n", this.mailservice);
			System.out.printf("  fileservice  : %s\n", this.fileservice);
		}

		@Override
		public String getDescription() {
			return this.username;
		}

		@Override
		public String getPn() {
			return this.username;
		}

		public int getLastnameIndex() {
			return lastnameIndex;
		}

		public String getMailserviceFqn() {
			return mailservice;
		}

		public String getFileserviceFqn() {
			return fileservice;
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
			
			String mId = this.props.getProperty("machineId", null);
			if (mId == null) {
				throw new IllegalStateException("No machineId defined for service: " + this.objNameFromCfgFile);
			}
			String[] submacs = mId.split("-");
			if (submacs.length != 6) {
				throw new IllegalStateException("Invalid processor id format (not XX-XX-XX-XX-XX-XX): " + mId);
			}
			
			long macId = 0;
			for (int i = 0; i < submacs.length; i++) {
				macId = macId << 8;
				try {
					macId |= Integer.parseInt(submacs[i], 16) & 0xFF;
				} catch (Exception e) {
					throw new IllegalStateException("Error: invalid processor id format (not XX-XX-XX-XX-XX-XX): " + mId);
				}
			}
			this.machineId = macId;
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
		public String getPn() {
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
		
		public ChsClearinghouseSvc(File cfgFile) {
			super(CHEntries0.clearinghouseService, cfgFile);
			this.mailservice = this.getLcObjName(this.props.getProperty("mailservice", "none")) + domOrgPartLc;
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
		public String getPn() {
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
		
		public ChsUserGroup(File cfgFile) {
			super(CHEntries0.userGroup, cfgFile);
		}
		
	}
	
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
