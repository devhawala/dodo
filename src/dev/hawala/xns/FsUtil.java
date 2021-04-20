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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import dev.hawala.xns.level4.filing.fs.FileEntry;
import dev.hawala.xns.level4.filing.fs.FsConstants;
import dev.hawala.xns.level4.filing.fs.PathElement;
import dev.hawala.xns.level4.filing.fs.Volume;
import dev.hawala.xns.level4.filing.fs.Volume.Session;
import dev.hawala.xns.level4.filing.fs.iContentSink;
import dev.hawala.xns.level4.filing.fs.iContentSource;
import dev.hawala.xns.level4.filing.fs.iErrorRaiser;
import dev.hawala.xns.level4.filing.fs.iValueSetter;

/**
 * Command line utility for working with a volume of a Dodo file service:
 * <ul>
 * <li>list the content of a folder (terse or verbose, flat or recursive)</li>
 * <li>import files or directories from the OS into a folder</li>
 * <li>export files or folders from a folder to the OS</li>
 * </ul>
 * <p>
 * For using this utility, the volume may not be open by a runnning Dodo
 * server instance, as a volume can be opened only by one program at a time.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin 2020
 */
public class FsUtil {
	
	/*
	 * (technical) user who works working with the volume
	 */
	
	private static final String workingUser = "fsutil:dev:hawala";
	
	/*
	 * utilities for writing to stdout
	 */
	
	private static void log(String format, Object... args) {
		System.out.printf(format, args);
	}
	
	private static void sep() {
		System.out.println();
	}
	
	/*
	 * extension-based identification of file type: text or unspecified
	 */
	
	private static final String[] textTypes = {
		".mesa",
		".config",
		".df",
		".txt",
		".doc",
		".cm",
		".st",
		".sources",
		".changes",
		".initialchanges",
		".nsmail",
		".msg",
		".map",
		".pack",
		".bootmesa",
		".loadmap",
		".includes",
		".includedby",
		".stats",
		".xref"
	};
	
	private static Long getType(File f) {
		String filename = f.getName().toLowerCase();
		for (int i = 0; i < textTypes.length; i++) {
			if (filename.endsWith(textTypes[i])) {
				return FsConstants.tText;
			}
		}
		return FsConstants.tUnspecified;
	}
	
	/*
	 * file import
	 */
	
	private static long createVolumeFile(
							String indent,
							Session session,
							File srcFile,
							long parentID,
							Long type) throws IOException {
		log("%simporting file: %s\n", indent, srcFile.getName());
		try (FileInputStream fis = new FileInputStream(srcFile)) {
			iContentSource contentSource = (b) -> {
				int count;
				try {
					count = fis.read(b);
				} catch (IOException e) {
					return -1;
				}
				if (count < 0) {
					return 0;
				} else {
					return count;
				}
			};
			iValueSetter createTimeSetter = (f) -> f.setCreatedOn(getMesaTime(srcFile.lastModified()));
			FileEntry fe = session.createFile(parentID, false, srcFile.getName(), 1, type, workingUser, Arrays.asList(createTimeSetter), contentSource);
			return fe.getFileID();
		}
	}
	
	private static long createVolumeFolder(Session session, String name, long parentID) throws IOException {
		FileEntry fe = session.createFile(parentID, true, name, 1, FsConstants.tDirectory, workingUser, Collections.emptyList(), null);
		return fe.getFileID();
	}
	
	private static void importDirectory(String indent, Session session, long intoFolderID, File srcDir) throws IOException {
		File[] files = srcDir.listFiles();
		for (File f : files) {
			if (f.isDirectory()) {
				log("%screating folder for: %s\n", indent, f.getName());
				long newFolderID = createVolumeFolder(session, f.getName(), intoFolderID);
				importDirectory(indent + "  ", session, newFolderID, f);
			} else {
				createVolumeFile(indent, session, f, intoFolderID, getType(f));
			}
		}
	}
	
	private static void importItem(Session session, long intoFolderID, File src) throws IOException {
		if (src.isDirectory()) {
			importDirectory("", session, intoFolderID, src);
		} else {
			createVolumeFile("", session, src, intoFolderID, getType(src));
		}
	}
	
	/*
	 * file export
	 */
	
	private static void exportItem(String indent, Volume volume, FileEntry fe, File destDir) throws IOException {
		String effectiveIndent = (indent == null) ? "" : indent;
		
		String osFilename = fe.getName()
				.replace(File.separatorChar, '_')
				.replace(File.pathSeparatorChar, '_')
				.replace('/', '_')
				.replace('\\', '_')
				.replace(':', '_')
				.replace('*', '_')
				.replace('|', '_')
				.replace('"', '_')
				.replace('<', '_')
				.replace('>', '_')
				.replace('?', '_')
				.replace('&', '_')
				.replace('\u0000', '_')
				;
		
		if (fe.isDirectory()) {
			if (indent == null) {
				log("%sskipping folder (-r not specified): %s\n", effectiveIndent, fe.getName());
				return;
			}
			
			log("%sexporting directory: %s\n", effectiveIndent, fe.getName());
			String newIndent = effectiveIndent + "  ";
			
			File newDir = new File(destDir, osFilename);
			if (newDir.exists()) {
				if (!newDir.isDirectory()) {
					log("%s ## item already exists in filesystem but is a plain file (won't overwrite): %s\n", effectiveIndent, fe.getName());
					return;
				}
			} else {
				if (!newDir.mkdirs()) {
					log("%s ## error creating new directory: %s\n", effectiveIndent, newDir.getAbsolutePath());
					return;
				}
			}
			
			List<FileEntry> files = fe.getChildren().getChildren();
			for (FileEntry f : files) {
				exportItem(newIndent, volume, f, newDir);
			}
		} else {
			log("%sexporting file: %s\n", effectiveIndent, fe.getName());
			
			File file = new File(destDir, osFilename);
			if (file.exists()) {
				if (file.isDirectory()) {
					log("%s ## item already exists but is directory (can't overwrite): %s\n", effectiveIndent, fe.getName());
					return;
				}
			} else {
				file.createNewFile();
			}
			
			try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
				iContentSink sink = (b,c) -> {
					try {
						if (b != null) { bos.write(b, 0, c); }
					} catch (IOException e) {
						log("%s## error: failed to write to file '%s': %s\n", effectiveIndent, fe.getName(), e.getMessage());
						return 0;
					}
					return c;
				};
				volume.retrieveContent(fe.getFileID(), sink, workingUser);
			}
			
			file.setLastModified(getUnixTime(fe.getCreatedOn()));
		}
	}
	
	/*
	 * dump file info (verbose)
	 */
	
	private static void dumpFileInfo(String indent, FileEntry fe) {
		String effectiveIndent = (indent == null) ? "" : indent;
		log("%s%s\n"
				   +"%s    [%06d] %s: %-30s (version: %d) (created: %s) (type: %d)%s\n"
				   +"%s             byteSize: %-8d created/read/modified by: %s / %s / %s\n",
				   effectiveIndent,
				   fe.getPathname(),
				   effectiveIndent,
				   fe.getFileID(),
				   (fe.isTemporary() ? "temp" : fe.isDirectory() ? "dir." : "file"),
				   fe.getName(),
				   fe.getVersion(),
				   fmtMesaTime(fe.getCreatedOn()),
				   fe.getType(),
				   (!fe.isDirectory()
						   ? "(checksum: " + fe.getChecksum() + " , dataSize: " + fe.getDataSize() + " , storedSize: " + fe.getStoredSize() + ")"
						   : fe.isChildrenUniquelyNamed() ? " (children uniqely named: true)" : " (children uniqely named: false)"),
				   effectiveIndent,
				   fe.getDataSize(),
				   fe.getCreatedBy(), fe.getReadBy(), fe.getModifiedBy()
				);
		if (fe.isDirectory() && indent != null) {
			List<FileEntry> children = fe.getChildren().getChildren();
			dumpFileList(indent + "    ", children);
		}
	}
	
	private static void dumpFileList(String indent, List<FileEntry> fileList) {
		for (FileEntry fe : fileList) {
			dumpFileInfo(indent, fe);
		}
	}
	
	/*
	 * list file info (terse)
	 */
	
	private static void listFile(String indent, FileEntry fe) {
		String effectiveIndent = (indent == null) ? "" : indent;
		log("%s%s (%s:%6d) %8d %s;%d\n",
			effectiveIndent,
			fmtMesaTime(fe.getCreatedOn()),
			(fe.isTemporary() ? "t" : fe.isDirectory() ? "d" : "f"),
			fe.getType(),
			fe.getDataSize(),
			fe.getName(),
			fe.getVersion()
			);
		if (fe.isDirectory() && indent != null) {
			List<FileEntry> children = fe.getChildren().getChildren();
			listFileList(indent + "    ", children);
		}
	}
	
	private static void listFileList(String indent, List<FileEntry> fileList) {
		for (FileEntry fe : fileList) {
			listFile(indent, fe);
		}
	}
	
	/*
	 * command line entry
	 */
	
	private static final String[] validCommands = { "dump" , "ls" , "export" , "import" };

	public static void main(String[] args) throws InterruptedException, Exception {
		if (args.length < 3) {
			usage();
		}
		
		// get the mandatory command line parameters
		String volumePath = args[0];
		String pathInVolume = args[1];
		String cmd = args[2].toLowerCase();
		String command = null;
		for (String c : validCommands) {
			if (c.equals(cmd)) { command = cmd; }
		}
		if (command == null) {
			log("error: invalid command: %s\n", cmd);
			usage();
		}
		
		// check for additional parameters
		String indent = null; // non-recursive actions if null
		List<String> srcNames = new ArrayList<>();
		for (int i = 3; i < args.length; i++) {
			if ("-r".equalsIgnoreCase(args[i])) {
				indent = "";
			} else {
				srcNames.add(args[i]);
			}
		}
		
		// import needs items to copy into the volume
		if ("import".equals(command) && srcNames.isEmpty()) {
			log("error: no items specified for import\n");
			System.exit(-1);
		}
		
		// get the (pseudo-) volume name form the volume path 
		String volumeDirectory = volumePath.replace('\\', '/');
		int idx = volumeDirectory.lastIndexOf('/');
		String volumeName = (idx >= 0) ? volumeDirectory.substring(idx + 1) : volumeDirectory;
		if (volumeName.isEmpty()) {
			log("error: invalid volume spec.: '%s\n", volumePath);
			System.exit(-1);
		}
		
		// open the folder
		Volume vol = Volume.openVolume(volumeDirectory, new ErrorRaiser());
		List<PathElement> path = PathElement.parse(pathInVolume);
		FileEntry folder = vol.openByPath(path, null, null, null, workingUser);
		if (folder == null) {
			log("error: folder not found: %s\n", pathInVolume);
			vol.close();
			System.exit(-1);
		} else if (!folder.isDirectory() && !"export".equals(command)) {
			log("error: not a folder: %s\n", pathInVolume);
			System.exit(-1);
		}
		
		// dispatch and execute the command
		sep();
		switch(command) {
		
		case "dump": {
				log("files in folder: %s\n", folder.getPathname());
				sep();
				List<FileEntry> files = folder.getChildren().getChildren();
				dumpFileList(indent, files);
				break;
			}
		
		case "ls": {
			log("files in folder: %s\n", folder.getPathname());
			sep();
				List<FileEntry> files = folder.getChildren().getChildren();
				listFileList(indent, files);
				break;
			}
		
		case "export": {
				log("export items from: %s\n", folder.getPathname());
				exportItem("  ", vol, folder, new File("."));
				break;
			}
		
		case "import": {
			log("importing into folder: %s\n", folder.getPathname());
			try (Session session = vol.startModificationSession()) {
					for (String name : srcNames) {
						File f = new File(name);
						if (!f.exists() || !f.canRead()) {
							log(" ## error, file not found or readable: %s\n", name);
						} else {
							importItem(session, folder.getFileID(), f);
						}
					}
					break;
				}
			}
		
		default:
			log("error: invalid command: %s\n", command);
		}
		
		sep();
		vol.close();
		log("volume closed\n");
	}
	
	private static void usage() {
		log("Usage:\n");
		log("  fsutil <volume> <path-to-folder> dump [-r]\n");
		log("  fsutil <volume> <path-to-folder> ls [-r]\n");
		log("  fsutil <volume> <path-to-item>   export [-r]\n");
		log("  fsutil <volume> <path-to-folder> import <src1> [<src2> ...]\n");
		System.exit(1);
	}
	
	/*
	 * ErrorRaiser: abort if any error occurs
	 */
	
	private static class ErrorRaiser implements iErrorRaiser {

		@Override
		public void fileNotFound(long fileID, String msg) {
			throw new RuntimeException(msg);
		}

		@Override
		public void duplicateFilenameForChildrenUniquelyNamed(String msg) {
			throw new RuntimeException(msg);
		}

		@Override
		public void notADirectory(long fileID, String msg) {
			throw new RuntimeException(msg);
		}

		@Override
		public void wouldCreateLoopInHierarchy(String msg) {
			throw new RuntimeException(msg);
		}

		@Override
		public void operationNotAllowed(String msg) {
			throw new RuntimeException(msg);
		}

		@Override
		public void serviceUnavailable(String msg) {
			throw new RuntimeException(msg);
		}

		@Override
		public void attributeValueError(int attributeType, String msg) {
			throw new RuntimeException(msg);
		}

		@Override
		public void fileContentDamaged(String msg) {
			throw new RuntimeException(msg);
		}
		
	}

	/*
	 * XNS time <-> Unix time conversion
	 */

	private static final long earliestTime = 2114294400;
	
	private static long getMesaTime(long unixTimeMillis) {
		long unixTimeSecs = unixTimeMillis / 1000;
		long mesaSecs = (unixTimeSecs + (731 * 86400) + earliestTime) & 0x00000000FFFFFFFFL;
		return mesaSecs;
	}
	
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
	
	private static long getUnixTime(long mesaSecs) {
		long unixSecs = mesaSecs - (731 * 86400) - earliestTime;
		return unixSecs * 1000L;
	}
	
	private static String fmtMesaTime(long mesaSecs) {
		Date date = new Date(getUnixTime(mesaSecs));
		return sdf.format(date);
	}
}
