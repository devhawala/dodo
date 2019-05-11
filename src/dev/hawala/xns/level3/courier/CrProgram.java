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

package dev.hawala.xns.level3.courier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.hawala.xns.Log;
import dev.hawala.xns.level3.courier.iWireStream.DeserializeException;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level3.courier.iWireStream.SerializeException;
import dev.hawala.xns.level3.courier.exception.CourierCheckedMethodError;
import dev.hawala.xns.level3.courier.exception.CourierRuntimeException;

/**
 * Base class for the definition of the interface of Courier programs.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public abstract class CrProgram {
	
	public abstract int getProgramNumber();
	
	public abstract int getVersionNumber();
	
	protected String pgmIntro = null;
	
	protected String getPgmIntro() {
		if (this.pgmIntro == null) {
			this.pgmIntro = "CrProgram[ " + this.getProgramNumber() + " , " + this.getVersionNumber() + " ]";
		}
		return this.pgmIntro;
	}
	
	
	/*
	 * declaration of Courier enum holder creation
	 */
	
	private static class RealENUM<T extends Enum<T>> extends ENUM<T> {
		
		private final Map<Short,T> wire2enum;
		private final Map<T,Short> enum2wire;
		
		private RealENUM(Map<Short,T> w2e, Map<T,Short> e2w) {
			this.wire2enum = w2e;
			this.enum2wire = e2w;
		}
		
		@Override
		public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
			if (this.value == null) {
				throw new IllegalStateException("no enum value present on serialize()");
			}
			Short wireValue = this.enum2wire.get(this.value);
			if (wireValue == null) {
				throw new SerializeException("no wire representaiton available for enum value :" + this.value);
			}
			ws.writeI16(wireValue & 0xFFFF);
		}

		@Override
		public void deserialize(iWireStream ws) throws EndOfMessageException {
			Short wireValue = Short.valueOf((short) ws.readI16());
			if (this.wire2enum.containsKey(wireValue)) {
				this.value = this.wire2enum.get(wireValue);
			} else {
				throw new DeserializeException("no enum value mapped for wire value: " + wireValue);
			}
		}
		
		@Override
		public StringBuilder append(StringBuilder to, String indent, String fieldName) {
			String valueStr = (this.value != null) ? this.value.toString() : "<null>";
			to.append(indent).append(fieldName).append(": ").append(valueStr);
			return to;
		}

		@Override
		protected boolean isAcceptable(T val) {
			return this.enum2wire.containsKey(val);
		}
	}
	
	public static class EnumMaker<T extends Enum<T>> implements iWireDynamic<ENUM<T>> {
		
		private final Map<Short,T> wire2enum;
		private final Map<T,Short> enum2wire;
		
		private EnumMaker(Class<T> ec, Map<Short,T> w2e, Map<T,Short> e2w) {
			this.wire2enum = w2e;
			this.enum2wire = e2w;
		}
		
		public ENUM<T> get() {
			return this.make();
		}

		@Override
		public ENUM<T> make() {
			return new RealENUM<T>(this.wire2enum, this.enum2wire);
		}
	}

	public static class EnumBuilder<T extends Enum<T>> {
		
		private final Class<T> enumClass;
		
		private final Map<Short,T> wire2enum = new HashMap<>();
		
		private final Map<T,Short> enum2wire = new HashMap<>();
		
		private EnumMaker<T> maker = null;
		
		private EnumBuilder(Class<T> ec) {
			this.enumClass = ec;
		}
		
		public EnumBuilder<T> map(int wireValue, T value) {
			return this.map((short)(wireValue & 0xFFFF), value);
		}
		
		public EnumBuilder<T> map(short wireValue, T value) {
			if (this.maker != null) {
				throw new IllegalStateException("EnumBuilder already finalized (EnumMaker<" + this.enumClass.getName() + "> was created)");
			}
			this.wire2enum.put(wireValue, value);
			this.enum2wire.put(value,  wireValue);
			return this;
		}
		
		public EnumMaker<T> get() {
			if (this.maker == null) {
				if (this.enum2wire.isEmpty()) {
					T[] allValues = this.enumClass.getEnumConstants();
					if (allValues.length > 0 && allValues[0] instanceof CrEnum) {
						for (T value : allValues) {
							CrEnum cre = (CrEnum)value;
							this.map(cre.getWireValue(), value);
						}
					} else {
						for (T value : allValues) {
							this.map(value.ordinal(), value);
						}
					}
				}
				this.maker = new EnumMaker<T>(this.enumClass, this.wire2enum, this.enum2wire);
			}
			return this.maker;
		}
	}
	
	public static <T extends Enum<T>> EnumBuilder<T> buildEnum(Class<T> ec) {
		return new EnumBuilder<T>(ec);
	}
	
	/*
	 * simple Courier enum for CHOICE selections
	 */
	
	public enum ChoiceNums { zero, one, two, three, four, five, six, seven, eight, nine }
	public static final EnumMaker<ChoiceNums> choiceNumMaker = buildEnum(ChoiceNums.class).get();
	
	/*
	 * CHOICE
	 */
	
	
	private static class RealCHOICE<T extends Enum<T>> extends CHOICE<T> {
		
		private final Map<T,iWireDynamic<RECORD>> choiceMap;
		
		private RealCHOICE(ENUM<T> cv, Map<T,iWireDynamic<RECORD>> choices) {
			super(cv);
			this.choiceMap = choices;
		}

		@Override
		public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
			if (this.choiceContent == null) {
				throw new IllegalStateException("no content choice present on serialize()");
			}
			this.choiceValue.serialize(ws);
			this.choiceContent.serialize(ws);
		}

		@Override
		public void deserialize(iWireStream ws) throws EndOfMessageException {
			this.choiceValue.deserialize(ws);
			if (!this.isAcceptableChoice(this.choiceValue.get())) {
				throw new IllegalArgumentException("deserialized value for CHOICE has no mapped content definition");
			}
			this.choiceContent = this.createContentFor(this.choiceValue.get());
			this.choiceContent.deserialize(ws);
		}

		@Override
		public StringBuilder append(StringBuilder to, String indent, String fieldName) {
			to.append(indent).append(fieldName).append(": CHOICE");
			if (this.choiceContent == null) {
				to.append(" <null> { <null> }");
			} else {
				to.append(" {\n");
				String newIndent = indent + "    ";
				this.choiceContent.append(to, newIndent, this.choiceValue.value.toString());
				to.append("\n").append(newIndent).append("}");
			}
			return to;
		}

		@Override
		protected boolean isAcceptableChoice(T val) {
			return this.choiceMap.containsKey(val);
		}

		@Override
		protected RECORD createContentFor(T choiceValue) {
			iWireDynamic<RECORD> contentMaker = this.choiceMap.get(choiceValue);
			if (contentMaker == null) {
				throw new IllegalArgumentException("value for CHOICE has no mapped content definition");
			}
			return contentMaker.make();
		}
		
	}
	
	public static class ChoiceMaker<T extends Enum<T>> implements iWireDynamic<CHOICE<T>> {
		
		private final iWireDynamic<ENUM<T>> choiceEnumBuilder;
		
		private final Map<T,iWireDynamic<RECORD>> choiceMap;
		
		private ChoiceMaker(iWireDynamic<ENUM<T>> choiceEnumBuilder, Map<T,iWireDynamic<RECORD>> choiceMap) {
			this.choiceEnumBuilder = choiceEnumBuilder;
			this.choiceMap = choiceMap;
		}

		@Override
		public CHOICE<T> make() {
			return new RealCHOICE<T>(this.choiceEnumBuilder.make(), this.choiceMap);
		}
		
	}
	
	public static class ChoiceBuilder<T extends Enum<T>> {
		
		private final iWireDynamic<ENUM<T>> choiceEnumBuilder;
		
		private final Map<T,iWireDynamic<RECORD>> choiceMap = new HashMap<>();
		
		private ChoiceMaker<T> maker = null;
		
		private ChoiceBuilder(iWireDynamic<ENUM<T>> choiceEnumBuilder) {
			this.choiceEnumBuilder = choiceEnumBuilder;
		}
		
		public ChoiceBuilder<T> choice(T selector, iWireDynamic<RECORD> recordBuilder) {
			if (this.maker != null) {
				throw new IllegalStateException("ChoiceBuilder already finalized (ChoiceMaker was created)");
			}
			this.choiceMap.put(selector, recordBuilder);
			return this;
		}
		
		public ChoiceMaker<T> get() {
			if (this.maker == null) {
				this.maker = new ChoiceMaker<T>(this.choiceEnumBuilder, this.choiceMap);
			}
			return this.maker;
		}
		
	}
	
	public static <T extends Enum<T>> ChoiceBuilder<T> buildChoice(iWireDynamic<ENUM<T>> choiceEnumBuilder) {
		return new ChoiceBuilder<T>(choiceEnumBuilder);
	}
	
	/*
	 * declaration of ERRORs of a Courier program
	 */
	
	protected class ERROR<T extends ErrorRECORD> implements iWireDynamic<T> {
		
		private final Class<T> errorRecordClass;
		
		private ERROR(Class<T> errorRecordClass) {
			this.errorRecordClass = errorRecordClass;
		}

		@Override
		public T make() {
			try {
				return this.errorRecordClass.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				throw new CourierRuntimeException();
			}
		}
		
		public boolean is(ErrorRECORD e) {
			if (e == null) { return false; }
			return this.errorRecordClass.isAssignableFrom(e.getClass());
		}
	}
	
	protected <T extends ErrorRECORD> ERROR<T> mkERROR(Class<T> errorClass) {
		return new ERROR<T>(errorClass);
	}
	
	/*
	 * procedures of a Courier program
	 */
	
	private boolean logParamsAndResults = false;
	
	@SuppressWarnings("unchecked")
	public <T extends CrProgram> T setLogParamsAndResults(boolean doLog) {
		this.logParamsAndResults = doLog;
		return (T)this;
	}
	
	private void log(String procName, String msg, String argType, RECORD arg) {
		if (!msg.endsWith("\n")) {
		msg += "\n";
		}
		Log.C.printf("proc " + procName, msg);
		if (this.logParamsAndResults && argType != null) {
			StringBuilder sb = new StringBuilder();
			arg.append(sb, "  ", argType);
			sb.append("\n");
			Log.C.printf("proc " + procName, sb.toString());
		}
	}
	
	private void log(String procName, String msg) {
		this.log(procName, msg, null, null);
	}
	
	
	private final Map<Integer,PROC<?,?>> procImplementations = new HashMap<>();
	
	public void dispatch(
					int transaction,
					iWireStream connection) throws NoMoreWriteSpaceException, EndOfMessageException {
		int procNo = connection.readI16();
		if (!this.procImplementations.containsKey(procNo)) {
			Log.C.printf(null, "%s.dispatch() ## unimplemented proc # %d ... rejecting\n", this.getPgmIntro(), procNo);
			connection.dropToEOM(Constants.SPPSST_RPC); 
			connection.writeI16(transaction);
			connection.writeI16(1); // MessageType.reject(1)
			connection.writeI16(2); // RejectCode.noSuchProcedureValue(2)
			connection.writeEOM();
			return;
		}
		
		PROC<?,?> proc = this.procImplementations.get(procNo);
		Log.C.printf(null, "%s.dispatch() -- invoking proc %s\n", this.getPgmIntro(), proc.getName());
		proc.process(transaction, connection);
		Log.C.printf(null, "%s.dispatch() -- finished proc %s\n", this.getPgmIntro(), proc.getName());
	}
	
	@FunctionalInterface
	public interface CourierProcedureImplementation<P extends RECORD, R extends RECORD> {
		void execute(P callParameters, R returnParameters);
	}
	
	public class PROC<P extends RECORD, R extends RECORD> {
		
		private final String procName;
		
		private final int procNumber;
		
		private final iWireDynamic<P> callParameters;
		
		private final iWireDynamic<R> returnParameters;
		
		private final List<ERROR<?>> declaredErrors;
		
		private CourierProcedureImplementation<P,R> implementation = null;
		
		private PROC(
					String procName,
					int procNumber,
					iWireDynamic<P> callParameters,
					iWireDynamic<R> returnParameters,
					List<ERROR<?>> declaredErrors) {
			this.procName = (procName != null && !procName.isEmpty()) ? procName + "[ #" + procNumber + " ]" : "#" + procNumber;
			this.procNumber = procNumber;
			this.callParameters = callParameters;
			this.returnParameters = returnParameters;
			this.declaredErrors = declaredErrors;
		}
		
		public String getName() {
			return this.procName;
		}
		
		public void use(CourierProcedureImplementation<P,R> implementation) {
			synchronized(procImplementations) {
				this.implementation = implementation;
				if (implementation != null) {
					procImplementations.put(this.procNumber, this);
				} else if (procImplementations.containsKey(this.procNumber)) {
					procImplementations.remove(this.procNumber);
				}
			}
		}
		
		public int getProcNumber() {
			return this.procNumber;
		}
		
		public void process(
				int transaction,
				iWireStream connection) throws NoMoreWriteSpaceException, EndOfMessageException {
			
			// sanity check (dispatch should not have happened!)
			if (this.implementation == null) {
				connection.dropToEOM(Constants.SPPSST_RPC); 
				this.encodeReject(transaction, 2, connection); // RejectCode.noSuchProcedureValue(2)
				return;
			}
			
			// create the call and return data structures
			P inParams = this.callParameters.make();
			R outParams = this.returnParameters.make();
			
			// read then call parameters and signal a rejection if the data length does not match
			try {
				inParams.deserialize(connection);
			} catch (Exception e) {
				this.encodeInvalidArgumentsReject(transaction, connection);
				return;
			}
			if (!connection.isAtEnd()) {
				connection.dropToEOM(Constants.SPPSST_RPC);
				this.encodeInvalidArgumentsReject(transaction, connection);
				return;
			}
			CrProgram.this.log(procName, "call", "params", inParams);
			
			try {
				
				// execute the call, producing either the return data or some error
				this.implementation.execute(inParams, outParams);
				
			} catch (CourierCheckedMethodError ce) {
				// encode the checked error, if this error is declared for the method  
				ErrorRECORD errorData = ce.getDetails();
				for (ERROR<?> e: this.declaredErrors) {
					if (e.is(errorData)) {
						this.encodeAbort(transaction, connection, errorData);
						return;
					}
				}
				
				// this error type is not declared for the method, so use fallback
				System.out.printf("\n###\n### rejecting because of undeclared checked error raised: %s : %s\n", ce.getClass().getName(), ce.getMessage());
				ce.printStackTrace(System.out);
				System.out.printf("\n###\n\n");
				this.encodeInvalidArgumentsReject(transaction, connection);
				return;
			} catch (Throwable thr) {
				// do a fallback handling for any other error
				System.out.printf("\n###\n### rejecting because of unexpected unchecked exception raised: %s : %s\n", thr.getClass().getName(), thr.getMessage());
				thr.printStackTrace(System.out);
				System.out.printf("\n###\n\n");
				this.encodeInvalidArgumentsReject(transaction, connection);
				return;
			}
			
			// send back the return data
			this.encodeReturn(transaction, connection, outParams);
		}
		
		private void encodeReject(int transaction, int reason, iWireStream connection) throws NoMoreWriteSpaceException {
			CrProgram.this.log(procName, "encode reject with code: " + reason);
			connection.writeI16(1); // MessageType.reject(1)
			connection.writeI16(transaction);
			connection.writeI16(reason);
			connection.writeEOM();
		}
		
		private void encodeInvalidArgumentsReject(int transaction, iWireStream connection) throws NoMoreWriteSpaceException {
			this.encodeReject(transaction, 3, connection); // RejectCode.invalidArguments(3)
		}
		
		private void encodeAbort(int transaction, iWireStream connection, ErrorRECORD abortData) throws NoMoreWriteSpaceException {
			CrProgram.this.log(procName, "encodeAbort", "abortData", abortData);
			connection.writeI16(3); // MessageType.abort(3)
			connection.writeI16(transaction);
			connection.writeI16(abortData.getErrorCode());
			abortData.serialize(connection);
			connection.writeEOM();
		}
		
		private void encodeReturn(int transaction, iWireStream connection, RECORD resultData) throws NoMoreWriteSpaceException {
			CrProgram.this.log(procName, "encodeReturn", "resultData", resultData);
			connection.writeI16(2); // MessageType.return(2)
			connection.writeI16(transaction);
			resultData.serialize(connection);
			connection.writeEOM();
		}
		
	}
	
	protected <P extends RECORD, R extends RECORD> PROC<P,R> mkPROC(
					int functionCode,
					iWireDynamic<P> callParameters,
					iWireDynamic<R> returnParameters,
					ERROR<?>... declaredErrors
				) {
		return mkPROC(null, functionCode, callParameters, returnParameters, declaredErrors);
	}

		
	protected <P extends RECORD, R extends RECORD> PROC<P,R> mkPROC(
					String name,
					int functionCode,
					iWireDynamic<P> callParameters,
					iWireDynamic<R> returnParameters,
					ERROR<?>... declaredErrors
				) {
		List<ERROR<?>> errList = Arrays.asList(declaredErrors);
		return new PROC<P,R>(name, functionCode, callParameters, returnParameters, errList);
	}
	
}
