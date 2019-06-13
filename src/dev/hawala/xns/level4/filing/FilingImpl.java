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

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.hawala.xns.level3.courier.CHOICE;
import dev.hawala.xns.level3.courier.CourierRegistry;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.StreamOf;
import dev.hawala.xns.level3.courier.WireWriter;
import dev.hawala.xns.level3.courier.iWireData;
import dev.hawala.xns.level3.courier.iWireStream;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level3.courier.exception.CourierException;
import dev.hawala.xns.level4.common.AuthChsCommon.Credentials;
import dev.hawala.xns.level4.common.AuthChsCommon.CredentialsType;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.StrongVerifier;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.AuthChsCommon.Verifier;
import dev.hawala.xns.level4.common.BulkData1;
import dev.hawala.xns.level4.common.ChsDatabase;
import dev.hawala.xns.level4.common.StrongAuthUtils;
import dev.hawala.xns.level4.filing.Filing6.Filing6LogonParams;
import dev.hawala.xns.level4.filing.FilingCommon.AccessErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.AccessProblem;
import dev.hawala.xns.level4.filing.FilingCommon.ArgumentProblem;
import dev.hawala.xns.level4.filing.FilingCommon.Attribute;
import dev.hawala.xns.level4.filing.FilingCommon.AttributeSequence;
import dev.hawala.xns.level4.filing.FilingCommon.AttributeTypeErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.AttributeTypeSequence;
import dev.hawala.xns.level4.filing.FilingCommon.AttributeValueErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.ChangeAttributesParams;
import dev.hawala.xns.level4.filing.FilingCommon.ChangeControlsParams;
import dev.hawala.xns.level4.filing.FilingCommon.ConnectionErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.ConnectionProblem;
import dev.hawala.xns.level4.filing.FilingCommon.Content;
import dev.hawala.xns.level4.filing.FilingCommon.ContinueResults;
import dev.hawala.xns.level4.filing.FilingCommon.CopyParams;
import dev.hawala.xns.level4.filing.FilingCommon.CopyResults;
import dev.hawala.xns.level4.filing.FilingCommon.CreateParams;
import dev.hawala.xns.level4.filing.FilingCommon.DeserializeParams;
import dev.hawala.xns.level4.filing.FilingCommon.FileHandleAndSessionRecord;
import dev.hawala.xns.level4.filing.FilingCommon.FileHandleRecord;
import dev.hawala.xns.level4.filing.FilingCommon.Filing4or5LogonParams;
import dev.hawala.xns.level4.filing.FilingCommon.FilterRecord;
import dev.hawala.xns.level4.filing.FilingCommon.FindParams;
import dev.hawala.xns.level4.filing.FilingCommon.GetAttributesParams;
import dev.hawala.xns.level4.filing.FilingCommon.GetAttributesResults;
import dev.hawala.xns.level4.filing.FilingCommon.GetControlsParams;
import dev.hawala.xns.level4.filing.FilingCommon.GetControlsResults;
import dev.hawala.xns.level4.filing.FilingCommon.HandleErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.HandleProblem;
import dev.hawala.xns.level4.filing.FilingCommon.ListParams;
import dev.hawala.xns.level4.filing.FilingCommon.LogoffOrContinueParams;
import dev.hawala.xns.level4.filing.FilingCommon.LogonResults;
import dev.hawala.xns.level4.filing.FilingCommon.MoveParams;
import dev.hawala.xns.level4.filing.FilingCommon.OpenParams;
import dev.hawala.xns.level4.filing.FilingCommon.ReplaceBytesParams;
import dev.hawala.xns.level4.filing.FilingCommon.ReplaceParams;
import dev.hawala.xns.level4.filing.FilingCommon.RetrieveBytesParams;
import dev.hawala.xns.level4.filing.FilingCommon.RetrieveParams;
import dev.hawala.xns.level4.filing.FilingCommon.ScopeCountRecord;
import dev.hawala.xns.level4.filing.FilingCommon.ScopeDepthRecord;
import dev.hawala.xns.level4.filing.FilingCommon.ScopeDirection;
import dev.hawala.xns.level4.filing.FilingCommon.ScopeDirectionRecord;
import dev.hawala.xns.level4.filing.FilingCommon.ScopeType;
import dev.hawala.xns.level4.filing.FilingCommon.ScopeTypeErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.SerializeParams;
import dev.hawala.xns.level4.filing.FilingCommon.SerializedFile;
import dev.hawala.xns.level4.filing.FilingCommon.SerializedTree;
import dev.hawala.xns.level4.filing.FilingCommon.ServiceErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.ServiceProblem;
import dev.hawala.xns.level4.filing.FilingCommon.SessionErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.SessionProblem;
import dev.hawala.xns.level4.filing.FilingCommon.SpaceErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.SpaceProblem;
import dev.hawala.xns.level4.filing.FilingCommon.StoreParams;
import dev.hawala.xns.level4.filing.FilingCommon.TransferErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.TransferProblem;
import dev.hawala.xns.level4.filing.FilingCommon.UndefinedErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.iWireStreamForSerializedTree;
import dev.hawala.xns.level4.filing.fs.FileEntry;
import dev.hawala.xns.level4.filing.fs.FsConstants;
import dev.hawala.xns.level4.filing.fs.PathElement;
import dev.hawala.xns.level4.filing.fs.UninterpretedAttribute;
import dev.hawala.xns.level4.filing.fs.Volume;
import dev.hawala.xns.level4.filing.fs.iContentSink;
import dev.hawala.xns.level4.filing.fs.iContentSource;
import dev.hawala.xns.level4.filing.fs.iValueFilter;
import dev.hawala.xns.level4.filing.fs.iValueGetter;
import dev.hawala.xns.level4.filing.fs.iValueSetter;

/**
 * Implementation of the Filing Courier programs (PROGRAM 10 VERSIONs 4,5,6)
 * for a set of XNS File Services.
 * 
 * @author Dr. Hans-Walter Latz / Berlin 2019
 */
public class FilingImpl {
	
	private static boolean logParamsAndResults = false;

	private static long networkId = -1;
	private static long machineId = -1;
	private static ChsDatabase chsDatabase = null;
	
	private static final Map<String,Service> services = new HashMap<>();
	private static Service defaultService = null;
	
	private static final Map<Integer,Session> sessions = new HashMap<>();
	
	private static void log(String pattern, Object... args) {
		System.out.printf(pattern + "\n", args);
	}
	
	/*
	 * Management of XNS File Services to be provided to the network
	 */
	
	public static void init(long network, long machine, ChsDatabase chsDb) {
		networkId = network;
		machineId = machine;
		chsDatabase = chsDb;
	}
	
	public static boolean addVolume(ThreePartName serviceName, String volumeBasedirName) {
		if (chsDatabase == null || networkId < 0 || machineId < 0) {
			throw new IllegalStateException("Not initialized (chsDatabase == null || networkId < 0 || machineId < 0)");
		}
		synchronized(services) {
			String svcName = serviceName.getLcFqn();
			if (services.containsKey(svcName)) {
				log("Error: attempt to re-open service %s (volume directory: %s)", svcName, volumeBasedirName);
				return false;
			}
			log("Starting file service '%s' (volume directory: %s)", svcName, volumeBasedirName);
			try {
				Volume volume = Volume.openVolume(volumeBasedirName, new ErrorRaiser());
				Service svc = new Service(chsDatabase, machineId, serviceName, volume);
				services.put(svcName, svc);
				if (defaultService == null) {
					defaultService = svc;
				}
			} catch (Exception e) {
				log("Error: unable to open volume, cause: %s", e.getMessage());
				return false;
			}
			log("File service '%s' started", svcName);
			return true;
		}
	}
	
	public static void closeVolume(Name serviceName) {
		synchronized(services) {
			String svcName = serviceName.getLcFqn();
			if (!services.containsKey(svcName)) {
				log("Error: attempt to close not-opened service %s", svcName);
				return;
			}
			log("Stopping file service '%s'", svcName);
			Service svc = services.get(svcName);
			svc.shutdown();
			try {
				svc.getVolume().close();
			} catch (IOException e) {
				log("Error: problems closing volume, cause: %s", e.getMessage());
			}
			log("File service '%s' stopped", svcName);
		}
	}
	
	/*
	 * ************************* Courier registration/deregistration
	 */
	
	/**
	 * register Courier-Programs Filing4, Filing5 and Filing6 with
	 * this implementation to Courier dispatcher.
	 */
	public static void register() {
		if (networkId < 0 || machineId < 0 || chsDatabase == null) {
			throw new IllegalStateException("FilingImpl not correctly initialized (missing networkId, machineId or chsDatabase)");
		}
		if (services.isEmpty()) {
			throw new IllegalStateException("FilingImpl not correctly initialized (no volumes opened)");
		}
		
		Filing4 v4 = new Filing4().setLogParamsAndResults(false);
		Filing5 v5 = new Filing5().setLogParamsAndResults(false);
		Filing6 v6 = new Filing6().setLogParamsAndResults(false);
		
		v4.Logon.use(FilingImpl::logon4or5);
		v5.Logon.use(FilingImpl::logon4or5);
		v6.Logon.use(FilingImpl::logon6);
		
		v4.Continue.use(FilingImpl::continueSession);
		v5.Continue.use(FilingImpl::continueSession);
		v6.Continue.use(FilingImpl::continueSession);
		
		v4.Logoff.use(FilingImpl::logoff);
		v5.Logoff.use(FilingImpl::logoff);
		v6.Logoff.use(FilingImpl::logoff);
		
		v4.Open.use(FilingImpl::open);
		v5.Open.use(FilingImpl::open);
		v6.Open.use(FilingImpl::open);
		
		v4.Close.use(FilingImpl::close);
		v5.Close.use(FilingImpl::close);
		v6.Close.use(FilingImpl::close);
		
		v4.Create.use(FilingImpl::create);
		v5.Create.use(FilingImpl::create);
		v6.Create.use(FilingImpl::create);
		
		v4.Delete.use(FilingImpl::delete);
		v5.Delete.use(FilingImpl::delete);
		v6.Delete.use(FilingImpl::delete);
		
		v4.GetControls.use(FilingImpl::getControls);
		v5.GetControls.use(FilingImpl::getControls);
		v6.GetControls.use(FilingImpl::getControls);
		
		v4.ChangeControls.use(FilingImpl::changeControls);
		v5.ChangeControls.use(FilingImpl::changeControls);
		v6.ChangeControls.use(FilingImpl::changeControls);
		
		v4.GetAttributes.use(FilingImpl::getAttributes);
		v5.GetAttributes.use(FilingImpl::getAttributes);
		v6.GetAttributes.use(FilingImpl::getAttributes);
		
		v4.ChangeAttributes.use(FilingImpl::changeAttributes);
		v5.ChangeAttributes.use(FilingImpl::changeAttributes);
		v6.ChangeAttributes.use(FilingImpl::changeAttributes);
		
		v4.UnifyAccessLists.use(FilingImpl::unifyAccessLists);
		v5.UnifyAccessLists.use(FilingImpl::unifyAccessLists);
		v6.UnifyAccessLists.use(FilingImpl::unifyAccessLists);
		
		v4.Copy.use(FilingImpl::copy);
		v5.Copy.use(FilingImpl::copy);
		v6.Copy.use(FilingImpl::copy);
		
		v4.Move.use(FilingImpl::move);
		v5.Move.use(FilingImpl::move);
		v6.Move.use(FilingImpl::move);
		
		v4.Store.use(FilingImpl::store);
		v5.Store.use(FilingImpl::store);
		v6.Store.use(FilingImpl::store);
		
		v4.Retrieve.use(FilingImpl::retrieve);
		v5.Retrieve.use(FilingImpl::retrieve);
		v6.Retrieve.use(FilingImpl::retrieve);
		
		v4.Replace.use(FilingImpl::replace);
		v5.Replace.use(FilingImpl::replace);
		v6.Replace.use(FilingImpl::replace);
		
		v4.Serialize.use(FilingImpl::serialize);
		v5.Serialize.use(FilingImpl::serialize);
		v6.Serialize.use(FilingImpl::serialize);
		
		v4.Deserialize.use(FilingImpl::deserialize);
		v5.Deserialize.use(FilingImpl::deserialize);
		v6.Deserialize.use(FilingImpl::deserialize);
		
		v4.RetrieveBytes.use(FilingImpl::retrieveBytes);
		v5.RetrieveBytes.use(FilingImpl::retrieveBytes);
		v6.RetrieveBytes.use(FilingImpl::retrieveBytes);
		
		v4.ReplaceBytes.use(FilingImpl::replaceBytes);
		v5.ReplaceBytes.use(FilingImpl::replaceBytes);
		v6.ReplaceBytes.use(FilingImpl::replaceBytes);
		
		v4.Find.use(FilingImpl::find);
		v5.Find.use(FilingImpl::find);
		v6.Find.use(FilingImpl::find);
		
		v4.List.use(FilingImpl::list);
		v5.List.use(FilingImpl::list);
		v6.List.use(FilingImpl::list);
		
		CourierRegistry.register(v4);
		CourierRegistry.register(v5);
		CourierRegistry.register(v6);
	}
	
	/**
	 * unregister Filing4, Filing5 and Filing6 implementations
	 * from Courier dispatcher
	 */
	public static void unregister() {
		CourierRegistry.unregister(Filing4.PROGRAM, Filing4.VERSION);
		CourierRegistry.unregister(Filing5.PROGRAM, Filing5.VERSION);
		CourierRegistry.unregister(Filing6.PROGRAM, Filing6.VERSION);
	}
	
	/*
	 * ************************* sessions
	 */
	
	private static void checkVerifier(Verifier verifier) {
//		currently not checked, must possibly implemented if we don't trust our clients...
	}
	
	private static synchronized void addSession(Session session) {
		sessions.put(session.getSessionId(), session);
	}
	
	private static synchronized void dropSession(Session session) {
		sessions.remove(session.getSessionId());
	}
	
	private static Session resolveSession(FilingCommon.Session filingSession) {
		return resolveSession(filingSession, true);
	}
	
	private static synchronized Session resolveSession(FilingCommon.Session filingSession, boolean dropClosed) {
		int sessionId = filingSession.token.get();
		checkVerifier(filingSession.verifier);
		Session session = sessions.get(sessionId);
		if (session == null) {
			new SessionErrorRecord(SessionProblem.tokenInvalid).raise();
		}
		if (session.isClosed() && dropClosed) {
			sessions.remove(sessionId);
			new SessionErrorRecord(SessionProblem.tokenInvalid).raise();
		}
		return session;
	}
	
	/*
	 * ************************* implementation of service procedures
	 */
	
	/*
	 * Filing4 / Filing5 :
	 * Logon: PROCEDURE [ service: Clearinghouse.Name, credentials: Clearinghouse.Credentials,
	 *                    verifier: Clearinghouse.Verifier ]
	 *   RETURNS [ session: Session ]
	 *   REPORTS [ AuthenticationError, ServiceError, SessionError, UndefinedError ]
	 *   = 0;
	 * 
	 * Filing6 :
	 * 
	 * Logon: PROCEDURE [ service: Clearinghouse.Name, credentials: Filing6.Credentials,
	 *                    verifier: Clearinghouse.Verifier ]
	 *   RETURNS [ session: Session ]
	 *   REPORTS [ AuthenticationError, ServiceError, SessionError, UndefinedError ]
	 *   = 0;
	 *   
	 */
	
	private static void logon4or5(Filing4or5LogonParams params, LogonResults results) {
		logParams("Logon[4,5]", params);
		
		innerLogon(params.service, params.credentials, params.verifier, params.remoteHostId.get(), results);
	}
	
	private static void logon6(Filing6LogonParams params, LogonResults results) {
		logParams("Logon6", params);
		
		innerLogon(params.service, params.credentials.primary, params.verifier, params.remoteHostId.get(), results);
	}
	
	private static void innerLogon(ThreePartName service, Credentials credentials, Verifier verifier, Long remoteHostId, LogonResults results) {
		String svcName = service.getLcFqn();
		final Service svc;
		if (svcName.isEmpty() || "::".equals(svcName)) {
			svc = defaultService;
		} else {
			svc = services.get(svcName);
		}
		if (svc == null) {
			new ServiceErrorRecord(ServiceProblem.serviceUnavailable).raise();
		}
		
		/*
		 * XDE uses simple credentials, GlobalView uses strong credentials, both authentication methods
		 * are supported by Dodo since the implementation of CHS and Auth services.
		 * 
		 * The problem is here that FilingX.Logon returns a verifier along with the token (session-id proper),
		 * where the "strongness" of the returned verifier must match the credentials. But the Filing protocol
		 * specification is a bit weak on the content of the verifier, referring to the Authentication Protocol
		 * (which defines how to build a new verifier in general) and telling that "the verifier *may* change
		 * with each call"...
		 * First experiments made clear that any simple verifier returned by Logon is simply ignored (both arbitrary
		 * length and content were accepted) but any newly created but plausible strong verifier (using current time
		 * and ticks and correct with respect to the Authentication protocol) is rejected by GlobalView (with
		 * an "Authentication[other]" message being displayed)
		 * 
		 * Furthermore, GlobalView does honor neither the file service defined as "accepts simple credentials only" in CHS
		 * (by always sending strong instead of simple credentials) nor an AuthenticationError[inappropriatePrimaryCredentials]
		 * raised by the file service (by re-authenticating with simple credentials).
		 * 
		 * Further (heavy) testing resulted in the following properties for the strong verifier to be returned:
		 * - it must be the verifier sent by GlobalView with ticks incremented by 1
		 * - probably: if ticks overflows, it must be reset to 0 (?) and timestamp incremented (?)
		 * - although the Authentication Protocol states that the verifier is to be XORed with the recipients machine-id
		 *   (this would be the id of the GlobalView machine, as this one receives the returned verifier!), the verifier
		 *   returned by Logon must be XORed with the file server machine-id (although it is the sender of the verifier!).
		 *   
		 * So: the Filing protocol specification looks good, but is heavily incomplete and misleading!
		 */
		
		StrongVerifier decodedVerifier = StrongVerifier.make();
		Session session = svc.createSession(credentials, verifier, remoteHostId, decodedVerifier);
		addSession(session);
		
		results.session.token.set(session.getSessionId());
		if (credentials.type.get() == CredentialsType.simple) {
			// return the initiators verifier 
			results.session.verifier.add().set(verifier.get(0).get());
		} else {
			// create a strong verifier based on the received verifier
			int[] conversationKey = session.getConversationKey();
			if (conversationKey != null && conversationKey.length == 4) {
				// xor-ing values
				long xorHostId = machineId; // the server machine, not(!) the remoteHostId extracted from the Logon request
				long rcptTimestampMachineId32Bits = (xorHostId >> 16) & 0xFFFFFFFFL; // left justified machine-id => upper 32 bits 
				long rcptTicksMachineId32Bits = (xorHostId & 0x0000FFFFL) << 16;     // left justified machine-id => lower 32 bits
				
				// new verifier values
				long newTicks = decodedVerifier.ticks.get() + 1;
				long newTimestamp = decodedVerifier.timeStamp.get();
				if (newTicks > 0xFFFFFFFFL) {
					newTicks = 0;
					newTimestamp++;
				}
				
				// plain (unencrypted) verifier with xor-ed values
				StrongVerifier verfr = StrongVerifier.make();
				verfr.ticks.set(newTicks ^ rcptTicksMachineId32Bits);
				verfr.timeStamp.set(newTimestamp ^ rcptTimestampMachineId32Bits);
				
				// encrypt verifier and transfer into results
				try {
					WireWriter writer = new WireWriter();
					verfr.serialize(writer);
					int[] sourceBytes = writer.getWords();
					int[] encrypted = StrongAuthUtils.xnsDesEncrypt(conversationKey, sourceBytes);
					for (int i = 0; i < encrypted.length; i++) {
						results.session.verifier.add().set(encrypted[i]);
					}
				} catch (Exception e) {
					// log and set no verifier => let the invoker decide if acceptable
					log("** !! unable to serialize or encrypt the verifier in logon results: " + e.getMessage());
				}
			}
		}
		
		logResult("Logon", results);
	}
	
	/*
	 * Continue: PROCEDURE [ session: Session ]
	 *   RETURNS [ continuance: CARDINAL ]
	 *   REPORTS [ AuthenticationError, SessionError, UndefinedError ]
	 *   = 19;
	 */
	private static void continueSession(LogoffOrContinueParams params, ContinueResults results) {
		logParams("Continue", params);
		
		Session session = resolveSession(params.session);
		results.continuance.set(session.continueUse());
		
		logResult("Continue", results);
	}
	
	/*
	 * Logoff: PROCEDURE [ session: Session ]
	 *   REPORTS [ AuthenticationError, ServiceError, SessionError, UndefinedError ]
	 *   = 1;
	 */
	private static void logoff(LogoffOrContinueParams params, RECORD results) {
		logParams("Logoff", params);
		
		Session session = resolveSession(params.session, false);
		if (session != null) {
			session.close();
			dropSession(session);
		} else {
			new SessionErrorRecord(SessionProblem.tokenInvalid).raise();
		}
	}
	
	/*
	 * Open: PROCEDURE [ attributes: AttributeSequence, directory: Handle,
	 *                   controls: ControlSequence, session: Session ]
	 *   RETURNS [ file: Handle ]
	 *   REPORTS [ AccessError, AttributeTypeError, AttributeValueError,
	 *             AuthenticationError, ControlTypeError, ControlValueError,
	 *             HandleError, SessionError, UndefinedError ]
	 *   = 2;
	 */
	private static void open(OpenParams params, FileHandleRecord results) {
		logParams("open", params);
		
		// check session
		Session session = resolveSession(params.session);
		Volume vol = session.getService().getVolume();
		session.continueUse();
		
		// check specified parent directory handle
		Handle directoryHandle = Handle.get(params.directory);
		
		// attributes specifying the file to open
		// - exactly one of: fileID, name, pathname
		// - optional: parentID, type, version
		int primaryAttrCount = 0;
		Long fileId = null;
		String name = null;
		String pathname = null;
		Long parentId = null;
		Long type = null;
		Integer version = null;
		AttributeSequence attrs = params.attributes;
		for (int i = 0; i < attrs.value.size(); i++) {
			Attribute av = attrs.value.get(i);
			switch((int)(av.type.get() & 0xFFFF)) {
			case FilingCommon.atFileID:
				if (primaryAttrCount == 1) {
					new AttributeValueErrorRecord(ArgumentProblem.unreasonable, FilingCommon.atFileID).raise();
				}
				primaryAttrCount = 1;
				fileId = av.getAsFileID();
				break;
			case FilingCommon.atName:
				if (primaryAttrCount == 1) {
					new AttributeValueErrorRecord(ArgumentProblem.unreasonable, FilingCommon.atName).raise();
				}
				primaryAttrCount = 1;
				name = av.getAsString();
				break;
			case FilingCommon.atPathname:
				if (primaryAttrCount == 1) {
					new AttributeValueErrorRecord(ArgumentProblem.unreasonable, FilingCommon.atPathname).raise();
				}
				primaryAttrCount = 1;
				pathname = av.getAsString();
				break;
			case FilingCommon.atParentID:
				parentId = av.getAsFileID();
				break;
			case FilingCommon.atType:
				type = av.getAsLongCardinal();
				break;
			case FilingCommon.atVersion:
				version = av.getAsCardinal();
				break;
			default:
				// ignored -> ?? signal error ("Only the following interpreted attributes may be included ...")
			}
		}
		
		// check the file specs
		if (primaryAttrCount == 0) {
			if ((directoryHandle == null || directoryHandle.isNullHandle())
					&& (parentId == null || parentId.longValue() == 0L)) {
				Handle rootHandle = new Handle(session, vol.rootDirectory);
				rootHandle.setIdTo(results.file);
				logResult("Open", results);
				return;
			}
			new AttributeValueErrorRecord(ArgumentProblem.missing, FilingCommon.atFileID).raise();
		}
		if (directoryHandle != null && parentId != null) {
			if (directoryHandle.isVolumeRoot()) {
				if (parentId != 0) {
					new AttributeValueErrorRecord(ArgumentProblem.unreasonable, FilingCommon.atParentID).raise();
				}
			} else {
				if (directoryHandle.getFe().getFileID() != parentId) {
					new AttributeValueErrorRecord(ArgumentProblem.unreasonable, FilingCommon.atParentID).raise();
				}
			}
		} else if (directoryHandle != null && !directoryHandle.isVolumeRoot()) {
			parentId = directoryHandle.getFe().getFileID();
		}
		FileEntry fe = null;
		if (fileId != null) {
			if (fileId == FsConstants.rootFileID) {
				fe = vol.rootDirectory;
			} else {
				fe = vol.openByFileID(fileId, parentId, type, session.getUsername());
			}
		} else if (name != null) {
			fe = vol.openByName(name, parentId, type, version, session.getUsername());
		} else if (pathname != null) {
			List<PathElement> path = PathElement.parse(pathname);
			fe = vol.openByPath(path, parentId, type, version, session.getUsername());
		}
		
		// check the outcome
		if (fe != null) {
			Handle newHandle = new Handle(session, fe);
			newHandle.setIdTo(results.file);
		} else {
			new AccessErrorRecord(AccessProblem.fileNotFound).raise();
		}
		logResult("Open", results);
	}
	
	/*
	 * Close: PROCEDURE [ file: Handle, session: Session ]
	 *   REPORTS [ AuthenticationError, HandleError, SessionError, UndefinedError ]
	 *   = 3;
	 */
	private static void close(FileHandleAndSessionRecord params, RECORD results) {
		logParams("close", params);
		
		// check session
		resolveSession(params.session).continueUse();
		
		// check specified file handle
		Handle handle = Handle.get(params.handle);
		if (handle == null) { return; } // silently ignore closing the nullHandle ...
		
		// close and done
		handle.close();
	}
	
	/*
	 * Create: PROCEDURE [ directory: Handle, attributes: AttributeSequence,
	 *                     controls: ControlSequence, session: Session ]
	 *   RETURNS [ file: Handle ]
	 *   REPORTS [ AccessError, AttributeTypeError, AttributeValueError,
	 *             AuthenticationError, ControlTypeError, ControlValueError,
	 *             HandleError, InsertionError, SessionError, SpaceError,
	 *             UndefinedError ]
	 *   = 4;
	 */
	private static void create(CreateParams params, FileHandleRecord results) {
		logParams("store", params);
		
		// check session
		Session session = resolveSession(params.session);
		session.continueUse();
		
		// check specified directory file handle
		Handle dirHandle = Handle.get(params.directory);
		if (dirHandle.isVolumeRoot()) { // the file system root cannot be modified
			new AccessErrorRecord(AccessProblem.accessRightsInsufficient).raise();
		}
		if (dirHandle.isNullHandle()) {
			new HandleErrorRecord(HandleProblem.directoryRequired).raise();
		}
		
		// TODO: check (access-)controls on directory
		
		// let the file be created
		FileEntry fe = createFile(null, session, dirHandle.getFe().getFileID(), params.attributes, null, null);
		
		// prepare results
		Handle fileHandle = new Handle(session, fe);
		fileHandle.setIdTo(results.file);
		logResult("store", results);
	}
	
	/*
	 * Delete: PROCEDURE [ file: Handle, session: Session ]
	 *   REPORTS [ AccessError, AuthenticationError, HandleError, SessionError, UndefinedError ]
	 *   = 5;
	 */
	private static void delete(FileHandleAndSessionRecord params, RECORD results) {
		logParams("delete", params);
		
		// check session
		Session session = resolveSession(params.session);
		session.continueUse();
		
		// check specified file handle
		Handle fileHandle = Handle.get(params.handle);
		if (fileHandle.isVolumeRoot()) { // the file system root cannot be deleted
			new AccessErrorRecord(AccessProblem.accessRightsInsufficient).raise();
		}
		if (fileHandle.isNullHandle()) {
			new HandleErrorRecord(HandleProblem.nullDisallowed).raise();
		}
		FileEntry fe = fileHandle.getFe();
		if (fe.getParentID() == FsConstants.rootFileID) { // file system root folders cannot be deleted
			new AccessErrorRecord(AccessProblem.accessRightsInsufficient).raise();
		}
		
		// TODO: check (access-)controls on directory
		
		// so delete the file (including the subtree if this is a directory!)
		try (Volume.Session modSession = session.getService().getVolume().startModificationSession()) {
			modSession.deleteFile(fe, session.getUsername());
		} catch (InterruptedException e) {
			System.out.printf("!!! InterruptedException !!!\n");
			new UndefinedErrorRecord(FilingCommon.UNDEFINEDERROR_CANNOT_MODIFY_VOLUME).raise();
		} catch (CourierException ce) { // this is a RuntimeException, why is it catch-ed by Exception ???????
			System.out.printf("!!! CourierException !!!\n");
			throw ce;
		} catch (Exception e) {
			System.out.printf("!!! Exception: %s -- %s !!!\n", e.getClass().getName(), e.getMessage());
			new SpaceErrorRecord(SpaceProblem.mediumFull).raise();
		}
	}
	
	/*
	 * GetControls: PROCEDURE [ file: Handle, types: ControlTypeSequence,
	 *                          session: Session ]
	 *   RETURNS [ controls: ControlSequence ]
	 *   REPORTS [ AccessError, AuthenticationError, ControlTypeError,
	 *             HandleError, SessionError, UndefinedError ]
	 *   = 6;
	 */
	private static void getControls(GetControlsParams params, GetControlsResults results) {
		notImplemented("getControls", params);
	}
	
	/*
	 * ChangeControls: PROCEDURE [ file: Handle, controls: ControlSequence,
	 *                             session: Session ]
	 *   REPORTS [ AccessError, AuthenticationError, ControlTypeError, ControlValueError,
	 *             HandleError, SessionError, UndefinedError ]
	 *   = 7;
	 */
	private static void changeControls(ChangeControlsParams params, RECORD results) {
		notImplemented("changeControls", params);
	}
	
	/*
	 * GetAttributes: PROCEDURE [ file: Handle, types: AttributeTypeSequence,
	 *                            session: Session ]
	 *   RETURNS [ attributes: AttributeSequence ]
	 *   REPORTS [ AccessError, AttributeTypeError, AuthenticationError,
	 *             HandleError, SessionError, UndefinedError ]
	 *   = 8;
	 */
	private static void getAttributes(GetAttributesParams params, GetAttributesResults results) {
		logParams("getAttributes", params);
		
		// check session
		resolveSession(params.session).continueUse();
		
		// check specified file handle
		Handle fileHandle = Handle.get(params.handle);
		if (fileHandle == null || fileHandle.isNullHandle()) {
			new HandleErrorRecord(HandleProblem.nullDisallowed).raise();
		}
		FileEntry fe = fileHandle.getFe();
		if (fe == null) {
			new HandleErrorRecord(HandleProblem.nullDisallowed).raise();
		}
		
		// transfer the requested values
		List<iValueGetter<FilingCommon.AttributeSequence>> getters = getFile2CourierAttributeGetters(params.types);
		for (iValueGetter<FilingCommon.AttributeSequence> getter : getters) {
			getter.access(results.attributes, fe);
		}
		
		// done
		logResult("getAttributes", results);
	}
	
	/*
	 * ChangeAttributes: PROCEDURE [ file: Handle, attributes: AttributeSequence,
	 *                               session: Session ]
	 *   REPORTS [ AccessError, AttributeTypeError, AttributeValueError,
	 *             AuthenticationError, HandleError, InsertionError,
	 *             SessionError, SpaceError, UndefinedError ]
	 *   = 9;
	 */
	private static void changeAttributes(ChangeAttributesParams params, RECORD results) {
		logParams("changeAttributes", params);
		
		// check session
		Session session = resolveSession(params.session);
		Volume vol = session.getService().getVolume();
		session.continueUse();
		
		// check specified file handle
		Handle fileHandle = Handle.get(params.handle);
		if (fileHandle == null || fileHandle.isNullHandle()) {
			new HandleErrorRecord(HandleProblem.nullDisallowed).raise();
		}
		if (fileHandle.isVolumeRoot()) { // the file system root cannot be modified
			new AccessErrorRecord(AccessProblem.accessRightsInsufficient).raise();
		}
		FileEntry fe = fileHandle.getFe();
		if (fe == null) {
			new HandleErrorRecord(HandleProblem.nullDisallowed).raise();
		}
		
		// change attributes
		List<iValueSetter> attrSetters = getCourier2FileAttributeSetters(params.attributes);
		try (Volume.Session modSession = vol.startModificationSession()) {
			modSession.updateFileAttributes(fe, attrSetters, session.getUsername());
		} catch (InterruptedException e) {
			new UndefinedErrorRecord(FilingCommon.UNDEFINEDERROR_CANNOT_MODIFY_VOLUME).raise();
		} catch (EndOfMessageException e) {
			new ConnectionErrorRecord(ConnectionProblem.otherCallProblem).raise();
		} catch (CourierException ce) { // this is a RuntimeException, why is it catch-ed by Exception ???????
			throw ce;
		} catch (Exception e1) {
			new SpaceErrorRecord(SpaceProblem.mediumFull).raise();
		}
	}
	
	/*
	 * UnifyAccessLists: PROCEDURE [ directory: Handle, session: Session ]
	 *   REPORTS [ AccessError, AuthenticationError, HandleError, SessionError,
	 *             UndefinedError ]
	 *   = 20;
	 */
	private static void unifyAccessLists(FileHandleAndSessionRecord params, RECORD results) {
		logParams("unifyAccessLists", params);
		log("** unifyAccessLists(...) not really implemented, but ignored to allow GlobalView to proceed! **\n");
	}
	
	/*
	 * Copy: PROCEDURE [ file, destinationDirectory: Handle ,
	 *                   attributes: AttributeSequence, controls: ControlSequence,
	 *                   session: Session ]
	 *   RETURNS [ newFile: Handle ]
	 *   REPORTS [ AccessError, AttributeTypeError, AttributeValueError,
	 *             AuthenticationError, ControlTypeError, ControlValueError,
	 *             HandleError, InsertionError, SessionError, SpaceError,
	 *             UndefinedError ]
	 *   = 10;
	 */
	private static void copy(CopyParams params, CopyResults results) {
		notImplemented("copy", params);
	}
	
	/*
	 * Move: PROCEDURE [ file, destinationDirectory: Handle ,
	 *                   attributes: AttributeSequence, session: Session ]
	 *   REPORTS [ AccessError, AttributeTypeError, AttributeValueError,
	 *             AuthenticationError, HandleError, InsertionError,
	 *             SessionError, SpaceError, UndefinedError ]
	 *   = 11;
	 */
	private static void move(MoveParams params,RECORD result) {
		notImplemented("move", params);
	}
	
	/*
	 * Store: PROCEDURE [ directory: Handle, attributes: AttributeSequence,
	 *                    controls: ControlSequence, content: BulkData.Source,
	 *                    session: Session ]
	 *   RETURNS [ file: Handle ]
	 *   REPORTS [ AccessError, AttributeTypeError, AttributeValueError,
	 *             AuthenticationError, ConnectionError, ControlTypeError,
	 *             ControlValueError, HandleError, InsertionError,	SessionError,
	 *             SpaceError, TransferError, UndefinedError ]
	 *   = 12;
	 */
	private static void store(StoreParams params, FileHandleRecord results) {
		logParams("store", params);
		
		// check session
		Session session = resolveSession(params.session);
		session.continueUse();
		
		// check specified directory file handle
		Handle dirHandle = Handle.get(params.directory);
		if (dirHandle.isVolumeRoot()) { // the file system root cannot be modified
			new AccessErrorRecord(AccessProblem.accessRightsInsufficient).raise();
		}
		if (dirHandle.isNullHandle()) {
			new HandleErrorRecord(HandleProblem.directoryRequired).raise();
		}
		
		// TODO: check (access-)controls on directory
		
		final FileEntry fe;
		try {
			ByteContentSource source = new ByteContentSource(params.content);
			fe = createFile(null, session, dirHandle.getFe().getFileID(), params.attributes, source, null);
		} catch (EndOfMessageException e) {
			new ConnectionErrorRecord(ConnectionProblem.otherCallProblem).raise();
			return;
		}
		
		// prepare results
		Handle fileHandle = new Handle(session, fe);
		fileHandle.setIdTo(results.file);
		logResult("store", results);
	}
	
	/*
	 * Retrieve: PROCEDURE [ file: Handle, content: BulkData.Sink, session: Session ]
	 *   REPORTS [ AccessError, AuthenticationError, ConnectionError,
	 *             HandleError, SessionError, TransferError, UndefinedError ]
	 *   = 13;
	 */
	private static void retrieve(RetrieveParams params, RECORD results) {
		logParams("retrieve", params);
		
		// check session
		Session session = resolveSession(params.session);
		Volume vol = session.getService().getVolume();
		session.continueUse();
		
		// check specified file handle
		Handle fileHandle = Handle.get(params.file);
		if (fileHandle == null || fileHandle.isNullHandle()) {
			new HandleErrorRecord(HandleProblem.nullDisallowed).raise();
		}
		FileEntry fe = fileHandle.getFe();
		if (fe == null) {
			new HandleErrorRecord(HandleProblem.nullDisallowed).raise();
		}
		
		// transfer file content
		try {
			ByteContentSink sink = new ByteContentSink(params.content);
			vol.retrieveContent(fe.getFileID(), sink, session.getUsername());
		} catch (NoMoreWriteSpaceException | IOException e) {
			log("##  Filing.Retrieve() => %s : %s\n", e.getClass().getName(), e.getMessage());
			new TransferErrorRecord(TransferProblem.aborted).raise();
		}
	}
	
	/*
	 * Replace: PROCEDURE [ file: Handle,  attributes: AttributeSequence,
	 *                      content: BulkData.Source, session: Session ]
	 *   REPORTS [ AccessError, AttributeTypeError, AttributeValueError,
	 *             AuthenticationError, ConnectionError, HandleError,
	 *             SessionError, SpaceError, TransferError, UndefinedError ]
	 *   = 14;
	 */
	private static void replace(ReplaceParams params, RECORD results) {
		notImplemented("replace", params);
	}
	
	/*
	 * Serialize: PROCEDURE [ file: Handle, serializedFile: BulkData.Sink,
	 *                        session: Session ]
	 *   REPORTS [ AccessError, AuthenticationError, ConnectionError,
	 *             HandleError, SessionError, TransferError, UndefinedError ]
	 *   = 15;
	 */
	private static void serialize(SerializeParams params, RECORD results) {
		logParams("serialize", params);
		
		// check session
		Session session = resolveSession(params.session);
		session.continueUse();
		
		// check file
		Handle fileHandle = Handle.get(params.file);
		if (fileHandle == null || fileHandle.isNullHandle()) {
			new HandleErrorRecord(HandleProblem.nullDisallowed).raise();
		}
		if (fileHandle.isVolumeRoot()) {
			new AccessErrorRecord(AccessProblem.accessRightsInsufficient).raise();
		}
		
		// build up the tree structure
		SerializedFile serializedFile = new SerializedFile(
				ws -> new SerializeTreeWireStream(ws, session)
			);
		FileEntry startFe = fileHandle.getFe();
		fillSerializeData(startFe, serializedFile.file);
		
		// transfer the data
		sendBulkData("serialize", params.serializedFile, serializedFile);
	}
	
	// attributes used by GV 2.1 for serializing files and directories
	private static final int[] GvSerializedFileAttributeTypes = {
		FilingCommon.atAccessList,
		FilingCommon.atBackedUpOn,
		FilingCommon.atChecksum,
		FilingCommon.atCreatedBy,
		FilingCommon.atCreatedOn,
		FilingCommon.atIsDirectory,
		FilingCommon.atName,
		FilingCommon.atDataSize,
		FilingCommon.atStoredSize,
		FilingCommon.atSubtreeSize,
		FilingCommon.atType
	};
	
	// additional attributes used by GV 2.1 when serializing directories
	// warning: one of these attributes triggers promotion from file to directory (!! overriding isDirectory == false !!)
	private static final int[] GvSerializedDirectoryAttributeTypes = {
		FilingCommon.atChildrenUniquelyNamed,
		FilingCommon.atDefaultAccessList,
		FilingCommon.atOrdering,
		FilingCommon.atSubtreeSizeLimit
	};

	// generate attributes for serialized as GV 2.1 Filing does it
	// (same attribute set in same sequence)
	private static void fillGvSerializedFileAttributes(AttributeSequence s, FileEntry fe) {
		for (int i = 0; i < GvSerializedFileAttributeTypes.length; i++) {
			AttributeUtils.fillAttribute(s, GvSerializedFileAttributeTypes[i], fe);
		}
		if (fe.isDirectory()) {
			for (int i = 0; i < GvSerializedDirectoryAttributeTypes.length; i++) {
				AttributeUtils.fillAttribute(s, GvSerializedDirectoryAttributeTypes[i], fe);
			}
		}
		for (UninterpretedAttribute a: fe.getUninterpretedAttributes()) {
			AttributeUtils.file2courier_uninterpreted(s, a.getType(), fe);
		}
	}
	
	// fill the serialized tree structure with attribute metadata, file-contents
	// will be added and removed dynamically while serializing to the courier data stream
	private static void fillSerializeData(FileEntry fe, SerializedTree to) {
		fillGvSerializedFileAttributes(to.attributes, fe);
		to.clientData = fe;
		if (fe.isDirectory() && fe.getChildren() != null) {
			for (FileEntry child : fe.getChildren().getChildren()) {
				SerializedTree childSerializedTree = to.children.add();
				fillSerializeData(child, childSerializedTree);
			}
		}
	}
	
	private static class SerializeTreeByteSink implements iContentSink {

		private final Content content;
		
		private boolean atWordStart = true;
		private int word = 0;
		
		private SerializeTreeByteSink(Content content) {
			this.content = content;
		}
		
		private void putSingleByte(byte b) {
			if (this.atWordStart) {
				this.word = (b << 8) & 0xFF00;
				this.content.data.add(this.word);
				this.atWordStart = false;
			} else {
				this.word |= (b & 0xFF);
				this.content.data.setLast(this.word);
				this.atWordStart = true;
			}
		}

		@Override
		public int write(byte[] buffer, int count) {
			if (buffer == null) {
				this.content.lastByteSignificant.set(this.atWordStart);
				return 0;
			}
			
			if (count > buffer.length) { count = buffer.length; }
			int transferred = 0;
			while (transferred < count) {
				this.putSingleByte(buffer[transferred++]);
			}
			return transferred;
		}
		
	}
	
	private static class SerializeTreeWireStream extends WireStreamWrapper {
		
		private final Session session;
		
		private SerializeTreeWireStream(iWireStream ws, Session session) {
			super(ws);
			this.session = session;
		}

		@Override
		public void beginSerializedTree(SerializedTree node) { }

		@Override
		public void beforeContent(SerializedTree node) {
			try {
//				StringBuilder sb = new StringBuilder();
//				node.attributes.append(sb, "   ", "serializedTree.beforeContent()");
//				System.out.println(sb.toString());
				
				// initialize the content to be empty
				node.content.data.clear();
				node.content.lastByteSignificant.set(true); // 0 bytes so far == even == last byte is significant!
					// remark: not setting lastByteSignificant when transmitting 0 words
					//         will reveal a huge bug in the NSFiling implementation Mesa code:
					//         having this flag false will (always) subtract 1 from the transmitted length
					//         for computing the files content length, resulting in LAST[LONG CARDINAL] !!
					//         the net effect will be that transferring that file to another location
					//		   will try to copy the huge data amount of 4 GByte (whole disk up to error)!
				
				// add file content if present
				if (node.clientData != null && node.clientData instanceof FileEntry) {
					FileEntry fe = (FileEntry)node.clientData;
					if (fe.getHasContent()) {
						this.session.getService().getVolume().retrieveContent(
							fe.getFileID(),
							new SerializeTreeByteSink(node.content),
							this.session.getUsername());
					}
				}
			} catch (IOException e) {
				node.content.data.clear();
			}
		}

		@Override
		public void afterContent(SerializedTree node) {
			node.content.data.clear();
		}

		@Override
		public void endSerializedTree(SerializedTree node) { }

		@Override
		public void aborted(SerializedTree node) {
			node.content.data.clear();
		}
		
	}
	
	/*
	 * Deserialize: PROCEDURE [ directory: Handle, attributes: AttributeSequence,
	 *                          controls: ControlSequence, serializedFile: BulkData.Source,
	 *                          session: Session ]
	 *   RETURNS [ file: Handle ]
	 *   REPORTS [ AccessError, AttributeTypeError, AttributeValueError,
	 *             AuthenticationError, ConnectionError, ControlTypeError,
	 *             ControlValueError, HandleError, InsertionError,
	 *             SessionError, SpaceError, TransferError, UndefinedError ]
	 *   = 16;
	 */
	private static void deserialize(DeserializeParams params, FileHandleRecord results) {
		logParams("deserialize", params);
		
		// check session
		Session session = resolveSession(params.session);
		session.continueUse();
		
		// check directory
		Handle dirHandle = Handle.get(params.directory);
		if (dirHandle == null || dirHandle.isNullHandle()) {
			new HandleErrorRecord(HandleProblem.nullDisallowed).raise();
		}
		if (dirHandle.isVolumeRoot()) {
			new AccessErrorRecord(AccessProblem.accessRightsInsufficient).raise();
		}
		
		
		// do the transfer and deserialization
		try (Volume.Session modSession = session.getService().getVolume().startModificationSession()) {
			SerializedFile deserializedFile = new SerializedFile(
					ws -> new DeserializeTreeWireStream(ws, dirHandle.getFe().getFileID(), session, results, modSession)
					);
			params.serializedFile.receive(deserializedFile);
		} catch (EndOfMessageException e) {
			new ConnectionErrorRecord(ConnectionProblem.otherCallProblem).raise();
		} catch (Exception e) {
			System.out.printf("!!! Exception: %s -- %s !!!\n", e.getClass().getName(), e.getMessage());
			new SpaceErrorRecord(SpaceProblem.mediumFull).raise();
		}
	}
	
	private static class DeserializeTreeByteSource implements iContentSource {

		private final Content content;
		
		private int remainingBytes;
		private int wcount;
		private int wpos = 0;
		
		private int word = 0;
		private boolean atWordEnd = true;
		
		private DeserializeTreeByteSource(Content content) {
			this.content = content;

			this.wcount = this.content.data.size();
			this.remainingBytes = 2 * wcount;
			if (!this.content.lastByteSignificant.get() && this.wcount > 0) {
				this.remainingBytes--;
			}
		}
		
		private byte getByte() {
			if (this.wpos >= this.wcount) { return 0; }
			if (this.atWordEnd) {
				this.word = this.content.data.get(this.wpos++);
				this.atWordEnd = false;
				return (byte)((this.word >>> 8) & 0xFF);
			}
			this.atWordEnd = true;
			return (byte)(this.word & 0xFF);
		}
		
		@Override
		public int read(byte[] buffer) {
			if (buffer == null) {
				this.remainingBytes = 0;
				return -1;
			}
			
			int transferred = 0;
			while (this.remainingBytes > 0 && transferred < buffer.length) {
				buffer[transferred++] = this.getByte();
				this.remainingBytes--;
			}
			return transferred;
		}
		
	}
	
	private static class DeserializeTreeWireStream extends WireStreamWrapper {
		
		private final Deque<Long> parentIDs = new ArrayDeque<>();
		private long currentParentId;
		
		private final Session session;
		
		private boolean hadRootFile = false;
		private final FileHandleRecord results;
		
		private final Volume.Session modSession;
		
		private DeserializeTreeWireStream(iWireStream ws, long parentID, Session session, FileHandleRecord results, Volume.Session modSession) {
			super(ws);
			this.session = session;
			this.results = results;

			this.parentIDs.push(parentID); // fallback in case of nesting handling problems
			this.currentParentId = parentID;
			
			this.modSession = modSession;
		}
		
		/*
		 * Lifecycle methods
		 */

		@Override
		public void beginSerializedTree(SerializedTree node) { }

		@Override
		public void beforeContent(SerializedTree node) { }

		@Override
		public void afterContent(SerializedTree node) {
			iContentSource source
							= (node.content.data.size() > 0)
							? new DeserializeTreeByteSource(node.content)
							: null;
			
			// if the file cannot be created => throw AbortTransmissionException to abort transmission
			log("afterContent() -> before createFile()");
			FileEntry fe = createFile(
							this.modSession,
							this.session,
							this.currentParentId,
							node.attributes,
							source,
							e -> { log("afterContent() -> createFile() -> Exception"); throw new AbortTransmissionException(); });
			if (fe == null) { log("afterContent() -> createFile() -> null"); throw new AbortTransmissionException(); }
			
			node.content.data.clear(); // free memory space no longer used...
			
			if (!this.hadRootFile) {
				this.hadRootFile = true;
				Handle fileHandle = new Handle(session, fe);
				fileHandle.setIdTo(results.file);
			}
			
			long fileID = fe.getFileID();
			this.parentIDs.push(this.currentParentId);
			this.currentParentId = fileID;
		}

		@Override
		public void endSerializedTree(SerializedTree node) {
			this.currentParentId = this.parentIDs.pop();
		}

		@Override
		public void aborted(SerializedTree node) {
			this.currentParentId = this.parentIDs.pop();
		}
		
	}
	
	/*
	 * RetrieveBytes: PROCEDURE [ file: Handle, range: ByteRange,
	 *                            sink: BulkData.Sink, session: Session ]
	 *   REPORTS [ AccessError, HandleError, RangeError, SessionError,
	 *             UndefinedError ]
	 *   = 22;
	 */
	private static void retrieveBytes(RetrieveBytesParams params, RECORD results) {
		notImplemented("retrieveBytes", params);
	}
	
	/*
	 * ReplaceBytes: PROCEDURE [ file: Handle, range: ByteRange,
	 *                           source: BulkData.Source, session: Session ]
	 *   REPORTS [ AccessError, HandleError, RangeError, SessionError,
	 *             SpaceError, UndefinedError ]
	 *   = 23;
	 */
	private static void replaceBytes(ReplaceBytesParams params, RECORD results) {
		notImplemented("replaceBytes", params);
	}
	
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
	private static void find(FindParams params, FileHandleRecord results) {
		notImplemented("find", params);
	}
	
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
	private static void list(ListParams params, RECORD results) {
		logParams("list", params);
		
		// check session
		Session session = resolveSession(params.session);
		
		// check specified file handle
		Handle dirHandle = Handle.get(params.directory);
		long dirFileID = (dirHandle == null || dirHandle.isNullHandle() || dirHandle.isVolumeRoot()) ? 0 : dirHandle.getFe().getFileID();
		
		// check if we support this scope?
		int maxCount = FilingCommon.unlimitedCount;
		int maxDepth = 1;
		iValueFilter valueFilter = fe -> true;
		for (int i = 0; i < params.scope.size(); i++) {
			CHOICE<ScopeType> singleScope = params.scope.get(i);
			switch(singleScope.getChoice()) {
			case count: {
				ScopeCountRecord scr = (ScopeCountRecord)singleScope.getContent();
				maxCount = scr.value.get();
				break; }
			case depth:
			case depthFiling4: {
				ScopeDepthRecord sdr = (ScopeDepthRecord)singleScope.getContent();
				maxDepth = sdr.value.get();
				break; }
			case direction: {
				ScopeDirectionRecord sdr = (ScopeDirectionRecord)singleScope.getContent();
				if (sdr.value.get() != ScopeDirection.forward) {
					new ScopeTypeErrorRecord(ArgumentProblem.unimplemented, ScopeType.direction).raise();
				}
				break; }
			case filter: {
				FilterRecord ft = (FilterRecord)singleScope.getContent();
				valueFilter = AttributeUtils.buildPredicate(ft.value);
				break; }
			}
		}
		
		// prepare the attribute getters
		List<iValueGetter<FilingCommon.AttributeSequence>> getters = getFile2CourierAttributeGetters(params.types);
		
		// do the enumeration
		final List<FileEntry> hits;
		if (maxDepth < 2) {
			hits = session.getService().getVolume().listDirectory(
						dirFileID,
						valueFilter,
						maxCount,
						session.getUsername());
		} else {
			hits = session.getService().getVolume().findFiles(
					dirFileID,
					valueFilter,
					maxCount,
					maxDepth,
					session.getUsername());
		}
		
		// build the result stream and send it
		StreamOf<AttributeSequence> stream = new StreamOf<>(0, 1, 16, AttributeSequence::make);
		for (FileEntry fe : hits) {
			AttributeSequence as = stream.add();
			for (iValueGetter<FilingCommon.AttributeSequence> getter : getters) {
				getter.access(as, fe);
			}
		}
		sendBulkData("list", params.listing, stream);
	}
	
	/*
	 * common file functionality
	 */
	
	@FunctionalInterface
	private interface ExceptionHandler {
		void accept(Exception e); // may only throw RuntimeException
	}
	
	private static FileEntry createFile(
				Volume.Session modificationSession,
				Session session,
				long directoryFileID,
				AttributeSequence pAttributes,
				iContentSource source,
				ExceptionHandler exceptionHandler) {
		// get the volume
		Volume vol = session.getService().getVolume();
		
		// extract the minimal attributes => required: name ; optional: isDirectory(false), version(1/next), type(unknown)
		String name = null;
		boolean isDirectory = false;
		Integer version = null;
		Long type = null;
		for (int i = 0; i < pAttributes.value.size(); i++) {
			Attribute attr = pAttributes.value.get(i);
			switch((int)(attr.type.get() & 0xFFFF)) {
			case FilingCommon.atName:
				name = attr.getAsString();
				break;
			case FilingCommon.atIsDirectory:
				isDirectory = attr.getAsBoolean();
				break;
			case FilingCommon.atVersion:
				version = attr.getAsCardinal();
				break;
			case FilingCommon.atType:
				type = attr.getAsLongCardinal();
				break;
			}
		}
		if (name == null) {
			new AttributeValueErrorRecord(ArgumentProblem.missing, FilingCommon.atName).raise();
		}
		
		// create the new file
		List<iValueSetter> attrSetters = getCourier2FileAttributeSetters(pAttributes);
		System.out.printf("volStartModificationsession()\n");
		try {
			System.out.printf(
					"createFile( dirId = %d , isdirectory = %s , name = '%s' )\n", 
					directoryFileID, "" + isDirectory, name);
			Volume.Session modSession = (modificationSession == null)
					? vol.startModificationSession()
					: modificationSession;
			final FileEntry fe;
			try {
				fe = modSession.createFile(
									directoryFileID,
									isDirectory,
									name,
									version,
									type,
									session.getUsername(),
									attrSetters,
									source);
			} finally {
				if (modificationSession == null) {
					modSession.close();
				}
			}
			System.out.printf("-> done createFile(): fileID = %d\n", (fe != null) ? fe.getFileID() : -2);
			return fe;
		} catch (InterruptedException e) {
			System.out.printf("!!! InterruptedException !!!\n");
			new UndefinedErrorRecord(FilingCommon.UNDEFINEDERROR_CANNOT_MODIFY_VOLUME).raise();
		} catch (EndOfMessageException e) {
			System.out.printf("!!! EndOfMessageException !!!\n");
			new ConnectionErrorRecord(ConnectionProblem.otherCallProblem).raise();
		} catch (CourierException ce) { // this is a RuntimeException, why is it catch-ed by Exception ???????
			System.out.printf("!!! CourierException !!!\n");
			throw ce;
		} catch (Exception e) {
			System.out.printf("!!! Exception: %s -- %s !!!\n", e.getClass().getName(), e.getMessage());
			if (exceptionHandler != null) {
				exceptionHandler.accept(e);
			} else {
				new SpaceErrorRecord(SpaceProblem.mediumFull).raise();
			}
		}
		return null; // keep the compiler happy
	}
	
	/*
	 * utils
	 */
	
	private static void logParams(String methodName, RECORD params) {
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "arguments");
			log("##\n## procedure FilingImpl.%s (\n%s\n## ) ...\n",
				methodName, sb.toString());
		}
	}
	
	private static void logResult(String methodName, RECORD results) {
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			log("##\n## procedure FilingImpl.%s (...) => \n%s\n##\n",
				methodName, sb.toString());
		}
	}
	
	private static void sendBulkData(String procName, BulkData1.Sink sink, iWireData streamData) {
		System.out.printf("FilingImpl.%s() :: sending data via bulk data transfer\n", procName);
		try {
			sink.send(streamData, true);
		} catch (NoMoreWriteSpaceException e) {
			System.out.printf("FilingImpl.%s() :: NoMoreWriteSpaceException (i.e. abort) during BDT.send()\n", procName);
			new ConnectionErrorRecord(ConnectionProblem.otherCallProblem).raise();
		}
	}
	
	private static List<iValueGetter<FilingCommon.AttributeSequence>> getFile2CourierAttributeGetters(AttributeTypeSequence types) {
		List<iValueGetter<FilingCommon.AttributeSequence>> getters = new ArrayList<>();
		
		if (types.isAllAttributeTypes()) {
			getters.add(AttributeUtils::file2courier_allAttributes);
			return getters;
		}
		
		for (int i = 0; i < types.size(); i++) {
			int attrType = (int)types.get(i).get();
			iValueGetter<FilingCommon.AttributeSequence> getter;
			if (attrType < AttributeUtils.file2courier_interpreted.size()) {
				if ((getter = AttributeUtils.file2courier_interpreted.get(attrType)) != null) {
					getters.add(getter);
				} else {
					new AttributeTypeErrorRecord(ArgumentProblem.unimplemented, attrType).raise();
				}
			} else {
				getters.add( (s,fe) -> AttributeUtils.file2courier_uninterpreted(s, attrType, fe) );
			}
		}
		return getters;
	}
	
	private static List<iValueSetter> getCourier2FileAttributeSetters(AttributeSequence as) {
		List<iValueSetter> setters = new ArrayList<>();
		for (int i = 0; i < as.value.size(); i++) {
			Attribute a = as.value.get(i);
			setters.add(AttributeUtils.courier2file(a));
		}
		return setters;
	}
	
	/*
	 * support-wrapper-base-class for (de)serializing file trees with life-cycle handling
	 */
	
	private static abstract class WireStreamWrapper implements iWireStreamForSerializedTree {
		
		private final iWireStream ws;
		
		private WireStreamWrapper(iWireStream ws) {
			this.ws = ws;
		}

		@Override
		public void writeI48(long value) throws NoMoreWriteSpaceException {
			this.ws.writeI48(value);
		}

		@Override
		public void writeI32(int value) throws NoMoreWriteSpaceException {
			this.ws.writeI32(value);
		}

		@Override
		public void writeI16(int value) throws NoMoreWriteSpaceException {
			this.ws.writeI16(value);
		}

		@Override
		public void writeS16(short value) throws NoMoreWriteSpaceException {
			this.ws.writeS16(value);
		}

		@Override
		public void writeI8(int value) throws NoMoreWriteSpaceException {
			this.ws.writeI8(value);
		}

		@Override
		public void writeS8(short value) throws NoMoreWriteSpaceException {
			this.ws.writeS8(value);
		}

		@Override
		public void writeEOM() throws NoMoreWriteSpaceException {
			this.ws.writeEOM();
		}

		@Override
		public void beginStreamType(byte datastreamType) throws NoMoreWriteSpaceException {
			this.ws.beginStreamType(datastreamType);
		}

		@Override
		public void resetWritingToWordBoundary() {
			this.ws.resetWritingToWordBoundary();
		}

		@Override
		public long readI48() throws EndOfMessageException {
			return this.ws.readI48();
		}

		@Override
		public int readI32() throws EndOfMessageException {
			return this.ws.readI32();
		}

		@Override
		public int readI16() throws EndOfMessageException {
			return this.ws.readI16();
		}

		@Override
		public short readS16() throws EndOfMessageException {
			return this.ws.readS16();
		}

		@Override
		public int readI8() throws EndOfMessageException {
			return this.ws.readI8();
		}

		@Override
		public short readS8() throws EndOfMessageException {
			return this.ws.readS8();
		}

		@Override
		public boolean isAtEnd() {
			return this.ws.isAtEnd();
		}

		@Override
		public void dropToEOM(byte reqDatastreamType) throws EndOfMessageException {
			this.ws.dropToEOM(reqDatastreamType);
		}

		@Override
		public byte getStreamType() {
			return this.ws.getStreamType();
		}

		@Override
		public void resetReadingToWordBoundary() {
			this.ws.resetReadingToWordBoundary();
		}

		@Override
		public Long getPeerHostId() {
			return this.ws.getPeerHostId();
		}
		
		@Override
		public void sendAbort() {
			this.ws.sendAbort();
		}
	}
	
	
	
	/*
	 * temp
	 */
	private static void notImplemented(String methodName, RECORD params) {
		StringBuilder sb = new StringBuilder();
		params.append(sb, "  ", "arguments");
		log("##\n## procedure FilingImpl.%s (\n%s\n## ) => UndefinedErrorRecord(42).raise()\n##\n",
			methodName, sb.toString());
		new UndefinedErrorRecord(FilingCommon.UNDEFINEDERROR_UNIMPLEMENTED).raise();
	}
}
