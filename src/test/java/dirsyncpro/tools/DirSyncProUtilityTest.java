package dirsyncpro.tools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.junit.jupiter.api.Test;

class DirSyncProUtilityTest {

    @Test
    void addToArrayAppendsEntry() {
        String[] initial = {"alpha", "beta"};

        String[] result = ArrayTools.addToArray("gamma", initial);

        assertArrayEquals(new String[] {"alpha", "beta", "gamma"}, result);
    }

    @Test
    void deleteFromArrayRemovesMatchingEntry() {
        String[] initial = {"alpha", "beta", "gamma"};

        String[] result = ArrayTools.deleteFromArray("beta", initial);

        assertArrayEquals(new String[] {"alpha", "gamma"}, result);
    }

    @Test
    void replaceInArrayReplacesExistingEntryAndAddsMissingEntry() {
        String[] arrayWithMatch = {"alpha", "beta", "gamma"};
        String[] arrayWithoutMatch = {"alpha", "beta"};

        String[] replaced = ArrayTools.replaceInArray("beta", "delta", arrayWithMatch);
        String[] appended = ArrayTools.replaceInArray("theta", "delta", arrayWithoutMatch);

        assertArrayEquals(new String[] {"alpha", "delta", "gamma"}, replaced);
        assertArrayEquals(new String[] {"alpha", "beta", "delta"}, appended);
    }

    @Test
    void replaceDateAndTimeWildcardsUsesFixedValues() {
        Date date = new GregorianCalendar(2024, Calendar.MAY, 15, 10, 12, 13).getTime();

        String replacedDate = WildcardTools.replaceDateWildcards("<date> <DD>/<MM>/<YYYY>", date);
        String replacedTime = WildcardTools.replaceTimeWildcards("<time> <hh>:<mm>:<ss>", date);

        assertEquals("2024-05-15 15/05/2024", replacedDate);
        assertEquals("10_12_13 10:12:13", replacedTime);
    }

    @Test
    void replaceUserWildcardsUsesSystemProperties() {
        String originalUser = System.getProperty("user.name");
        String originalHome = System.getProperty("user.home");
        try {
            System.setProperty("user.name", "tester");
            System.setProperty("user.home", "/tmp/test-home");

            String result = WildcardTools.replaceUserWildcards("<username>@<userhome>");

            assertEquals("tester@/tmp/test-home", result);
        } finally {
            if (originalUser == null) {
                System.clearProperty("user.name");
            } else {
                System.setProperty("user.name", originalUser);
            }
            if (originalHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", originalHome);
            }
        }
    }

    @Test
    void cmpDatesHandlesToleranceAndOrdering() {
        long newer = 100_000L;
        long older = 0L;

        assertEquals(0, DateTool.cmpDates(newer, newer, 0, false));
        assertEquals(-1, DateTool.cmpDates(newer, older, 0, false));
        assertEquals(1, DateTool.cmpDates(older, newer, 0, false));
        assertEquals(0, DateTool.cmpDates(newer, newer + 1_000L, 2, false));
    }

    @Test
    void humanReadableFormattingUsesBinaryUnits() {
        assertEquals("0 B", TextFormatTool.getHumanReadable(0));
        assertEquals("1.0 KB", TextFormatTool.getHumanReadable(1024));
        assertEquals("1.5 KB", TextFormatTool.getHumanReadable(1536));
        assertEquals("1.0 MB", TextFormatTool.getHumanReadable(1_048_576));
    }

    @Test
    void wildcardToolsReplaceAllReplacesEveryOccurrence() {
        String result = WildcardTools.replaceAll("<date>-<date>-<date>", "<date>", "2024-05-15");

        assertEquals("2024-05-15-2024-05-15-2024-05-15", result);
    }

    @Test
    void timeToolConvertToIndicationUsesStableReadableUnits() {
        assertEquals("less than a minute", TimeTool.convertToIndication(30_000L));
        assertEquals("1 minutes", TimeTool.convertToIndication(90_000L));
        assertEquals("1 hours 0 minutes", TimeTool.convertToIndication(3_600_000L));
    }
}
