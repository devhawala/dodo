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

package dev.hawala.xns.level4.filing;

import dev.hawala.xns.level3.courier.RECORD;

/**
 * Definition of the Filing Courier program (PROGRAM 10 VERSION 5)
 * (transcribed from Filing5.cr).
 * <p>
 * Remark: the Filing protocol is mostly defined in {@code FilingCommon},
 * only the version-specific "Logon", "Find" and "List" procedures are defined
 * here to complete the protocol definition.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin 2019
 */
public class Filing5 extends FilingCommon {

	public static final int PROGRAM = 10;
	public static final int VERSION = 5;

	@Override
	public int getProgramNumber() { return PROGRAM; }

	@Override
	public int getVersionNumber() { return VERSION; }
	
	/*
	 * ************ REMOTE PROCEDURES ************
	 */
	
	/*
	 * -- Logging On (Filing version 5) --
	 */
	
	/*
	 * Logon: PROCEDURE [ service: Clearinghouse.Name, credentials: Credentials,
	 *                    verifier: Verifier ]
	 *   RETURNS [ session: Session ]
	 *   REPORTS [ AuthenticationError, ServiceError, SessionError, UndefinedError ]
	 *   = 0;
	 */
	public final PROC<Filing4or5LogonParams,LogonResults> Logon = mkPROC(
						"Logon",
						0,
						Filing4or5LogonParams::make,
						LogonResults::make,
						AuthenticationError, ServiceError, SessionError, UndefinedError
						);
	
	
	/*
	 * -- Locating and Listing Files in a Directory --
	 */
	
	/*
	 * Find: PROCEDURE [ directory: Handle, scope: ScopeSequence,
	 *                   controls: ControlSequence, session: Session ]
	 *   RETURNS [ file: Handle ]
	 *   REPORTS [ AccessError, AuthenticationError,
	 *             ControlTypeError, ControlValueError, HandleError,
	 *             ScopeTypeError, ScopeValueError,
	 *             SessionError, UndefinedError ]
	 *   = 17;
	 */
	public final PROC<FindParams,FileHandleRecord> Find = mkPROC(
						"Find",
						17,
						FindParams::make,
						FileHandleRecord::make,
						AccessError, AuthenticationError,
						ControlTypeError, ControlValueError, HandleError,
						ScopeTypeError, ScopeValueError,
						SessionError, UndefinedError
						);
	
	/*
	 * List: PROCEDURE [ directory: Handle, types: AttributeTypeSequence,
	 *                   scope: ScopeSequence, listing: BulkData.Sink,
	 *                   session: Session ]
	 *   REPORTS [ AccessError, AttributeTypeError,
	 *             AuthenticationError, ConnectionError,
	 *             HandleError,
	 *             ScopeTypeError, ScopeValueError,
	 *             SessionError, TransferError, UndefinedError ]
	 *   = 18;
	 */
	public final PROC<ListParams,RECORD> List = mkPROC(
						"List",
						18,
						ListParams::make,
						RECORD::empty,
						AccessError, AttributeTypeError,
						AuthenticationError, ConnectionError,
						HandleError,
						ScopeTypeError, ScopeValueError,
						SessionError, TransferError, UndefinedError
						);

}