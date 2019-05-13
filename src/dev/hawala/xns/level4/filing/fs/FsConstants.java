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

/**
 * Constants used through-out the volume and client implementations.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public final class FsConstants {
	
	/*
	 * checksums
	 */
	public static final int unknownChecksum = 0xFFFF; // 177777B
	
	/*
	 * fileIDs
	 */
	public static final long noFileID = -1;
	public static final long rootFileID = 0x7FFF_FFFF_FFFF_FFFFL; // max. positive long
	
	/*
	 * subtree size limit
	 */
	public static final long nullSubtreeSizeLimit = 0x00000000FFFFFFFFL;
	
	/*
	 * access rights (AccessType & AccessSequence
	 */
	public static final short readAccess   = 0x0001; // all
	public static final short writeAccess  = 0x0002; // all
	public static final short ownerAccess  = 0x0004; // all
	public static final short addAccess    = 0x0010; // directories only
	public static final short removeAccess = 0x0020; // directories only
	public static final short fullAccess   = 0x7FFF;
	
	/*
	 * file types
	 */
	public static final long tUnspecified = 0;
	public static final long tDirectory = 1;
	public static final long tText = 2;
	public static final long tSerialized = 3;
	public static final long tEmpty = 4;
	public static final long tAscii = 6;
	
	/*
	 * special users
	 */
	public static final String noUser = "::";
	
	/*
	 * attribute interpretation
	 */
	public static final int intrNone = 0;
	public static final int intrBoolean = 1;
	public static final int intrCardinal = 2;
	public static final int intrLongCardinal = 3;
	public static final int intrTime = 4;
	public static final int intrInteger = 5;
	public static final int intrLongInteger = 6;
	public static final int intrString = 7;
	
	/*
	 * size
	 */
	public static final int pageSizeBytes = 512;
	
	/*
	 * Known uninterpreted attributes
	 */
	
	public static final long attrStarOwner = 4351;
}
