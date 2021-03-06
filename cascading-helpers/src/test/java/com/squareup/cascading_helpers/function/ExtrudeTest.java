package com.squareup.cascading_helpers.function;

import cascading.flow.FlowDef;
import cascading.flow.hadoop.HadoopFlowProcess;
import cascading.pipe.Pipe;
import cascading.scheme.hadoop.SequenceFile;
import cascading.tap.hadoop.Hfs;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;
import cascading.tuple.TupleEntryCollector;
import cascading.tuple.TupleEntryIterator;
import com.squareup.cascading_helpers.CascadingHelper;
import com.squareup.cascading_helpers.pump.Pump;
import com.squareup.cascading_helpers.TestHelpers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class ExtrudeTest {
  private static final String TMP_DIR = "/tmp/ExtrudeTest";

  @Test
  public void testExtrude() {
    List<Tuple> results = TestHelpers.exec(
        new Extrude("output"),
        new Fields("first", "second"),
        new Tuple("first", "second"));

    assertEquals(2, results.size());
    assertEquals(Arrays.asList(new Tuple("first"), new Tuple("second")), results);
  }

  @Test
  public void testInFlow() throws Exception {
    FileSystem fs = FileSystem.get(new Configuration());
    fs.delete(new Path(TMP_DIR), true);

    Hfs input =
        new Hfs(new SequenceFile(new Fields("constant", "first", "second")), TMP_DIR + "/inputs");
    TupleEntryCollector collector = input.openForWrite(new HadoopFlowProcess());
    collector.add(new Tuple("constant 1", "a", "b"));
    collector.add(new Tuple("constant 2", "c", "d"));
    collector.close();

    Hfs output = new Hfs(new SequenceFile(new Fields("constant", "output")), TMP_DIR + "/outputs");

    Pipe pipe = Pump.prime()
        .each(new Extrude("output"), "first", "second")
        .retain("constant", "output")
        .toPipe();
    FlowDef flow = new FlowDef()
        .addSource("input", input)
        .addTailSink(pipe, output);
    CascadingHelper.setTestMode();
    CascadingHelper.get().getFlowConnector().connect(flow).complete();

    List<String> results = new ArrayList<String>();
    TupleEntryIterator iterator = output.openForRead(new HadoopFlowProcess());
    while (iterator.hasNext()) {
      TupleEntry tupleEntry = iterator.next();
      results.add(tupleEntry.getString(0) + "\t" + tupleEntry.getString(1));
    }
    assertEquals(Arrays.asList("constant 1\ta", "constant 1\tb", "constant 2\tc", "constant 2\td"), results);
  }
}
