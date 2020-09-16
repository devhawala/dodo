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

import java.util.function.Consumer;

import dev.hawala.xns.level3.courier.ARRAY;
import dev.hawala.xns.level3.courier.BOOLEAN;
import dev.hawala.xns.level3.courier.CARDINAL;
import dev.hawala.xns.level3.courier.CHOICE;
import dev.hawala.xns.level3.courier.CrEnum;
import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.INTEGER;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.LONG_INTEGER;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.StreamOfUnspecified;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level3.courier.UNSPECIFIED2;
import dev.hawala.xns.level3.courier.WireSeqOfUnspecifiedReader;
import dev.hawala.xns.level3.courier.WireWriter;
import dev.hawala.xns.level3.courier.iWireData;
import dev.hawala.xns.level3.courier.iWireDynamic;
import dev.hawala.xns.level3.courier.iWireStream;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.common.AuthChsCommon;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.BulkData1;
import dev.hawala.xns.level4.filing.fs.FsConstants;

/**
 * Definition of the Filing Courier program (PROGRAM 10)
 * parts common to versions 4, 5 and 6,
 * (transcribed from Filing4.cr, Filing5.cr, Filing6.cr).
 * <p>
 * Remark: the files 'Filing4.cr' found in the internet are inaccurate
 * or even wrong regarding some datatypes, the errors were discovered when
 * using StarOS 5.0 (running under Darkstar) to access the Dodo file services.
 * These deviations between existing 'Filing4.cr' files and the Courier data
 * streams produced (resp. expected) by a *real* Filing4 client relate to:
 * </p>
 * <ul>
 * <li>AccessType and AccessSequence (bits in a single word for Filing4
 * instead of an ENUM resp. a SEQUENCE of this enum</li>
 * <li>ScopeType and ScopeSequence, where the Filing4 ScopeType enum
 * has an additional value with a related value record for the scope
 * definition; this results in incompatible find() and list() procedures</li>
 * </ul>
 * <p>(see the comments in the corresponding declarations below)</p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin 2019,2020
 */
public abstract class FilingCommon extends CrProgram {
	
	/*
	 * local implementation specifics: values for UndefinedError.problem
	 */
	
	public static final int UNDEFINEDERROR_UNIMPLEMENTED = 42; // unimplemented courier procedure
	public static final int UNDEFINEDERROR_CANNOT_MODIFY_VOLUME = 43; // error while initiating a change to volume
	public static final int UNDEFINEDERROR_ENCODE_ERROR = 44; // error while encoding some file value to courier
	
	/*
	 * ************ TYPES AND CONSTANTS ************
	 */
	
	/*
	 * -- Attributes (individual attributes defined later) --
	 */

	/*
	 * AttributeType: TYPE = LONG CARDINAL;
	 * AttributeTypeSequence: TYPE = SEQUENCE OF AttributeType;
	 * allAttributeTypes: AttributeTypeSequence = [37777777777B];
	 */
	// AttributeType <~> LONG_CARDINAL 
	public static class AttributeTypeSequence extends SEQUENCE<LONG_CARDINAL> {
		private AttributeTypeSequence(iWireDynamic<LONG_CARDINAL> builder) { super(builder); }
		public static AttributeTypeSequence make() { return new AttributeTypeSequence(LONG_CARDINAL::make); }
		
		public boolean isAllAttributeTypes() {
			for (int i = 0; i < this.size(); i++) {
				if (this.get(i).get() == attributeTypeAll) { return true; }
			}
			return false;
		}
		public AttributeTypeSequence asAllAttributeTypes() {
			this.clear().add().set(attributeTypeAll);
			return this;
		}
	}
	public static final long attributeTypeAll = 037777777777L;
	
	/*
	 * Attribute: TYPE = RECORD [type: AttributeType, value: SEQUENCE OF UNSPECIFIED];
	 * AttributeSequence: TYPE = RECORD [value: SEQUENCE OF Attribute];
	 */
	public static class Attribute extends RECORD {
		public final LONG_CARDINAL type = mkLONG_CARDINAL();
		public final SEQUENCE<UNSPECIFIED> value = mkSEQUENCE(UNSPECIFIED::make);

		private Attribute() {}
		public static Attribute make() { return new Attribute(); }
		
		public <T extends iWireData> T decodeData(iWireDynamic<T> maker) throws EndOfMessageException {
			T data = maker.make();
			
			WireSeqOfUnspecifiedReader reader = new WireSeqOfUnspecifiedReader(this.value);
			data.deserialize(reader);
			
			return data;
		}
		
		public <T extends iWireData> void encodeData(T data) throws NoMoreWriteSpaceException {
			WireWriter writer = new WireWriter();
			data.serialize(writer);
			int[] words = writer.getWords();
			this.value.clear();
			for (int i = 0; i < words.length; i++) {
				this.value.add().set(words[i]);
			}
		}
		
		private <T extends iWireData> T as(iWireDynamic<T> maker) {
			try {
				return this.decodeData(maker);
			} catch(Exception e) {
				new AttributeValueErrorRecord(ArgumentProblem.illegal, this.type.get()).raise();
				return null; // keep the compiler happy...
			}
		}
		public String getAsString() { return this.as(STRING::make).get(); }
		public boolean getAsBoolean() { return this.as(BOOLEAN::make).get(); }
		public int getAsCardinal() { return this.as(CARDINAL::make).get(); }
		public long getAsLongCardinal() { return this.as(LONG_CARDINAL::make).get(); }
		public int getAsInteger() { return this.as(INTEGER::make).get(); }
		public int getAsLongInteger() { return this.as(LONG_INTEGER::make).get(); }
		public long getAsTime() { return this.as(LONG_CARDINAL::make).get(); }
		public long getAsFileID() { return this.as(FileID::make).get(); }
		public String getAsChsName() { return this.as(ThreePartName::make).getString(); }
		
		private <T extends iWireData> void set(long type, iWireDynamic<T> maker, Consumer<T> setter) {
			T data = maker.make();
			setter.accept(data);
			try {
				this.encodeData(data);
			} catch (Exception e) {
				new UndefinedErrorRecord(UNDEFINEDERROR_ENCODE_ERROR).raise();
			}
			this.type.set(type);
		}
		public void setAsString(long type, String value) { this.set(type, STRING::make, v -> v.set(value) ); }
		public void setAsBoolean(long type, boolean value) { this.set(type, BOOLEAN::make, v -> v.set(value) ); }
		public void setAsCardinal(long type, int value) { this.set(type, CARDINAL::make, v -> v.set(value) ); }
		public void setAsLongCardinal(long type, long value) { this.set(type, LONG_CARDINAL::make, v -> v.set(value) ); }
		public void setAsInteger(long type, int value) { this.set(type, INTEGER::make, v -> v.set(value) ); }
		public void setAsLongInteger(long type, int value) { this.set(type, LONG_INTEGER::make, v -> v.set(value) ); }
		public void setAsTime(long type, long value) { this.set(type, LONG_CARDINAL::make, v -> v.set(value) ); }
		public void setAsFileID(long type, long value) { this.set(type, FileID::make, v -> v.set(value) ); }
		public void setAsChsName(long type, String value) { this.set(type, ThreePartName::make, v -> v.from(value) ); }
		
		public void setAsNullValue(long type) {
			this.type.set(type);
			this.value.clear();
		}
		
		public void setAsPosition(long position) {
			this.type.set(FilingCommon.atPosition);
			
			// manually emulate a SEQUENCE
			if (position <= 0xFFFF) {
				this.value.add().set(1);       // SEQUENCE<UNSPECIFIED> :: length = 1
				this.value.add().set((position == 0xFFFF) ? 0 : 0xFFFF);
			} else {
				int posUpper = (int)((position >> 16) & 0xFFFFL);
				int posLower = (int)(position & 0xFFFFL);
				this.value.add().set(2);       // SEQUENCE<UNSPECIFIED> :: length = 2 
				this.value.add().set(posUpper);// SEQUENCE<UNSPECIFIED> :: element[0]
				this.value.add().set(posLower);// SEQUENCE<UNSPECIFIED> :: element[1]
			}
		}
		
		public long getAsPosition() {
			if (this.type.get() != FilingCommon.atPosition) {
				return 0;
			}
			if (this.value.size() < 2) {
				return 0;
			} else if (this.value.size() == 2) {
				return (this.value.get(1).get() == 0xFFFF) ? 0xFFFF : 0;
			} else {
				int posUpper = this.value.get(1).get();
				int posLower = this.value.get(2).get();
				return (posUpper << 16) | posLower;
			}
		}
		
	}
	public static class AttributeSequence extends RECORD {
		public final SEQUENCE<Attribute> value = mkSEQUENCE(Attribute::make);
		
		private AttributeSequence() {}
		public static AttributeSequence make() { return new AttributeSequence(); }
	}
	
	
	/*
	 * -- Controls --
	 */
	
	/*
	 * ControlType: TYPE = {lockControl(0), timeoutControl(1), accessControl(2)};
	 * ControlTypeSequence: TYPE = SEQUENCE 3 OF ControlType;
	 */
	public enum ControlType { lockControl , timeoutControl , accessControl }
	public static final EnumMaker<ControlType> mkControlType = buildEnum(ControlType.class).get();
	public static class ControlTypeSequence extends SEQUENCE<ENUM<ControlType>> {
		private ControlTypeSequence() { super(3, mkControlType); }
		public static ControlTypeSequence make() { return new ControlTypeSequence(); }
	}
	
	/*
	 * Lock: TYPE = {lockNone(0), share(1), exclusive(2)};
	 */
	public enum Lock { lockNone , share , exclusive }
	public static final EnumMaker<Lock> mkLock = buildEnum(Lock.class).get();
	
	/*
	 * Timeout: TYPE = CARDINAL;	-- in seconds --
	 * defaultTimeout: Timeout = 177777B;	-- actual value impl.-dependent --
	 */
	// Timeout <~> CARDINAL
	public static final int defaultTimeout = 0177777;
	
	/*
	 * Filing version 5/6:
	 * 
	 *   AccessType: TYPE = {
	 *     readAccess(0), writeAccess(1), ownerAccess(2),	-- all files --
	 *     addAccess(3), removeAccess(4) };		-- directories only --
	 *   AccessSequence: TYPE = SEQUENCE 5 OF AccessType;
	 *   -- fullAccess: AccessSequence = [177777B]; --
	 * 
	 * Filing version 4: (defined as above in all "Filing4.cr" files found in the internet, but seems to be in reality as follows)
	 * 
	 *   AccessSequence ~ CARDINAL with a bit set for each AccessType
	 */
	public enum AccessType implements CrEnum {
		readAccess(0), writeAccess(1), ownerAccess(2), addAccess(3), removeAccess(4), fullAccess(0177777);
		
		final int wireValue;
		private AccessType(int wireValue) { this.wireValue = wireValue; }
		@Override public int getWireValue() { return this.wireValue; }
	}
	public static final EnumMaker<AccessType> mkAccessType = buildEnum(AccessType.class).get();
	
	public static class AccessSequence implements iWireData {
		
		private CARDINAL                   forFiling4 = null;
		private SEQUENCE<ENUM<AccessType>> forFilingPost4 = null;
		
		private AccessSequence(boolean isFiling4) {
			if (isFiling4) {
				this.forFiling4 = new CARDINAL();
			}
		}
		
		private void ensureRepresented() {
			if (this.forFiling4 == null && this.forFilingPost4 == null) {
				this.forFilingPost4 = new SEQUENCE<ENUM<AccessType>>(5, mkAccessType);
			}
		}
		
		public void switchToFiling4() {
			if (this.forFiling4 != null) { return; } // already there
			this.forFiling4 = new CARDINAL();
			if (this.forFilingPost4 != null) {
				for (int i = 0; i < this.forFilingPost4.size(); i++) {
					this.add(this.forFilingPost4.get(i).get());
				}
			}
			this.forFilingPost4 = null;
		}
		
		public static AccessSequence makeAny() { return new AccessSequence(false); }
		public static AccessSequence make4() { return new AccessSequence(true); }
		public static AccessSequence makePost4() {
			AccessSequence as = new AccessSequence(false);
			as.ensureRepresented();
			return as;
		}

		private static int map(AccessType accessType) {
			switch(accessType) {
			case readAccess:   return 0x0010; // if this bit is not set, StarOS refuses to open a folder, so 'readAccess(0)' is assumed to be bit 0x0010
			case writeAccess:  return 0x0008; // the placement of other bits are assumed based on the enum-wir-index in AccessType 
			case ownerAccess:  return 0x0004; // ...
			case addAccess:    return 0x0002; // ...
			case removeAccess: return 0x0001; // ...
			case fullAccess:   return 0x001F; // (all above bits)
			default: return 0; // why is this branch required if all enum-values have their branch??
			}
		}
		
		public void add(AccessType accessType) {
			if (this.forFiling4 != null) {
				this.forFiling4.set((this.forFiling4.get() | map(accessType)) & 0xFFFF);
			} else {
				this.ensureRepresented();
				for (int i = 0; i < this.forFilingPost4.size(); i++) {
					if (this.forFilingPost4.get(i).get() == accessType) {
						return;
					}
				}
				this.forFilingPost4.add().set(accessType);
			}
		}
		
		public void clear() {
			if (this.forFiling4 != null) {
				this.forFiling4.set(0);
			} else if (this.forFilingPost4 != null) {
				this.forFilingPost4.clear();
			}
		}
		
		public void remove(AccessType accessType) {
			if (this.forFiling4 != null) {
				this.forFiling4.set((this.forFiling4.get() & (map(accessType) ^ 0xFFFF)) & 0xFFFF);
			} else {
				this.ensureRepresented();
				if (this.has(accessType)) {
					SEQUENCE<ENUM<AccessType>> newRepr = new SEQUENCE<ENUM<AccessType>>(5, mkAccessType);
					for (int i = 0; i < this.forFilingPost4.size(); i++) {
						ENUM<AccessType> val = this.forFilingPost4.get(i);
						if (val.get() != accessType) {
							newRepr.add(val);
						}
					}
					this.forFilingPost4 = newRepr;
				}
			}
		}
		
		public boolean has(AccessType accessType) {
			if (this.forFiling4 != null) {
				int accessTypeBit = map(accessType);
				return (this.forFiling4.get() & accessTypeBit) == accessTypeBit;
			} else if (this.forFilingPost4 != null) {
				for (int i = 0; i < this.forFilingPost4.size(); i++) {
					if (this.forFilingPost4.get(i).get() == accessType) {
						return true;
					}
					if (this.forFilingPost4.get(i).get() == AccessType.fullAccess) {
						return true;
					}
				}
				return false;
			} else {
				return false;
			}
		}

		@Override
		public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
			if (this.forFiling4 != null) {
				this.forFiling4.serialize(ws);
			} else {
				this.ensureRepresented();
				this.forFilingPost4.serialize(ws);
			}
		}
	
		@Override
		public void deserialize(iWireStream ws) throws EndOfMessageException {
			if (this.forFiling4 != null) {
				this.forFiling4.deserialize(ws);
			} else if (this.forFilingPost4 != null) {
				this.forFilingPost4.deserialize(ws);
			} else {
				// decide how to decode by interpreting the first word...
				int firstWord = ws.readI16() & 0xFFFF;
				if (firstWord == 0) {
					// well this means "no access" in all filing versions (no bits in Filing4-word resp. empty sequence for Filing5/6)
					// so leave our fields blank, also signaling "no access values set"
				} else if (firstWord <= 5 && !ws.isAtEnd()) {
					// this is the only ambiguous case!
					// assuming that a file has at least 'readAccess' if it is accessible at all, a Filing4-Access should be > 15
					// so any value larger than 5 (the max. length of a Filing5/6 AccessSequence) is assumed to be Filing4
					// however: if a programmer uses only a unfavorable mix of 'add', 'remove' or 'owner' accesses, we will run into trouble... 
					this.ensureRepresented(); // switch to post-Filing4
					
					// assuming that a AccessSequence-instance not yet bound to a Filing-version is used in attribute lists only,
					// a word-sequence is decoded by 'ws', we switch to post-filing4 as there is more than one word in the serialized attribute
					for (int i = 0; i < firstWord; i++) {
						ENUM<AccessType> elem = mkAccessType.make();
						elem.deserialize(ws);
						this.forFilingPost4.add(elem);
					}
				} else {
					// this is a filing4 access list (single word)
					this.forFiling4 = new CARDINAL();
					this.forFiling4.set(firstWord);
					// there should be no more data for this item (if really Filing4), but who knows...
				}
			}
		}
	
		@Override
		public StringBuilder append(StringBuilder to, String indent, String fieldName) {
			if (this.forFiling4 != null) {
				return this.forFiling4.append(to, indent, fieldName);
			} else {
				this.ensureRepresented();
				return this.forFilingPost4.append(to, indent, fieldName);
			}
		}
		
	}
	
	/*
	 * Control: TYPE = CHOICE ControlType OF {
	 *   lockControl => RECORD [value: Lock],
	 *   timeoutControl => RECORD [value: Timeout],
	 *   accessControl => RECORD [value: AccessSequence]};
	 * ControlSequence: TYPE = SEQUENCE 3 OF Control;
	 */
	public static class LockControl extends RECORD {
		public final ENUM<Lock> lock = mkENUM(mkLock);
		
		private LockControl() {}
		public static LockControl make() { return new LockControl(); }
	}
	public static class TimeoutControl extends RECORD {
		public CARDINAL value = mkCARDINAL();
		
		private TimeoutControl() {}
		public static TimeoutControl make() { return new TimeoutControl(); }
	}
	public static class AccessControl extends RECORD {
		public final AccessSequence value = mkMember(AccessSequence::makeAny);
		
		private AccessControl() {}
		public static AccessControl make() { return new AccessControl(); }
	}
	public static final ChoiceMaker<ControlType> mkControl = buildChoice(mkControlType)
			.choice(ControlType.lockControl, LockControl::make)
			.choice(ControlType.timeoutControl, TimeoutControl::make)
			.choice(ControlType.accessControl, AccessControl::make)
			.get();
	public static class ControlSequence extends SEQUENCE<CHOICE<ControlType>> {
		private ControlSequence() { super(3, mkControl); }
		public static ControlSequence make() { return new ControlSequence(); }
	}
	
	
	/*
	 * -- Scopes --
	 */
	
	/*
	 * ScopeCount: TYPE = CARDINAL;
	 * unlimitedCount: ScopeCount = 177777B;
	 */
	// ScopeCount <~> CARDINAL
	public static final int unlimitedCount = 0177777;
	
	/*
	 * ScopeDepth: TYPE = CARDINAL;
	 * allDescendants: ScopeDepth = 177777B;
	 */
	// ScopeDepth <~> CARDINAL
	public static final int allDescendants = 0177777;
	
	/*
	 * ScopeDirection: TYPE = {forward(0), backward(1)};
	 */
	public enum ScopeDirection { forward , backward }
	public static final EnumMaker<ScopeDirection> mkScopeDirection = buildEnum(ScopeDirection.class).get();
	
	/*
	 * Interpretation: TYPE = { interpretationNone(0), boolean(1), cardinal(2),
	 *   longCardinal(3), time(4), integer(5), longInteger(6), string(7) };
	 */
	public enum Interpretation { interpretationNone , bool , cardinal , longCardinal , time , integer , longInteger , string  }
	public static final EnumMaker<Interpretation> mkInterpretation = buildEnum(Interpretation.class).get();
	
	/*
	 * FilterType: TYPE = {
	 *   -- relations --
	 *   less(0), lessOrEqual(1), equal(2), notEqual(3), greaterOrEqual(4), greater(5),
	 *   -- logical --
	 *   and(6), or(7), not(8),
	 *   -- constants --
	 *   filterNone(9), all(10),
	 *   -- patterns --
	 *   matches(11) };
	 */
	public enum FilterType { less , lessOrEqual , equal , notEqual , greaterOrEqual , greater , and , or , not , none , all , matches }
	public static final EnumMaker<FilterType> mkFilterType = buildEnum(FilterType.class).get();
	
	/*
	 * Filter: TYPE = CHOICE FilterType OF {
	 *   less, lessOrEqual, equal, notEqual, greaterOrEqual, greater =>
	 *      RECORD [attribute: Attribute, interpretation: Interpretation],
	 *   and, or => SEQUENCE OF Filter,
	 *   not => Filter,
	 *   filterNone, all => RECORD [],
	 *   matches => [attribute: Attribute] };
	 * nullFilter: Filter = all [];
	 */
	public static class FilterAttribute extends RECORD {
		public final Attribute attribute = mkMember(Attribute::make);
		public final ENUM<Interpretation> interpration = mkENUM(mkInterpretation);
		
		private FilterAttribute() {}
		public static FilterAttribute make() { return new FilterAttribute(); }
	}
	public static class FilterRecord extends RECORD {
		public final CHOICE<FilterType> value = mkMember(mkFilter);
		
		private FilterRecord() {}
		public static FilterRecord make() { return new FilterRecord(); }
		
		public boolean isNullFilter() {
			return this.value.getChoice() == FilterType.all;
		}
		public FilterRecord asNullFilter() {
			this.value.setChoice(FilterType.all);
			return this;
		}
	}
	public static class FilterSequence extends SEQUENCE<CHOICE<FilterType>> {
		private FilterSequence() { super(mkFilter); }
		public static FilterSequence make() { return new FilterSequence(); }
		
	}
	public static class FilterSequenceRecord extends RECORD {
		public final FilterSequence seq = mkMember(FilterSequence::make);
		
		private FilterSequenceRecord() {}
		public static FilterSequenceRecord make() { return new FilterSequenceRecord(); }
	}
	public static final ChoiceMaker<FilterType> mkFilter = buildChoice(mkFilterType)
			.choice(FilterType.less, FilterAttribute::make)
			.choice(FilterType.lessOrEqual, FilterAttribute::make)
			.choice(FilterType.equal, FilterAttribute::make)
			.choice(FilterType.notEqual, FilterAttribute::make)
			.choice(FilterType.greaterOrEqual, FilterAttribute::make)
			.choice(FilterType.greater, FilterAttribute::make)
			.choice(FilterType.and, FilterSequenceRecord::make)
			.choice(FilterType.or, FilterSequenceRecord::make)
			.choice(FilterType.not, FilterRecord::make)
			.choice(FilterType.none, RECORD::empty)
			.choice(FilterType.all, RECORD::empty)
			.choice(FilterType.matches, Attribute::make)
			.get();
	public static boolean isNullFilter(CHOICE<FilterType> attr) {
		return attr.getChoice() == FilterType.all;
	}
	public static CHOICE<FilterType> asNullFilter(CHOICE<FilterType> attr) {
		attr.setChoice(FilterType.all);
		return attr;
	}
	
	/*
	 * Filing version 5 or 6:
	 * 
	 * ScopeType: TYPE = { count(0), direction(1), filter(2), depth(3) };
	 * Scope: TYPE = CHOICE ScopeType OF {
	 *   count => RECORD [value: ScopeCount],
	 *   depth => RECORD [value: ScopeDepth],
	 *   direction => RECORD [value: ScopeDirection],
	 *   filter => RECORD [value: ScopeFilter] };
	 * ScopeSequence: TYPE = SEQUENCE 4 OF Scope;
	 * 
	 * remarks: according to Filing4 revision comment of 87/03/23  11:19:35,
	 *          ScopeType.depth has code 4 in Filing4, but 3 in Filing5/Filing6,
	 *          so a 'depthFiling4' was added...
	 *          in Filing 4 code 3 has the meaning "ordering", which was dropped and replaced
	 *          by "depth" from Filing5 on...
	 * problem: StarOS does use code 3 with Filing version 4, so versions 4 resp. 5,6 have
	 *          incompatible Scope representations!
	 * So: this is for Filing version 5 or 6, and version 4 has a separate definition!
	 */
	public enum ScopeType { count , direction , filter , depth }
	public static final EnumMaker<ScopeType> mkScopeType = buildEnum(ScopeType.class).get();
	public static class ScopeCountRecord extends RECORD {
		public final CARDINAL value = mkCARDINAL();
		
		private ScopeCountRecord() {}
		public static ScopeCountRecord make() { return new ScopeCountRecord(); }
	}
	public static class ScopeDirectionRecord extends RECORD {
		public final ENUM<ScopeDirection> value = mkENUM(mkScopeDirection);
		
		private ScopeDirectionRecord() {}
		public static ScopeDirectionRecord make() { return new ScopeDirectionRecord(); }
	}
	public static class ScopeDepthRecord extends RECORD {
		public final CARDINAL value = mkCARDINAL();
		
		private ScopeDepthRecord() {}
		public static ScopeDepthRecord make() { return new ScopeDepthRecord(); }
	}
	public static final ChoiceMaker<ScopeType> mkScope = buildChoice(mkScopeType)
			.choice(ScopeType.count, ScopeCountRecord::make)
			.choice(ScopeType.direction, ScopeDirectionRecord::make)
			.choice(ScopeType.filter, FilterRecord::make)
			.choice(ScopeType.depth, ScopeDepthRecord::make)
			.get();
	public static class ScopeSequence extends SEQUENCE<CHOICE<ScopeType>> {
		private ScopeSequence() { super(4, mkScope); }
		public static ScopeSequence make() { return new ScopeSequence(); }
	}
	
	/*
	 * Filing version 4:
	 * assumptions for the 'ordering'-scope based on:
	 * - "Service 8.0 Filing Programmers Manual Nov84"
	 * - and analysis of StarOS Courier requests
	 * BUT definition resp. Courier representation of ScopeOrdering is highly unclear!
	 * 
	 * ScopeType: TYPE = { count(0), direction(1), filter(2), ordering(3), depth(4) };
	 * Scope: TYPE = CHOICE ScopeType OF {
	 *   count => RECORD [value: ScopeCount],
	 *   depth => RECORD [value: ScopeDepth],
	 *   direction => RECORD [value: ScopeDirection],
	 *   filter => RECORD [value: ScopeFilter],
	 *   ordering => RECORD[value ScopeOrdering] };
	 * ScopeSequence: TYPE = SEQUENCE 4 OF Scope;
	 */
	public enum ScopeType4 { count , direction , filter , ordering , depth }
	public static final EnumMaker<ScopeType4> mkScopeType4 = buildEnum(ScopeType4.class).get();
	public static class ScopeOrdering extends RECORD {
		public final ARRAY<UNSPECIFIED> words = mkARRAY(7, UNSPECIFIED::make); // 7 words are experimentally assumed based find() requests from StarOS
		
		private ScopeOrdering() { }
		public static ScopeOrdering make() { return new ScopeOrdering(); }
	}
	public static final ChoiceMaker<ScopeType4> mkScope4 = buildChoice(mkScopeType4)
			.choice(ScopeType4.count, ScopeCountRecord::make)
			.choice(ScopeType4.direction, ScopeDirectionRecord::make)
			.choice(ScopeType4.filter, FilterRecord::make)
			.choice(ScopeType4.depth, ScopeDepthRecord::make)
			.choice(ScopeType4.ordering, ScopeOrdering::make)
			.get();
	public static class ScopeSequence4 extends SEQUENCE<CHOICE<ScopeType4>> {
		private ScopeSequence4() { super(5, mkScope4); }
		public static ScopeSequence4 make() { return new ScopeSequence4(); }
	}
	
	/*
	 * -- Handles and Authentication --
	 */
	
	/*
	 * Common to all Filing versions
	 * #############################
	 * 
	 * Verifier: TYPE = Authentication.Verifier;
	 * SimpleVerifier: TYPE = Authentication.SimpleVerifier;
	 */
	// Verifier <~> Authentication.Verifier;
	// SimpleVerifier <~> Authentication.SimpleVerifier;
	
	/*
	 * Filing4 / Filing5
	 * #################
	 * 
	 * Credentials: TYPE = Authentication.Credentials;
	 */
	// Credentials <~> Authentication.Credentials;
	
	/*
	 * Filing6
	 * #######
	 * 
	 * ... defined in specific Courier definitions class
	 */
	
	
	/*
	 * Session: TYPE = RECORD [token: UNSPECIFIED2, verifier: Verifier ];
	 */
	public static class Session extends RECORD {
		public final UNSPECIFIED2 token = mkUNSPECIFIED2();
		public final AuthChsCommon.Verifier verifier = mkMember(AuthChsCommon.Verifier::make);
		
		private Session() {}
		public static Session make() { return new Session(); }
	}
	
	/*
	 * Handle: TYPE = UNSPECIFIED2;
	 * nullHandle: Handle = 0;
	 */
	// Handle <~> UNSPECIFIED2;
	public static boolean isNullHandle(UNSPECIFIED2 handle) {
		return handle.get() == 0;
	}
	public static UNSPECIFIED2 asNullHandle(UNSPECIFIED2 handle) {
		handle.set(0);
		return handle;
	}

	
	/*
	 * -- Random Access --
	 */
	
	/*
	 * ByteAddress: TYPE = LONG CARDINAL;
	 * ByteCount: TYPE = LONG CARDINAL;
	 * endOfFile: LONG CARDINAL = 3777777777B; -- logical end of file --
	 * 
	 * 
	 * ByteRange: TYPE = RECORD [ firstByte: ByteAddress, count: ByteCount ];
	 */
	public static class ByteRange extends RECORD {
		public final LONG_CARDINAL firstByte = mkLONG_CARDINAL();
		public final LONG_CARDINAL count = mkLONG_CARDINAL();
		
		private ByteRange() {}
		public static ByteRange make() { return new ByteRange(); }
	}
	
	
	/*
	 * -- REMOTE ERRORS --
	 */
	
	/*
	 * ArgumentProblem: TYPE = {
	 *   illegal(0),
	 *   disallowed(1),
	 *   unreasonable(2),
	 *   unimplemented(3),
	 *   duplicated(4),
	 *   missing(5) };
	 *   
	 * -- problem with an attribute type or value --
	 * AttributeTypeError: ERROR [ problem: ArgumentProblem, type: AttributeType] = 0;
	 * AttributeValueError: ERROR [ problem: ArgumentProblem, type: AttributeType] = 1;
	 * 
	 * -- problem with an control type or value --
	 * ControlTypeError: ERROR [ problem: ArgumentProblem, type: ControlType] = 2;
	 * ControlValueError: ERROR [ problem: ArgumentProblem, type: ControlType] = 3;
	 * 
	 * -- problem with an scope type or value --
	 * ScopeTypeError: ERROR [ problem: ArgumentProblem, type: ScopeType] = 4;
	 * ScopeValueError: ERROR [ problem: ArgumentProblem, type: ScopeType] = 5;
	 */
	public enum ArgumentProblem { illegal , disallowed , unreasonable , unimplemented , duplicated , missing }
	public static final EnumMaker<ArgumentProblem> mkArgumentProblem = buildEnum(ArgumentProblem.class).get();
	
	public static class AttributeTypeErrorRecord extends ErrorRECORD {
		public final ENUM<ArgumentProblem> problem = mkENUM(mkArgumentProblem);
		public final LONG_CARDINAL type = mkLONG_CARDINAL();
		@Override public int getErrorCode() { return 0; }
		public AttributeTypeErrorRecord(ArgumentProblem problem, long type) {
			this.problem.set(problem);
			this.type.set(type);
		}
	}
	public final ERROR<AttributeTypeErrorRecord> AttributeTypeError = mkERROR(AttributeTypeErrorRecord.class);
	
	public static class AttributeValueErrorRecord extends AttributeTypeErrorRecord {
		@Override public int getErrorCode() { return 1; }
		public AttributeValueErrorRecord(ArgumentProblem problem, long type) { super(problem, type); }
	}
	public final ERROR<AttributeValueErrorRecord> AttributeValueError = mkERROR(AttributeValueErrorRecord.class);
	
	public static class ControlTypeErrorRecord extends ErrorRECORD {
		public final ENUM<ArgumentProblem> problem = mkENUM(mkArgumentProblem);
		public final ENUM<ControlType> type = mkENUM(mkControlType);
		@Override public int getErrorCode() { return 2; }
		public ControlTypeErrorRecord(ArgumentProblem problem, ControlType type) {
			this.problem.set(problem);
			this.type.set(type);
		}
	}
	public final ERROR<ControlTypeErrorRecord> ControlTypeError = mkERROR(ControlTypeErrorRecord.class);
	
	public static class ControlValueErrorRecord extends ControlTypeErrorRecord {
		@Override public int getErrorCode() { return 3; }
		public ControlValueErrorRecord(ArgumentProblem problem, ControlType type) { super(problem, type); }
	}
	public final ERROR<ControlValueErrorRecord> ControlValueError = mkERROR(ControlValueErrorRecord.class);
	
	public static class ScopeTypeErrorRecord extends ErrorRECORD {
		public final ENUM<ArgumentProblem> problem = mkENUM(mkArgumentProblem);
		public final ENUM<ScopeType> type = mkENUM(mkScopeType);
		@Override public int getErrorCode() { return 4; }
		public ScopeTypeErrorRecord(ArgumentProblem problem, ScopeType type) {
			this.problem.set(problem);
			this.type.set(type);
		}
	}
	public final ERROR<ScopeTypeErrorRecord> ScopeTypeError = mkERROR(ScopeTypeErrorRecord.class);
	
	public static class ScopeValueErrorRecord extends ScopeTypeErrorRecord {
		@Override public int getErrorCode() { return 5; }
		public ScopeValueErrorRecord(ArgumentProblem problem, ScopeType type) { super(problem, type); }
	}
	public final ERROR<ScopeValueErrorRecord> ScopeValueError = mkERROR(ScopeValueErrorRecord.class);
	
	/*
	 * -- problem in obtaining access to a file --
	 * AccessProblem: TYPE = {
	 *   accessRightsInsufficient(0),
	 *   accessRightsIndeterminate(1),
	 *   fileChanged(2),
	 *   fileDamaged(3),
	 *   fileInUse(4),
	 *   fileNotFound(5),
	 *   fileOpen(6) };
	 * AccessError: ERROR [problem: AccessProblem] = 6;
	 */
	public enum AccessProblem { accessRightsInsufficient , accessRightsIndeterminate , fileChanged , fileDamaged , fileInUse , fileNotFound , fileOpen }
	public static final EnumMaker<AccessProblem> mkAccessProblem = buildEnum(AccessProblem.class).get();
	
	public static class AccessErrorRecord extends ErrorRECORD {
		public final ENUM<AccessProblem> problem = mkENUM(mkAccessProblem);
		@Override public int getErrorCode() { return 6; }
		public AccessErrorRecord(AccessProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<AccessErrorRecord> AccessError = mkERROR(AccessErrorRecord.class);
	
	/*
	 * -- problem with a credentials or verifier --
	 * AuthenticationError: ERROR [problem: Authentication.Problem] = 7;
	 */
	// remark: no differentiation between Filing4/5 and Filing6
	public static class AuthenticationErrorRecord extends ErrorRECORD {
		public final ENUM<AuthChsCommon.Problem> problem = mkENUM(AuthChsCommon.mkProblem);
		@Override public int getErrorCode() { return 7; }
		public AuthenticationErrorRecord(AuthChsCommon.Problem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<AuthenticationErrorRecord> AuthenticationError = mkERROR(AuthenticationErrorRecord.class);
	
	/*
	 * -- problem with a BDT --
	 * ConnectionProblem: TYPE = {
	 *   -- communication problems --
	 *   noRoute(0), noResponse(1), transmissionHardware(2), transportTimeout(3),
	 *   -- resource problems --
	 *   tooManyLocalConnections(4), tooManyRemoteConnections(5),
	 *   -- remote program implementation problems --
	 *   missingCourier(6), missingProgram(7), missingProcedure(8), protocolMismatch(9),
	 *   parameterInconsistency(10), invalidMessage(11), returnTimedOut(12),
	 *   -- miscellaneous --
	 *   otherCallProblem(177777B) };
	 * ConnectionError: ERROR [problem: ConnectionProblem] = 8;
	 */
	public enum ConnectionProblem implements CrEnum {
		noRoute(0), noResponse(1), transmissionHardware(2), transportTimeout(3),
		tooManyLocalConnections(4), tooManyRemoteConnections(5),
		missingCourier(6), missingProgram(7), missingProcedure(8), protocolMismatch(9),
		parameterInconsistency(10), invalidMessage(11), returnTimedOut(12),
		otherCallProblem(0177777);
		
		final int wireValue;
		private ConnectionProblem(int wireValue) { this.wireValue = wireValue; }
		@Override public int getWireValue() { return this.wireValue; }
	}
	public static final EnumMaker<ConnectionProblem> mkConnectionProblem = buildEnum(ConnectionProblem.class).get();
	
	public static class ConnectionErrorRecord extends ErrorRECORD {
		public final ENUM<ConnectionProblem> problem = mkENUM(mkConnectionProblem);
		@Override public int getErrorCode() { return 8; }
		public ConnectionErrorRecord(ConnectionProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<ConnectionErrorRecord> ConnectionError = mkERROR(ConnectionErrorRecord.class);
	
	/*
	 * -- problem with file handle --
	 * HandleProblem: TYPE = { invalid(0), nullDisallowed(1), directoryRequired(2) };
	 * HandleError: ERROR [problem: HandleProblem] = 9;
	 */
	public enum HandleProblem { invalid , nullDisallowed , directoryRequired }
	public static final EnumMaker<HandleProblem> mkHandleProblem = buildEnum(HandleProblem.class).get();
	
	public static class HandleErrorRecord extends ErrorRECORD {
		public final ENUM<HandleProblem> problem = mkENUM(mkHandleProblem);
		@Override public int getErrorCode() { return 9; }
		public HandleErrorRecord(HandleProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<HandleErrorRecord> HandleError = mkERROR(HandleErrorRecord.class);
	
	/*
	 * -- problem during insertion in directory or changing attributes --
	 * InsertionProblem: TYPE = { positionUnavailable(0), fileNotUnique(1), loopInHierarchy(2) };
	 * InsertionError: ERROR [problem: InsertionProblem] = 10;
	 */
	public enum InsertionProblem { positionUnavailable , fileNotUnique , loopInHierarchy }
	public static final EnumMaker<InsertionProblem> mkInsertionProblem = buildEnum(InsertionProblem.class).get();
	
	public static class InsertionErrorRecord extends ErrorRECORD {
		public final ENUM<InsertionProblem> problem = mkENUM(mkInsertionProblem);
		@Override public int getErrorCode() { return 10; }
		public InsertionErrorRecord(InsertionProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<InsertionErrorRecord> InsertionError = mkERROR(InsertionErrorRecord.class);
	
	/*
	 * -- problem during random access operation --
	 * RangeError: ERROR [problem: ArgumentProblem] = 16;
	 */
	public static class RangeErrorRecord extends ErrorRECORD {
		public final ENUM<ArgumentProblem> problem = mkENUM(mkArgumentProblem);
		@Override public int getErrorCode() { return 16; }
		public RangeErrorRecord(ArgumentProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<RangeErrorRecord> RangeError = mkERROR(RangeErrorRecord.class);
	
	/*
	 * -- problem during logon or logoff --
	 * ServiceProblem: TYPE = { cannotAuthenticate(0), serviceFull(1), serviceUnavailable(2), sessionInUse(3) };
	 * ServiceError: ERROR [problem: ServiceProblem] = 11;
	 */
	public enum ServiceProblem { cannotAuthenticate , serviceFull , serviceUnavailable , sessionInUse }
	public static final EnumMaker<ServiceProblem> mkServiceProblem = buildEnum(ServiceProblem.class).get();
	
	public static class ServiceErrorRecord extends ErrorRECORD {
		public final ENUM<ServiceProblem> problem = mkENUM(mkServiceProblem);
		@Override public int getErrorCode() { return 11; }
		public ServiceErrorRecord(ServiceProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<ServiceErrorRecord> ServiceError = mkERROR(ServiceErrorRecord.class);
	
	/*
	 * -- problem with a session --
	 * SessionProblem: TYPE = { tokenInvalid(0) };
	 * SessionError: ERROR [problem: SessionProblem ] = 12;
	 */
	public enum SessionProblem { tokenInvalid }
	public static final EnumMaker<SessionProblem> mkSessionProblem = buildEnum(SessionProblem.class).get();
	
	public static class SessionErrorRecord extends ErrorRECORD {
		public final ENUM<SessionProblem> problem = mkENUM(mkSessionProblem);
		@Override public int getErrorCode() { return 12; }
		public SessionErrorRecord(SessionProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<SessionErrorRecord> SessionError = mkERROR(SessionErrorRecord.class);
	
	/*
	 * -- problem obtaining space for file contents or attributes --
	 * SpaceProblem: TYPE = { allocationExceeded(0), attributeAreaFull(1), mediumFull(2) };
	 * SpaceError: ERROR [problem: SpaceProblem ] = 13;
	 */
	public enum SpaceProblem { allocationExceeded , attributeAreaFull , mediumFull }
	public static final EnumMaker<SpaceProblem> mkSpaceProblem = buildEnum(SpaceProblem.class).get();
	
	public static class SpaceErrorRecord extends ErrorRECORD {
		public final ENUM<SpaceProblem> problem = mkENUM(mkSpaceProblem);
		@Override public int getErrorCode() { return 13; }
		public SpaceErrorRecord(SpaceProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<SpaceErrorRecord> SpaceError = mkERROR(SpaceErrorRecord.class);
	
	/*
	 * -- problem during BDT --
	 * TransferProblem: TYPE = { aborted(0), checksumIncorrect(1), formatIncorrect(2), noRendezvous(3), wrongDirection(4) };
	 * TransferError: ERROR [problem: TransferProblem ] = 14;
	 */
	public enum TransferProblem { aborted , checksumIncorrect , formatIncorrect , noRendezvous , wrongDirection }
	public static final EnumMaker<TransferProblem> mkTransferProblem = buildEnum(TransferProblem.class).get();
	
	public static class TransferErrorRecord extends ErrorRECORD {
		public final ENUM<TransferProblem> problem = mkENUM(mkTransferProblem);
		@Override public int getErrorCode() { return 14; }
		public TransferErrorRecord(TransferProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<TransferErrorRecord> TransferError = mkERROR(TransferErrorRecord.class);
	
	/*
	 * -- some undefined (and implementation-dependent) problem occurred --
	 * UndefinedProblem: TYPE = CARDINAL;
	 * UndefinedError: ERROR [problem: UndefinedProblem ] = 15;
	 */
	public static class UndefinedErrorRecord extends ErrorRECORD {
		public final CARDINAL problem = mkCARDINAL();
		@Override public int getErrorCode() { return 15; }
		public UndefinedErrorRecord(int problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<UndefinedErrorRecord> UndefinedError = mkERROR(UndefinedErrorRecord.class);
	
	
	/*
	 * -- BULK DATA FORMATS --
	 */
	
	/* support for SerializedTree for reducing memory consumption */
	
	/** 
	 * Specialized iWireStream with lifecycle callbacks
	 * during (de)serializing tree nodes
	 */
	public interface iWireStreamForSerializedTree extends iWireStream {
		
//		/** Invoked before processing the complete tree. */
//		void begin();
//		
//		/** Invoked after the complete tree was processed (or aborted). */
//		void end();
		
		/**
		 * Invoked before the current node is (de)serialized.
		 * @param node the current tree element.
		 */
		void beginSerializedTree(SerializedTree node);
		
		/**
		 * Invoked before the content of the current node was transferred.
		 * @param node the current tree element.
		 */
		void beforeContent(SerializedTree node);
		
		/**
		 * Invoked after the content of the current node was transferred.
		 * @param node the current tree element.
		 */
		void afterContent(SerializedTree node);
		
		/**
		 * Invoked after processing all children of the current node
		 * @param node the current tree element.
		 */
		void endSerializedTree(SerializedTree node);
		
		/**
		 * Invoked in case of an error raised while loading this node,
		 * for freeing resources.
		 * @param node the current tree element.
		 */
		void aborted(SerializedTree node);
	}
	
	/**
	 * Definition of a metho ocject that can be given when constructing
	 * a serialized file instance, allowing to wrap the plain courier wire-stream
	 * with a wrapper, for example {@code iWireStreamForSerializedTree} which
	 * can receive the lifecycle events during (de)serialization of the tree.
	 */
	@FunctionalInterface
	public interface WireStreamMapper {
		iWireStream map(iWireStream stream);
	}
	
	/*
	 * -- Serialized File Format, used in Serialize and Deserialize --
	 * 
	 * SerializedTree: TYPE = RECORD [
	 *   attributes: AttributeSequence,
	 *   content: RECORD [ data: BulkData.StreamOfUnspecified,
	 *                     lastByteSignificant: BOOLEAN ],
	 *   children: SEQUENCE OF SerializedTree ];
	 */
	public static class Content extends RECORD {
		public final StreamOfUnspecified data = mkMember(StreamOfUnspecified::make);
		public final BOOLEAN lastByteSignificant = mkBOOLEAN();
		
		private Content() {}
		public static Content make() { return new Content(); }
	}
	public static class SerializedTree extends RECORD {
		public final AttributeSequence attributes = mkMember(AttributeSequence::make);
		public final Content content = mkRECORD(Content::make);
		public final SEQUENCE<SerializedTree> children = mkSEQUENCE(SerializedTree::make);
		
		public Object clientData = null;
		
		@Override
		public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
			if (!(ws instanceof iWireStreamForSerializedTree)) {
				super.serialize(ws);
				return;
			}
			
			iWireStreamForSerializedTree stream = (iWireStreamForSerializedTree)ws;
			
			boolean done = false; 
			try {
				stream.beginSerializedTree(this);
				this.attributes.serialize(stream);
				stream.beforeContent(this);
				this.content.serialize(stream);
				stream.afterContent(this);
				this.children.serialize(stream);
				done = true;
				stream.endSerializedTree(this);
			} finally {
				if (!done) { stream.aborted(this); }
			}
		}
		
		@Override
		public void deserialize(iWireStream ws) throws EndOfMessageException {
			if (!(ws instanceof iWireStreamForSerializedTree)) {
				super.deserialize(ws);
				return;
			}
			
			iWireStreamForSerializedTree stream = (iWireStreamForSerializedTree)ws;
			
			boolean done = false; 
			try {
				stream.beginSerializedTree(this);
				this.attributes.deserialize(stream);
				stream.beforeContent(this);
				this.content.deserialize(stream);
				stream.afterContent(this);
				this.children.deserialize(stream);
				done = true;
				stream.endSerializedTree(this);
			} finally {
				if (!done) { stream.aborted(this); }
			}
		}
		
		private SerializedTree() {}
		public static SerializedTree make() { return new SerializedTree(); }
	}
	
	/*
	 * SerializedFile: TYPE = RECORD [ version: LONG CARDINAL, file: SerializedTree ];
	 * currentVersion: LONG CARDINAL = 3;
	 */
	public static class SerializedFile extends RECORD {
		private final LONG_CARDINAL version = mkLONG_CARDINAL();
		public final SerializedTree file = mkRECORD(SerializedTree::make);
		
		private final WireStreamMapper streamMapper;
		
		@Override
		public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
			if (this.streamMapper != null) { ws = this.streamMapper.map(ws); }
			super.serialize(ws);
		}
		
		@Override
		public void deserialize(iWireStream ws) throws EndOfMessageException {
			if (this.streamMapper != null) { ws = this.streamMapper.map(ws); }
			super.deserialize(ws);
		}
		
		public SerializedFile(WireStreamMapper mapper, boolean forFiling4) {
			this.streamMapper = mapper;
			this.version.set(forFiling4 ? 2 : 3);
		}
		public SerializedFile make() { return new SerializedFile(null, false); }
	}
	
	/*
	 * -- Attribute Series Format, used in List --
	 * StreamOfAttributeSequence: TYPE = CHOICE OF {
	 *   nextSegment(0) => RECORD [
	 *     segment: SEQUENCE OF AttributeSequence,
	 *     restOfStream: StreamOfAttributeSequence ],
	 *   lastSegment(1) => RECORD [
	 *     segment: SEQUENCE OF AttributeSequence ]
	 * };
	 */
	// used as: StreamOf<AttributeSequence>
	
	/*
	 * -- Line-oriented ASCII text file format, used in file interchange --
	 * AsciiString: TYPE = RECORD [
	 *   lastByteSignificant: BOOLEAN,
	 *   bytes: SEQUENCE OF UNSPECIFIED ];
	 */
	public static class AsciiString extends RECORD {
		public final BOOLEAN lastByteSignificant = mkBOOLEAN();
		public final SEQUENCE<UNSPECIFIED> bytes = mkSEQUENCE(UNSPECIFIED::make);
		
		private AsciiString() {}
		public static AsciiString make() { return new AsciiString(); }
	}
	
	/*
	 * StreamOfAsciiText: TYPE = CHOICE LineType OF {
	 *   nextLine => RECORD [
	 *     line: AsciiString,
	 *     restOfText: StreamOfAsciiText ],
	 *   lastLine => AsciiString};
	 */
	// used as: StreamOf<AsciiString>
	
	
	/*
	 * -- INTERPRETED ATTRIBUTE DEFINITIONS --
	 * 
	 * -- attributes --
	 */
	
	
	/*
	 * accessList: AttributeType = 19;
	 * AccessEntry: TYPE = RECORD [key: Clearinghouse.Name, access: AccessSequence];
	 * AccessList: TYPE = RECORD [entries: SEQUENCE OF AccessEntry, defaulted: BOOLEAN];
	 */
	public static class AccessEntry extends RECORD {
		public final AuthChsCommon.Name key = mkMember(AuthChsCommon.Name::make);
		public final AccessSequence access = mkMember(AccessSequence::makeAny);
		
		private AccessEntry() {}
		public static AccessEntry make() { return new AccessEntry(); }
		
		public void forFiling4() { this.access.switchToFiling4(); }
	}
	public static class AccessList extends RECORD {
		public final SEQUENCE<AccessEntry> entries = mkSEQUENCE(AccessEntry::make);
		public final BOOLEAN defaulted = mkBOOLEAN();
		
		private AccessList() {}
		public static AccessList make() { return new AccessList(); }
	}
	public static final int atAccessList = 19;
	
	/*
	 * checksum: AttributeType = 0;
	 * Checksum: TYPE = CARDINAL;
	 * unknownChecksum: Checksum = 177777B;
	 */
	public static final int atChecksum = 0;
	public static final int unknownChecksum = 0177777;
	
	/*
	 * childrenUniquelyNamed: AttributeType = 1;
	 * ChildrenUniquelyNamed: TYPE = BOOLEAN;
	 */
	public static final int atChildrenUniquelyNamed = 1;
	
	/*
	 * createdBy: AttributeType = 2;
	 * CreatedBy: TYPE = User;
	 */
	public static final int atCreatedBy = 2;
	
	/*
	 * createdOn: AttributeType = 3;
	 * CreatedOn: TYPE = Time;
	 */
	public static final int atCreatedOn = 3;
	
	/*
	 * dataSize: AttributeType = 16;
	 * DataSize: TYPE = LONG CARDINAL;
	 */
	public static final int atDataSize = 16;
	
	/*
	 * defaultAccessList: AttributeType = 20;
	 * DefaultAccessList: TYPE = AccessList;
	 * 
	 * (only from Filing5 upwards?)
	 */
	public static final int atDefaultAccessList = 20;
	
	/*
	 * fileID: AttributeType = 4;
	 * FileID: TYPE = ARRAY 5 OF UNSPECIFIED;
	 * nullFileID: FileID = [0,0,0,0,0];
	 */
	public static final int atFileID = 4;
	public static final long DRAWER_PARENT_ROOTDIR_FILEID = 0x7FFF_FFFF_FFFF_0001L; // alternative FileID for file drawer parent, see AttributeUtils
	public static class FileID extends ARRAY<UNSPECIFIED> {
		private FileID() { super(5, UNSPECIFIED::make); }
		public static FileID make() { return new FileID(); }
		
		public boolean isNullFileID() {
			return this.get(0).get() == 0
				&& this.get(1).get() == 0
				&& this.get(2).get() == 0
				&& this.get(3).get() == 0
				&& this.get(4).get() == 0;
		}
		
		public FileID asNullFileID() {
			this.get(0).set(0);
			this.get(1).set(0);
			this.get(2).set(0);
			this.get(3).set(0);
			this.get(4).set(0);
			return this;
		}
		
		public FileID set(long internalId) {
			long id = (internalId == FsConstants.rootFileID) ? 0 : internalId;
			int w0 = (int)((id >>> 48) & 0xFFFFL);
			int w1 = (int)((id >>> 32) & 0xFFFFL);
			int w3 = (int)((id >>> 16) & 0xFFFFL);
			int w4 = (int)(id & 0xFFFFL);
			this.get(0).set(w0);
			this.get(1).set(w1);
			this.get(2).set(0);
			this.get(3).set(w3);
			this.get(4).set(w4);
			return this;
		}
		
		public long get() {
			if (this.get(2).get() == 0) {
				long w0 = this.get(0).get();
				long w1 = this.get(1).get();
				long w3 = this.get(3).get();
				long w4 = this.get(4).get();
				
				long externalId = (long)((w0 << 48) | (w1 << 32) | (w3 << 16) | w4);
				return (externalId == 0 || externalId == DRAWER_PARENT_ROOTDIR_FILEID) ? FsConstants.rootFileID : externalId;
			} else {
				new HandleErrorRecord(HandleProblem.invalid).raise();
				return -1; // keep the compiler happy
			}
		}
	}
	
	/*
	 * isDirectory: AttributeType = 5;
	 * IsDirectory: TYPE = BOOLEAN;
	 */
	public static final int atIsDirectory = 5;
	
	/*
	 * isTemporary: AttributeType = 6;
	 * IsTemporary: TYPE = BOOLEAN;
	 */
	public static final int atIstemporary = 6;
	
	/*
	 * modifiedBy: AttributeType = 7;
	 * ModifiedBy: TYPE = User;
	 */
	public static final int atModifiedBy = 7;
	
	/*
	 * modifiedOn: AttributeType = 8;
	 * ModifiedOn: TYPE = Time;
	 */
	public static final int atModifiedOn = 8;
	
	/*
	 * name: AttributeType = 9;	-- name relative to parent --
	 * Name: TYPE = STRING;	-- must not exceed 100 bytes --
	 */
	public static final int atName = 9;
	
	/*
	 * numberOfChildren: AttributeType = 10;
	 * NumberOfChildren: TYPE = CARDINAL;
	 */
	public static final int atNumberOfChildren = 10;
	
	/*
	 * ordering: AttributeType = 11;
	 * Ordering: TYPE = RECORD [key: AttributeType, ascending: BOOLEAN, interpretation: Interpretation];
	 * 
	 * defaultOrdering: Ordering = [key: name, ascending: TRUE, interpretation: string];
	 * byAscendingPosition: Ordering = [key: position, ascending: TRUE, interpretation: interpretationNone];
	 * byDescendingPosition: Ordering = [key: position, ascending: FALSE, interpretation: interpretationNone];
	 */
	public static final int atOrdering = 11;
	public static class Ordering extends RECORD {
		public final LONG_CARDINAL key = mkLONG_CARDINAL();
		public final BOOLEAN ascending = mkBOOLEAN();
		public final ENUM<Interpretation> interpretation = mkENUM(mkInterpretation);
		
		private Ordering() {}
		public static Ordering make() {
			Ordering o = new Ordering();
			o.key.set(atName);
			o.ascending.set(true);
			o.interpretation.set(Interpretation.string);
			return o;
		}
		
		public boolean isDefaultOrdering() {
			return this.key.get() == atName
				&& this.ascending.get() == true
				&& this.interpretation.get() == Interpretation.string;
		}
		public Ordering asDefaultOrdering() {
			this.key.set(atName);
			this.ascending.set(true);
			this.interpretation.set(Interpretation.string);
			return this;
		}
		
		public boolean isByAscendingPosition() {
			return this.key.get() == atPosition
				&& this.ascending.get() == true
				&& this.interpretation.get() == Interpretation.interpretationNone;
		}
		public Ordering asByAscendingPosition() {
			this.key.set(atPosition);
			this.ascending.set(true);
			this.interpretation.set(Interpretation.interpretationNone);
			return this;
		}
		
		public boolean isByDescendingPosition() {
			return this.key.get() == atPosition
				&& this.ascending.get() == false
				&& this.interpretation.get() == Interpretation.interpretationNone;
		}
		public Ordering asByDescendingPosition() {
			this.key.set(atPosition);
			this.ascending.set(false);
			this.interpretation.set(Interpretation.interpretationNone);
			return this;
		}
	}
	
	/*
	 * parentID: AttributeType = 12;
	 * ParentID: TYPE = FileID;
	 */
	public static final int atParentID = 12;
	
	/*
	 * pathname: AttributeType = 21;
	 * Pathname: TYPE = STRING;
	 */
	public static final int atPathname = 21;
	
	/*
	 * position: AttributeType = 13;
	 * Position: TYPE = SEQUENCE 100 OF UNSPECIFIED;
	 * firstPosition: Position = [0];
	 * lastPosition: Position = [177777B];
	 */
	public static final int atPosition = 13;
	public static class Position extends SEQUENCE<UNSPECIFIED> {
		private Position() { super(100, UNSPECIFIED::make); }
		public static Position make() { return new Position(); }
		
		public boolean isFirstPosition() { return this.size() == 1 && this.get(0).get() == 0; }
		public void asFirstPosition() { this.clear(); this.add().set(0); }
		
		public boolean isLastPosition() { return this.size() == 1 && this.get(0).get() == 0177777; }
		public void asLastPosition() { this.clear(); this.add().set(0177777); }
	}
	
	/*
	 * readBy: AttributeType = 14;
	 * ReadBy: TYPE = User;
	 */
	public static final int atReadBy = 14;
	
	/*
	 * readOn: AttributeType = 15;
	 * ReadOn: TYPE = Time;
	 */
	public static final int atReadOn = 15;
	
	/*
	 * storedSize: AttributeType = 26;
	 * StoredSize: TYPE = LONG CARDINAL;
	 */
	public static final int atStoredSize = 26;
	
	/*
	 * subtreeSize: AttributeType = 27;
	 * SubtreeSize: TYPE = LONG CARDINAL;
	 */
	public static final int atSubtreeSize = 27;
	
	/*
	 * subtreeSizeLimit: AttributeType = 28;
	 * SubtreeSizeLimit: TYPE = LONG CARDINAL;
	 * nullSubtreeSizeLimit: SubtreeSizeLimit = 37777777777B;
	 */
	public static final int atSubtreeSizeLimit = 28;
	public static final int nullSubtreeSizeLimit = (int)0xFFFFFFFF;
	
	/*
	 * type: AttributeType = 17;
	 * Type: TYPE = LONG CARDINAL;
	 */
	public static final int atType = 17;
	
	/*
	 * version: AttributeType = 18;
	 * Version: TYPE = CARDINAL;
	 * lowestVersion: Version = 0;
	 * highestVersion: Version = 177777B;
	 */
	public static final int atVersion = 18;
	public static final int lowestVersion = 0;
	public static final int highestVersion = 0177777;
	
	
	/* *** begin additional file attributes defined in Filing 8.0 Programmer's Manual (pp. 6-3 etc.) *** */
	
	public static final int atService = 22;
	// Mesa :: ServiceRecord: TYPE = RECORD [name: NSName.NameRecord , systemElement : System.NetworkAddress ];
	
	public static final int atBackedUpOn = 23;
	// Mesa :: BackupUpOn: TYPE = Time;
	public static final int BackedUp_Never = 0x7E059280;
	
	public static final int atFiledBy = 24;
	// Mesa :: FiledBy: TYPE = CHSName;
	
	public static final int atFiledOn = 25;
	// Mesa :: FiledOn: TYPE = Time;
	
	/* *** end additional file attributes defined in Filing 8.0 Programmer's Manual *** */
	
	
	/*
	 * -- FILE TYPES --
	 * tUnspecified: Type = 0;
	 * tDirectory: Type = 1;
	 * tText: Type = 2;
	 * tSerialized: Type = 3;
	 * tEmpty: Type = 4;
	 * tAscii: Type = 6;
	 * tAsciiText: Type = 7;
	 */
	public static final int tUnspecified    = 0;
	public static final int tDirectory      = 1;
	public static final int tText           = 2;
	public static final int tSerialized     = 3;
	public static final int tEmpty          = 4; // also (see below): VPMailNote
	public static final int tAscii          = 6;
	public static final int tAsciiText      = 7;

	// from xns/examples/filing/common/filetypes.h
	public static final int tVPMailNote     = 4;    /* VP Mail Note: ASCII */
	public static final int tVPDrawer       = 4098; /* VP File Drawer: image */
	public static final int tFont           = 4290; /* printer fonts: image */
	public static final int tVP             = 4353; /* ViewPoint: image */
	public static final int tInterpress     = 4361; /* VP Interpress master: image */
	public static final int tVPRecordsfile  = 4365; /* VP Records file: image */
	public static final int tVPSpreadsheet  = 4381; /* VP Spreadsheet: image */
	public static final int tVPDictionary   = 4383; /* VP Dictionary: image */
	public static final int tVPApplication  = 4387; /* VP Application: image */
	public static final int tVPApplication2 = 4423; /* VP Application: image */
	public static final int tVPReference    = 4427; /* VP Reference Icon: image */
	public static final int tVPCalendar     = 4436; /* VP Calendar: image */
	public static final int tVPBook         = 4444; /* VP Book: image */
	public static final int tVPCanvas       = 4428; /* VP Canvas: image */
	public static final int t860            = 5120; /* 860 file: image */
	public static final int tVPIcons        = 6010; /* VP Icon file: image */
	
	
	/*
	 * ************ REMOTE PROCEDURES ************
	 */
	
	/*
	 * -- Logging On and Off --
	 */
	
	/*
	 * ** Logon is version specific to Filing4/5 and Filing6
	 */
	
	// special Courier type for getting the remote host-id, but with no effects on the Courier communication
	protected static class RemoteHostId implements iWireData {
		
		private Long hostId = null;
		
		public Long get() { return hostId; }

		@Override
		public void serialize(iWireStream ws) { return; }

		@Override
		public void deserialize(iWireStream ws) { this.hostId = ws.getPeerHostId(); }

		@Override
		public StringBuilder append(StringBuilder to, String indent, String fieldName) { return to; }
		
		private RemoteHostId() {}
		public static RemoteHostId make() { return new RemoteHostId(); }
		
	}
	
	public static class Filing4or5LogonParams extends RECORD {
		public AuthChsCommon.Name service = mkRECORD(AuthChsCommon.Name::make);
		public AuthChsCommon.Credentials credentials = mkRECORD(AuthChsCommon.Credentials::make);
		public AuthChsCommon.Verifier verifier = mkMember(AuthChsCommon.Verifier::make);
		
		// dummy to provide the invokers machineid
		public RemoteHostId remoteHostId = mkMember(RemoteHostId::make);
		
		private Filing4or5LogonParams() {}
		public static Filing4or5LogonParams make() { return new Filing4or5LogonParams(); }
	}
	
	public static class LogonResults extends RECORD {
		public final Session session = mkRECORD(Session::make);
		
		private LogonResults() {}
		public static LogonResults make() { return new LogonResults(); }
	}
	
	/*
	 * Logoff: PROCEDURE [ session: Session ]
	 *   REPORTS [ AuthenticationError, ServiceError, SessionError, UndefinedError ]
	 *   = 1;
	 */
	public static class LogoffOrContinueParams extends RECORD {
		public final Session session = mkRECORD(Session::make);
		
		private LogoffOrContinueParams() {}
		public static LogoffOrContinueParams make() { return new LogoffOrContinueParams(); }
	}
	public final PROC<LogoffOrContinueParams,RECORD> Logoff = mkPROC(
						"Logoff",
						1,
						LogoffOrContinueParams::make,
						RECORD::empty,
						AuthenticationError, ServiceError, SessionError, UndefinedError
						);
	
	/*
	 * Continue: PROCEDURE [ session: Session ]
	 *   RETURNS [ continuance: CARDINAL ]
	 *   REPORTS [ AuthenticationError, SessionError, UndefinedError ]
	 *   = 19;
	 */
	public static class ContinueResults extends RECORD {
		public final CARDINAL continuance = mkCARDINAL();
		
		private ContinueResults() {}
		public static ContinueResults make() { return new ContinueResults(); }
	}
	public final PROC<LogoffOrContinueParams,ContinueResults> Continue = mkPROC(
						"Continue",
						19,
						LogoffOrContinueParams::make,
						ContinueResults::make,
						AuthenticationError, SessionError, UndefinedError
						);
	
	/*
	 * -- Opening and Closing Files --
	 */
	
	/*
	 * Open: PROCEDURE [ attributes: AttributeSequence, directory: Handle,
	 *                   controls: ControlSequence, session: Session ]
	 *   RETURNS [ file: Handle ]
	 *   REPORTS [ AccessError, AttributeTypeError, AttributeValueError,
	 *             AuthenticationError, ControlTypeError, ControlValueError,
	 *             HandleError, SessionError, UndefinedError ]
	 *   = 2;
	 */
	public static class OpenParams extends RECORD {
		public final AttributeSequence attributes = mkRECORD(AttributeSequence::make);
		public final UNSPECIFIED2 directory = mkUNSPECIFIED2();
		public final ControlSequence controls = mkMember(ControlSequence::make);
		public final Session session = mkRECORD(Session::make);
		
		private OpenParams() {}
		public static OpenParams make() { return new OpenParams(); }
	}
	public static class FileHandleRecord extends RECORD {
		public final UNSPECIFIED2 file = mkUNSPECIFIED2();
		
		private FileHandleRecord() {}
		public static FileHandleRecord make() { return new FileHandleRecord(); }
	}
	public final PROC<OpenParams,FileHandleRecord> Open = mkPROC(
						"Open",
						2,
						OpenParams::make,
						FileHandleRecord::make,
						AccessError, AttributeTypeError, AttributeValueError,
						AuthenticationError, ControlTypeError, ControlValueError,
						HandleError, SessionError, UndefinedError
						);
	
	/*
	 * Close: PROCEDURE [ file: Handle, session: Session ]
	 *   REPORTS [ AuthenticationError, HandleError, SessionError, UndefinedError ]
	 *   = 3;
	 */
	public static class FileHandleAndSessionRecord extends RECORD {
		public final UNSPECIFIED2 handle = mkUNSPECIFIED2();
		public final Session session = mkRECORD(Session::make);
		
		private FileHandleAndSessionRecord() {}
		public static FileHandleAndSessionRecord make() { return new FileHandleAndSessionRecord(); }
	}
	public final PROC<FileHandleAndSessionRecord,RECORD> Close = mkPROC(
						"Close",
						3,
						FileHandleAndSessionRecord::make,
						RECORD::empty,
						AuthenticationError, HandleError, SessionError, UndefinedError
						);
	
	
	/*
	 * -- Creating and Deleting Files --
	 */
	
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
	public static class CreateParams extends RECORD {
		public final UNSPECIFIED2 directory = mkUNSPECIFIED2();
		public final AttributeSequence attributes = mkRECORD(AttributeSequence::make);
		public final ControlSequence controls = mkMember(ControlSequence::make);
		public final Session session = mkRECORD(Session::make);
		
		private CreateParams() {}
		public static CreateParams make() { return new CreateParams(); }
	}
	public final PROC<CreateParams,FileHandleRecord> Create = mkPROC(
						"Create",
						4,
						CreateParams::make,
						FileHandleRecord::make,
						AccessError, AttributeTypeError, AttributeValueError,
						AuthenticationError, ControlTypeError, ControlValueError,
						HandleError, InsertionError, SessionError, SpaceError,
						UndefinedError
						);
	
	/*
	 * Delete: PROCEDURE [ file: Handle, session: Session ]
	 *   REPORTS [ AccessError, AuthenticationError, HandleError, SessionError, UndefinedError ]
	 *   = 5;
	 */
	public final PROC<FileHandleAndSessionRecord,RECORD> Delete = mkPROC(
						"Delete",
						5,
						FileHandleAndSessionRecord::make,
						RECORD::empty,
						AccessError, AuthenticationError, HandleError, SessionError, UndefinedError
						);
	
	/*
	 * -- Getting and Changing Controls (transient) --
	 */
	
	/*
	 * GetControls: PROCEDURE [ file: Handle, types: ControlTypeSequence,
	 *                          session: Session ]
	 *   RETURNS [ controls: ControlSequence ]
	 *   REPORTS [ AccessError, AuthenticationError, ControlTypeError,
	 *             HandleError, SessionError, UndefinedError ]
	 *   = 6;
	 */
	public static class GetControlsParams extends RECORD {
		public final UNSPECIFIED2 handle = mkUNSPECIFIED2();
		public final ControlTypeSequence types = mkMember(ControlTypeSequence::make);
		public final Session session = mkRECORD(Session::make);
		
		private GetControlsParams() {}
		public static GetControlsParams make() { return new GetControlsParams(); }
	}
	public static class GetControlsResults extends RECORD {
		public final ControlSequence controls = mkMember(ControlSequence::make);
		
		private GetControlsResults() {}
		public static GetControlsResults make() { return new GetControlsResults(); }
	}
	public final PROC<GetControlsParams,GetControlsResults> GetControls = mkPROC(
						"GetControls",
						6,
						GetControlsParams::make,
						GetControlsResults::make,
						AccessError, AuthenticationError, ControlTypeError,
						HandleError, SessionError, UndefinedError
						);
	
	/*
	 * ChangeControls: PROCEDURE [ file: Handle, controls: ControlSequence,
	 *                             session: Session ]
	 *   REPORTS [ AccessError, AuthenticationError, ControlTypeError, ControlValueError,
	 *             HandleError, SessionError, UndefinedError ]
	 *   = 7;
	 */
	public final static class ChangeControlsParams extends RECORD {
		public final UNSPECIFIED2 handle = mkUNSPECIFIED2();
		public final ControlSequence controls = mkMember(ControlSequence::make);
		public final Session session = mkRECORD(Session::make);
		
		private ChangeControlsParams() {}
		public static ChangeControlsParams make() { return new ChangeControlsParams(); }
	}
	public final PROC<ChangeControlsParams,RECORD> ChangeControls = mkPROC(
						"ChangeControls",
						7,
						ChangeControlsParams::make,
						RECORD::empty,
						AccessError, AuthenticationError, ControlTypeError, ControlValueError,
						HandleError, SessionError, UndefinedError
						);
	
	/*
	 * -- Getting and Changing Attributes (permanent) --
	 */
	
	/*
	 * GetAttributes: PROCEDURE [ file: Handle, types: AttributeTypeSequence,
	 *                            session: Session ]
	 *   RETURNS [ attributes: AttributeSequence ]
	 *   REPORTS [ AccessError, AttributeTypeError, AuthenticationError,
	 *             HandleError, SessionError, UndefinedError ]
	 *   = 8;
	 */
	public static class GetAttributesParams extends RECORD {
		public final UNSPECIFIED2 handle = mkUNSPECIFIED2();
		public final AttributeTypeSequence types = mkMember(AttributeTypeSequence::make);
		public final Session session = mkRECORD(Session::make);
		
		private GetAttributesParams() {}
		public static GetAttributesParams make() { return new GetAttributesParams(); }
	}
	public static class GetAttributesResults extends RECORD {
		public final AttributeSequence attributes = mkMember(AttributeSequence::make);
		
		private GetAttributesResults() {}
		public static GetAttributesResults make() { return new GetAttributesResults(); }
	}
	public final PROC<GetAttributesParams,GetAttributesResults> GetAttributes = mkPROC(
						"GetAttributes",
						8,
						GetAttributesParams::make,
						GetAttributesResults::make,
						AccessError, AttributeTypeError, AuthenticationError,
						HandleError, SessionError, UndefinedError
						);
	
	/*
	 * ChangeAttributes: PROCEDURE [ file: Handle, attributes: AttributeSequence,
	 *                               session: Session ]
	 *   REPORTS [ AccessError, AttributeTypeError, AttributeValueError,
	 *             AuthenticationError, HandleError, InsertionError,
	 *             SessionError, SpaceError, UndefinedError ]
	 *   = 9;
	 */
	public static class ChangeAttributesParams extends RECORD {
		public final UNSPECIFIED2 handle = mkUNSPECIFIED2();
		public final AttributeSequence attributes = mkMember(AttributeSequence::make);
		public final Session session = mkRECORD(Session::make);
		
		private ChangeAttributesParams() {}
		public static ChangeAttributesParams make() { return new ChangeAttributesParams(); }
	}
	public final PROC<ChangeAttributesParams,RECORD> ChangeAttributes = mkPROC(
						"ChangeAttributes",
						9,
						ChangeAttributesParams::make,
						RECORD::empty,
						AccessError, AttributeTypeError, AttributeValueError,
						AuthenticationError, HandleError, InsertionError,
						SessionError, SpaceError, UndefinedError
						);
	
	/*
	 * UnifyAccessLists: PROCEDURE [ directory: Handle, session: Session ]
	 *   REPORTS [ AccessError, AuthenticationError, HandleError, SessionError,
	 *             UndefinedError ]
	 *   = 20;
	 */
	public final PROC<FileHandleAndSessionRecord,RECORD> UnifyAccessLists = mkPROC(
						"UnifyAccessLists",
						20,
						FileHandleAndSessionRecord::make,
						RECORD::empty,
						AccessError, AuthenticationError, HandleError, SessionError,
						UndefinedError
						);
	
	
	/*
	 * -- Copying and Moving Files --
	 */
	
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
	public static class CopyParams extends RECORD {
		public final UNSPECIFIED2 handle = mkUNSPECIFIED2();
		public final UNSPECIFIED2 destinationDirectory = mkUNSPECIFIED2();
		public final AttributeSequence attributes = mkMember(AttributeSequence::make);
		public final ControlSequence controls = mkMember(ControlSequence::make);
		public final Session session = mkRECORD(Session::make);
		
		private CopyParams() {}
		public static CopyParams make() { return new CopyParams(); }
	}
	public static class CopyResults extends RECORD {
		public final UNSPECIFIED2 newHandle = mkUNSPECIFIED2();

		private CopyResults() {}
		public static CopyResults make() { return new CopyResults(); }
	}
	public final PROC<CopyParams,CopyResults> Copy = mkPROC(
						"Copy",
						10,
						CopyParams::make,
						CopyResults::make,
						AccessError, AttributeTypeError, AttributeValueError,
						AuthenticationError, ControlTypeError, ControlValueError,
						HandleError, InsertionError, SessionError, SpaceError,
						UndefinedError
						);
	
	/*
	 * Move: PROCEDURE [ file, destinationDirectory: Handle ,
	 *                   attributes: AttributeSequence, session: Session ]
	 *   REPORTS [ AccessError, AttributeTypeError, AttributeValueError,
	 *             AuthenticationError, HandleError, InsertionError,
	 *             SessionError, SpaceError, UndefinedError ]
	 *   = 11;
	 */
	public static class MoveParams extends RECORD {
		public final UNSPECIFIED2 handle = mkUNSPECIFIED2();
		public final UNSPECIFIED2 destinationDirectory = mkUNSPECIFIED2();
		public final AttributeSequence attributes = mkMember(AttributeSequence::make);
		public final Session session = mkRECORD(Session::make);
		
		private MoveParams() {}
		public static MoveParams make() { return new MoveParams(); }
	}
	public final PROC<MoveParams,RECORD> Move = mkPROC(
						"Move",
						11,
						MoveParams::make,
						RECORD::empty,
						AccessError, AttributeTypeError, AttributeValueError,
						AuthenticationError, HandleError, InsertionError,
						SessionError, SpaceError, UndefinedError
						);
	
	
	/*
	 * -- Transfering Bulk Data (File Content) --
	 */
	
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
	public static class StoreParams extends RECORD {
		public final UNSPECIFIED2 directory = mkUNSPECIFIED2();
		public final AttributeSequence attributes = mkMember(AttributeSequence::make);
		public final ControlSequence controls = mkMember(ControlSequence::make);
		public final BulkData1.Source content = mkRECORD(BulkData1.Source::make);
		public final Session session = mkRECORD(Session::make);
		
		private StoreParams() {}
		public static StoreParams make() { return new StoreParams(); }
	}
	public final PROC<StoreParams,FileHandleRecord> Store = mkPROC(
						"Store",
						12,
						StoreParams::make,
						FileHandleRecord::make,
						AccessError, AttributeTypeError, AttributeValueError,
						AuthenticationError, ConnectionError, ControlTypeError,
						ControlValueError, HandleError, InsertionError,	SessionError,
						SpaceError, TransferError, UndefinedError
						);
	
	/*
	 * Retrieve: PROCEDURE [ file: Handle, content: BulkData.Sink, session: Session ]
	 *   REPORTS [ AccessError, AuthenticationError, ConnectionError,
	 *             HandleError, SessionError, TransferError, UndefinedError ]
	 *   = 13;
	 */
	public static class RetrieveParams extends RECORD {
		public final UNSPECIFIED2 file = mkUNSPECIFIED2();
		public final BulkData1.Sink content = mkRECORD(BulkData1.Sink::make);
		public final Session session = mkRECORD(Session::make);
		
		private RetrieveParams() {}
		public static RetrieveParams make() { return new RetrieveParams(); }
	}
	public final PROC<RetrieveParams,RECORD> Retrieve = mkPROC(
						"Retrieve",
						13,
						RetrieveParams::make,
						RECORD::empty,
						AccessError, AuthenticationError, ConnectionError,
						HandleError, SessionError, TransferError, UndefinedError
						);
	
	/*
	 * Replace: PROCEDURE [ file: Handle,  attributes: AttributeSequence,
	 *                      content: BulkData.Source, session: Session ]
	 *   REPORTS [ AccessError, AttributeTypeError, AttributeValueError,
	 *             AuthenticationError, ConnectionError, HandleError,
	 *             SessionError, SpaceError, TransferError, UndefinedError ]
	 *   = 14;
	 */
	public static class ReplaceParams extends RECORD {
		public final UNSPECIFIED2 file = mkUNSPECIFIED2();
		public final AttributeSequence attributes = mkMember(AttributeSequence::make);
		public final BulkData1.Source content = mkRECORD(BulkData1.Source::make);
		public final Session session = mkRECORD(Session::make);
		
		private ReplaceParams() {}
		public static ReplaceParams make() { return new ReplaceParams(); }
	}
	public final PROC<ReplaceParams,RECORD> Replace = mkPROC(
						"Replace",
						14,
						ReplaceParams::make,
						RECORD::empty,
						AccessError, AttributeTypeError, AttributeValueError,
						AuthenticationError, ConnectionError, HandleError,
						SessionError, SpaceError, TransferError, UndefinedError
						);
	
	
	/*
	 * -- Transferring Bulk Data (Serialized Files) --
	 */
	
	/*
	 * Serialize: PROCEDURE [ file: Handle, serializedFile: BulkData.Sink,
	 *                        session: Session ]
	 *   REPORTS [ AccessError, AuthenticationError, ConnectionError,
	 *             HandleError, SessionError, TransferError, UndefinedError ]
	 *   = 15;
	 */
	public static class SerializeParams extends RECORD {
		public final UNSPECIFIED2 file = mkUNSPECIFIED2();
		public final BulkData1.Sink serializedFile = mkRECORD(BulkData1.Sink::make);
		public final Session session = mkRECORD(Session::make);
		
		private SerializeParams() {}
		public static SerializeParams make() { return new SerializeParams(); }
	}
	public final PROC<SerializeParams,RECORD> Serialize = mkPROC(
						"Serialize",
						15,
						SerializeParams::make,
						RECORD::empty,
						AccessError, AuthenticationError, ConnectionError,
						HandleError, SessionError, TransferError, UndefinedError
						);
	
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
	public static class DeserializeParams extends RECORD {
		public final UNSPECIFIED2 directory = mkUNSPECIFIED2();
		public final AttributeSequence attributes = mkMember(AttributeSequence::make);
		public final ControlSequence controls = mkMember(ControlSequence::make);
		public final BulkData1.Source serializedFile = mkRECORD(BulkData1.Source::make);
		public final Session session = mkRECORD(Session::make);
		
		private DeserializeParams() {}
		public static DeserializeParams make() { return new DeserializeParams(); }
	}
	public final PROC<DeserializeParams,FileHandleRecord> Deserialize = mkPROC(
						"Deserialize",
						16,
						DeserializeParams::make,
						FileHandleRecord::make,
						AccessError, AttributeTypeError, AttributeValueError,
						AuthenticationError, ConnectionError, ControlTypeError,
						ControlValueError, HandleError, InsertionError,
						SessionError, SpaceError, TransferError, UndefinedError
						);
	
	
	/*
	 * -- Random Access to File Data --
	 */
	
	/*
	 * RetrieveBytes: PROCEDURE [ file: Handle, range: ByteRange,
	 *                            sink: BulkData.Sink, session: Session ]
	 *   REPORTS [ AccessError, HandleError, RangeError, SessionError,
	 *             UndefinedError ]
	 *   = 22;
	 */
	public static class RetrieveBytesParams extends RECORD {
		public final UNSPECIFIED2 file = mkUNSPECIFIED2();
		public final ByteRange range = mkRECORD(ByteRange::make);
		public final BulkData1.Sink sink = mkRECORD(BulkData1.Sink::make);
		public final Session session = mkRECORD(Session::make);
		
		private RetrieveBytesParams() {}
		public static RetrieveBytesParams make() { return new RetrieveBytesParams(); }
	}
	public final PROC<RetrieveBytesParams,RECORD> RetrieveBytes = mkPROC(
						"RetrieveBytes",
						22,
						RetrieveBytesParams::make,
						RECORD::empty,
						AccessError, HandleError, RangeError, SessionError,
						UndefinedError
						);
	
	/*
	 * ReplaceBytes: PROCEDURE [ file: Handle, range: ByteRange,
	 *                           source: BulkData.Source, session: Session ]
	 *   REPORTS [ AccessError, HandleError, RangeError, SessionError,
	 *             SpaceError, UndefinedError ]
	 *   = 23;
	 */
	public static class ReplaceBytesParams extends RECORD {
		public final UNSPECIFIED2 file = mkUNSPECIFIED2();
		public final ByteRange range = mkRECORD(ByteRange::make);
		public final BulkData1.Source source = mkRECORD(BulkData1.Source::make);
		public final Session session = mkRECORD(Session::make);
		
		private ReplaceBytesParams() {}
		public static ReplaceBytesParams make() { return new ReplaceBytesParams(); }
	}
	public final PROC<ReplaceBytesParams,RECORD> ReplaceBytes = mkPROC(
						"ReplaceBytes",
						23,
						ReplaceBytesParams::make,
						RECORD::empty,
						AccessError, HandleError, RangeError, SessionError,
						SpaceError, UndefinedError
						);
	
	
	/*
	 * -- Locating and Listing Files in a Directory --
	 * 
	 * due to incompatible definition of the scope (version 4 vs. version 5/6), different
	 * versions of find() and list() must be given in the version specific subclasses
	 * 
	 * the version specific parameter structures are defined here
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
	public static class FindParams extends RECORD {
		public final UNSPECIFIED2 directory = mkUNSPECIFIED2();
		public final ScopeSequence scope = mkMember(ScopeSequence::make);
		public final ControlSequence controls = mkMember(ControlSequence::make);
		public final Session session = mkRECORD(Session::make);
		
		private FindParams() {}
		public static FindParams make() { return new FindParams(); }
	}
	public static class FindParams4 extends RECORD {
		public final UNSPECIFIED2 directory = mkUNSPECIFIED2();
		public final ScopeSequence4 scope = mkMember(ScopeSequence4::make);
		public final ControlSequence controls = mkMember(ControlSequence::make);
		public final Session session = mkRECORD(Session::make);
		
		private FindParams4() {}
		public static FindParams4 make() { return new FindParams4(); }
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
	public static class ListParams extends RECORD {
		public final UNSPECIFIED2 directory = mkUNSPECIFIED2();
		public final AttributeTypeSequence types = mkMember(AttributeTypeSequence::make);
		public final ScopeSequence scope = mkMember(ScopeSequence::make);
		public final BulkData1.Sink listing = mkRECORD(BulkData1.Sink::make);
		public final Session session = mkRECORD(Session::make);
		
		private ListParams() {}
		public static ListParams make() { return new ListParams(); }
	}
	public static class ListParams4 extends RECORD {
		public final UNSPECIFIED2 directory = mkUNSPECIFIED2();
		public final AttributeTypeSequence types = mkMember(AttributeTypeSequence::make);
		public final ScopeSequence4 scope = mkMember(ScopeSequence4::make);
		public final BulkData1.Sink listing = mkRECORD(BulkData1.Sink::make);
		public final Session session = mkRECORD(Session::make);
		
		private ListParams4() {}
		public static ListParams4 make() { return new ListParams4(); }
	}
	
}