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

import dev.hawala.xns.Log;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;

/**
 * Clearinghouse database (currently faked).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class ChsDatabase {
	
	private static boolean produceStrongKeyAsSpecified = true;

	/**
	 * Return the simple password for a user (16 bit hash value
	 * of the plain text password).
	 * <p>
	 * <b>ATTENTION</p>: currently faked, the object name (case insensitive)
	 * is the password!
	 * </p>
	 * 
	 * @param forName the full qualified name of the user
	 * @return the simple password of the user or {@code null}
	 * 		if the user does not have a simple password.
	 * @throws IllegalArgumentException if the user does not exist.
	 */
	public static Integer getSimplePassword(Name forName) {
		String objectName = forName.object.get();
		int objectNameHash = computePasswordHash(objectName);
		return Integer.valueOf(objectNameHash);
	}
	
	/**
	 * Return the strong password for a user or service.
	 * <p>
	 * <b>ATTENTION</p>: currently faked, the object name (case insensitive)
	 * is the password!
	 * </p>
	 * 
	 * @param forName the full qualified name of the user
	 * @return the strong password of the user or {@code null}
	 * 		if the user does not have a simple password.
	 * @throws IllegalArgumentException if the user does not exist.
	 */
	public static byte[] getStrongPassword(Name forName) {
		String objectName = forName.object.get();
		try {
			byte[] keyBytes = StrongAuthUtils.getStrongKey(objectName, produceStrongKeyAsSpecified);
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
	
	private static int computePasswordHash(String password) {
		String passwd = password.toLowerCase();
		int hash = 0;
		for (int i = 0; i < passwd.length(); i++) {
			char c = passwd.charAt(i);
			int cv = c;
			hash = ((hash << 16) + cv) % 65357;
		}
		return hash;
	}
	
}
