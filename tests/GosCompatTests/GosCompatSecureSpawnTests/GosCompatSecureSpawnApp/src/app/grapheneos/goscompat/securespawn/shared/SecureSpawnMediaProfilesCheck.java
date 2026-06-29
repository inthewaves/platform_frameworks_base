package app.grapheneos.goscompat.securespawn.shared;

import android.hardware.Camera;
import android.media.CamcorderProfile;

import app.grapheneos.goscompat.securespawn.SecureSpawnCheck;

/**
 * Checks that the device camcorder profiles load after the process cwd is changed off "/". The
 * native MediaProfiles singleton (frameworks/av/media/libmedia/MediaProfiles.cpp) loads the
 * media_profiles XML on the first CamcorderProfile use and caches it for the process life; before
 * that file was patched to use absolute search paths the lookup was resolved relative to cwd. This
 * must therefore run before any other CamcorderProfile use in the process.
 */
public final class SecureSpawnMediaProfilesCheck {
    // The MediaProfiles default instance (used when the XML is not found) defines no profiles for
    // the front camera, so a non-empty subset here proves the device XML loaded despite the chdir.
    // Probing several qualities avoids depending on which specific qualities a given device's front
    // camera declares (e.g. some devices omit QUALITY_HIGH for the front camera).
    private static final int[] PROBE_QUALITIES = {
            CamcorderProfile.QUALITY_LOW,
            CamcorderProfile.QUALITY_HIGH,
            CamcorderProfile.QUALITY_480P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_2160P,
    };

    private SecureSpawnMediaProfilesCheck() {
    }

    public static MediaProfilesCwd run(boolean execSpawned, String chdirTarget) {
        String naturalCwd = SecureSpawnCheck.getCwd();
        int chdirResult = SecureSpawnCheck.changeDir(chdirTarget);
        String cwdAfterChdir = SecureSpawnCheck.getCwd();

        // First CamcorderProfile use in this process: forces the native MediaProfiles singleton to
        // load now, at the changed (non-"/") cwd.
        int frontCameraId = frontFacingCameraId();
        int frontProfileCount = 0;
        boolean frontHasHighProfile = false;
        if (frontCameraId >= 0) {
            for (int quality : PROBE_QUALITIES) {
                if (hasProfile(frontCameraId, quality)) {
                    frontProfileCount++;
                    if (quality == CamcorderProfile.QUALITY_HIGH) {
                        frontHasHighProfile = true;
                    }
                }
            }
        }

        return new MediaProfilesCwd(
                execSpawned,
                naturalCwd,
                chdirTarget,
                chdirResult,
                cwdAfterChdir,
                frontCameraId,
                frontProfileCount,
                frontHasHighProfile);
    }

    // Legacy camera index space (what CamcorderProfile.get/hasProfile expect). Enumeration does
    // not require the CAMERA permission.
    @SuppressWarnings("deprecation")
    private static int frontFacingCameraId() {
        try {
            int count = Camera.getNumberOfCameras();
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int id = 0; id < count; id++) {
                Camera.getCameraInfo(id, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    return id;
                }
            }
        } catch (RuntimeException e) {
            return -1;
        }
        return -1;
    }

    private static boolean hasProfile(int cameraId, int quality) {
        try {
            return CamcorderProfile.hasProfile(cameraId, quality);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public record MediaProfilesCwd(
            boolean execSpawned,
            String naturalCwd,
            String chdirTarget,
            int chdirResult,
            String cwdAfterChdir,
            int frontCameraId,
            int frontProfileCount,
            boolean frontHasHighProfile) {
        public boolean changedDirectoryOffRoot() {
            return chdirResult() == 0 && cwdAfterChdir() != null && !"/".equals(cwdAfterChdir());
        }

        public boolean frontProfilesLoaded() {
            return frontProfileCount() > 0;
        }

        @Override
        public String toString() {
            return "execSpawned=" + execSpawned()
                    + "\nnaturalCwd=" + naturalCwd()
                    + "\nchdirTarget=" + chdirTarget()
                    + "\nchdirResult=" + chdirResult()
                    + "\ncwdAfterChdir=" + cwdAfterChdir()
                    + "\nchangedDirectoryOffRoot=" + changedDirectoryOffRoot()
                    + "\nfrontCameraId=" + frontCameraId()
                    + "\nfrontProfileCount=" + frontProfileCount()
                    + "\nfrontHasHighProfile=" + frontHasHighProfile()
                    + "\nfrontProfilesLoaded=" + frontProfilesLoaded();
        }
    }
}
