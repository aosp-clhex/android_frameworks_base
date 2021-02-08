/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.app.smartspace;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link SmartspaceTarget} is a data class which holds all properties necessary to inflate a
 * smartspace card. It contains data and related metadata which is supposed to be utilized by
 * smartspace clients based on their own UI/UX requirements. Some of the properties have
 * {@link SmartspaceAction} as their type because they can have associated actions.
 *
 * <p><b>NOTE: </b>
 * If {@link mWidgetId} is set, it should be preferred over all other properties.
 * Else, if {@link mSliceUri} is set, it should be preferred over all other data properties.
 * Otherwise, the instance should be treated as a data object.
 *
 * @hide
 */
@SystemApi
public final class SmartspaceTarget implements Parcelable {

    /** A unique Id for an instance of {@link SmartspaceTarget}. */
    @NonNull
    private final String mSmartspaceTargetId;

    /** A {@link SmartspaceAction} for the header in the Smartspace card. */
    @Nullable
    private final SmartspaceAction mHeaderAction;

    /** A {@link SmartspaceAction} for the base action in the Smartspace card. */
    @Nullable
    private final SmartspaceAction mBaseAction;

    /** A timestamp indicating when the card was created. */
    @NonNull
    private final long mCreationTimeMillis;

    /**
     * A timestamp indicating when the card should be removed from view, in case the service
     * disconnects or restarts.
     */
    @NonNull
    private final long mExpiryTimeMillis;

    /** A score assigned to a target. */
    @NonNull
    private final float mScore;

    /** A {@link List<SmartspaceAction>} containing all action chips. */
    @NonNull
    private final List<SmartspaceAction> mActionChips;

    /** A {@link List<SmartspaceAction>} containing all icons for the grid. */
    @NonNull
    private final List<SmartspaceAction> mIconGrid;

    /**
     * {@link FeatureType} indicating the feature type of this card.
     *
     * @see FeatureType
     */
    @FeatureType
    @NonNull
    private final int mFeatureType;

    /**
     * Indicates whether the content is sensitive. Certain UI surfaces may choose to skip rendering
     * real content until the device is unlocked.
     */
    @NonNull
    private final boolean mSensitive;

    /** Indicating if the UI should show this target in its expanded state. */
    @NonNull
    private final boolean mShouldShowExpanded;

    /** A Notification key if the target was generated using a notification. */
    @Nullable
    private final String mSourceNotificationKey;

    /** {@link ComponentName} for this target. */
    @NonNull
    private final ComponentName mComponentName;

    /** {@link UserHandle} for this target. */
    @NonNull
    private final UserHandle mUserHandle;

    /** Target Ids of other {@link SmartspaceTarget}s if they are associated with this target. */
    @Nullable
    private final String mAssociatedSmartspaceTargetId;

    /** {@link Uri} Slice Uri if this target is a slice. */
    @Nullable
    private final Uri mSliceUri;

    /** {@link AppWidgetProviderInfo} if this target is a widget. */
    @Nullable
    private final AppWidgetProviderInfo mWidgetId;

    public static final int FEATURE_UNDEFINED = 0;
    public static final int FEATURE_WEATHER = 1;
    public static final int FEATURE_CALENDAR = 2;
    public static final int FEATURE_COMMUTE_TIME = 3;
    public static final int FEATURE_FLIGHT = 4;
    public static final int FEATURE_TIPS = 5;
    public static final int FEATURE_REMINDER = 6;
    public static final int FEATURE_ALARM = 7;
    public static final int FEATURE_ONBOARDING = 8;
    public static final int FEATURE_SPORTS = 9;
    public static final int FEATURE_WEATHER_ALERT = 10;
    public static final int FEATURE_CONSENT = 11;
    public static final int FEATURE_STOCK_PRICE_CHANGE = 12;
    public static final int FEATURE_SHOPPING_LIST = 13;
    public static final int FEATURE_LOYALTY_CARD = 14;
    public static final int FEATURE_MEDIA = 15;
    public static final int FEATURE_BEDTIME_ROUTINE = 16;
    public static final int FEATURE_FITNESS_TRACKING = 17;
    public static final int FEATURE_ETA_MONITORING = 18;
    public static final int FEATURE_MISSED_CALL = 19;
    public static final int FEATURE_PACKAGE_TRACKING = 20;
    public static final int FEATURE_TIMER = 21;
    public static final int FEATURE_STOPWATCH = 22;
    public static final int FEATURE_UPCOMING_ALARM = 23;

    /**
     * @hide
     */
    @IntDef(prefix = {"FEATURE_"}, value = {
            FEATURE_UNDEFINED,
            FEATURE_WEATHER,
            FEATURE_CALENDAR,
            FEATURE_COMMUTE_TIME,
            FEATURE_FLIGHT,
            FEATURE_TIPS,
            FEATURE_REMINDER,
            FEATURE_ALARM,
            FEATURE_ONBOARDING,
            FEATURE_SPORTS,
            FEATURE_WEATHER_ALERT,
            FEATURE_CONSENT,
            FEATURE_STOCK_PRICE_CHANGE,
            FEATURE_SHOPPING_LIST,
            FEATURE_LOYALTY_CARD,
            FEATURE_MEDIA,
            FEATURE_BEDTIME_ROUTINE,
            FEATURE_FITNESS_TRACKING,
            FEATURE_ETA_MONITORING,
            FEATURE_MISSED_CALL,
            FEATURE_PACKAGE_TRACKING,
            FEATURE_TIMER,
            FEATURE_STOPWATCH,
            FEATURE_UPCOMING_ALARM
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FeatureType {
    }

    private SmartspaceTarget(Parcel in) {
        this.mSmartspaceTargetId = in.readString();
        this.mHeaderAction = in.readTypedObject(SmartspaceAction.CREATOR);
        this.mBaseAction = in.readTypedObject(SmartspaceAction.CREATOR);
        this.mCreationTimeMillis = in.readLong();
        this.mExpiryTimeMillis = in.readLong();
        this.mScore = in.readFloat();
        this.mActionChips = in.createTypedArrayList(SmartspaceAction.CREATOR);
        this.mIconGrid = in.createTypedArrayList(SmartspaceAction.CREATOR);
        this.mFeatureType = in.readInt();
        this.mSensitive = in.readBoolean();
        this.mShouldShowExpanded = in.readBoolean();
        this.mSourceNotificationKey = in.readString();
        this.mComponentName = in.readTypedObject(ComponentName.CREATOR);
        this.mUserHandle = in.readTypedObject(UserHandle.CREATOR);
        this.mAssociatedSmartspaceTargetId = in.readString();
        this.mSliceUri = in.readTypedObject(Uri.CREATOR);
        this.mWidgetId = in.readTypedObject(AppWidgetProviderInfo.CREATOR);
    }

    private SmartspaceTarget(String smartspaceTargetId,
            SmartspaceAction headerAction, SmartspaceAction baseAction, long creationTimeMillis,
            long expiryTimeMillis, float score,
            List<SmartspaceAction> actionChips,
            List<SmartspaceAction> iconGrid, int featureType, boolean sensitive,
            boolean shouldShowExpanded, String sourceNotificationKey,
            ComponentName componentName, UserHandle userHandle,
            String associatedSmartspaceTargetId, Uri sliceUri,
            AppWidgetProviderInfo widgetId) {
        mSmartspaceTargetId = smartspaceTargetId;
        mHeaderAction = headerAction;
        mBaseAction = baseAction;
        mCreationTimeMillis = creationTimeMillis;
        mExpiryTimeMillis = expiryTimeMillis;
        mScore = score;
        mActionChips = actionChips;
        mIconGrid = iconGrid;
        mFeatureType = featureType;
        mSensitive = sensitive;
        mShouldShowExpanded = shouldShowExpanded;
        mSourceNotificationKey = sourceNotificationKey;
        mComponentName = componentName;
        mUserHandle = userHandle;
        mAssociatedSmartspaceTargetId = associatedSmartspaceTargetId;
        mSliceUri = sliceUri;
        mWidgetId = widgetId;
    }

    /**
     * Returns the Id of the target.
     */
    @NonNull
    public String getSmartspaceTargetId() {
        return mSmartspaceTargetId;
    }

    /**
     * Returns the header action of the target.
     */
    @Nullable
    public SmartspaceAction getHeaderAction() {
        return mHeaderAction;
    }

    /**
     * Returns the base action of the target.
     */
    @Nullable
    public SmartspaceAction getBaseAction() {
        return mBaseAction;
    }

    /**
     * Returns the creation time of the target.
     */
    @NonNull
    public long getCreationTimeMillis() {
        return mCreationTimeMillis;
    }

    /**
     * Returns the expiry time of the target.
     */
    @NonNull
    public long getExpiryTimeMillis() {
        return mExpiryTimeMillis;
    }

    /**
     * Returns the score of the target.
     */
    @NonNull
    public float getScore() {
        return mScore;
    }

    /**
     * Return the action chips of the target.
     */
    @NonNull
    public List<SmartspaceAction> getActionChips() {
        return mActionChips;
    }

    /**
     * Return the icons of the target.
     */
    @NonNull
    public List<SmartspaceAction> getIconGrid() {
        return mIconGrid;
    }

    /**
     * Returns the feature type of the target.
     */
    @NonNull
    public int getFeatureType() {
        return mFeatureType;
    }

    /**
     * Returns whether the target is sensitive or not.
     */
    @NonNull
    public boolean isSensitive() {
        return mSensitive;
    }

    /**
     * Returns whether the target should be shown in expanded state.
     */
    @NonNull
    public boolean shouldShowExpanded() {
        return mShouldShowExpanded;
    }

    /**
     * Returns the source notification key of the target.
     */
    @Nullable
    public String getSourceNotificationKey() {
        return mSourceNotificationKey;
    }

    /**
     * Returns the component name of the target.
     */
    @NonNull
    public ComponentName getComponentName() {
        return mComponentName;
    }

    /**
     * Returns the user handle of the target.
     */
    @NonNull
    public UserHandle getUserHandle() {
        return mUserHandle;
    }

    /**
     * Returns the id of a target associated with this instance.
     */
    @Nullable
    public String getAssociatedSmartspaceTargetId() {
        return mAssociatedSmartspaceTargetId;
    }

    /**
     * Returns the slice uri, if the target is a slice.
     */
    @Nullable
    public Uri getSliceUri() {
        return mSliceUri;
    }

    /**
     * Returns the AppWidgetProviderInfo, if the target is a widget.
     */
    @Nullable
    public AppWidgetProviderInfo getWidgetId() {
        return mWidgetId;
    }

    /**
     * @see Parcelable.Creator
     */
    @NonNull
    public static final Creator<SmartspaceTarget> CREATOR = new Creator<SmartspaceTarget>() {
        @Override
        public SmartspaceTarget createFromParcel(Parcel source) {
            return new SmartspaceTarget(source);
        }

        @Override
        public SmartspaceTarget[] newArray(int size) {
            return new SmartspaceTarget[size];
        }
    };

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(this.mSmartspaceTargetId);
        dest.writeTypedObject(this.mHeaderAction, flags);
        dest.writeTypedObject(this.mBaseAction, flags);
        dest.writeLong(this.mCreationTimeMillis);
        dest.writeLong(this.mExpiryTimeMillis);
        dest.writeFloat(this.mScore);
        dest.writeTypedList(this.mActionChips);
        dest.writeTypedList(this.mIconGrid);
        dest.writeInt(this.mFeatureType);
        dest.writeBoolean(this.mSensitive);
        dest.writeBoolean(this.mShouldShowExpanded);
        dest.writeString(this.mSourceNotificationKey);
        dest.writeTypedObject(this.mComponentName, flags);
        dest.writeTypedObject(this.mUserHandle, flags);
        dest.writeString(this.mAssociatedSmartspaceTargetId);
        dest.writeTypedObject(this.mSliceUri, flags);
        dest.writeTypedObject(this.mWidgetId, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "SmartspaceTarget{"
                + "mSmartspaceTargetId='" + mSmartspaceTargetId + '\''
                + ", mHeaderAction=" + mHeaderAction
                + ", mBaseAction=" + mBaseAction
                + ", mCreationTimeMillis=" + mCreationTimeMillis
                + ", mExpiryTimeMillis=" + mExpiryTimeMillis
                + ", mScore=" + mScore
                + ", mActionChips=" + mActionChips
                + ", mIconGrid=" + mIconGrid
                + ", mFeatureType=" + mFeatureType
                + ", mSensitive=" + mSensitive
                + ", mShouldShowExpanded=" + mShouldShowExpanded
                + ", mSourceNotificationKey='" + mSourceNotificationKey + '\''
                + ", mComponentName=" + mComponentName
                + ", mUserHandle=" + mUserHandle
                + ", mAssociatedSmartspaceTargetId='" + mAssociatedSmartspaceTargetId + '\''
                + ", mSliceUri=" + mSliceUri
                + ", mWidgetId=" + mWidgetId
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SmartspaceTarget that = (SmartspaceTarget) o;
        return mCreationTimeMillis == that.mCreationTimeMillis
                && mExpiryTimeMillis == that.mExpiryTimeMillis
                && Float.compare(that.mScore, mScore) == 0
                && mFeatureType == that.mFeatureType
                && mSensitive == that.mSensitive
                && mShouldShowExpanded == that.mShouldShowExpanded
                && mSmartspaceTargetId.equals(that.mSmartspaceTargetId)
                && Objects.equals(mHeaderAction, that.mHeaderAction)
                && Objects.equals(mBaseAction, that.mBaseAction)
                && Objects.equals(mActionChips, that.mActionChips)
                && Objects.equals(mIconGrid, that.mIconGrid)
                && Objects.equals(mSourceNotificationKey, that.mSourceNotificationKey)
                && mComponentName.equals(that.mComponentName)
                && mUserHandle.equals(that.mUserHandle)
                && Objects.equals(mAssociatedSmartspaceTargetId,
                that.mAssociatedSmartspaceTargetId)
                && Objects.equals(mSliceUri, that.mSliceUri)
                && Objects.equals(mWidgetId, that.mWidgetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSmartspaceTargetId, mHeaderAction, mBaseAction, mCreationTimeMillis,
                mExpiryTimeMillis, mScore, mActionChips, mIconGrid, mFeatureType, mSensitive,
                mShouldShowExpanded, mSourceNotificationKey, mComponentName, mUserHandle,
                mAssociatedSmartspaceTargetId, mSliceUri, mWidgetId);
    }

    /**
     * A builder for {@link SmartspaceTarget} object.
     *
     * @hide
     */
    @SystemApi
    public static final class Builder {
        private final String mSmartspaceTargetId;
        private SmartspaceAction mHeaderAction;
        private SmartspaceAction mBaseAction;
        private long mCreationTimeMillis;
        private long mExpiryTimeMillis;
        private float mScore;
        private List<SmartspaceAction> mActionChips = new ArrayList<>();
        private List<SmartspaceAction> mIconGrid = new ArrayList<>();
        private int mFeatureType;
        private boolean mSensitive;
        private boolean mShouldShowExpanded;
        private String mSourceNotificationKey;
        private final ComponentName mComponentName;
        private final UserHandle mUserHandle;
        private String mAssociatedSmartspaceTargetId;
        private Uri mSliceUri;
        private AppWidgetProviderInfo mWidgetId;

        /**
         * A builder for {@link SmartspaceTarget}.
         *
         * @param smartspaceTargetId the id of this target
         * @param componentName      the componentName of this target
         * @param userHandle         the userHandle of this target
         */
        public Builder(@NonNull String smartspaceTargetId,
                @NonNull ComponentName componentName, @NonNull UserHandle userHandle) {
            this.mSmartspaceTargetId = smartspaceTargetId;
            this.mComponentName = componentName;
            this.mUserHandle = userHandle;
        }

        /**
         * Sets the header action.
         */
        @NonNull
        public Builder setHeaderAction(@NonNull SmartspaceAction headerAction) {
            this.mHeaderAction = headerAction;
            return this;
        }

        /**
         * Sets the base action.
         */
        @NonNull
        public Builder setBaseAction(@NonNull SmartspaceAction baseAction) {
            this.mBaseAction = baseAction;
            return this;
        }

        /**
         * Sets the creation time.
         */
        @NonNull
        public Builder setCreationTimeMillis(@NonNull long creationTimeMillis) {
            this.mCreationTimeMillis = creationTimeMillis;
            return this;
        }

        /**
         * Sets the expiration time.
         */
        @NonNull
        public Builder setExpiryTimeMillis(@NonNull long expiryTimeMillis) {
            this.mExpiryTimeMillis = expiryTimeMillis;
            return this;
        }

        /**
         * Sets the score.
         */
        @NonNull
        public Builder setScore(@NonNull float score) {
            this.mScore = score;
            return this;
        }

        /**
         * Sets the action chips.
         */
        @NonNull
        public Builder setActionChips(@NonNull List<SmartspaceAction> actionChips) {
            this.mActionChips = actionChips;
            return this;
        }

        /**
         * Sets the icon grid.
         */
        @NonNull
        public Builder setIconGrid(@NonNull List<SmartspaceAction> iconGrid) {
            this.mIconGrid = iconGrid;
            return this;
        }

        /**
         * Sets the feature type.
         */
        @NonNull
        public Builder setFeatureType(@NonNull int featureType) {
            this.mFeatureType = featureType;
            return this;
        }

        /**
         * Sets whether the contents are sensitive.
         */
        @NonNull
        public Builder setSensitive(@NonNull boolean sensitive) {
            this.mSensitive = sensitive;
            return this;
        }

        /**
         * Sets whether to show the card as expanded.
         */
        @NonNull
        public Builder setShouldShowExpanded(@NonNull boolean shouldShowExpanded) {
            this.mShouldShowExpanded = shouldShowExpanded;
            return this;
        }

        /**
         * Sets the source notification key.
         */
        @NonNull
        public Builder setSourceNotificationKey(@NonNull String sourceNotificationKey) {
            this.mSourceNotificationKey = sourceNotificationKey;
            return this;
        }

        /**
         * Sets the associated smartspace target id.
         */
        @NonNull
        public Builder setAssociatedSmartspaceTargetId(
                @NonNull String associatedSmartspaceTargetId) {
            this.mAssociatedSmartspaceTargetId = associatedSmartspaceTargetId;
            return this;
        }

        /**
         * Sets the slice uri.
         *
         * <p><b>NOTE: </b> If {@link mWidgetId} is also set, {@link mSliceUri} should be ignored.
         */
        @NonNull
        public Builder setSliceUri(@NonNull Uri sliceUri) {
            this.mSliceUri = sliceUri;
            return this;
        }

        /**
         * Sets the widget id.
         *
         * <p><b>NOTE: </b> If {@link mWidgetId} is set, all other @Nullable params should be
         * ignored.
         */
        @NonNull
        public Builder setWidgetId(@NonNull AppWidgetProviderInfo widgetId) {
            this.mWidgetId = widgetId;
            return this;
        }

        /**
         * Builds a new {@link SmartspaceTarget}.
         *
         * @throws IllegalStateException when non null fields are set as null.
         */
        @NonNull
        public SmartspaceTarget build() {
            if (mSmartspaceTargetId == null
                    || mComponentName == null
                    || mUserHandle == null) {
                throw new IllegalStateException("Please assign a value to all @NonNull args.");
            }
            return new SmartspaceTarget(mSmartspaceTargetId,
                    mHeaderAction, mBaseAction, mCreationTimeMillis, mExpiryTimeMillis, mScore,
                    mActionChips, mIconGrid, mFeatureType, mSensitive, mShouldShowExpanded,
                    mSourceNotificationKey, mComponentName, mUserHandle,
                    mAssociatedSmartspaceTargetId, mSliceUri, mWidgetId);
        }
    }
}
