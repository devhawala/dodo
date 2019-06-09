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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import dev.hawala.xns.level4.common.Time2;

/**
 * Implementation of a volume holding the file system for a XNS File Service.
 * <p>
 * A volume instance provides all functionality for a persistent representation
 * of the files with content and metadata as defined by the Filing Courier protocol
 * (program 10, versions 4, 5 and 6). However it does not provide any operations at the
 * Courier protocol level, which must be implemented by a client using one or more volumes
 * for providing file services at protocol level.
 * </p>
 * <p>
 * All files necessary for representing the (xns) file system of the volume are located inside
 * a single (os) directory, see method {@code openVolume(...)}. Opening a volume for an empty
 * directory creates a new file system and initializes the required files and sub-directories
 * in that directory. However, for a useful file system, new volumes should only be created
 * for directories containing a file {@code root-folder.lst} defining the root folders in the
 * volume ("File Drawers" in Star/ViewPoint/GlobalView jargon).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class Volume {
	
	public static class VolumeLockedException extends Exception {
		private static final long serialVersionUID = 7559714964127502888L;
	}
	
	// max. number of files in content-directories when adding files
	private static final int DATA_DIR_LIMIT = 256;
	
	private static final String METADATA_SUBDIR = "metadata";
	private static final String OLDMETADATA_SUBDIR = "old-metadata";
	private static final String DATADIR_PATTERN = "files-%016X";
	private static final String DATAFILE_PATTERN = "%016X.data";
	private static final String ROOTFOLDERS_FILE = "root-folder.lst";
	
	private static final String METADATA_GEN_DELTA_PATTERN = "%016X.delta-meta";
	private static final String METADATA_GEN_FULL_PATTERN = "%016X.full-meta";
	private static final String METADATA_LIST_PATTERN = "^[0-9A-Za-z]{16}\\.(full|delta)-meta$";
	private static final String DATADIR_LIST_PATTERN = "^files-[0-9A-Za-z]{16}$";
	
	private final String volumeName;
	private final File baseDir;
	private final File metadataDir;
	private final iErrorRaiser errorRaiser;
	private final FileLock volumeLock;
	
	private final Map<Long,FileEntry> fileEntries = new HashMap<>(); // all files in the volume
	
	public final FileEntry rootDirectory;
	private final DirectoryChildren rootFolders; // = new DirectoryChildren(null); // top-level: all root folders ("file drawers")
	
	private long nextFileID = 1;
	private long lastUsedTS = System.currentTimeMillis();

	private int metadataDeltaCount = 0; // TODO: force writing a baseline before creating a new delta when a max. delta count is reached  
	private boolean closed = false;
	
	private List<Long> dataDirStarts = new ArrayList<>();
	private int filesInlastDataDir = 0;
	
	private Session currentSession = null;
	private Thread currentSessionThread = null;
	private int currentSessionNesting = 0;
	
	private final Map<Long,Long> readTimes = new HashMap<>();
	private final Map<Long,String> readUsers = new HashMap<>();
	
	// private constructor to prevent rogue creation of volumes => force using Volume.openVolume()
	@SuppressWarnings("resource")
	private Volume(File dir, iErrorRaiser errorRaiser) throws VolumeLockedException {
		try {
			File lockFile = new File(dir, "volumeLock");
			FileChannel channel = new RandomAccessFile(lockFile, "rw").getChannel(); // the lock is closed() in Volume.close(), not here
			this.volumeLock = channel.tryLock(); // the lock is released in Volume.close()
			if (this.volumeLock == null) {
				throw new OverlappingFileLockException();
			}
		} catch (OverlappingFileLockException | IOException e) {
			System.out.println("ERROR: volume already opened by an other process, cannot open volume concurently, ABORTING");
			throw new VolumeLockedException();
		}
		
		this.volumeName = dir.getName();
		this.baseDir = dir;
		this.metadataDir = new File(this.baseDir, METADATA_SUBDIR);
		this.errorRaiser = errorRaiser;
		
		this.rootDirectory = FileEntry.createRootFileEntry();
		this.rootFolders = this.rootDirectory.getChildren();
	}
	
	/**
	 * Open the volume located at the given directory.
	 * <p>
	 * The directory name is also the volume name, located at
	 * the path specified in the directory name.
	 * <br/>
	 * This directory can be initially empty, but should at least contain
	 * a file {@code root-folder.lst}, which names the folders ("file drawers")
	 * that should exist in the volume root; each line in this file has
	 * the following tab separated fields: folder-name, folder-owner-chs-name,
	 * access-chs-names (given as o!|w!|r!chs-name). This file defines the folders
	 * that should exists <i>at least</i>, so root folders already in the volume
	 * will <b>not</b> be deleted if not in the list. 
	 * </p>
	 * <p>
	 * This directory will be filled with the following items:
	 * <ul>
	 *   <li>
	 *   directories {@code files-HHHHHHHH} containing the file contents for fileIDs
	 *   starting with HHHHHHHH (hex fileID). Each content file is named {@code HHHHHHHH.data}.
	 *   </li>
	 *   <li>
	 *   directory {@code metadata} containing the current metadata for all files in the volume
	 *   the following metadata files are maintained:
	 *   <ul>
	 *     <li>
	 *     {@code [timestamp].full-meta}&nbsp;: the metadata for all files in the volume at <i>[timestamp]</i>;
	 *     this is the baseline when loading the current metadata state 
	 *     </li>
	 *     <li>
	 *     {@code [timestamp].delta-meta}&nbsp;: the updates to the last baseline, multiple delta-meta files
	 *     from now descending in time back to the baseline are applied to the baseline file. 
	 *     </li>
	 *   </ul>
	 *   Each metadata file can have entries for the next free fileId ({@code N:}<i>decimal-num</i>,
	 *   for file read timestamps and users ({@code R:}<hex-fileID:hex-mesatime:user-chs-name),
	 *   for deleted files (line starts with minus-character, followed by the hex fileID) or for
	 *   existing files (complete file entry line).
	 *   <br/>
	 *   Obsolete metadata files are moved to the sub-directory {@code old-metadata}. 
	 * </ul>
	 * </p>
	 * 
	 * @param volumeDirectory directory with path in the local file system where the volume is
	 * 		located, specifying both the location and the volume name
	 * @param errorRaiser object mapping error events recognized during volume operations
	 * 		to the corresponding error representation at client level
	 * @return the opened volume, ready to be used for XNS file operations
	 * @throws VolumeLockedException this volume is already in use by an other process 
	 * 		and cannot be used by this process.
	 * @throws IOException for problems accessing volume files
	 */
	public static Volume openVolume(String volumeDirectory, iErrorRaiser errorRaiser) throws VolumeLockedException, IOException {
		File volDir = new File(volumeDirectory);
		if (!volDir.exists() || !volDir.isDirectory() || !volDir.canRead() || !volDir.canWrite()) {
			throw new IllegalArgumentException("Invalid or not usable volume directory specified");
		}
		
		// create the volume object and ensure that the minimal directories are there
		// simplifying life for subsequent open subfunctions)
		Volume vol = new Volume(volDir, errorRaiser);
		System.out.printf("Opening volume '%s' from directory: %s\n", vol.volumeName, vol.baseDir.getAbsolutePath());
		if (!vol.metadataDir.exists()) { vol.metadataDir.mkdir(); }
		File oldMetadataDir = new File(volDir, OLDMETADATA_SUBDIR);
		if (!oldMetadataDir.exists()) { oldMetadataDir.mkdir(); }
		File filesZeroDir = new File(volDir, String.format(DATADIR_PATTERN, 0));
		if (!filesZeroDir.exists()) { filesZeroDir.mkdir(); }
		
		// load metadata files
		// 1. list files with filenames matching metadata file patterns
		// 2. sort *descending* by name
		// 3. load metadata from them until the first baseline file was loaded
		List<Long> deletedFiles = new ArrayList<>(); // fileIDs of deleted files => ignore their (older) metadata
		File[] childFiles = vol.metadataDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return filename.matches(METADATA_LIST_PATTERN);
			}
		});
		List<File> children = new ArrayList<>(Arrays.asList(childFiles));
		children.sort( (first, second) -> second.getName().compareTo(first.getName()) );
		for (File f : children) {
			System.out.printf("loading metadata from: %s\n", f.getName());
			vol.loadMetadataFile(f, deletedFiles);	
			if (f.getName().endsWith(".full-meta")) { break; } // baseline reached
		}
		for (Long deletedFile : deletedFiles) {
			vol.fileEntries.remove(deletedFile);
		}
		for (Entry<Long, Long> r: vol.readTimes.entrySet()) {
			Long fi = r.getKey();
			FileEntry f = vol.fileEntries.get(fi);
			if (f == null) { continue; } // recorded read info for a subsequently deleted file
			f.setReadOn(r.getValue());
			f.setReadBy(vol.readUsers.get(fi));
		}
		vol.readTimes.clear();
		vol.readUsers.clear();
		
		// build file tree
		for (FileEntry fe : vol.fileEntries.values()) {
			vol.checkParentRelation(fe);
		}
		
		// ensure the requested root folders are present
		vol.checkRootFolders();
		for (FileEntry fe : vol.fileEntries.values()) {
			vol.checkForOwnerProperty(fe);
		}
		
		// scan file contents directories
		File[] dataDirectories = vol.baseDir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return filename.matches(DATADIR_LIST_PATTERN);
			}
		});
		List<File> dataDirs = new ArrayList<>(Arrays.asList(dataDirectories));
		dataDirs.sort( (first, second) -> first.getName().compareTo(second.getName()) );
		int lastFileCount = 0;
		for (File dataDir : dataDirs) {
			if (!dataDir.isDirectory()) { continue; }
			String countParts = dataDir.getName().substring("Files-".length());
			long startId = Long.parseUnsignedLong(countParts,16); // should not raise exceptions as pattern-matched
			vol.dataDirStarts.add(startId);
			lastFileCount = dataDir.list().length;
		}
		vol.filesInlastDataDir = lastFileCount;
		
		// create the current baseline metadata => reduce number of files to read in next time (if volumne is not closed normally)
		vol.saveMetadataBaseline();
		
		// move now obsolete metadata file to old-metadata
		for (File f : children) {
			f.renameTo(new File(oldMetadataDir.getAbsolutePath() + "/" + f.getName()));
		}
		
		// check in-memory file system
		vol.checkConsistency();
		
		// done
		System.out.printf("Volume '%s' opened\n", vol.volumeName);
		return vol;
	}
	
	public void close() throws IOException {
		// create a new baseline if the volume was changed since opening it
		if (this.metadataDeltaCount > 0) {
			// get current metadata files
			File[] metadataFiles = this.metadataDir.listFiles( (dir,fn) -> fn.matches(METADATA_LIST_PATTERN) );
			
			// create the current baseline metadata
			this.saveMetadataBaseline();
			
			// move now obsolete metadata file to old-metadata
			File oldMetadataDir = new File(this.baseDir, OLDMETADATA_SUBDIR);
			if (!oldMetadataDir.exists()) { oldMetadataDir.mkdir(); }
			for (File f : metadataFiles) {
				f.renameTo(new File(oldMetadataDir.getAbsolutePath() + "/" + f.getName()));
			}
		}
		
		// shutdown the volume
		FileChannel chan = this.volumeLock.channel();
		this.volumeLock.release();
		chan.close();
		this.closed = true;
		this.fileEntries.clear();
		this.rootFolders.getChildren().clear();
		this.dataDirStarts.clear();
	}
	
	public static class Session implements AutoCloseable {
		
		private final Volume volume;
		private final Map<Long,FileEntry> deltaFiles = new HashMap<>();
		private final List<Long> deletedFileIds = new ArrayList<>();
		
		private Session(Volume volume) {
			this.volume = volume;
		}
		
		// public modification methods
		
		public FileEntry createFile(
						long parentID,
						boolean isDirectory,
						String name,
						Integer version,
						Long type,
						String creatingUser,
						List<iValueSetter> valueSetters,
						iContentSource contentSource) throws IOException {
			// is it a valid parent?
			FileEntry parent = this.volume.fileEntries.get(parentID);
			if (parent == null) { this.volume.errorRaiser.fileNotFound(parentID, "Invalid parentID + " + parentID + " (not found)"); }
			
			// non-unique filename where uniqueness is required?
			FileEntry oldChild = parent.getChildren().get(name);
			if (oldChild != null && parent.isChildrenUniquelyNamed()) {
				this.volume.errorRaiser.duplicateFilenameForChildrenUniquelyNamed("Duplicate file name when childrenUniquelyNamed");
			}
			
			// determine the version for the new file
			int fVersion = (version != null) ? version.intValue() : 1;
			if (oldChild != null && fVersion <= oldChild.getVersion()) {
				fVersion = oldChild.getVersion() + 1;
			}
			
			// create the file with file attributes and content
			FileEntry fe = new FileEntry(this.volume.getNextFileID(), parentID, isDirectory, name, fVersion, (type == null) ? 0 : type.longValue(), creatingUser);
			fe.setParent(parent);
			for (iValueSetter vs : valueSetters) {
				vs.access(fe);
			}
			this.readInContent(fe, contentSource);
			this.volume.fileEntries.put(fe.getFileID(), fe);
			
			// add it to the parent
			parent.getChildren().add(fe);
			parent.setModifiedOn(fe.getCreatedOn());
			parent.setModifiedBy(creatingUser);
			
			// add new file and parent to changes to persist
			this.deltaFiles.put(fe.getFileID(), fe);
			this.deltaFiles.put(parentID, parent);
			
			// done
			this.volume.checkConsistency();
			return fe;
		}
		
		
		public void updateFileAttributes(long fileID, List<iValueSetter> valueSetters, String updatingUser) {
			FileEntry fe = this.volume.fileEntries.get(fileID);
			if (fe != null) {
				this.updateFileAttributes(fe, valueSetters, updatingUser);
			} else {
				this.volume.errorRaiser.fileNotFound(fileID, "File not found with fileID: " + fileID);
			}
		}
		
		public void updateFileAttributes(FileEntry fe, List<iValueSetter> valueSetters, String updatingUser) {
			if (fe == null || valueSetters == null || valueSetters.isEmpty()) {
				return; // nothing to do
			}
			
			// apply the new attributes and set modification info
			for (iValueSetter vs: valueSetters) {
				vs.access(fe);
			} 
			fe.setModifiedOn(Time2.getMesaTime());
			fe.setModifiedBy(updatingUser);
			
			// add to changes to persist
			this.deltaFiles.put(fe.getFileID(), fe);
		}
		
		public void moveFile(long fileID, long newParentFileID, String movingUser) {
			FileEntry fe = this.volume.fileEntries.get(fileID);
			FileEntry newParentFe = this.volume.fileEntries.get(newParentFileID);
			
			if (fe == null) {
				this.volume.errorRaiser.fileNotFound(fileID, "File not found with fileID: " + fileID);
			} else if (newParentFe == null) {
				this.volume.errorRaiser.fileNotFound(newParentFileID, "File not found with newParentFileID: " + newParentFileID);
			} else {
				this.moveFile(fe, newParentFe, movingUser);
			}
		}
		
		public void moveFile(FileEntry fe, FileEntry newParentFe, String movingUser) {
			if (!newParentFe.isDirectory()) {
				this.volume.errorRaiser.notADirectory(newParentFe.getFileID(), "Move file aborted: Target file for move is not a directory");
			}
			if (fe.getFileID() == newParentFe.getFileID()) {
				this.volume.errorRaiser.wouldCreateLoopInHierarchy("Move file aborted: Moving file into itelf as parent");
			}
			
			// get the current parent
			long currParentFileID = fe.getParentID();
			if (currParentFileID == 0) {
				this.volume.errorRaiser.operationNotAllowed("Move file aborted: Root folder ('file drawers') cannot be moved");
			}
			FileEntry currParent = this.volume.fileEntries.get(currParentFileID);
			if (currParent == null) {
				this.volume.errorRaiser.fileNotFound(currParentFileID, "Move file aborted: File not found with currParentFileID: " + currParentFileID);
			}
			
			// check if moving would break the tree structure
			if (fe.isDirectory()) {
				long feFileID = fe.getFileID();
				long checkFileID = newParentFe.getFileID();
				while (checkFileID != 0) {
					if (checkFileID == feFileID) {
						this.volume.errorRaiser.wouldCreateLoopInHierarchy("Move file aborted: new parent is a child of file to move");
					}
				}
			}
			
			long now = Time2.getMesaTime();
			
			// remove file from current parent
			currParent.getChildren().getChildren().remove(fe);
			currParent.setModifiedOn(now);
			currParent.setModifiedBy(movingUser);
			this.deltaFiles.put(currParentFileID, currParent);
			
			// add file to new parent
			newParentFe.getChildren().add(fe);
			newParentFe.setModifiedOn(now);
			newParentFe.setModifiedBy(movingUser);
			this.deltaFiles.put(newParentFe.getFileID(), newParentFe);
			
			// change file itself
			fe.setParentID(newParentFe.getFileID());
			fe.setParent(newParentFe);
			fe.setModifiedOn(now);
			fe.setModifiedBy(movingUser);
			this.deltaFiles.put(fe.getFileID(), fe);

			this.volume.checkConsistency();
		}
		
		public void replaceFileContent(long fileID, iContentSource contentSource, String updatingUser) throws IOException {
			FileEntry fe = this.volume.fileEntries.get(fileID);
			if (fe != null) {
				this.replaceFileContent(fe, contentSource, updatingUser);
			} else {
				this.volume.errorRaiser.fileNotFound(fileID, "File not found with fileID: " + fileID);
			}
		}
		
		public void replaceFileContent(FileEntry fe, iContentSource contentSource, String updatingUser) throws IOException {
			if (fe == null || contentSource == null) {
				return; // nothing to do?
			}
			
			// get new content and set modification info
			this.readInContent(fe, contentSource);
			fe.setModifiedOn(Time2.getMesaTime());
			fe.setModifiedBy(updatingUser);
			
			// add to changes to persist
			this.deltaFiles.put(fe.getFileID(), fe);
		}
		
		private void readInContent(FileEntry fe, iContentSource contentSource) throws IOException {
			if (contentSource == null) {
				fe.setChecksum(FsConstants.unknownChecksum);
				fe.setDataSize(0);
				fe.setHasContent(false);
				return;
			}
			
			int checksum = 0;
			int dataSize = 0;
			File contentFile = this.volume.getDataFile(fe.getFileID(), true);
			try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(contentFile))) {
				byte[] buffer = new byte[512];
				int bytesTransferred;
				while((bytesTransferred = contentSource.read(buffer)) > 0) {
					bos.write(buffer, 0, bytesTransferred);
					dataSize += bytesTransferred;
					if (bytesTransferred < buffer.length) {
						buffer[bytesTransferred] = (byte)0;
					}
					for (int i = 0; i < bytesTransferred; i += 2) {
						int b1 = buffer[i] & 0xFF;
						int b2 = buffer[i+1] & 0xFF;
						int w = (b1 << 8) | b2;
						checksum = checksum(checksum, w);
					}
				}
			}
			fe.setChecksum(checksum);
			fe.setDataSize(dataSize);
			fe.setHasContent(true);
		}
		
		private static int checksum(int cksum, int data) {
			int temp = (cksum + (data & 0xFFFF)) & 0xFFFF;
			if (cksum > temp) { temp = temp + 1; }
			if (temp >= 0100000) {
				temp = (temp * 2) + 1;
			} else {
				temp = temp * 2;
			}
			return temp & 0xFFFF;
		}
		
//		public void replaceFileContentRange(long fileID, iContentSource contentSource, int from, int count) {
//			** content range access not supported so far **
//		}
		
		public void deleteFile(long fileID, String deleter) {
			FileEntry fe = this.volume.fileEntries.get(fileID);
			if (fe != null) {
				this.deleteFile(fe, deleter);
			} else {
				this.volume.errorRaiser.fileNotFound(fileID, "File not found with fileID: " + fileID);
			}
		}
		
		public void deleteFile(FileEntry fe, String deleter) {
			// if directory: remove children (recursively depth-first)
			if (fe.isDirectory()) {
				List<FileEntry> children = new ArrayList<>(fe.getChildren().getChildren());
				for (FileEntry child : children) {
					this.deleteFile(child, deleter);
				}
			}
			
			// remove this file from parent and tree
			long parentID = fe.getParentID();
			if (parentID != 0) {
				// 'fe' not a root folder, so it has a parent
				FileEntry pfe = this.volume.fileEntries.get(parentID);
				if (pfe != null) {
					pfe.getChildren().getChildren().remove(fe);
					pfe.setModifiedOn(Time2.getMesaTime());
					pfe.setModifiedBy(deleter);
					this.deltaFiles.put(parentID, pfe);
				}
			}
			
			// remove the file itself
			long fileID = fe.getFileID();
			this.volume.fileEntries.remove(fileID);
			this.deletedFileIds.add(fileID);
			this.deltaFiles.remove(fileID);
			File contentFile;
			try {
				contentFile = this.volume.getDataFile(fe.getFileID(), false);
				if (contentFile != null && contentFile.exists()) {
					contentFile.delete();
				}
			} catch (IOException e) {
				System.out.printf("Unable to remove content file for deleted fileID %d\n", fileID);
				// ignored
			}

			this.volume.checkConsistency();
		}

		// close session (AutoCloseable)
		@Override
		public void close() throws Exception {
			System.out.printf("Volume.Session -> (Auto)close (nesting = %d)\n", this.volume.currentSessionNesting);

			this.volume.checkConsistency();
			
			synchronized(this.volume) {
				try {
					try {
						// write metadata delta for accumulated modifications if top-level nesting
						if (this.volume.currentSessionNesting == 1 && !(this.deletedFileIds.isEmpty() && this.deltaFiles.isEmpty())) {
							this.volume.writeMetadataFile(this.deletedFileIds, this.deltaFiles.values());
						}
					} catch(Throwable e) {
						System.out.printf("## Volume.Session.close(): %s -- msg: %s\n", e.getClass().getName(), e.getMessage());
						e.printStackTrace(System.out);
						throw e;
					}
				} finally {
					// release lock on edit operations (even if writing delta fails!)
					this.volume.currentSessionNesting--;
					if (this.volume.currentSessionNesting <= 0) {
						this.volume .currentSession = null;
						this.volume.currentSessionThread = null;
						this.volume.notifyAll();
					}
				}
			}
		}
		
	}
	
	private void checkClosed() {
		if (this.closed) {
			this.errorRaiser.serviceUnavailable("Volume is closed, volume usage no longer possible");
		}
	}
	
	public Session startModificationSession() throws InterruptedException {
		// no changes on closed session
		this.checkClosed();
		
		// use lock for edit operations, possibly waiting for other Session(s) to be finished
		synchronized(this) {
			// check if sessions in the same thread are nested (completely inutile, but prevent locking self)
			if (this.currentSessionThread == Thread.currentThread()) {
				this.currentSessionNesting++;
				return this.currentSession;
			}
			
			// create new session
			while(this.currentSession != null) {
				this.wait();
			}
			this.currentSession = new Session(this);
			this.currentSessionThread = Thread.currentThread();
			this.currentSessionNesting = 1;
		}
		
		return this.currentSession;
	}
	
	public FileEntry openByFileID(long fileID, Long parentID, Long type, String readingUser) {
		this.checkClosed();
		
		FileEntry fe = this.fileEntries.get(fileID);
		if (fe == null) {
			this.errorRaiser.fileNotFound(fileID, "File not found with fileID: " + fileID);
		}
		
		if (parentID != null && fe.getParentID() != parentID) {
			this.errorRaiser.attributeValueError(12 /*ParentID*/, "Specified parent is not the parent of requested file");
		}
		if (type != null && fe.getType() != type) {
			this.errorRaiser.attributeValueError(17 /*type*/, "Specified type is not the type of requested file");
		}
		
		this.recordReadAccess(fe,  readingUser);
		
		return fe;
	}
	
	public FileEntry openByName(String name, Long parentID, Long type, Integer version, String readingUser) {
		this.checkClosed();
		
		final FileEntry parentFe;
		if (parentID == null || parentID == 0) {
			parentFe = null;
		} else {
			parentFe = this.fileEntries.get(parentID);
			if (parentFe == null) {
				this.errorRaiser.fileNotFound(parentID, "File not found with fileID: " + parentID);
			}
			if (!parentFe.isDirectory() || parentFe.getChildren() == null) {
				this.errorRaiser.notADirectory(parentID, "Specified parent file is not a directory");
			}
		}
		
		FileEntry fe = this.findByNameInDir(name, parentFe, type, version);
		this.recordReadAccess(fe,  readingUser);
		return fe;
	}
	
	private FileEntry findByNameInDir(String name, FileEntry parentFe, Long type, Integer version) {
		DirectoryChildren dirFiles = (parentFe != null) ? parentFe.getChildren() : this.rootFolders;
		boolean findExactVersion = (version != null && version > PathElement.LOWEST_VERSION && version < PathElement.HIGHEST_VERSION);
		boolean findLowestVersion = (version != null && version <= PathElement.LOWEST_VERSION);
		FileEntry fe = null;
		for (FileEntry f : dirFiles.getChildren()) {
			if (name.equalsIgnoreCase(f.getName())) {
				if (findExactVersion) {
					if (f.getVersion() == version) {
						fe = f;
						break;
					}
				} else if (fe != null) {
					if (findLowestVersion && f.getVersion() < fe.getVersion()) {
						fe = f;
					} else if (f.getVersion() > fe.getVersion()) {
						fe = f;
					}
				} else {
					fe = f;
				}
			}
		}
		
		if (fe == null) {
			this.errorRaiser.fileNotFound(0, "File not found with name: " + name);
		}
		if (type != null && fe.getType() != type) {
			this.errorRaiser.attributeValueError(17 /*type*/, "Specified type is not the type of requested file");
		}
		
		return fe;
	}
	
	public FileEntry openByPath(List<PathElement> path, Long baseFileID, Long type, Integer version, String readingUser) {
		this.checkClosed();
		
		if (path == null || path.isEmpty()) {
			this.errorRaiser.attributeValueError(21 /*pathname*/, "Specified path is empty");
		}
		
		FileEntry parentFe = null;
		if (baseFileID != null && baseFileID != 0) {
			parentFe = this.openByFileID(baseFileID, null, null, null);
			if (!parentFe.isDirectory() || parentFe.getChildren() == null) {
				this.errorRaiser.notADirectory(baseFileID, "Specified base file is not a directory");
			}
		}
		
		int fileElementIndex = path.size() - 1;
		for (int i = 0; i < fileElementIndex; i++) {
			PathElement pathElem = path.get(i);
			parentFe = this.findByNameInDir(pathElem.getName(), parentFe, null, pathElem.getVersion());
			if (!parentFe.isDirectory() || parentFe.getChildren() == null) {
				this.errorRaiser.notADirectory(parentFe.getFileID(), "Path element is not a directory");
			}
		}
		
		FileEntry fe = this.findByNameInDir(path.get(fileElementIndex).getName(), parentFe, type, version);
		this.recordReadAccess(fe,  readingUser);
		return fe;
	}
	
	public<T> void getAttributes(long fileID, T clientData,  List<iValueGetter<T>> valueGetters, String readingUser) {
		this.checkClosed();
		
		FileEntry fe = this.openByFileID(fileID, null, null, readingUser);
		for (iValueGetter<T> vg : valueGetters) {
			vg.access(clientData, fe);
		}
	}
	
	public boolean hasContent(long fileID) {
		FileEntry fe = this.fileEntries.get(fileID);
		if (fe == null) {
			this.errorRaiser.fileNotFound(fileID, "File not found with fileID: " + fileID);
		}
		return fe.getHasContent();
	}
	
	public void retrieveContent(long fileID, iContentSink contentSink, String readingUser) throws IOException {
		this.checkClosed();
		
		FileEntry fe = this.openByFileID(fileID, null, null, readingUser);
		
		if (!fe.getHasContent()) {
			contentSink.write(null,  0); // attempt to get content where there is none => signal EOF
			return;
		}
		
		File contentFile = this.getDataFile(fe.getFileID(), false);
		if (contentFile == null) {
			this.errorRaiser.fileContentDamaged("File content missing or lost");
		}
		try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(contentFile))) {
			byte[] buffer = new byte[512];
			int bytesTransferred;
			while((bytesTransferred = bis.read(buffer)) > 0) {
				if (contentSink.write(buffer, bytesTransferred) < bytesTransferred) {
					throw new IOException("BulkTransfer aborted by other end (NoMoreWriteSpaceException)");
				}
			}
		} finally {
			contentSink.write(null,  0); // signal EOF and cleanup
		}
	}
	
//	public void retrieveContentRange(long fileID, iContentSink contentSink, int from, int count, String readingUser) {
//		** content range access not supported so far ** 
//	}
	
	public List<FileEntry> listDirectory(long directoryFileID, iValueFilter predicate, int maxCount, String readingUser) {
		this.checkClosed();
		
		if (maxCount <= 0) { maxCount = 0x7FFF_FFFF; }
		
		FileEntry directoryFe = null;
		if (directoryFileID != 0) {
			directoryFe = this.openByFileID(directoryFileID, null, null, null);
			if (!directoryFe.isDirectory() || directoryFe.getChildren() == null) {
				this.errorRaiser.notADirectory(directoryFileID, "Specified directory file is not a directory");
			}
		}
		DirectoryChildren children = (directoryFe != null) ? directoryFe.getChildren() : this.rootFolders; // this should already be sorted by parents sort-spec
		
		List<FileEntry> hits = new ArrayList<>();
		for (FileEntry fe : children.getChildren()) {
			if (predicate.isElligible(fe)) {
				hits.add(fe);
				this.recordReadAccess(fe,  readingUser);
				if (hits.size() >= maxCount) {
					return hits;
				}
			}
		}
		
		return hits;
	}
	
	public List<FileEntry> findFiles(long baseFileID, iValueFilter predicate, int maxCount, int maxDepth, String readingUser) {
		this.checkClosed();

		if (maxCount <= 0) { maxCount = 0x7FFF_FFFF; }
		if (maxDepth <= 0) { maxDepth = 0x7FFF_FFFF; }
		
		FileEntry baseFe = null;
		if (baseFileID != 0) {
			baseFe = this.openByFileID(baseFileID, null, null, null);
			if (!baseFe.isDirectory() || baseFe.getChildren() == null) {
				this.errorRaiser.notADirectory(baseFileID, "Specified directory file is not a directory");
			}
		}
		DirectoryChildren children = (baseFe != null) ? baseFe.getChildren() : this.rootFolders;
		
		List<FileEntry> hits = new ArrayList<>();
		this.recursiveFindFiles(hits, 1, children, predicate, maxCount, maxDepth, readingUser);
		return hits;
	}
	
	private void recursiveFindFiles(List<FileEntry> hits, int currDepth, DirectoryChildren files, iValueFilter predicate, int maxCount, int maxDepth, String readingUser) {
		if (files == null) { return; }
		for (FileEntry fe : files.getChildren()) {
			if (predicate.isElligible(fe)) {
				hits.add(fe);
				this.recordReadAccess(fe,  readingUser);
			}
			if (fe.isDirectory() && currDepth < maxDepth) {
				this.recursiveFindFiles(hits, currDepth + 1, fe.getChildren(), predicate, maxCount, maxDepth, readingUser);
			}
			if (hits.size() >= maxCount) {
				return;
			}
		}
	}
	
	private synchronized void recordReadAccess(FileEntry fe, String readingUser) {
		if (readingUser == null) { return; }
		long now = Time2.getMesaTime();
		fe.setReadOn(now);;
		this.readTimes.put(fe.getFileID(), now);
		fe.setReadBy(readingUser);
		this.readUsers.put(fe.getFileID(), readingUser);
	}
	
	private void loadMetadataFile(File f, List<Long> deletedFiles) {
		try (BufferedReader br = new BufferedReader(new FileReader(f))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.isEmpty()) { continue; } // ignore empty lines
				String what = "?";
				try {
					if (line.startsWith("N:")) {
						what = "next fileID";
						if (line.length() > 2) {
							int next = Integer.parseInt(line.substring(2));
							if (next > this.nextFileID) {
								this.nextFileID = next;
							}
						}
					} else if (line.startsWith("R:")) {
						what = "read file infos";
						String idString = line.substring(2, 18);
						long id = Long.parseUnsignedLong(idString, 16);
						if (!this.readTimes.containsKey(id)) {
							String timeString = line.substring(19,35);
							String username = line.substring(36);
							long time = Long.parseUnsignedLong(timeString, 16);
							this.readTimes.put(id,  time);
							this.readUsers.put(id, username);	
						}
					} else if (line.startsWith("-")) {
						what = "deleted FileID";
						if (line.length() > 1) {
							long id = Long.parseUnsignedLong(line.substring(1), 16);
							deletedFiles.add(id);
						}
					} else {
						what = "file entry";
						FileEntry e = new FileEntry(line);
						if (!this.fileEntries.containsKey(e.getFileID()) && !deletedFiles.contains(e.getFileID())) {
							this.fileEntries.put(e.getFileID(), e);
						}
					}
				} catch (Exception e) {
					System.out.printf("loadMetadataFile, error while reading %s: %s\n", what, e.getMessage());
					// line ignored...
				}
			}
		} catch(Exception e) {
			System.out.printf("loadMetadataFile, error while reading file: %s\n", e.getMessage());
			// file ignored
		}
	}
	
	private synchronized long getUniqueTS() {
		long ts = System.currentTimeMillis();
		while(ts <= this.lastUsedTS) {
			try { Thread.sleep(1); } catch (InterruptedException e) { }
			ts = System.currentTimeMillis();
		}
		this.lastUsedTS = ts;
		return ts;
	}
	
	private void saveMetadataBaseline() throws IOException {
		this.writeMetadataFile(null, this.fileEntries.values());
	}
	
	private void writeMetadataFile(List<Long> deletedFileIds, Collection<FileEntry> files) throws IOException {
		long ts = this.getUniqueTS();
		String fnPattern = (deletedFileIds == null) ? METADATA_GEN_FULL_PATTERN : METADATA_GEN_DELTA_PATTERN; 
		String filename = String.format(fnPattern, ts);
		String tmpFilename = filename + ".tmp";
		
		File tmpFile = new File(this.metadataDir, tmpFilename);
		try (PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(tmpFile)))) {
			// next free file id
			ps.printf("N:%d\n", this.nextFileID);
			
			// if delta...
			if (deletedFileIds != null) {
				// ...write out all file read access timestamps and users
				for (Entry<Long, Long> r : this.readTimes.entrySet()) {
					ps.printf("R:%016X:%016X:%s\n", r.getKey(), r.getValue(), this.readUsers.get(r.getKey()));
				}
			
				// ...write out ids of files deleted since last delta resp. baseline
				for (long id : deletedFileIds) {
					ps.printf("-%016X\n", id);
				}
				
				// record that we have one more delta => write baseline on Columne.close()
				this.metadataDeltaCount++;
			}
			
			// metadata of new/updated (delta) resp. all (baseline) files in volume
			for (FileEntry file : files) {
				file.externalize(ps);
				ps.println();
			}
			
			// sort children lists for directories possibly modified and just saved
			for (FileEntry file : files) {
				if (file.isDirectory() && file.getChildren() != null) {
					file.getChildren().reorder();
				}
			}
		}
		
		// switch from temp to final filename
		File finalFile = new File(this.metadataDir, filename);
		tmpFile.renameTo(finalFile);
		
		// forget file read info (as persisted now, either as delta or as baseline)
		this.readTimes.clear();
		this.readUsers.clear();
	}
	
	private void checkRootFolders() {
		File rootDefs = new File(this.baseDir, ROOTFOLDERS_FILE);
		if (!rootDefs.exists() || !rootDefs.canRead()) {
			System.out.printf("No or not readable root folder definitions for volume %s found\n", this.volumeName);
			return;
		}
		
		try (BufferedReader br = new BufferedReader(new FileReader(rootDefs))) {
			// make sure the "Desktops" folder exists (created at volume initialization)
			if (this.rootFolders.getChildren().isEmpty()) {
				FileEntry fe = new FileEntry(this.nextFileID++, 0, true, "Desktops", 1, 1, "system:system:system");
				this.fileEntries.put(fe.getFileID(), fe);
				this.rootFolders.getChildren().add(fe);
				List<AccessEntry> accessList = fe.getAccessList();
				List<AccessEntry> defAccessList = fe.getDefaultAccessList();
				short desktopsAccess = FsConstants.readAccess | FsConstants.writeAccess | FsConstants.addAccess | FsConstants.removeAccess;
				accessList.add(new AccessEntry("*:*:*", desktopsAccess));
				defAccessList.add(new AccessEntry("*:*:*", desktopsAccess));
			}
			
			// process the root folder definitions, adding new folders defined there, but leaving existing folders untouched
			String line;
			while ((line = br.readLine()) != null) {
				if (line.isEmpty()) { continue; } // ignore empty lines
				
				// get the definition components
				String[] parts = line.split("\t");
				if (parts.length < 2) { continue; }
				
				String folderName = parts[0];
				String folderOwner = parts[1];
				if (folderName.isEmpty() || folderOwner.isEmpty()) { continue; }
				
				// check if the folder is present
				boolean folderExists = false;
				for (FileEntry e : this.rootFolders.getChildren()) {
					if (folderName.equalsIgnoreCase(e.getName())) {
						folderExists = true;
						break;
					}
				}
				if (folderExists) { continue; }
				
				// create missing root folder
				System.out.printf("checkRootFolders(): creating root folder '%s'\n", folderName);
				FileEntry fe = new FileEntry(this.nextFileID++, 0, true, folderName, 1, 4098, folderOwner); // "VPDrawer" -> the type listed by VP/GV at fileservice root level
				this.fileEntries.put(fe.getFileID(), fe);
				this.rootFolders.getChildren().add(fe);
				List<AccessEntry> accessList = fe.getAccessList();
				List<AccessEntry> defAccessList = fe.getDefaultAccessList();
				accessList.add(new AccessEntry(folderOwner, FsConstants.fullAccess));
				defAccessList.add(new AccessEntry(folderOwner, FsConstants.fullAccess));
				for (int i = 2; i < parts.length; i++) {
					String def = parts[i];
					if (def.length() < 7) { continue; }
					String key = def.substring(2);
					int access;
					if (def.startsWith("o!") || def.startsWith("O!")) {
						access = FsConstants.fullAccess;
					} else if (def.startsWith("r!") || def.startsWith("R!")) {
						access = FsConstants.readAccess;
					} else if (def.startsWith("w!") || def.startsWith("W!")) {
						access = FsConstants.readAccess | FsConstants.writeAccess | FsConstants.addAccess | FsConstants.removeAccess;
					} else {
						continue;
					}
					accessList.add(new AccessEntry(key, access));
					defAccessList.add(new AccessEntry(key, access));
				}
			}
		} catch(Exception e) {
			System.out.printf("checkRootFolders, error while reading root folders file: %s\n", e.getMessage());
		}
		this.rootFolders.reorder();
	}
	
	// ensure that there is the Owner-property 4351 expected by Star/ViewPoint/GlobalView on file drawers
	private void checkForOwnerProperty(FileEntry fe) {
		UninterpretedAttribute attr = fe.getUninterpretedAttribute(FsConstants.attrStarOwner);
		if (attr == null) {
			String owner = fe.getCreatedBy();
			if (owner == null) { return; }
			int len = owner.length();
			short[] words = new short[(len+1)/2];
			int w = 0;
			for (int i = 0; i < len; i++) {
				int c = owner.charAt(i) & 0xFF;
				if ((i & 1) == 1) {
					words[w] |= c;
					w++;
				} else {
					words[w] = (short)(c << 8);
				}
			}
			
			attr = new UninterpretedAttribute(FsConstants.attrStarOwner);
			attr.add(len);
			for (int i = 0; i < words.length; i++) {
				attr.add(words[i]);
			}
			fe.getUninterpretedAttributes().add(attr);
		}
	}
	
	private void checkParentRelation(FileEntry fe) {
		FileEntry parent = fe.getParent();
		if (parent != null) { return; } // already checked and in tree
		
		long parentId = fe.getParentID();
		if (parentId == FsConstants.rootFileID) {
			// this is a root folder
			if (!this.rootFolders.getChildren().contains(fe)) {
				this.rootFolders.getChildren().add(fe);
			}
			return;
			
		}
		
		parent = this.fileEntries.get(parentId);
		if (parent == null) {
			// what?
			System.out.printf("ERROR: parent-FileEntry not availabe for: %s\n", fe.toString());
			return;
		}
		this.checkParentRelation(parent);
		parent.getChildren().getChildren().add(fe);
		fe.setParent(parent);
	}
	
	private synchronized long getNextFileID() {
		return this.nextFileID++;
	}
	
	private File getDataFile(long fileID, boolean createIfNotExists) throws IOException {
		String filename = String.format(DATAFILE_PATTERN, fileID);
		
		int idx = 0;
		for (int i = 0; i < this.dataDirStarts.size(); i++) {
			long dirStart = this.dataDirStarts.get(i);
			if (fileID < dirStart) { break; }
			idx = i;
		}
		
		String dirName = String.format(DATADIR_PATTERN, this.dataDirStarts.get(idx));
		File dataDir = new File(this.baseDir, dirName);
		File f = new File(dataDir, filename);
		if (f.exists()) {
			return f;
		}
		if (!createIfNotExists) {
			return null; // signal non-existence
		}
		
		if (idx == (this.dataDirStarts.size() - 1) && filesInlastDataDir >= DATA_DIR_LIMIT) {
			this.dataDirStarts.add(fileID);
			filesInlastDataDir = 1;
			dirName = String.format(DATADIR_PATTERN, fileID);
			dataDir = new File(this.baseDir, dirName);
			dataDir.mkdir();
			f = new File(dataDir, filename);
		}
		f.createNewFile();
		
		return f;
	}
	
	private void checkConsistency() {
		// check the tree structure internal references
		List<FileEntry> treeFiles = new ArrayList<>();
		for (FileEntry f : this.rootFolders.getChildren()) {
			this.checkTreeFileEntry(f, treeFiles);
		}
		
		// check that the file-entry map is basically consistent
		// and if all known files are in the tree
		for (Entry<Long, FileEntry> e : this.fileEntries.entrySet()) {
			if (e.getKey() == null) {
				System.out.printf("check: file-entry-map has null key\n");
				continue;
			} else if (e.getValue() == null) {
				System.out.printf("check: file-entry-map has null entry\n");
				continue;
			} else if (e.getValue().getFileID() != e.getKey()) {
				System.out.printf("check: file-entry-map key = %06X has entry with fileID = %06X\n", e.getKey(), e.getValue().getFileID());
			}
			if (!treeFiles.contains(e.getValue())) {
				System.out.printf("check: file-entry-map fileID %06X not found in file tree\n", e.getValue().getFileID());
			}
		}
		
		// check if the tree has unknown files
		for (FileEntry fe : treeFiles) {
			if (!this.fileEntries.containsKey(fe.getFileID())) {
				System.out.printf("check: fileID %06X in file tree not found in file treefile-entry-map\n", fe.getFileID());
			}
		}
	}
	
	private void checkTreeFileEntry(FileEntry fe, List<FileEntry> treeFiles) {
		if (fe == null) {
			System.out.printf("check: null FileEntry found in the tree\n");
			return;
		}
		long fileID = fe.getFileID();
		
		if (treeFiles.contains(fe)) {
			System.out.printf("check: fileID %06X found more than once in the tree\n", fileID);
			return;
		}
		treeFiles.add(fe);
		
		long parentID = fe.getParentID();
		if (parentID == FsConstants.rootFileID) {
			if (fe.getParent() != null) {
				System.out.printf("check: fileID %06X has parentID = 0, but parent is not null\n", fileID);
			}
		} else if (fe.getParent() == null) {
			System.out.printf("check: fileID %06X has parentID = %06X, but parent is null\n", fileID, parentID);
		} else if (fe.getParent().getFileID() != parentID) {
			System.out.printf("check: fileID %06X has parentID = %06X, but parent.fileID is: %06X\n", fileID, parentID, fe.getParent().getFileID());
		}
		
		if (!fe.isDirectory()) {
			if (fe.getChildren() != null) {
				System.out.printf("check: fileID %06X has isDirectory == false, but has children-siubstructure (count: %d)\n", fileID, fe.getChildren().getChildren().size());
			}
			return;
		}
		
		DirectoryChildren children = fe.getChildren();
		if (children == null) {
			System.out.printf("check: fileID %06X has isDirectory == true, but has no children-substructure\n", fileID);
			return;
		}
		if (children.getDirectory() != fe) {
			System.out.printf("check: fileID %06X has isDirectory == true, but children-substructure has deviating directory-file (%s)\n", fileID, children.getDirectory() != null ? String.format("%06X", children.getDirectory().getFileID()) : "null");
		}
		for (FileEntry f : children.getChildren()) {
			this.checkTreeFileEntry(f, treeFiles);
		}
	}
	
	public void dumpHierarchy(PrintStream ps) {
		for (FileEntry fe : this.rootFolders.getChildren()) {
			this.dumpFileEntry(ps, "", fe);
		}
	}
	
	private void dumpFileEntry(PrintStream ps, String indent, FileEntry fe) {
		if (fe.isDirectory()) {
			ps.printf("%s[%06X] %s [dir,children# %d]\n", indent, fe.getFileID(), fe.getName(), fe.getChildren().getChildren().size());
			for (FileEntry f : fe.getChildren().getChildren()) {
				this.dumpFileEntry(ps, indent + "  ", f);
			}
		} else {
			ps.printf("%s[%06X] %s\n", indent, fe.getFileID(), fe.getName());
		}
	}

}
