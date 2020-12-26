/*
Copyright (c) 2019, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level4.filing.fs;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import dev.hawala.xns.level4.common.Time2;

/**
 * Metadata for a single file in the volume with externalizing (save to disk) and
 * internalizing (restore from disk) of the metadata.
 * <br/>
 * The metadata managed in an instance of this class reflect all attributes defined
 * and documented in the Filing Courier protocol.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class FileEntry {
	
	/*
	 * ************* attributes defined in the filing protocol
	 */
	
	/*
	 * identification related attributes
	 */
	
	// 4.2.2.1 fileID (attribute-type = 4)
	private final long fileID; // lower 48 bits are significant => fileID[2..4] ; fileID[0..1] === hashValue(file-service-name)
	
	// 4.2.2.2 isDirectory (attribute-type = 5)
	private final boolean isDirectory;
	
	// 4.2.2.3 isTemporary (attribute-type = 6)
	private final boolean isTemporary; // necessary? => temporary <-> not persisted, not in directory tree => separate storage area??
	
	// 4.2.2.4 name (attribute-type = 9)
	private String name; // in fact an xstring => special representation Courier => here => disk => here => Courier !!!
	private String lcName = null;

	// 4.2.2.5 pathname (attribute-type = 21)
	// *** dynamically recomputed ***
	// private String pathname; // in fact an xstring => special representation Courier => here => disk => here => Courier !!!
	
	// 4.2.2.6 type (attribute-type = 17)
	private long type = FsConstants.tUnspecified; // lower 32 bits <-> long cardinal
	
	// 4.2.2.7 version (attribute-type = 18)
	private int version = 1; // lower 16 bits <-> cardinal
	
	/*
	 * content-related attributes
	 */
	
	// 4.2.3.1 checksum (attribute-type = 0)
	private int checksum = FsConstants.unknownChecksum; // cardinal
	
	// 4.2.3.2 dataSize (attribute-type = 16)
	private long dataSize = 0; // lower 32 bits <-> long cardinal
	
	// 4.2.3.3 storedSize (attribute-type = 26)
	// **** 'datasize' rounded up to page size + 1 page ****
	private long storedSize = FsConstants.pageSizeBytes; // lower 32 bits <-> long cardinal
	
	/*
	 * parent-related attributes
	 */
	
	// 4.2.4.1 parentID (attribute-type = 12)
	private long parentID; // lower 48 bits, see fileID
	
	// 4.2.4.2 position (attribute-type = 13)
	private long position = 0; // lower 63 bits are relevant, so we use the first 4 words of the SEQUENCE 100 OF UNSPECIFIED
	
	/*
	 * event-related attributes
	 */
	
	// 4.2.5.1 createdBy (attribute-type = 2)
	private String createdBy; // CHS.ThreePartName
	
	// 4.2.5.2 createdOn (attribute-type = 3)
	private long createdOn; // Time2.Time
	
	// 4.2.5.3 readBy (attribute-type = 14)
	private String readBy = FsConstants.noUser; // CHS.ThreePartName
	
	// 4.2.5.4 readBy (attribute-type = 15)
	private long readOn = -1; // Time2.Time (long cardinal => long(-1) ~ not set)
	
	// 4.2.5.5 modifiedBy (attribute-type = 7)
	private String modifiedBy = FsConstants.noUser; // CHS.ThreePartName
	
	// 4.2.5.6 modifiedOn (attribute-type = 8)
	private long modifiedOn = -1; // Time2.Time (long cardinal => long(-1) ~ not set)
	
	/*
	 * directory-related attributes
	 */
	
	// 4.2.6.1 childrenUniquelyNamed (attribute-type = 1) 
	private boolean childrenUniquelyNamed = true;
	
	// 4.2.6.2 numberOfChildren (attribute-type = 10)
	// *** dynamically recomputed ***
	// private int numberOfChildren = -1; // cardinal, persistence not needed, computed on load (-1 ~ not yet computed)
	
	// 4.2.6.3 ordering (attribute-type = 11)
	private long orderingKey = 9; // 1st field of the record (default: name)
	private boolean orderingAscending = true; // 2nd field of the record
	private int orderingInterpretation = FsConstants.intrNone; // 3rd field of the record (enum)
	
	// 4.2.6.4 subtreeSize (attribute-type = 27)
	// *** dynamically recomputed ***
	// private long subtreeSize = -1; // lower 64 bits <-> long cardinal, persistence not needed, computed on load (-1 ~ not yet computed)
	
	// 4.2.6.4 subtreeSizeLimit (attribute-type = 28)
	private long subtreeSizeLimit = FsConstants.nullSubtreeSizeLimit; // lower 64 bits <-> long cardinal
	
	/*
	 * access-related attributes
	 */
	
	// 4.2.7.1 accessList (attribute-type = 19)
	private final List<AccessEntry> accessList = new ArrayList<>();
	private boolean accessListDefaulted = true;
	
	// 4.2.7.1 accessList (attribute-type = 20)
	private final List<AccessEntry> defaultAccessList = new ArrayList<>();
	private boolean defaultAccessListDefaulted = true;
	
	/*
	 * uninterpreted attributes
	 */
	
	private final List<UninterpretedAttribute> uninterpretedAttributes = new ArrayList<>();
	
	/*
	 * implementation private persistent attributes
	 */
	
	private boolean hasContent = false; 
	
	/*
	 * ************* internal attributes at runtime
	 */
	
	private FileEntry parent; // null -> direct child of volume root OR temporary file
	
	private DirectoryChildren children = null; // only when isDirectory = true, managed by the Volume owning this file-entry
	
	
	/*
	 * construction, internalizing and externalizing
	 */
	
	// create the impossible file as root directory instance
	private FileEntry() {
		this.fileID = FsConstants.rootFileID;
		this.isDirectory = true;
		this.isTemporary = false;
		this.name = "";
		this.version = 1;
		this.type = FsConstants.tDirectory;
		this.parentID = 0;
		
		this.createdOn = 0; // beginning of mesa time...
		this.createdBy = FsConstants.noUser;
		
		this.children = new DirectoryChildren(null);
	}
	
	/**
	 * @return a file entry that can be used as dummy root file for a volume.
	 */
	public static FileEntry createRootFileEntry() {
		return new FileEntry();
	}
	
	/** create a temporary file */
	public FileEntry(long fileID, String name, String creator) {
		this(fileID, FsConstants.noFileID, false, name, 1, 0, creator);
	}
	
	public FileEntry(long fileID, long parentID, boolean isDirectory, String name, int version, long type, String creator) {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Invalid empty name for FileEntry");
		}
		if (fileID == FsConstants.rootFileID) {
			throw new IllegalArgumentException("root directory cannot be created");
		}
		
		this.fileID = fileID;
		this.parentID = parentID;
		this.name = name;
		this.version = version;
		this.type = type;

		this.isTemporary = (parentID == FsConstants.noFileID); // ?? < 0
		this.isDirectory = (this.isTemporary) ? false : isDirectory;
		
		this.createdOn = Time2.getMesaTime();
		this.createdBy = (creator == null || creator.isEmpty()) ? FsConstants.noUser : creator;
		
		if (this.isDirectory) {
			this.children = new DirectoryChildren(this);
		}
	}
	
	public FileEntry(String externalized) {
		String[] parts = externalized.split("\t");
		if (parts.length < 27) { // 21 for singular attributes, 1 for hasContent, min. 2x2 for access attributes + min. 1 for uninterpreted attributes  
			throw new IllegalArgumentException("Invalid externalized file-entry (< 26 components)");
		}
		
		// persisted files are never temporary
		this.isTemporary = false;
		
		// singular attributes
		this.fileID = Long.parseUnsignedLong(parts[0], 16);
		this.isDirectory = (Integer.parseInt(parts[1]) != 0);
		this.name = parts[2];
		this.type = Long.parseLong(parts[3]);
		this.version = Integer.parseInt(parts[4]);
		this.checksum = Integer.parseInt(parts[5], 16) & 0xFFFF;
		this.dataSize = Long.parseLong(parts[6]);
		this.storedSize = Long.parseLong(parts[7]);
		this.parentID = Long.parseUnsignedLong(parts[8], 16); // parseLong() is incapable to parse negative hex values !?!?! (https://stackoverflow.com/questions/1410168/how-to-parse-negative-long-in-hex-in-java)
		this.position = Long.parseUnsignedLong(parts[9], 16);
		this.createdBy = parts[10];
		this.createdOn = Long.parseUnsignedLong(parts[11], 16);
		this.readBy = parts[12];
		this.readOn = Long.parseUnsignedLong(parts[13], 16);
		this.modifiedBy = parts[14];
		this.modifiedOn = Long.parseUnsignedLong(parts[15], 16);
		this.childrenUniquelyNamed = (Integer.parseInt(parts[16]) != 0);
		this.orderingKey = Long.parseLong(parts[17]);
		this.orderingAscending = (Integer.parseInt(parts[18]) != 0);
		this.orderingInterpretation = Integer.parseInt(parts[19]);
		this.subtreeSizeLimit = Long.parseLong(parts[20]);
		this.hasContent = (Integer.parseInt(parts[21]) != 0);
		
		if (this.isDirectory) {
			this.children = new DirectoryChildren(this);
		}
		
		int f = 22;
		
		// access list
		int accessListSize = Integer.parseInt(parts[f++]);
		this.accessListDefaulted = (Integer.parseInt(parts[f++]) != 0);
		for (int i = 0; i < accessListSize; i++) {
			this.accessList.add(new AccessEntry(parts[f++]));
		}
		
		// default access list
		int defaultAccessListSize = Integer.parseInt(parts[f++]);
		this.defaultAccessListDefaulted = (Integer.parseInt(parts[f++]) != 0);
		for (int i = 0; i < defaultAccessListSize; i++) {
			this.defaultAccessList.add(new AccessEntry(parts[f++]));
		}
		
		// uninterpreted attributes
		int attrCount  = Integer.parseInt(parts[f++]);
		for (int i = 0; i < attrCount; i++) {
			try {
				this.uninterpretedAttributes.add(new UninterpretedAttribute(parts[f++]));
			} catch (IllegalArgumentException iae) {
				System.out.printf("## error internalizing file '%s', ignoring uninterpreted attribute, reason: %s\n", iae.getMessage());
			}
		}
	}
	
	public void externalize(PrintStream to) {
		// singular attributes
		to.printf("%X\t%d\t%s\t%d\t%d" // fileID, isDirectory, name, type, version
	          + "\t%X\t%d\t%d"         // checksum, dataSize, storedSize
			  + "\t%X\t%X"             // parentID, position
	          + "\t%s\t%X"             // createdBy, createdOn
			  + "\t%s\t%X"             // readBy, readOn
	          + "\t%s\t%X"             // modifiedBy, modifiedOn
	          + "\t%d\t%d\t%d\t%d\t%d" // childrenUniquelyNamed, orderingKey, orderingAscending, orderingInterpretation, subtreeSizeLimit
	          + "\t%d"                 // hasContent
	        ,
			this.fileID, // hex
			(this.isDirectory) ? 1 : 0,
			this.name,
			this.type,
			this.version,
			this.checksum, // hex
			this.dataSize,
			this.storedSize,
			this.parentID, // hex
			this.position, // hex
			this.createdBy,
			this.createdOn, // hex
			this.readBy,
			this.readOn, // hex
			this.modifiedBy,
			this.modifiedOn, // hex
			(this.childrenUniquelyNamed) ? 1 : 0,
			this.orderingKey,
			(this.orderingAscending) ? 1 : 0,
			this.orderingInterpretation,
			this.subtreeSizeLimit,
			(this.hasContent) ? 1 : 0
		);
		
		// access list
		to.printf("\t%d\t%d", this.accessList.size(), (this.accessListDefaulted) ? 1 : 0);
		for (AccessEntry e : this.accessList) {
			to.print("\t");
			e.externalize(to);
		}
		
		// default access list
		to.printf("\t%d\t%d", this.defaultAccessList.size(), (this.defaultAccessListDefaulted) ? 1 : 0);
		for (AccessEntry e : this.defaultAccessList) {
			to.print("\t");
			e.externalize(to);
		}
		
		// uninterpreted attributes
		to.printf("\t%d", this.uninterpretedAttributes.size());
		for (UninterpretedAttribute a : this.uninterpretedAttributes) {
			to.print("\t");
			a.externalize(to);
		}
	}
	
	
	/*
	 * internal methods
	 */
	
	private String computePathname() {
		String parentPath = (this.parent != null) ? this.parent.computePathname() + "/" : "";
		return parentPath + this.name + "!" + this.version;
	}
	
	/*
	 * public methods for dynamically recomputed attributes
	 */
	
	public String getPathname() {
		return this.computePathname();
	}

	public long getSubtreeSize() {
		long subtreeSize = this.getStoredSize();
		
		if (this.isDirectory && this.children != null) {
			for (FileEntry fe : this.children.getChildren()) {
				subtreeSize += fe.getSubtreeSize();
			}
		}
		
		return subtreeSize;
	}
	
	/*
	 * simple attribute getter/setter
	 */

	public long getFileID() {
		return fileID;
	}

	public boolean isDirectory() {
		return isDirectory;
	}

	public boolean isTemporary() {
		return isTemporary;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
		this.lcName = null;
	}
	
	public String getLcName() {
		if (this.lcName == null) {
			this.lcName = (this.name != null) ? this.name.toLowerCase() : "";
		}
		return this.lcName;
	}

	public long getType() {
		return type;
	}

	public void setType(long type) {
		this.type = type;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public int getChecksum() {
		return checksum;
	}

	public void setChecksum(int checksum) {
		this.checksum = checksum;
	}

	public long getDataSize() {
		return dataSize;
	}

	public void setDataSize(long dataSize) {
		this.dataSize = dataSize;
		this.storedSize = (((dataSize + FsConstants.pageSizeBytes - 1) / FsConstants.pageSizeBytes) + 1) * FsConstants.pageSizeBytes;
	}

	public long getStoredSize() {
		return storedSize;
	}

	public long getParentID() {
		return (parentID == 0 && !isTemporary) ? FsConstants.rootFileID : parentID;
	}

	public void setParentID(long parentID) {
		this.parentID = parentID;
	}

	public long getPosition() {
		return position;
	}

	public void setPosition(long position) {
		this.position = position;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public long getCreatedOn() {
		return createdOn;
	}

	public void setCreatedOn(long createdOn) {
		this.createdOn = createdOn;
	}

	public String getReadBy() {
		return readBy;
	}

	public void setReadBy(String readBy) {
		this.readBy = readBy;
	}

	public long getReadOn() {
		return readOn;
	}

	public void setReadOn(long readOn) {
		this.readOn = readOn;
	}

	public String getModifiedBy() {
		return modifiedBy;
	}

	public void setModifiedBy(String modifiedBy) {
		this.modifiedBy = modifiedBy;
	}

	public long getModifiedOn() {
		return modifiedOn;
	}

	public void setModifiedOn(long modifiedOn) {
		this.modifiedOn = modifiedOn;
	}

	public boolean isChildrenUniquelyNamed() {
		return childrenUniquelyNamed;
	}

	public void setChildrenUniquelyNamed(boolean childrenUniquelyNamed) {
		this.childrenUniquelyNamed = childrenUniquelyNamed;
	}

	public int getNumberOfChildren() {
		if (!this.isDirectory || this.children == null) {
			return 0;
		}
		return this.children.getChildren().size();
	}

	public long getOrderingKey() {
		return orderingKey;
	}

	public void setOrderingKey(long orderingKey) {
		this.orderingKey = orderingKey;
	}

	public boolean isOrderingAscending() {
		return orderingAscending;
	}

	public void setOrderingAscending(boolean orderingAscending) {
		this.orderingAscending = orderingAscending;
	}

	public int getOrderingInterpretation() {
		return orderingInterpretation;
	}

	public void setOrderingInterpretation(int orderingInterpretation) {
		this.orderingInterpretation = orderingInterpretation;
	}

	public long getSubtreeSizeLimit() {
		return subtreeSizeLimit;
	}

	public void setSubtreeSizeLimit(long subtreeSizeLimit) {
		this.subtreeSizeLimit = subtreeSizeLimit;
	}

	public List<AccessEntry> getAccessList() {
		return accessList;
	}

	public boolean isAccessListDefaulted() {
		return accessListDefaulted;
	}

	public void setAccessListDefaulted(boolean accessListDefaulted) {
		this.accessListDefaulted = accessListDefaulted;
	}

	public List<AccessEntry> getDefaultAccessList() {
		return defaultAccessList;
	}

	public boolean isDefaultAccessListDefaulted() {
		return defaultAccessListDefaulted;
	}

	public void setDefaultAccessListDefaulted(boolean defaultAccessListDefaulted) {
		this.defaultAccessListDefaulted = defaultAccessListDefaulted;
	}
	
	public boolean getHasContent() {
		return hasContent;
	}
	
	public void setHasContent(boolean hasContent) {
		this.hasContent = hasContent;
	}
	
	/*
	 * getter/setter for runtime fields
	 */

	public FileEntry getParent() {
		return parent;
	}

	public void setParent(FileEntry parent) {
		this.parent = parent;
	}

	public DirectoryChildren getChildren() {
		return children;
	}

	public List<UninterpretedAttribute> getUninterpretedAttributes() {
		return uninterpretedAttributes;
	}
	
	public UninterpretedAttribute getUninterpretedAttribute(long type) {
		for (UninterpretedAttribute a : this.uninterpretedAttributes) {
			if (a.getType() == type) {
				return a;
			}
		}
		return null;
	}

	
	/*
	 * misc methods
	 */

	@Override
	public String toString() { // generated by eclipse
		return "FileEntry [fileID=" + fileID + ", isDirectory=" + isDirectory + ", isTemporary=" + isTemporary
				+ ", name=" + name + ", type=" + type + ", version=" + version + ", checksum=" + checksum
				+ ", dataSize=" + dataSize + ", storedSize=" + storedSize + ", parentID=" + parentID + ", position="
				+ position + ", createdBy=" + createdBy + ", createdOn=" + createdOn + ", readBy=" + readBy
				+ ", readOn=" + readOn + ", modifiedBy=" + modifiedBy + ", modifiedOn=" + modifiedOn
				+ ", childrenUniquelyNamed=" + childrenUniquelyNamed + ", orderingKey=" + orderingKey
				+ ", orderingAscending=" + orderingAscending + ", orderingInterpretation=" + orderingInterpretation
				+ ", subtreeSizeLimit=" + subtreeSizeLimit + ", accessList=" + accessList + ", accessListDefaulted="
				+ accessListDefaulted + ", defaultAccessList=" + defaultAccessList + ", defaultAccessListDefaulted="
				+ defaultAccessListDefaulted + ", uninterpretedAttributes=" + uninterpretedAttributes + "]";
	}
	
}