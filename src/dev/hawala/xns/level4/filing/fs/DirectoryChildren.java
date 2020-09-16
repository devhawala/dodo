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

import java.util.ArrayList;
import java.util.List;

/**
 * List of children for a directory.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class DirectoryChildren {
	
	private final FileEntry directory;
	
	private final List<FileEntry> children = new ArrayList<>();
	
	public DirectoryChildren(FileEntry dir) {
		this.directory = dir;
	}

	public FileEntry getDirectory() {
		return directory;
	}

	public List<FileEntry> getChildren() {
		return children;
	}
	
	public FileEntry get(long fileID) {
		for (FileEntry e : this.children) {
			if (e.getFileID() == fileID) { return e; }
		}
		return null;
	}
	
	public FileEntry get(String name) {
		FileEntry fe = null;
		for (FileEntry e : this.children) {
			if (e.getName().equalsIgnoreCase(name)) {
				if (fe == null || fe.getVersion() < e.getVersion()) {
					fe = e;
				}
			}
		}
		return fe;
	}
	
	public void add(FileEntry newChild) {
		// TODO: extend for positioning the new child at the parents ordering position
		this.children.add(newChild);
	}
	
	public void reorder() {
		final boolean ascending
			= (this.directory != null && this.directory.getOrderingKey() == 9)
			? this.directory.isOrderingAscending()
			: true;
		
		this.children.sort( (l,r) -> {
			int res = l.getLcName().compareTo(r.getLcName());
			if (res == 0) { res = Integer.compare(l.getVersion(), r.getVersion()); }
			return ascending ? res : - res;
		});
		
		// setting the position of the files in their directory is purely transient, persisted values are
		// irrelevant as it is replaced each time the volume is loaded or the directory child list is changed
		long position = 0x00010001; // start behind 'lastPosition' (although it is highly improbable that a position in the list will ever be 65535
		for (FileEntry f : this.children) {
			f.setPosition(position++);
		}
	}

}