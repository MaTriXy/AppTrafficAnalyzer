package me.ycdev.android.trafficanalyzer.stats;

import java.io.FileInputStream;
import java.io.IOException;

import android.os.Parcel;
import android.os.Parcelable;

import me.ycdev.android.trafficanalyzer.utils.AppLogger;
import me.ycdev.android.trafficanalyzer.utils.ProcFileReader;
import me.ycdev.androidlib.utils.IoUtils;

public class StatsSnapshot implements Comparable<StatsSnapshot>, Parcelable {
    private static final String TAG = "StatsSnapshot";
    private static final boolean DEBUG = AppLogger.DEBUG;

    public long createTime;
    public long clockTime;
    public String fileName;
    public String notes;

    public StatsSnapshot() {
        // nothing to do
    }

    @Override
    public int compareTo(StatsSnapshot another) {
        if (createTime > another.createTime) {
            return 1;
        } else if (createTime < another.createTime) {
            return -1;
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StatsSnapshot) {
            StatsSnapshot rhs = (StatsSnapshot) o;
            return createTime == rhs.createTime && clockTime == rhs.clockTime;
        }
        return false;
    }

    public UidTrafficStats parse(int uid) throws IOException, StatsParseException {
        ProcFileReader reader = new ProcFileReader(new FileInputStream(fileName));
        UidTrafficStats uidStats = new UidTrafficStats();
        uidStats.uid = uid;
        try {
            // consume the header line
            checkHeaderLine(reader);
            TagTrafficStats entry = new TagTrafficStats();
            while (reader.hasMoreData()) {
                reader.nextInt(); // skip "idx"
                entry.iface = reader.nextString();
                entry.tag = kernelToTag(reader.nextString());
                int tagUid = reader.nextInt();
                if (tagUid != uid) {
                    continue;
                }
                int set = reader.nextInt();
                if (set != 1 && set != 0) {
                    throw new StatsParseException("unknown cnt_set value: " + set);
                }
                entry.foreground = (set == 1);
                entry.recvBytes = reader.nextLong();
                reader.nextLong(); // skip "rx_packets"
                entry.sendBytes = reader.nextLong();

                uidStats.addTagTrafficStats(entry.clone());

                reader.finishLine(); // consume left characters in this line
            }
        } finally {
            IoUtils.closeQuietly(reader);
        }
        return uidStats;
    }

    private static void checkHeaderLine(ProcFileReader reader) throws IOException, StatsParseException {
        // header: idx iface acct_tag_hex uid_tag_int cnt_set rx_bytes rx_packets tx_bytes tx_packets ...
        String[] fieldsToCheck = new String[] {
                "idx", "iface", "acct_tag_hex", "uid_tag_int", "cnt_set",
                "rx_bytes", "rx_packets", "tx_bytes", "tx_packets"
        };
        for (String field : fieldsToCheck) {
            String word = reader.nextString();
            if (!field.equals(word)) {
                throwFieldFailureException(word, field);
            }
        }
        reader.finishLine(); // consume left characters in header line
    }

    private static void throwFieldFailureException(String fieldName, String expectedField) throws StatsParseException {
        throw new StatsParseException("Unknown field \"" + fieldName + "\" when check " + expectedField);
    }


    /**
     * Convert {@code /proc/} tag format to {@link Integer}. Assumes incoming
     * format like {@code 0x7fffffff00000000}.
     */
    private static int kernelToTag(String string) {
        int length = string.length();
        if (length > 10) {
            return Long.decode(string.substring(0, length - 8)).intValue();
        } else {
            return 0;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(createTime);
        dest.writeLong(clockTime);
        dest.writeString(fileName);
        dest.writeString(notes);
    }

    private StatsSnapshot(Parcel in) {
        createTime = in.readLong();
        clockTime = in.readLong();
        fileName = in.readString();
        notes = in.readString();
    }

    public static final Parcelable.Creator<StatsSnapshot> CREATOR =
            new Parcelable.Creator<StatsSnapshot>() {
        public StatsSnapshot createFromParcel(Parcel in) {
            return new StatsSnapshot(in);
        }

        public StatsSnapshot[] newArray(int size) {
            return new StatsSnapshot[size];
        }
    };
}
