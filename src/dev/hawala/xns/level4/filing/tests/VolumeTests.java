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

package dev.hawala.xns.level4.filing.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.hawala.xns.level4.filing.fs.FileEntry;
import dev.hawala.xns.level4.filing.fs.Volume;
import dev.hawala.xns.level4.filing.fs.Volume.Session;
import dev.hawala.xns.level4.filing.fs.Volume.VolumeLockedException;
import dev.hawala.xns.level4.filing.fs.iContentSource;
import dev.hawala.xns.level4.filing.fs.iValueSetter;

public class VolumeTests {
	
	private static final String basedir = "testdata/testfs";
	private static final String volumename = "testvol";
	
	private static final String samplesdir = "testdata/samplecontent/";

	
	private static String prepareVolumeDir() throws IOException {
		String volumeDirectory = basedir + "/" + volumename;
		dropVolumeDir(volumeDirectory);
		
		File voldir = new File(volumeDirectory);
		voldir.mkdirs();
		
		File rootFolderDefs = new File(voldir, "root-folder.lst");
		try (PrintStream ps = new PrintStream(rootFolderDefs)) {
			ps.println("hans\thans:dev:hawala");
			ps.println("test\thans:dev:hawala\tw!*:dev:hawala");
		}
		
		return volumeDirectory;
	}
	
	private static void dropVolumeDir(String volumeDirectory) {
		File volDir = new File(volumeDirectory);
		if (!volDir.exists()) { return; }
		if (volDir.isDirectory()) {
			dropChildren(volDir);
		}
		volDir.delete();
	}
	
	private static void dropChildren(File dir) {
		File[] childFiles = dir.listFiles();
		for (File f : childFiles) {
			if (f.isDirectory()) {
				dropChildren(f);
			}
			f.delete();
		}
	}
	
	private static void log(String pattern, Object... args) {
		System.out.printf(pattern, args);
	}
	
	private static void sep() {
		System.out.println();
	}
	
	public static void main(String[] args) throws InterruptedException, Exception {
		String volumeDirectory = prepareVolumeDir();
		
		log("## Opening volume %s (located in: %s)\n", volumename, volumeDirectory);
		Volume vol = Volume.openVolume(volumeDirectory, new TestErrorRaiser());
		vol.dumpHierarchy(System.out);
		log("## done opening volume\n");
		sep();
		
		log("## listing from root\n");
		dumpFileList(vol.listDirectory(0, fe -> true, -1, "peter:dev:hawala"));
		sep();
		
		long folderHansFileID;
		log("## get fileID of folder 'hans'\n");
		{
			FileEntry fe = vol.openByName("HANS", 0L, null, null, "paule:dev:hawala");
			folderHansFileID = fe.getFileID();
		}
		sep();
		
		log("## changing folder 'hans' to children uniqely named = false\n");
		try (Session session = vol.startModificationSession()) {
			FileEntry fe = vol.openByFileID(folderHansFileID, null, null, "mary:dev:hawala");
			
			List<iValueSetter> valueSetters = new ArrayList<>();
			valueSetters.add( f -> f.setChildrenUniquelyNamed(false) );
			session.updateFileAttributes(fe, valueSetters, "mary:dev:hawala");
		}
		sep();
		
		log("## listing from root\n");
		dumpFileList(vol.listDirectory(0, fe -> true, -1, null));
		sep();
		
		log("## creating first file in folder 'hans'\n");
		try (Session session = vol.startModificationSession()) {
			long fileId = createVolumeFile(session, "Filing4.cr.txt", folderHansFileID, "Filing4.courier", null, 2L, "mats:dev:hawala");
			log ("  -> 'Filing4.cr.txt' -> 'Filing4.courier' => fileID = %d\n", fileId);
		}
		sep();
		
//		log("## volume.dumpHierarchy()\n");
//		vol.dumpHierarchy(System.out);
//		sep();
		
		log("## listing from root\n");
		dumpFileList(vol.findFiles(0, fe -> true, 0, 0, null));
		sep();
		
		log("########## closing and re-opening volume ######## \n");
		vol.close();
		vol = Volume.openVolume(volumeDirectory, new TestErrorRaiser());
		sep();
		
//		log("## volume.dumpHierarchy()\n");
//		vol.dumpHierarchy(System.out);
//		sep();
		
		log("## listing from root\n");
		dumpFileList(vol.findFiles(0, fe -> true, 0, 0, null));
		sep();
		
		log("## creating same file in additional versions in folder 'hans'\n");
		try (Session session = vol.startModificationSession()) {
			long fileIdA = createVolumeFile(session, "Filing4.cr.txt", folderHansFileID, "Filing4.courier", 5, 2L, "carl:dev:hawala");
			log ("  -> 'Filing4.cr.txt' -> 'Filing4.courier;?5?' => fileID = %d\n", fileIdA);

			long fileIdB = createVolumeFile(session, "Filing4.cr.txt", folderHansFileID, "Filing4.courier", null, 2L, "carl:dev:hawala");
			log ("  -> 'Filing4.cr.txt' -> 'Filing4.courier;?6?' => fileID = %d\n", fileIdB);
		}
		sep();
		
		log("## listing from root\n");
		dumpFileList(vol.findFiles(0, fe -> true, 0, 0, null));
		sep();
		
		log("## trying to open the volume while still open\n");
		try {
			Volume.openVolume(volumeDirectory, new TestErrorRaiser());
			log("ERROR ERROR ERROR :: did not get expected VolumeLockedException\n");
		} catch (VolumeLockedException vle) {
			log("  (OK) -> got expected VolumeLockedException\n");
		}
	}
	
	private static long createVolumeFile(
							Session session,
							String srcFilename,
							long parentID,
							String name,
							Integer version,
							Long type,
							String creatingUser) throws IOException {
		try (FileInputStream fis = new FileInputStream(samplesdir + srcFilename)) {
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
			FileEntry fe = session.createFile(parentID, false, name, version, type, creatingUser, Collections.emptyList(), contentSource);
			return fe.getFileID();
		}
	}
	
	private static void dumpFileList(List<FileEntry> fileList) {
		for (FileEntry fe : fileList) {
			log("%s\n"
			   +"    [%06d] %s: %-20s (version: %d) (type: %d)%s\n"
			   +"             created/read/modified by: %s / %s / %s\n",
			   fe.getPathname(),
			   fe.getFileID(),
			   (fe.isTemporary() ? "temp" : fe.isDirectory() ? "dir." : "file"),
			   fe.getName(),
			   fe.getVersion(),
			   fe.getType(),
			   (!fe.isDirectory()
					   ? "(checksum: " + fe.getChecksum() + " , dataSize: " + fe.getDataSize() + " , storedSize" + fe.getStoredSize() + ")"
					   : fe.isChildrenUniquelyNamed() ? " (children uniqely named: true)" : " (children uniqely named: false)"),
			   fe.getCreatedBy(), fe.getReadBy(), fe.getModifiedBy()
			);
		}
	}
}
