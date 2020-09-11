package MetaServer.sse;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class TableParseTest {
    private static final String data =
            "+--------+----------------+-----------+\n" +
                    "|database|       tableName|isTemporary|\n" +
                    "+--------+----------------+-----------+\n" +
                    "| default|            cfit|      false|\n" +
                    "| default|             cpd|      false|\n" +
                    "| default|demomodelingdata|      false|\n" +
                    "| default|             df1|      false|\n" +
                    "| default|          df1lgd|      false|\n" +
                    "| default|             df2|      false|\n" +
                    "| default|          df2lgd|      false|\n" +
                    "| default|             df3|      false|\n" +
                    "| default|          df3lgd|      false|\n" +
                    "| default|     gpstracking|      false|\n" +
                    "| default|             src|      false|\n" +
                    "+--------+----------------+-----------+";

    private static final String headersOnly =
            "+--------+----------------+-----------+\n" +
                    "|database|       tableName|isTemporary|\n" +
                    "+--------+----------------+-----------+\n" +
                    "+--------+----------------+-----------+";

    @Test
    public void testParse() {
        String[] lines = data.split("\n");
        assertEquals(lines.length, 15);
        // parse header
        List<String> columns = parseLine(lines[1]);
        assertEquals(columns.size(), 3);

        String[] rows = Arrays.copyOfRange(lines, 3, lines.length - 1);
        assertEquals(rows.length, 11);
        List<List<String>> rdd = Arrays.stream(rows).map(TableParseTest::parseLine).collect(Collectors.toList());
        assertEquals(rdd.size(), 11);

        List<String> row = rdd.get(0);
        assertEquals(row.get(1), "cfit");
    }

    private static List<String> parseLine(String line) {
        return Arrays.stream(line.split("\\|"))
                .map(String::trim)
                .filter((s) -> s.length() > 0)
                .collect(Collectors.toList());
    }
}
