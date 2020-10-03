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

import dev.hawala.xns.level3.courier.CHOICE;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level4.common.AuthChsCommon;

/**
 * Definition of the Filing Courier program (PROGRAM 10 VERSION 6)
 * (transcribed from Filing6.cr).
 * <p>
 * Remark: the Filing protocol is mostly defined in {@code FilingCommon},
 * only the version-specific "Logon", "Find" and "List" procedures are defined
 * here to complete the protocol definition.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin 2019
 */
public class Filing6 extends FilingCommon {

	public static final int PROGRAM = 10;
	public static final int VERSION = 6;

	@Override
	public int getProgramNumber() { return PROGRAM; }

	@Override
	public int getVersionNumber() { return VERSION; }

	
	/*
	 * ************ TYPES AND CONSTANTS ************
	 */
	
	/*
	 * -- Handles and Authentication --
	 */
	
	/*
	 * PrimaryCredentials: TYPE = Authentication.Credentials;
	 * -- nullPrimaryCredentials: PrimaryCredentials = Authentication.nullCredentials;
	 */
	// PrimaryCredentials <~> Authentication.Credentials
	
	/*
	 * -- Secondary credentials --
	 */
	
	/*
	 * SecondaryItemType: TYPE = LONG CARDINAL;
	 * SecondaryType: TYPE = SEQUENCE 10 OF SecondaryItemType;
	 */
	// SecondaryItemType <~> LONG CARDINAL
	public static class SecondaryType extends SEQUENCE<LONG_CARDINAL> {
		private SecondaryType() { super(10, LONG_CARDINAL::make); }
		public SecondaryType make() { return new SecondaryType(); }
	}
	
	/*
	 * SecondaryItem: TYPE = RECORD [
	 *   type: SecondaryItemType,
	 *   value: SEQUENCE OF UNSPECIFIED ];
	 *   
	 * Secondary: TYPE = SEQUENCE 10 OF SecondaryItem;
	 */
	public static class SecondaryItem extends RECORD {
		public final LONG_CARDINAL type = mkLONG_CARDINAL();
		public final SEQUENCE<UNSPECIFIED> value = mkSEQUENCE(UNSPECIFIED::make);
		
		private SecondaryItem() {}
		public static SecondaryItem make() { return new SecondaryItem(); }
	}
	public static class Secondary extends SEQUENCE<SecondaryItem> {
		private Secondary() { super(10, SecondaryItem::make); }
		public static Secondary make() { return new Secondary(); }
	}
	
	/*
	 * systemPassword: SecondaryItemType = 1;
	 * SystemPassword : TYPE = STRING;			-- value is private --
	 * 
	 * userName: SecondaryItemType = 2;
	 * UserName: TYPE = STRING;			-- value is not private --
	 * 
	 * userPassword: SecondaryItemType = 3;
	 * UserPassword: TYPE = STRING;			-- value is private --
	 * 
	 * userPassword2: SecondaryItemType = 4;
	 * UserPassword2: TYPE = STRING;			-- value is private --
	 * 
	 * userServiceName: SecondaryItemType = 5;
	 * UserServiceName: TYPE = STRING;			-- value is not private --
	 * 
	 * userServicePassword: SecondaryItemType = 6;
	 * UserServicePassword: TYPE = STRING;		-- value is private --
	 * 
	 * userServicePassword2: SecondaryItemType = 7;
	 * UserServicePassword2: TYPE = STRING;		-- value is private --
	 * 
	 * accountName: SecondaryItemType = 8;
	 * AccountName: TYPE = STRING;			-- value is not private --
	 * 
	 * accountPassword: SecondaryItemType = 9;
	 * AccountPassword: TYPE = STRING;			-- value is private --
	 * 
	 * accountPassword2: SecondaryItemType = 10;
	 * AccountPassword2: TYPE = STRING;		-- value is private --
	 * 
	 * secondaryString: SecondaryItemType = 1000;
	 * SecondaryString: TYPE = STRING;			-- value is not private --
	 * 
	 * privateSecondaryString: SecondaryItemType = 1001;
	 * PrivateSecondaryString: TYPE = STRING;		-- value is not private --
	 */
	public static final int sitSystemPassword = 1;
	public static final int sitUserName = 2;
	public static final int sitUserPassword = 3;
	public static final int sitUserPassword2 = 4;
	public static final int sitUserServiceName = 5;
	public static final int sitUserServicePassword = 6;
	public static final int sitUserServicePassword2 = 7;
	public static final int sitAccountName = 8;
	public static final int sitAccountPassword = 9;
	public static final int sitAccountPassword2 = 10;
	public static final int sitSecondaryString = 1000;
	public static final int sitPrivateSecondaryString = 1001;
	
	/*
	 * EncryptedSecondary: TYPE = SEQUENCE OF Authentication.Block;
	 */
	public static class EncryptedSecondary extends SEQUENCE<AuthChsCommon.Block> {
		private EncryptedSecondary() { super(AuthChsCommon.Block::make); }
		public static EncryptedSecondary make() { return new EncryptedSecondary(); }
	}
	
	/*
	 * Strength: TYPE = { strengthNone(0), simple(1), strong(2) };
	 * SecondaryCredentials: TYPE = CHOICE Strength OF {
	 *   strengthNone => RECORD [],
	 *   simple => RECORD [value: Secondary],
	 *   strong => RECORD [value: EncryptedSecondary] };
	 */
	public enum Strength { strengthNone , simple , strong }
	public static final EnumMaker<Strength> mkStrength = buildEnum(Strength.class).get();
	public static class SecondaryCredentialsSimple extends RECORD {
		public final Secondary value = mkMember(Secondary::make);
		
		private SecondaryCredentialsSimple() {}
		public static SecondaryCredentialsSimple make() { return new SecondaryCredentialsSimple(); }
	}
	public static class SecondaryCredentialsStrong extends RECORD {
		public final EncryptedSecondary value = mkMember(EncryptedSecondary::make);
		
		private SecondaryCredentialsStrong() {}
		public static SecondaryCredentialsStrong make() { return new SecondaryCredentialsStrong(); }
	}
	public static final ChoiceMaker<Strength> mkSecondaryCredentials = buildChoice(mkStrength)
							.choice(Strength.strengthNone, RECORD::empty)
							.choice(Strength.simple, SecondaryCredentialsSimple::make)
							.choice(Strength.strong, SecondaryCredentialsStrong::make)
							.get();
	
	/*
	 * Credentials: TYPE = RECORD [
	 *   primary: PrimaryCredentials,
	 *   secondary: SecondaryCredentials ];
	 */
	public static class Credentials extends RECORD {
		public final AuthChsCommon.Credentials primary = mkRECORD(AuthChsCommon.Credentials::make);
		public final CHOICE<Strength> secondary = mkCHOICE(mkSecondaryCredentials);
		
		private Credentials() {}
		public static Credentials make() { return new Credentials(); }
	}
	
	
	/*
	 * ************ REMOTE PROCEDURES ************
	 */
	
	/*
	 * -- Logging On (Filing version 6) --
	 */
	
	/*
	 * Logon: PROCEDURE [ service: Clearinghouse.Name, credentials: Credentials,
	 *                    verifier: Verifier ]
	 *   RETURNS [ session: Session ]
	 *   REPORTS [ AuthenticationError, ServiceError, SessionError, UndefinedError ]
	 *   = 0;
	 */
	public static class Filing6LogonParams extends RECORD {
		public AuthChsCommon.Name service = mkRECORD(AuthChsCommon.Name::make);
		public Credentials credentials = mkRECORD(Credentials::make);
		public AuthChsCommon.Verifier verifier = mkMember(AuthChsCommon.Verifier::make);
		
		private Filing6LogonParams() {}
		public static Filing6LogonParams make() { return new Filing6LogonParams(); }
	}
	public final PROC<Filing6LogonParams, LogonResults> Logon = mkPROC(
						"Logon",
						0,
						Filing6LogonParams::make,
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