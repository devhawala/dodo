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

package dev.hawala.xns.level4.chs;

import dev.hawala.xns.level3.courier.BOOLEAN;
import dev.hawala.xns.level3.courier.CARDINAL;
import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.Time2.Time;

/**
 * Clearinghouse entry formats as defined in:
 * <p>XSIS 168404 Clearinghouse Entry Formats , April 1984
 * <br/>(with some additions from CHEntries0.cr/CHPIDs.mesa)
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018,2023)
 */
public class CHEntries0 extends CrProgram {

	@Override public int getProgramNumber() { return 0; }
	@Override public int getVersionNumber() { return 0; }

	/*
	 * 2.1 Generic properties
	 */
	
	/*
	 * 2.1.1 AddressList property
	 * 
	 *   addressList: Clearinghouse.Property = 4;
	 *   AddressListValue: TYPE = Clearinghouse.NetworkAddressList;
	 */
	public static final int addressList = 4;
	
	/*
	 * 2.1.2 MailBoxes property
	 * 
	 *   mailboxes: Clearinghouse.property = 31;
	 *   MailboxesValue: TYPE = RECORD[
	 *     time: Time.Time,
	 *     mailService: ARRAY OF Clearinghouse.Name];
	 *       -- according to CHEntries0.cr:
	 *       --  -> mailService is specified as array, but in reality appears to be a sequence
	 *       --  -> defined this way (as SEQUENCE OF Name) here...
	 */
	public static final int mailboxes = 31;
	public static class MailboxesValue extends RECORD {
		public final Time time = mkMember(Time::make);
		public final SEQUENCE<Name> mailService = mkSEQUENCE(Name::make);  
		
		private MailboxesValue() {}
		public static MailboxesValue make() { return new MailboxesValue(); }
	}
	
	/*
	 * 2.1.3 Authentication levels supported
	 * 
	 *   authenticationLevel: Clearinghouse.Property = 8;
	 *   AuthenticationLevelValue: TYPE = RECORO[
	 *     simpleSupported, strongSupported: BOOLEAN];
	 */
	public static final int authenticationLevel = 8;
	public static class AuthenticationLevelValue extends RECORD {
		public final BOOLEAN simpleSupported = mkBOOLEAN();
		public final BOOLEAN strongSupported = mkBOOLEAN();
		
		private AuthenticationLevelValue() {}
		public static AuthenticationLevelValue make() { return new AuthenticationLevelValue(); }
	}
	
	/*
	 * 2.2 Clearinghouse Service entry
	 * 
	 *   ClearinghouseService: {
	 *     (ciearinghouseService, Description),
	 *     (addressList, AddressListValue),
	 *     (mailboxes, MailboxesValue),
	 *     (authenticationLevel , AuthenticationLevelValue)}
	 *   clearinghouseService: Clearinghouse.Property = 10021;
	 */
	public static final int clearinghouseService = 10021;
	
	/*
	 * 2.3 File Service entry
	 * 
	 *   FileService: {
	 *     (fileService, Description),
	 *     (addressList, AddressListValue),
	 *     (authenticationLevel, AuthenticationLevelValue)}
	 *   fileService: Clearinghouse.Property = 10000;
	 */
	public static final int fileService = 10000;
	
	/*
	 * 2.4 Mail Service entry
	 * 
	 *   MailService: {
	 *     (maiIService, Description),
	 *     (addressList, AddressListValue),
	 *     (authenticationLevel, AuthenticationLevelValue)}
	 *   mailService: Clearinghouse.Property = 10004;
	 */
	public static final int mailService = 10004;
	
	/*
	 * 2.5 Print Service entry
	 * 
	 *   PrintService: {
	 *     (printService, Description),
	 *     (addressList, AddressListValue),
	 *     (authenticationLevel, AuthenticationLevelValue)}
	 *   printService: Clearinghouse.Property = 10001;
	 */
	public static final int printService = 10001;
	
	/*
	 * 2.6 User entry
	 * 
	 *   User: {
	 *     (user, Description),
	 *     (userData, UserDataValue),
	 *     (mailboxes, MailboxesValue)}
	 *   user: Clearinghouse.Property = 10003;
	 *   userData: Clearinghouse.Property = 20000;
	 *   UserDataValue: TYPE = RECORD[
	 *     lastNameIndex: CARDINAL,
	 *     fileService: Clearinghouse.Name];
	 */
	public static final int user = 10003;
	public static final int userData = 20000;
	public static class UserDataValue extends RECORD {
		public final CARDINAL lastNameIndex = mkCARDINAL();
		public final Name fileService = mkMember(Name::make);
		
		private UserDataValue() {}
		public static UserDataValue make() { return new UserDataValue(); }
	}
	
	/*
	 * 2.7 UserGroup entry
	 * 
	 *   UserGroup: {
	 *     (userGroup, Description),
	 *     (members, Group)}
	 *   userGroup: Clearinghouse,Property = 10022;
	 *   members: Clearinghouse.Property = 3;
	 */
	public static final int userGroup = 10022;
	public static final int members = 3;

	
	/*
	 * **** additional Clearinghouse.properties according to CHEntries0.cr
	 */
	
	/*
	 * OBJECTS DEFINED IN CHPIDS.MESA
	 * 
	 * generic properties
	 * 
	 *   authKeys: Clearinghouse.Property = 6;
	 *   
	 *     -- the list of all services. Presumably, Star uses this property to
	 *     -- determine who on the net exports a services Exec.
	 *   services: Clearinghouse.Property = 51;
	 */
	public static final int authKeys = 6; // what content? dangerous?
	public static final int services = 51;
	
	/*
	 * ***** additional primary properties:  all have associated Item == Description
	 * 
	 * (see also: https://stuff.mit.edu/afs/athena/astaff/reference/4.3network/xns/courierlib/CHEntries0.cr)
	 */
	
	/*
	 * Workstation: {
	 *     (workstation, Description),
	 *     (addressList, AddressListValue)}
	 *   workstation: Clearinghouse.Property = 10005;
	 */
	public static final int workstation = 10005;
	
	/*
	 * ExternalCommunicationsService: {
	 *     (workstation, Description),
	 *     (addressList, AddressListValue)}
	 * externalCommunicationsService: Clearinghouse.Property = 10006;
	 */
	public static final int externalCommunicationsService = 10006;
	
	/*
	 * RS232CPort: {
	 *     (rs232CPort, Description),
	 *     (rs232CData, RS232CData),
	 *     (addressList, AddressListValue)}
	 * 
	 * rs232CPort: Clearinghouse.Property = 10007;
	 */
	public static final int rs232CPort = 10007;
	public static final int rs232CData = 20001;
	
	/*
	 * IBM3270Host: {
	 *     (rs232CPort, Description),
	 *     (ibm3270HostData, IBM3270HostData),
	 *     (addressList, AddressListValue)}
	 * 
	 * ibm3270Host: Clearinghouse.Property = 10010;
	 */
	public static final int ibm3270Host = 10010;
	public static final int ibm3270HostData = 20002;
	
	
}
