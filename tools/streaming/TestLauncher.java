import java.io.PrintWriter;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.deepsymmetry.beatlink.*;
import org.deepsymmetry.beatlink.data.*;

/** Observes the test application's own state; never sends playback commands. */
public final class TestLauncher {
    private static String quote(Object value) {
        if (value == null) return "null";
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private static void monitor() {
        Path path = Paths.get(System.getProperty("cdj3000x.log"));
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(path,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            while (true) {
                VirtualCdj cdj = VirtualCdj.getInstance();
                if (cdj.isRunning()) {
                    for (DeviceAnnouncement device : DeviceFinder.getInstance().getCurrentDevices()) {
                        if (!device.getDeviceName().equals("CDJ-3000X")) continue;
                        DeviceUpdate update = cdj.getLatestStatusFor(device);
                        if (!(update instanceof CdjStatus)) continue;
                        CdjStatus status = (CdjStatus) update;
                        int n = device.getDeviceNumber();
                        long statusAgeMillis = Math.max(0, (System.nanoTime() - status.getTimestamp()) / 1_000_000L);
                        TrackMetadata meta = MetadataFinder.getInstance().isRunning()
                                ? MetadataFinder.getInstance().getLatestMetadataFor(n) : null;
                        BeatGrid grid = BeatGridFinder.getInstance().isRunning()
                                ? BeatGridFinder.getInstance().getLatestBeatGridFor(n) : null;
                        WaveformPreview preview = WaveformFinder.getInstance().isRunning()
                                ? WaveformFinder.getInstance().getLatestPreviewFor(n) : null;
                        WaveformDetail detail = WaveformFinder.getInstance().isRunning()
                                ? WaveformFinder.getInstance().getLatestDetailFor(n) : null;
                        TrackPositionUpdate position = TimeFinder.getInstance().isRunning()
                                ? TimeFinder.getInstance().getLatestPositionFor(n) : null;
                        byte[] raw = status.getPacketBytes();
                        out.println("{\"time\":" + quote(Instant.now())
                                + ",\"virtualPlayer\":" + cdj.getDeviceNumber()
                                + ",\"player\":" + n
                                + ",\"firmware\":" + quote(status.getFirmwareVersion())
                                + ",\"metadataLimited\":" + DeviceFinder.getInstance().isDeviceMetadataLimited(device)
                                + ",\"deviceLibraryPlus\":" + device.isUsingDeviceLibraryPlus
                                + ",\"sourcePlayer\":" + status.getTrackSourcePlayer()
                                + ",\"sourceSlot\":" + quote(status.getTrackSourceSlot())
                                + ",\"rawSourceSlot\":" + (raw[0x29] & 255)
                                + ",\"trackType\":" + quote(status.getTrackType())
                                + ",\"rawTrackType\":" + (raw[0x2a] & 255)
                                + ",\"trackId\":" + status.getRekordboxId()
                                + ",\"statusBytes\":" + raw.length
                                + ",\"statusAgeMillis\":" + statusAgeMillis
                                + ",\"statusFresh\":" + (statusAgeMillis < 2000)
                                + ",\"positionAgeMillis\":" + (position == null ? "null" : Math.max(0, (System.nanoTime() - position.timestamp) / 1_000_000L))
                                + ",\"playing\":" + status.isPlaying()
                                + ",\"positionMillis\":" + (position == null ? "null" : position.milliseconds)
                                + ",\"precisePosition\":" + (position != null && position.precise)
                                + ",\"tempoMaster\":" + status.isTempoMaster()
                                + ",\"bpmHundredths\":" + status.getBpm()
                                + ",\"title\":" + quote(meta == null ? null : meta.getTitle())
                                + ",\"artist\":" + quote(meta == null ? null : meta.getArtist())
                                + ",\"durationSeconds\":" + (meta == null ? "null" : meta.getDuration())
                                + ",\"gridBeats\":" + (grid == null ? "null" : grid.beatCount)
                                + ",\"waveformPreview\":" + (preview != null)
                                + ",\"waveformDetail\":" + (detail != null)
                                + ",\"artwork\":" + (ArtFinder.getInstance().isRunning()
                                    && ArtFinder.getInstance().getLatestArtFor(n) != null) + "}");
                    }
                    out.flush();
                }
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            System.err.println("CDJ-3000X test observer stopped: " + e);
        }
    }

    public static void main(String[] args) throws Exception {
        if (System.getProperty("cdj3000x.log") != null) {
            Thread observer = new Thread(TestLauncher::monitor, "cdj3000x-test-observer");
            observer.setDaemon(true);
            observer.start();
        }
        Class.forName("beat_link_trigger.BeatLinkTrigger")
                .getMethod("main", String[].class).invoke(null, (Object) args);
    }
}
