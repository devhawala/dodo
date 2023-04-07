package dev.hawala.xns.level3.courier.tests;

import dev.hawala.xns.level3.courier.INTEGER;
import dev.hawala.xns.level3.courier.JsonStringReader;
import dev.hawala.xns.level3.courier.JsonStringWriter;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.StreamOfUnspecified;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.ContentType;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.Content_complex;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.Content_integer;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.Content_string;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.CrBasics;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.CrSequences;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.CrSubstructures;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.Data;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.PacketType;
import dev.hawala.xns.level3.courier.tests.CrTestProgram.TransmitType;

public class TestJson {
	
	private static void logf(String format, Object... args) {
		System.out.printf(format, args);
	}

	public static void main(String[] args) {
		{
			CrBasics recIn = new CrBasics();
			recIn.zeBool.set(true);
			recIn.zeCardinal.set(65000);
			recIn.zePacketType.set(PacketType.error);
			recIn.zeTransmitType.set(TransmitType.paper);
			recIn.zeInteger.set(-23);
			recIn.zeLongCardinal.set(4000000000L);
			recIn.zeLongInteger.set(-2000000000);
			recIn.zeString.set("abcdefg"); // 7 chars long
			recIn.zeUnspec.set(44000);
			recIn.zeUnspec2.set(1550000000);
			recIn.zeUnspec3.set(66000000000L);
			
			JsonStringWriter wr = new JsonStringWriter();
			recIn.serialize(wr);
			String json = wr.get();
			logf("recInb -> JSON:\n%s\n\n", json);
			
			CrBasics recOut = new CrBasics();
			JsonStringReader rd = new JsonStringReader(json);
			recOut.deserialize(rd);
			StringBuilder sb = new StringBuilder();
			String recStr = recOut.append(sb, "", "recOut").toString();
			logf("-------\n%s\n-------\n", recStr);
		}
		
		logf("\n+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n\n");
		{
			CrSubstructures recIn = new CrSubstructures();
			recIn.check1.set(0x1111);
			recIn.check2.set(0x2222);
			recIn.check3.set(0x3333);
			
			recIn.data.userdata1.set(11111);
			recIn.data.userdata2.set(222222222);
			
			recIn.content.setChoice(ContentType.empty);
			
			JsonStringWriter wr = new JsonStringWriter();
			recIn.serialize(wr);
			String json = wr.get();
			logf("recIn -> JSON:\n%s\n\n", json);
			
			CrSubstructures recOut = new CrSubstructures();
			JsonStringReader rd = new JsonStringReader(json);
			recOut.deserialize(rd);
			StringBuilder sb = new StringBuilder();
			String recStr = recOut.append(sb, "", "recOut").toString();
			logf("-------\n%s\n-------\n", recStr);
		}
		
		logf("\n+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n\n");
		{
			CrSubstructures recIn = new CrSubstructures();
			recIn.check1.set(0x1111);
			recIn.check2.set(0x2222);
			recIn.check3.set(0x3333);
			
			recIn.data.userdata1.set(11111);
			recIn.data.userdata2.set(222222222);
			
			recIn.content.setChoice(ContentType.integer);
			Content_integer ci = (Content_integer)recIn.content.getContent();
			ci.integer.set(-9999);
			
			JsonStringWriter wr = new JsonStringWriter();
			recIn.serialize(wr);
			String json = wr.get();
			logf("recIn -> JSON:\n%s\n\n", json);
			
			CrSubstructures recOut = new CrSubstructures();
			JsonStringReader rd = new JsonStringReader(json);
			recOut.deserialize(rd);
			StringBuilder sb = new StringBuilder();
			String recStr = recOut.append(sb, "", "recOut").toString();
			logf("-------\n%s\n-------\n", recStr);
		}
		
		logf("\n+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n\n");
		{
			CrSubstructures recIn = new CrSubstructures();
			recIn.check1.set(0x1111);
			recIn.check2.set(0x2222);
			recIn.check3.set(0x3333);
			
			recIn.data.userdata1.set(11111);
			recIn.data.userdata2.set(222222222);
			
			recIn.content.setChoice(ContentType.string);
			Content_string cs = (Content_string)recIn.content.getContent();
			cs.string.set("the string content in rec.content.string");
			
			JsonStringWriter wr = new JsonStringWriter();
			recIn.serialize(wr);
			String json = wr.get();
			logf("recIn -> JSON:\n%s\n\n", json);
			
			CrSubstructures recOut = new CrSubstructures();
			JsonStringReader rd = new JsonStringReader(json);
			recOut.deserialize(rd);
			StringBuilder sb = new StringBuilder();
			String recStr = recOut.append(sb, "", "recOut").toString();
			logf("-------\n%s\n-------\n", recStr);
		}
		
		logf("\n+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n\n");
		{
			CrSubstructures recIn = new CrSubstructures();
			recIn.check1.set(0x1111);
			recIn.check2.set(0x2222);
			recIn.check3.set(0x3333);
			
			recIn.data.userdata1.set(11111);
			recIn.data.userdata2.set(222222222);
			
			Content_complex cc = (Content_complex)recIn.content.setChoice(ContentType.complex);
			cc.flags.get(0).set(true);
			cc.flags.get(1).set(false);
			cc.flags.get(2).set(false);
			cc.flags.get(3).set(true);
			cc.string.set("the string content in rec.content.complex");
			cc.longCardinal.set(4000000000L);
			
			JsonStringWriter wr = new JsonStringWriter();
			recIn.serialize(wr);
			String json = wr.get();
			logf("recIn -> JSON:\n%s\n\n", json);
			
			CrSubstructures recOut = new CrSubstructures();
			JsonStringReader rd = new JsonStringReader(json);
			recOut.deserialize(rd);
			StringBuilder sb = new StringBuilder();
			String recStr = recOut.append(sb, "", "recOut").toString();
			logf("-------\n%s\n-------\n", recStr);
		}
		
		logf("\n+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n\n");
		{
			CrSequences recIn = new CrSequences();
			recIn.check1.set(0x1111);
			recIn.check2.set(0x2222);
			recIn.check3.set(0x3333);
			recIn.check4.set(0x4444);
			recIn.check5.set(0x5555);
			
			for (int i = 0; i < 5; i++) {
				INTEGER integer = recIn.seqInt.add();
				integer.set(5000 + i);
			}
			
			for (int i = 0; i < 11; i++) {
				Data data = recIn.sOfData.add();
				data.userdata1.set(7000 + i);
				data.userdata2.set(1001000 + i);
			}
			
			for (int i = 0; i < 7; i++) {
				STRING string = recIn.seqString.add();
				string.set("value of rec.seqString :: " + i);
			}
			
			Content_complex cc = (Content_complex)recIn.sOfContent.add().setChoice(ContentType.complex);
			cc.flags.get(0).set(true);
			cc.flags.get(1).set(false);
			cc.flags.get(2).set(true);
			cc.flags.get(3).set(false);
			cc.string.set("a test string");
			cc.longCardinal.set(1234567890L);
			
			recIn.sOfContent.add().setChoice(ContentType.empty);
			
			Content_integer ci = (Content_integer)recIn.sOfContent.add().setChoice(ContentType.integer);
			ci.integer.set(42);
			
			JsonStringWriter wr = new JsonStringWriter();
			recIn.serialize(wr);
			String json = wr.get();
			logf("recIn -> JSON:\n%s\n\n", json);
			
			CrSequences recOut = new CrSequences();
			JsonStringReader rd = new JsonStringReader(json);
			recOut.deserialize(rd);
			StringBuilder sb = new StringBuilder();
			String recStr = recOut.append(sb, "", "recOut").toString();
			logf("-------\n%s\n-------\n", recStr);
		}
		
		logf("\n+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n\n");
		{
			StreamOfUnspecified recIn = StreamOfUnspecified.make();
			for (int i = 0; i < 128; i++) {
				recIn.add(i * 3);
			}
			
			JsonStringWriter wr = new JsonStringWriter();
			recIn.serialize(wr);
			String json = wr.get();
			logf("recIn -> JSON:\n%s\n\n", json);
			
			StreamOfUnspecified recOut = StreamOfUnspecified.make();
			JsonStringReader rd = new JsonStringReader(json);
			recOut.deserialize(rd);
			StringBuilder sb = new StringBuilder();
			String recStr = recOut.append(sb, "", "recOut").toString();
			logf("-------\n%s\n-------\n", recStr);
		}
	
		logf("\n+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n\n");
		{
			String json = "{ \"zeBool\": true, \"zeTransmitType\": \"drums\" }";
			
			logf("recIn -> JSON:\n%s\n\n", json);
			
			CrBasics recOut = new CrBasics();
			JsonStringReader rd = new JsonStringReader(json);
			recOut.deserialize(rd);
			StringBuilder sb = new StringBuilder();
			String recStr = recOut.append(sb, "", "recOut").toString();
			logf("-------\n%s\n-------\n", recStr);
		}
	}
}
