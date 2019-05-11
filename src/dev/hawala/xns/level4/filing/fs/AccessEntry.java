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

/**
 * Single entry in an access list of a file. 
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class AccessEntry {

	/** Clearinghouse 3-part-name for the access grantee (user or group) */
	public final String key;
	
	/** access granted (see FsConstants.**Access */
	public final short access;
	
	public AccessEntry(String key, short access) {
		this.key = key;
		this.access = (short)(access & FsConstants.fullAccess);
	}
	
	public AccessEntry(String key, int access) {
		this(key, (short)(access & 0xFFFF));
	}
	
	public AccessEntry(String externalized) {
		if (externalized == null || externalized.length() < 10 || externalized.charAt(4) != '~') {
			throw new IllegalArgumentException("Invalid externalized AccessEntry");
		}
		this.access = Short.parseShort(externalized.substring(0, 4), 16);
		this.key = externalized.substring(5);
	}
	
	public void externalize(PrintStream to) {
		to.printf("%04X~%s", this.access, this.key);
	}

	@Override
	public String toString() {
		return "[key='" + key + "', access=" + access + "]";
	}
	
}
