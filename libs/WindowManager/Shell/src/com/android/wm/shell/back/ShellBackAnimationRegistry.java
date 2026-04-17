/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.back;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Configuration;
import android.os.UserHandle;
import android.util.Log;
import android.util.SparseArray;
import android.window.BackNavigationInfo;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;

/** Registry for all types of default back animations */
public class ShellBackAnimationRegistry {
    private static final String TAG = "ShellBackPreview";

    private final SparseArray<BackAnimationRunner> mAnimationDefinition = new SparseArray<>();
    private ShellBackAnimation mDefaultCrossActivityAnimation;
    private final ShellBackAnimation mCustomizeActivityAnimation;
    private final ShellBackAnimation mCrossTaskAnimation;
    private boolean mSupportedAnimatorsChanged = false;
    private final ArrayList<Integer> mSupportedAnimators = new ArrayList<>();
    /**
     * The userId that registered the current TYPE_RETURN_TO_HOME runner, or
     * {@link UserHandle#USER_NULL} if no runner is registered or if the registration did not
     * carry a user identity (e.g. SystemUI-owned entries, never the case for TYPE_RETURN_TO_HOME
     * in practice).
     */
    private int mHomeRunnerUserId = UserHandle.USER_NULL;

    public ShellBackAnimationRegistry(
            @ShellBackAnimation.CrossActivity @Nullable ShellBackAnimation crossActivityAnimation,
            @ShellBackAnimation.CrossTask @Nullable ShellBackAnimation crossTaskAnimation,
            @ShellBackAnimation.DialogClose @Nullable ShellBackAnimation dialogCloseAnimation,
            @ShellBackAnimation.CustomizeActivity @Nullable
                    ShellBackAnimation customizeActivityAnimation,
            @ShellBackAnimation.ReturnToHome @Nullable
                    ShellBackAnimation defaultBackToHomeAnimation) {
        if (crossActivityAnimation != null) {
            mAnimationDefinition.set(
                    BackNavigationInfo.TYPE_CROSS_ACTIVITY, crossActivityAnimation.getRunner());
        }
        if (crossTaskAnimation != null) {
            mAnimationDefinition.set(
                    BackNavigationInfo.TYPE_CROSS_TASK, crossTaskAnimation.getRunner());
        }
        if (dialogCloseAnimation != null) {
            mAnimationDefinition.set(
                    BackNavigationInfo.TYPE_DIALOG_CLOSE, dialogCloseAnimation.getRunner());
        }
        if (defaultBackToHomeAnimation != null) {
            mAnimationDefinition.set(
                    BackNavigationInfo.TYPE_RETURN_TO_HOME, defaultBackToHomeAnimation.getRunner());
        }

        mDefaultCrossActivityAnimation = crossActivityAnimation;
        mCustomizeActivityAnimation = customizeActivityAnimation;
        mCrossTaskAnimation = crossTaskAnimation;
        updateSupportedAnimators();
        // TODO(b/236760237): register dialog close animation when it's completed.
    }

    void registerAnimation(
            @BackNavigationInfo.BackTargetType int type, @NonNull BackAnimationRunner runner) {
        registerAnimation(type, runner, UserHandle.USER_NULL);
    }

    /**
     * Registers an animation runner for {@code type}, tagging the TYPE_RETURN_TO_HOME entry (and
     * only that type) with the userId of the caller that owns it. This is used at back-gesture
     * dispatch time to reject a stale runner left behind by a user whose launcher process has
     * since been cgroup-frozen by a user switch.
     *
     * @param ownerUserId the userId of the registering caller, or
     *                    {@link UserHandle#USER_NULL} if not tracked (e.g. SystemUI-owned
     *                    entries).
     */
    void registerAnimation(
            @BackNavigationInfo.BackTargetType int type, @NonNull BackAnimationRunner runner,
            int ownerUserId) {
        mAnimationDefinition.set(type, runner);
        // Only happen in test
        if (BackNavigationInfo.TYPE_CROSS_ACTIVITY == type) {
            mDefaultCrossActivityAnimation = null;
        }
        if (BackNavigationInfo.TYPE_RETURN_TO_HOME == type) {
            mHomeRunnerUserId = ownerUserId;
        }
        updateSupportedAnimators();
    }

    void unregisterAnimation(@BackNavigationInfo.BackTargetType int type) {
        mAnimationDefinition.remove(type);
        // Only happen in test
        if (BackNavigationInfo.TYPE_CROSS_ACTIVITY == type) {
            mDefaultCrossActivityAnimation = null;
        }
        if (BackNavigationInfo.TYPE_RETURN_TO_HOME == type) {
            mHomeRunnerUserId = UserHandle.USER_NULL;
        }
        updateSupportedAnimators();
    }

    @VisibleForTesting
    boolean hasAnimation(@BackNavigationInfo.BackTargetType int type) {
        return mAnimationDefinition.contains(type);
    }

    /**
     * Drops the TYPE_RETURN_TO_HOME entry if its registered owner userId does not match
     * {@code currentUserId}. Called at back-gesture dispatch time.
     *
     * <p>When the outgoing user's launcher process is cgroup-frozen across a user switch its
     * binder does not die, so neither an explicit
     * {@link com.android.wm.shell.back.IBackAnimation#clearBackToLauncherCallback} nor a
     * DeathRecipient fires. If the next back gesture on the new user would otherwise dispatch
     * to that frozen binder and time out after 2000 ms, evict the entry here so the framework
     * uses its default transition for returning to the home screen.
     *
     * @return {@code true} if an entry was evicted; {@code false} otherwise.
     */
    boolean evictHomeRunnerIfNotOwnedBy(int currentUserId) {
        if (!hasAnimation(BackNavigationInfo.TYPE_RETURN_TO_HOME)) {
            return false;
        }
        if (mHomeRunnerUserId == UserHandle.USER_NULL
                || mHomeRunnerUserId == currentUserId) {
            return false;
        }
        Log.d(TAG, "Evicting stale TYPE_RETURN_TO_HOME runner registered by userId="
                + mHomeRunnerUserId + ", currentUserId=" + currentUserId);
        unregisterAnimation(BackNavigationInfo.TYPE_RETURN_TO_HOME);
        return true;
    }

    /**
     * Clears the TYPE_RETURN_TO_HOME entry iff its recorded owner matches {@code callingUserId}
     * (or is {@link UserHandle#USER_NULL}, i.e. the entry was registered without an owner
     * identity, in which case there is nothing to reject on).
     *
     * <p>IBackAnimation.clearBackToLauncherCallback is unscoped at the AIDL level (no identity
     * argument), so a delayed teardown from a previous foreground user's launcher could
     * otherwise clear a runner that has since been replaced by the current user's launcher.
     * Only honor the clear if the caller still owns the current entry.
     *
     * @return {@code true} if the entry was cleared; {@code false} otherwise.
     */
    boolean clearHomeRunnerIfOwnedBy(int callingUserId) {
        if (!hasAnimation(BackNavigationInfo.TYPE_RETURN_TO_HOME)) {
            return false;
        }
        if (mHomeRunnerUserId != UserHandle.USER_NULL
                && mHomeRunnerUserId != callingUserId) {
            Log.d(TAG, "Ignoring cross-user clearBackToLauncherCallback from userId="
                    + callingUserId + " because the registered owner is userId="
                    + mHomeRunnerUserId);
            return false;
        }
        unregisterAnimation(BackNavigationInfo.TYPE_RETURN_TO_HOME);
        return true;
    }

    private void updateSupportedAnimators() {
        mSupportedAnimators.clear();
        for (int i = mAnimationDefinition.size() - 1; i >= 0; --i) {
            mSupportedAnimators.add(mAnimationDefinition.keyAt(i));
        }
        mSupportedAnimatorsChanged = true;
    }

    boolean hasSupportedAnimatorsChanged() {
        return mSupportedAnimatorsChanged;
    }

    ArrayList<Integer> getSupportedAnimators() {
        mSupportedAnimatorsChanged = false;
        return mSupportedAnimators;
    }

    /**
     * Start the {@link BackAnimationRunner} associated with a back target type.
     *
     * @param type back target type
     * @return true if the animation is started, false if animation is not found for that type.
     */
    boolean startGesture(@BackNavigationInfo.BackTargetType int type) {
        BackAnimationRunner runner = mAnimationDefinition.get(type);
        if (runner == null) {
            return false;
        }
        runner.startGesture();
        return true;
    }

    /**
     * Cancel the {@link BackAnimationRunner} associated with a back target type.
     *
     * @param type back target type
     * @return true if the animation is started, false if animation is not found for that type.
     */
    boolean cancel(@BackNavigationInfo.BackTargetType int type) {
        BackAnimationRunner runner = mAnimationDefinition.get(type);
        if (runner == null) {
            return false;
        }
        runner.cancelAnimation();
        return true;
    }

    boolean isAnimationCancelledOrNull(@BackNavigationInfo.BackTargetType int type) {
        BackAnimationRunner runner = mAnimationDefinition.get(type);
        if (runner == null) {
            return true;
        }
        return runner.isAnimationCancelled();
    }

    boolean isWaitingAnimation(@BackNavigationInfo.BackTargetType int type) {
        BackAnimationRunner runner = mAnimationDefinition.get(type);
        if (runner == null) {
            return false;
        }
        return runner.isWaitingAnimation();
    }

    void resetDefaultCrossActivity() {
        if (mDefaultCrossActivityAnimation == null
                || !mAnimationDefinition.contains(BackNavigationInfo.TYPE_CROSS_ACTIVITY)) {
            return;
        }
        mAnimationDefinition.set(
                BackNavigationInfo.TYPE_CROSS_ACTIVITY, mDefaultCrossActivityAnimation.getRunner());
    }

    void onConfigurationChanged(Configuration newConfig) {
        if (mCustomizeActivityAnimation != null) {
            mCustomizeActivityAnimation.onConfigurationChanged(newConfig);
        }
        if (mDefaultCrossActivityAnimation != null) {
            mDefaultCrossActivityAnimation.onConfigurationChanged(newConfig);
        }
        if (mCrossTaskAnimation != null) {
            mCrossTaskAnimation.onConfigurationChanged(newConfig);
        }
    }

    BackAnimationRunner getAnimationRunnerAndInit(BackNavigationInfo backNavigationInfo) {
        int type = backNavigationInfo.getType();
        // Initiate customized cross-activity animation, or fall back to cross activity animation
        if (type == BackNavigationInfo.TYPE_CROSS_ACTIVITY && mAnimationDefinition.contains(type)) {
            if (mCustomizeActivityAnimation != null
                    && mCustomizeActivityAnimation.prepareNextAnimation(
                            backNavigationInfo.getCustomAnimationInfo(), 0)) {
                mAnimationDefinition.get(type).resetWaitingAnimation();
                mAnimationDefinition.set(
                        BackNavigationInfo.TYPE_CROSS_ACTIVITY,
                        mCustomizeActivityAnimation.getRunner());
            } else if (mDefaultCrossActivityAnimation != null) {
                mDefaultCrossActivityAnimation.prepareNextAnimation(null,
                        backNavigationInfo.getLetterboxColor());
            }
        }
        BackAnimationRunner runner = mAnimationDefinition.get(type);
        if (runner == null) {
            Log.e(
                    TAG,
                    "Animation didn't be defined for type "
                            + BackNavigationInfo.typeToString(type));
        }
        return runner;
    }
}
