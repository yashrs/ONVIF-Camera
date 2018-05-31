package net.dhleong.vlcvideoview;

import android.net.Uri;
import android.util.Log;

import org.videolan.libvlc.util.VLCUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dhleong
 */
public class VlcOptions {

    private static final String TAG = "VlcOptions";

    public static int deblocking = -1;
    public static boolean enableFrameSkip = false;
    public static boolean enableTimeStretchingAudio = false;
    public static int networkCaching = 0;
    public static boolean verboseMode = BuildConfig.DEBUG;

    /**
     * Set this to a list VLC cli args if there are extra
     *  options you want to pass the VLC runtime. Make sure
     *  to do this BEFORE any call to
     *  {@link VlcVideoView#setVideoUri(Uri, List)}
     */
    public static List<String> extraArgs = null;

    static ArrayList<String> get() {
        ArrayList<String> options = new ArrayList<>(16);

        options.add(enableTimeStretchingAudio ? "--audio-time-stretch" : "--no-audio-time-stretch");
        options.add("--avcodec-skiploopfilter");
        options.add(String.valueOf(getDeblocking(deblocking)));
        options.add("--avcodec-skip-frame");
        options.add(enableFrameSkip ? "2" : "0");
        options.add("--avcodec-skip-idct");
        options.add(enableFrameSkip ? "2" : "0");
        options.add("--audio-resampler");
        options.add(getResampler());

        if (networkCaching > 0) {
            options.add("--network-caching=" +
                Math.min(60000, networkCaching));
        }

        final List<String> extra = extraArgs;
        if (extra != null) {
            options.addAll(extra);
        }

        options.add(verboseMode ? "-vv" : "-v");
        return options;
    }

    /*
     The below functions are borrowed from the official VLC app:
     */

    private static int getDeblocking(int deblocking) {
        int ret = deblocking;
        if (deblocking < 0) {
            /*
              Set some reasonable deblocking defaults:

              Skip all (4) for armv6 and MIPS by default
              Skip non-ref (1) for all armv7 more than 1.2 Ghz and more than 2 cores
              Skip non-key (3) for all devices that don't meet anything above
             */
            VLCUtil.MachineSpecs m = VLCUtil.getMachineSpecs();
            if (m == null) {
                return ret;
            } if ((m.hasArmV6 && !(m.hasArmV7)) || m.hasMips) {
                ret = 4;
            } else if (m.frequency >= 1200 && m.processors > 2) {
                ret = 1;
            } else if (m.bogoMIPS >= 1200 && m.processors > 2) {
                ret = 1;
                Log.d(TAG, "Used bogoMIPS due to lack of frequency info");
            } else
                ret = 3;
        } else if (deblocking > 4) { // sanity check
            ret = 3;
        }
        return ret;
    }

    private static String getResampler() {
        final VLCUtil.MachineSpecs m = VLCUtil.getMachineSpecs();
        return (m == null || m.processors > 2) ? "soxr" : "ugly";
    }

}
